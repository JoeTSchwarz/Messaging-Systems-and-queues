import java.io.*;
import java.awt.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import java.nio.file.*;
import java.awt.event.*;
import java.util.concurrent.*;
// Joe T. Schwarz (C)
public class uForum extends JFrame implements ActionListener {
  private static final long serialVersionUID = 31L;
  private String ufile = "ForumUserList.txt";
  private DefaultComboBoxModel<String> model;
  private UDPServerSocket uServer;
  private JTextArea taLog;
  private JComboBox<String> jcb;
  private JTextField line;
  private ExecutorService pool = Executors.newFixedThreadPool(2048);
  private volatile boolean closed = false, locked = false, soft = true;
  private ConcurrentHashMap<String, pChatter> cList = new ConcurrentHashMap<>();
  private volatile java.util.List<String> users = Collections.synchronizedList(new ArrayList<>(200));
  private volatile java.util.List<String> chatters = Collections.synchronizedList(new ArrayList<>(200));
  private volatile java.util.List<UDPSocket> out = Collections.synchronizedList(new ArrayList<>(200));
  // JavaSWING...the chores
  public uForum(String title, final int port) {
    super(title);
    File file = new File(ufile);
    if (file.exists()) try {
      users = Collections.synchronizedList(new ArrayList<>(Files.readAllLines(file.toPath())));
    } catch (Exception e) { 
      JOptionPane.showMessageDialog( null, e.toString()); 
      System.exit(0);
    }
    pool.execute(() -> {
      try {
        uServer = new UDPServerSocket(port);        
        while (!closed) pool.execute(new aChatter(uServer.accept()));
      } catch (Exception e) {
        if (!closed) e.printStackTrace();
        System.exit(0);
      }    
    });
    JPanel lPanel = new JPanel();
    line = new JTextField(35);
    line.setEditable(true);
    line.setBackground(Color.green);
    line.addActionListener(this);   
    lPanel.add( new JLabel("To "));
    Vector<String> vec = new Vector<>();
    vec.add("ALL");
    //
    model = new DefaultComboBoxModel<>(vec);
    jcb = new JComboBox<>(model);
    jcb.setPreferredSize(new Dimension(100, 25));
    jcb.addActionListener(this); 
    lPanel.add(jcb);
    lPanel.add(line);
       
    JPanel pnl = new JPanel();
    pnl.setBackground(Color.pink); 
        
    JButton butPRO = new JButton("SOFT");
    butPRO.setForeground(Color.black);
    butPRO.setFocusable(false);
    butPRO.setFont(new Font("Times",Font.BOLD, 11));
    butPRO.addActionListener(a -> {
      soft = !soft;
      butPRO.setText(soft?"SOFT":"HARD");
    });
        
    JButton butEXIT = new JButton("EXIT");
    butEXIT.setForeground(Color.black);
    butEXIT.setFocusable(false);
    butEXIT.setFont(new Font("Times",Font.BOLD, 11));
    butEXIT.addActionListener(a -> {
      actionExit(out.size());
    });
     
    JButton butBAN = new JButton("BAN");
    butBAN.setForeground(Color.black);
    butBAN.setFocusable(false);
    butBAN.setFont(new Font("Times",Font.BOLD, 11));
    butBAN.addActionListener(a -> {
      String id = (String)jcb.getSelectedItem();
      if (!"ALL".equals(id)) try {
        pChatter chatter = cList.get(id);
        String user[] =  chatter.profile.split("!");
        int idx = users.indexOf( chatter.profile);
        // invalidate PW of this user
        users.set(idx, user[0]+"!"+(char)0x03+"!"+user[2]);
        StringBuilder sb = new StringBuilder();
        for (int i = 0, mx = users.size(); i < mx; ++i) sb.append(users.get(i)+"\n");
        while(locked) { // is locked ?
          TimeUnit.MICROSECONDS.sleep(50);
        }
        locked = true; // lock it  
        FileOutputStream fou = new FileOutputStream(ufile); // NOT extended
        fou.write((sb.toString()+"\n").getBytes());
        fou.flush();
        fou.close();
        locked = false;
        // close this chatter
        chatter.mySelf.me.send((char)0x02+id+" is banned.");
        chatter.mySelf.banned = true;
        chatter.mySelf.me.close();
        model.removeElement(id);
      } catch (Exception ex) { }         
    });
    pnl.add(butPRO);
    pnl.add(butEXIT);
    pnl.add(butBAN);

    taLog = new JTextArea(16, 42);
    taLog.append("uForum with UDPServerSocket is open...\n"+
                 "SOFT: ONLY User-ID is banned (default).\n"+
                 "HARD: User-ID & Computer are banned.\n");
    taLog.setEditable(false);
    JPanel logPanel = new JPanel();
    taLog.setBackground(Color.lightGray);
    JScrollPane jTextArea = new JScrollPane(taLog);
    logPanel.setBorder(BorderFactory.createTitledBorder("Joe's uForum's Protocol"));
    taLog.setWrapStyleWord(true);
    jTextArea.setAutoscrolls(true);
    taLog.setLineWrap(true);
    logPanel.add(jTextArea);
      
    add("North",lPanel);
    add("Center",logPanel);
    add("South",pnl);
          
    pack();
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent we){
        actionExit(out.size());
      }
    });
    setVisible(true);
  }
  // This is the uForum's slaves
  private class aChatter implements Runnable {
    public aChatter(UDPSocket me) {
      this.me = me;
    }
    public String id;
    public UDPSocket me;
    //
    private int mIdx;
    public boolean banned = false;
    public void run() {
      try {
        // create an anonymous ID
        id = "u"+String.format("%08X", (int)(System.nanoTime() & 0xFFFFFFFF));
        model.addElement(id);
        String uid = verifyMe();
        if (uid == null) {
          locked = false;
          me.close();
          return;
        }
        out.add(me);
        // is a valid member
        chatters.add(id);
        updateList( );      
        synchronized(taLog) {
          taLog.append(id+" ("+uid+") joins iForum...\n");
        }
        mIdx = chatters.indexOf(id);
        String iTalk = "<"+id+"> ";
        // waiting for uChat
        while (true) {
          byte[] bb = me.read( );
          if (bb[0] == (byte)0x02) {
            someoneQuit(mIdx);
            return;
          }
          // <id>message OR <id>#message
          if (bb[0] == (byte) '<') 
          for (int i = 1; i < bb.length; ++i) if (bb[i] == '>') {
            String who = new String(bb, 1, i-1);
            String txt = new String(bb, ++i, bb.length-i);
            if (who.charAt(0) == 'A') { // for the public
              synchronized(taLog) {
                taLog.append(iTalk+"to everyone: "+txt+"\n");
              }
              while(locked) { // is locked?
                TimeUnit.MICROSECONDS.sleep(50);
              }
              locked = true; // lock it
              for (int j = 0, s = chatters.size(); j < s; ++j)
              if (!id.equals(chatters.get(j))) try {
                (out.get(j)).send(iTalk+txt);
              } catch (Exception w) {
                 // someone quits
                someoneQuit(j);
              }
              locked = false;
            } else if (!who.equals(id)){
              int idx = chatters.indexOf(who);
              synchronized(taLog) {
                taLog.append(iTalk+"to <"+who+"> "+txt+"\n");
              }
              while(locked) { // is locked?
                TimeUnit.MICROSECONDS.sleep(50);
              }
              locked = true; // lock it
              try {
                (out.get(idx)).send(iTalk+txt);
              } catch (Exception w) {
                 // someone quits
                someoneQuit(idx);
              }
              locked = false;
            }
            break;
          }
        }
      } catch (Exception e) { }
      locked = false;
    }
    private void updateList( ) throws Exception {
      int s = chatters.size( );
      StringBuilder sb = new StringBuilder((char)0x01+"Whom?!ALL!");
      for (int i = 0; i < s; ++i) sb.append(chatters.get(i)+"!");
      byte[] bb = sb.toString().getBytes( );
      while(locked) { // is locked?
        TimeUnit.MICROSECONDS.sleep(50);
      }
      locked = true; // lock it
      for (int i = 0; i < s; ++i) {
        out.get(i).send(bb);
        TimeUnit.MICROSECONDS.sleep(50);
      }
      locked = false;
    }
    private synchronized void someoneQuit(int ix) throws Exception {
      String id = chatters.get(ix);
      UDPSocket us = out.get(ix);
      taLog.append(id+(banned?" is banned.":" quitted.\n"));
      model.removeElement(id);
      chatters.remove(ix);
      out.remove(ix);
      us.close();
      updateList( );
    }
    private String verifyMe( ) throws Exception {
      byte[] bb = me.read();
      String data = new String(bb);
      String X = data.substring(1);
      String els[] = X.split("!");
      //
      String uID = Authenticate.decrypt(els[0]);
      if (data.charAt(0) == (char)0x04) { // forget PW
        int idx = 0, mx = users.size();
        while (idx < mx) if (users.get(idx).startsWith(els[0])) break; else ++idx;
        if (idx < mx) {
          els = users.get(idx).split("!");
          data = "Your PW:"+Authenticate.decrypt(els[1]);                                       
        } else {
          if (els[1].charAt(0) == (char)0x03) data = (char)0x02+"You are banned.";
          else data = (char)0x02+"Unknown ID:"+uID;
        }
        me.send(data);
        return null;
      }
      // register
      if (data.charAt(0) == (char)0x03) {
        while(locked) { // is locked?
          TimeUnit.MICROSECONDS.sleep(50);
        }
        locked = true; // lock it
        for (String user:users) if (user.indexOf(els[0]) == 0 || !soft && user.indexOf(els[2]) > 0) {
          locked = false; // unlocked
          me.send((char)0x02+uID+" existed or is banned.");
          return null;
        }
        users.add(X);
        // and save new chatter's ID + PW (both in encrypted format)
        FileOutputStream fou = new FileOutputStream(ufile, true); // extended
        fou.write((X+"\n").getBytes());
        fou.flush();
        fou.close();
        locked = false;
        // send this id to chatter
        locked = false;
        cList.put(id, new pChatter(X, this));
        me.send("Welcome "+uID+". Your ID:"+id);
        return uID;
      } else { // it's a login
        for (String user:users) if (user.equals(X)) {
          locked = false;
          cList.put(id, new pChatter(X, this));
          me.send("Welcome "+uID+". Your ID:"+id);
          return uID;
        }
      }
      // unknown ID/PW
      synchronized(taLog) {
        taLog.append("An unknown intruder was kicked out...\n");
      }
      me.send((char)0x02+uID+" is banned or inavlid ID/Password."); 
      locked = false;
      return null;
    }
  }
  //
  private class pChatter {
    public pChatter(String profile, aChatter mySelf) {
      this.profile = profile;
      this.mySelf = mySelf;
    }
    public aChatter mySelf;
    public String profile;
  }
  //
  public void actionPerformed(ActionEvent a) {
    String s = line.getText().trim();
    if (a.getActionCommand().equals("EXIT")) actionExit(out.size());
    else if (s.length() > 0) {
      int mx = chatters.size();
      String user = (String) jcb.getSelectedItem();
      if (s.length() > 0 && mx > 0) try {
        if ("ALL".equals(user)) {
          String X = "@all: "+s;
          for (int i = 0; i < mx; ++i) out.get(i).send(X);
          synchronized(taLog) {
            taLog.append("uForum to everyone: "+s+"\n");
          }
        } else {
          out.get(chatters.indexOf(user)).send("uForum to <"+user+">: "+s);
          synchronized(taLog) {
            taLog.append("iForum to <"+user+">: "+s+"\n");
          }
        }
        jcb.removeActionListener(this);
        model.setSelectedItem("ALL");
        jcb.addActionListener(this);
        line.setText("");
     } catch (Exception ex) {
        synchronized(taLog) {
          taLog.append("iForum is unable to send\""+s+"\" to <"+user+">\n");
        }
      }
    }
  }
  //----------------------------------------------------------------------
  public void actionExit(int mx) {
    closed = true; //shutdown the server
    if (uServer != null) try {
      for (UDPSocket us:out) us.send((char)0x02+" uForum is down.");
        uServer.close();
        Thread.sleep(50);
    } catch (Exception e) { 
       e.printStackTrace();
    }
    pool.shutdownNow();
    System.exit(0);
  }
  //
  public static void main(String... args) throws Exception {
    UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
    if (args.length == 1) {
      new uForum("Joe's iForum", Integer.parseInt(args[0]));
    } else {
      JOptionPane.showMessageDialog( null, "Usage: java iForum portNumber"); 
      System.exit(0);
    }
  }
}
