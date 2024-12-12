import java.io.*;
import java.awt.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import java.nio.file.*;
import java.awt.event.*;
import java.util.concurrent.*;
// Joe T. Schwarz (C)
public class iForum extends JFrame implements ActionListener {
  private static final long serialVersionUID = 31L;
  private String ufile = "ForumUserList.txt";
  private DefaultComboBoxModel<String> model;
  private ServerSocket cServer;
  private JTextArea taLog;
  private int port;
  private JComboBox<String> jcb;
  private JTextField line;
  private ExecutorService pool = Executors.newFixedThreadPool(2048);
  private volatile boolean closed = false, locked = false, soft = true;
  private ConcurrentHashMap<String, pChatter> cList = new ConcurrentHashMap<>();
  private java.util.List<String> users = Collections.synchronizedList(new ArrayList<>(200));
  private java.util.List<String> chatters = Collections.synchronizedList(new ArrayList<>(200));
  private java.util.List<OutputStream> out = Collections.synchronizedList(new ArrayList<>(200));
  //
  public iForum(String title, int port) {
    super(title);
    this.port = port;
    File file = new File(ufile);
    if (file.exists()) try {
      users = Collections.synchronizedList(new ArrayList<>(Files.readAllLines(file.toPath())));
    } catch (Exception e) { 
      JOptionPane.showMessageDialog( null, e.toString()); 
      System.exit(0);
    }
    pool.execute(() -> {
      try {
        cServer = new ServerSocket(port);        
        while (!closed) pool.execute(new aChatter(cServer.accept()));
      } catch (Exception e) {
        if (!closed) e.printStackTrace();
        System.exit(0);
      }    
    });
    //
    line = new JTextField(35);
    line.addActionListener(this);
    line.setBackground(Color.green);
    Vector<String> vec = new Vector<>();
    vec.add("ALL");
    model = new DefaultComboBoxModel<>(vec);
    jcb = new JComboBox<>(model);
    jcb.setPreferredSize(new Dimension(100, 25));
    jcb.addActionListener(this);
    
    JPanel forum = new JPanel();
    forum.add( new JLabel("To "));
    forum.add(jcb);
    forum.add(line);
    jcb.addActionListener(this); 
    //
    JPanel pnl = new JPanel();
    pnl.setBackground(Color.pink); 
    
    JButton butPRO = new JButton("SOFT");
    butPRO.setForeground(Color.black);
    butPRO.setFocusable(false);
    butPRO.setFont(new Font("Times",Font.BOLD, 11));
    butPRO.addActionListener(a -> {
      soft = !soft;
      butPRO.setText(soft? "SOFT":"HARD");
    });
        
    JButton butEXIT = new JButton("EXIT");
    butEXIT.setForeground(Color.black);
    butEXIT.setFocusable(false);
    butEXIT.setFont(new Font("Times",Font.BOLD, 11));
    butEXIT.addActionListener(this);
    
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
          TimeUnit.MILLISECONDS.sleep(50);
        }
        locked = true; // lock it  
        FileOutputStream fou = new FileOutputStream(ufile); // NOT extended
        fou.write((sb.toString()+"\n").getBytes());
        fou.flush();
        fou.close();
        locked = false;
        // close this chatter
        chatter.mySelf.meO.write(((char)0x02+id+" is banned.").getBytes());
        chatter.mySelf.banned = true;
        chatter.mySelf.me.close();
        model.removeElement(id);
      } catch (Exception ex) { }         
    });
    pnl.add(butPRO);
    pnl.add(butEXIT);
    pnl.add(butBAN);

    GridBagLayout gbs = new GridBagLayout();
    pnl.setLayout(new GridBagLayout());
    GridBagConstraints cCenter = new GridBagConstraints();
    cCenter.anchor  = GridBagConstraints.CENTER;
    gbs.setConstraints(pnl, cCenter);
    
    taLog = new JTextArea(16, 42);
    taLog.append("iForum is open...\n"+
                 "SOFT: ONLY User-ID is banned (default).\n"+
                 "HARD: User-ID & Computer are banned.\n");
    taLog.setEditable(false);
    JPanel logPanel = new JPanel();
    taLog.setBackground(Color.lightGray);
    JScrollPane jTextArea = new JScrollPane(taLog); 
    logPanel.setBorder(BorderFactory.createTitledBorder("Joe's iForum's Protocol"));
    taLog.setWrapStyleWord(true);
    jTextArea.setAutoscrolls(true);
    taLog.setLineWrap(true);

    logPanel.setLayout(new BorderLayout());
    logPanel.add(jTextArea, BorderLayout.CENTER);
        
    add("North", forum);
    add("Center",logPanel);
    add("South",pnl);
    
    pack( );
    setVisible(true);
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent we) {
        actionExit();
      }
    });
  }
  //----------------------------------------------------------------------
  private class aChatter implements Runnable {
     public aChatter(Socket me) {
       this.me = me;
     }
     public Socket me;
     public String id;
     public OutputStream meO;
     public boolean banned = false;
     //
     private InputStream meI;
     private int mIdx;
     public void run( ) {
      try {
        // create an anonymous ID
        id = "p"+String.format("%08X", (int)(System.nanoTime() & 0xFFFFFFFF));
        model.addElement(id);
        String uID = verifyMe();
        if (uID == null) {
          closeMe();
          return;
        }
        // is a valid member
        chatters.add(id);
        out.add(meO);
        //
        updateList( );      
        synchronized(taLog) {
          taLog.append(id+"("+uID+") joins iForum...\n");
        }
        me.setTcpNoDelay(true);    
        mIdx = chatters.indexOf(id);
        String iTalk = "<"+id+"> ";
        while (true) {
          byte[] bb = new byte[2048];
          int b = meI.read(bb);
          if (b < 0 || bb[0] == (byte)0x02) {
            closeMe( ); // I quit...
            someoneQuit(mIdx);
            return;
          }
          // <id>message OR <id>#message
          if (bb[0] == (byte) '<') 
          for (int i = 1; i < b; ++i) if (bb[i] == '>') {
            String who = new String(bb, 1, i-1);
            String txt = new String(bb, ++i, b-i);
            if (who.charAt(0) == 'A') { // for the public
              synchronized(taLog) {
                taLog.append(iTalk+"to everyone: "+txt+"\n");
              }
              while(locked) { // is locked?
                TimeUnit.MILLISECONDS.sleep(50);
              }
              locked = true; // lock it
              for (int j = 0, s = chatters.size(); j < s; ++j)
              if (!id.equals(chatters.get(j))) {
                try {
                  (out.get(j)).write((iTalk+txt).getBytes());
                } catch (Exception w) {
                   // someone quits
                  someoneQuit(j);
                }
              }
              locked = false;
            } else if (!who.equals(id)){
              int idx = chatters.indexOf(who);
              synchronized(taLog) {
                taLog.append(iTalk+"to <"+who+"> "+txt+"\n");
              }
              while(locked) { // is locked?
                TimeUnit.MILLISECONDS.sleep(50);
              }
              locked = true; // lock it
              try {
                (out.get(idx)).write((iTalk+txt).getBytes());
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
      try {
        closeMe( );
         // I quit
        someoneQuit(mIdx);
      } catch (Exception x) { }
    }
    private void updateList( ) throws Exception {
      int s = chatters.size( );
      StringBuilder sb = new StringBuilder((char)0x01+"Whom?!ALL!");
      for (int i = 0; i < s; ++i) sb.append(chatters.get(i)+"!");
      byte[] bb = sb.toString().getBytes( );
      while(locked) { // is locked?
        TimeUnit.MILLISECONDS.sleep(50);
      }
      locked = true; // lock it
      for (int i = 0; i < s; ++i) {
        try {
          OutputStream o = out.get(i);
          o.write(bb);
          o.flush( );
        } catch (Exception x) { }
        TimeUnit.MILLISECONDS.sleep(10);
      }
      locked = false;
    }
    public void closeMe( ) throws Exception {
      locked = false;
      if (me != null) {
        meI.close();
        meO.close();
        me.close();
      }
      me = null;
    }
    private synchronized void someoneQuit(int ix) throws Exception {
      String id = chatters.get(ix);
      taLog.append(id+(banned?" is banned.":" quitted.\n"));
      model.removeElement(id);
      chatters.remove(ix);
      out.remove(ix);
      updateList( );
    }
    private String verifyMe( ) throws Exception {
      meO = me.getOutputStream();
      meI = me.getInputStream();
      byte[] bb = new byte[256];
      int n = meI.read(bb);
      String data = new String(bb, 0, n);
      String X = data.substring(1);
      String els[] = X.split("!");
      //
      String uID = Authenticate.decrypt(els[0]);
      if (data.charAt(0) == (char)0x04) { // forget PW
        byte buf[] = null; int idx = 0, mx = users.size();
        while (idx < mx) if (users.get(idx).startsWith(els[0])) break; else ++idx;
        if (idx < mx) {
          els = users.get(idx).split("!");
          buf = ("Your PW:"+Authenticate.decrypt(els[1])).getBytes();                                       
        } else {
          if (els[1].charAt(0) == (char)0x03) buf = ((char)0x02+"You are banned.").getBytes();
          else buf = ((char)0x02+"Unknown ID:"+uID).getBytes();
        }
        meO.write(buf);
        meO.flush();
        return null;
      }
      // register
      if (data.charAt(0) == (char)0x03) {
        while(locked) { // is locked?
          TimeUnit.MILLISECONDS.sleep(50);
        }
        locked = true; // lock it
        for (String user:users) if (user.indexOf(els[0]) == 0 || !soft && user.indexOf(els[2]) > 0) {
          locked = false; // unlocked
          meO.write(((char)0x02+uID+" existed or is banned.").getBytes());
          meO.flush();
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
        meO.write(("Welcome "+uID+". Your ID:"+id).getBytes());
        meO.flush();
        return uID;
      } else { // it's a login
        for (String user:users) if (user.equals(X)) {
          locked = false;
          cList.put(id, new pChatter(X, this));
          meO.write(("Welcome "+uID+". Your ID:"+id).getBytes());
          meO.flush();
          return uID;
        }
      }
      // unknown ID/PW
      synchronized(taLog) {
        taLog.append("An unknown intruder was kicked out...\n");
      }
      meO.write(((char)0x02+uID+" is banned or inavlid ID/Password.").getBytes()); 
      meO.flush();
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
    if (a.getActionCommand().equals("EXIT")) actionExit();
    else if (s.length() > 0) {
        int mx = chatters.size();
        String user = (String) jcb.getSelectedItem();
        if (s.length() > 0 && mx > 0) try {
            if ("ALL".equals(user)) {
              byte[] buf = ("@all: "+s).getBytes();
              for (int i = 0; i < mx; ++i) out.get(i).write(buf);
              synchronized(taLog) {
                taLog.append("iForum to everyone: "+s+"\n");
              }
            } else {
              out.get(chatters.indexOf(user)).write(("iForum to <"+user+">: "+s).getBytes());
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
  public void actionExit( ) {
     closed = true; //shutdown the server
     if (cServer != null) try {
       cServer.close();
       Thread.sleep(50);
     } catch (Exception e) { }
    pool.shutdownNow();
    System.exit(0);
  }
  //
  public static void main(String... args) throws Exception {
    UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
    if (args.length == 1) {
      new iForum("Joe's iForum", Integer.parseInt(args[0]));
    } else {
      JOptionPane.showMessageDialog( null, "Usage: java iForum Port"); 
      System.exit(0);
    }
  }
}
