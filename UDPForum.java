import java.io.*;
import java.awt.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import java.nio.file.*;
import java.awt.event.*;
import java.util.concurrent.*;
// Joe T. Schwarz (C)
public class UDPForum extends Thread implements ActionListener {
    private int port;
    private JTextField line;
    private JTextArea taLog;
    private UDPForum me = this;
    private JComboBox<String> jcb;
    private DefaultComboBoxModel<String> model;
    private String ufile = "ForumUserList.txt";
    private ExecutorService pool = Executors.	newCachedThreadPool();
    private volatile boolean closed = false, locked = false, soft = true;
    // JavaSWING...the chores
    public UDPForum(String title, int port) {
        this.port = port;
        JFrame frame = new JFrame(title);
        File file = new File(ufile);
        if (file.exists()) try {
          users = Collections.synchronizedList(new ArrayList<>(Files.readAllLines(file.toPath())));
        } catch (Exception e) { 
          JOptionPane.showMessageDialog( null, e.toString()); 
          System.exit(0);
        }
        this.start();
        //
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
          shutdown();
        });
        
        JButton butBAN = new JButton("BAN");
        butBAN.setForeground(Color.black);
        butBAN.setFocusable(false);
        butBAN.setFont(new Font("Times",Font.BOLD, 11));
        butBAN.addActionListener(a -> {
          String id = (String)jcb.getSelectedItem();
          if (!"ALL".equals(id)) try {
            String uID = cList.get(id).uID;
            String user[] = uID.split("!");
            int idx = users.indexOf(uID);
            users.set(idx, user[0]+"!"+(char)0x03+"!"+user[2]);
            StringBuilder sb = new StringBuilder(); // ignore ALL start at 1
            for (int i = 1, mx = users.size(); i < mx; ++i) sb.append(users.get(i)+"\n");
            while(locked) { // is locked ?
              TimeUnit.MILLISECONDS.sleep(50);
            }
            locked = true; // lock it  
            FileOutputStream fou = new FileOutputStream(ufile); // NOT extended
            fou.write((sb.toString()+"\n").getBytes());
            fou.flush();
            fou.close();
            locked = false;
            // update list first.
            updateList(((char)03+id+userList()).getBytes());
            synchronized(this) {
              chatters.remove(id);
              model.removeElement(id);
              uChatter uc = cList.remove(id);
              ipList.remove(uc.ip.toString()+":"+uc.port);
            }
            synchronized(taLog) {
              taLog.append(Authenticate.decrypt(user[0])+" or "+id+" is banned.\n");
            }
          } catch (Exception ex) { }         
        });
        pnl.add(butPRO);
        pnl.add(butEXIT);
        pnl.add(butBAN);

        taLog = new JTextArea(16, 42);
        taLog.append("UDPForum is open...\n"+
                     "SOFT: ONLY User-ID is banned (default).\n"+
                     "HARD: User-ID & Computer are banned.\n");
        taLog.setEditable(false);
        JPanel logPanel = new JPanel();
        taLog.setBackground(Color.lightGray);
        JScrollPane jTextArea = new JScrollPane(taLog);
        logPanel.setBorder(BorderFactory.createTitledBorder("Joe's UDPForum's Protocol"));
        taLog.setWrapStyleWord(true);
        jTextArea.setAutoscrolls(true);
        taLog.setLineWrap(true);
        logPanel.add(jTextArea);
      
        frame.add("North",lPanel);
        frame.add("Center",logPanel);
        frame.add("South",pnl);
          
        frame.pack();
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we){
              if (!closed) shutdown();
            }
        });
        frame.setVisible(true);
    }
    public void run() {
      try {
        udpServer = new DatagramSocket(port);
        while (!closed) {
          DatagramPacket pkt = new DatagramPacket(new byte[65001], 65001);
          udpServer.receive(pkt);
          pool.execute(() -> {
            chatter(pkt);
          });
        }
      } catch (Exception e) {
        if (!closed) e.printStackTrace();
      }           
      shutdown();
    }
    private DatagramSocket udpServer;
    private ConcurrentHashMap<String, String> ipList = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, uChatter> cList = new ConcurrentHashMap<>();
    private java.util.List<String> users = Collections.synchronizedList(new ArrayList<>(200));
    private java.util.List<String> chatters = Collections.synchronizedList(new ArrayList<>(200));
    // This is the UDPForum's slaves
    private void chatter(DatagramPacket pkt) {
      try {
        byte buf[];
        int port = pkt.getPort();
        InetAddress ia = pkt.getAddress();
        uChatter uc = null;
        String id = null;
        String ip_port = ia.toString()+":"+port;
        DatagramSocket ds = new DatagramSocket();
        String data = new String(pkt.getData(), 0, pkt.getLength());
        if (data.charAt(0) == (char)0x01) { // login/register/forgetPW
          String X = data.substring(2);
          String els[] = X.split("!");
          String uID = Authenticate.decrypt(els[0]);
          if (data.charAt(1) == (char)0x04) { // forget PW
            int idx = 0, mx = users.size();
            while (idx < mx) if (users.get(idx).startsWith(els[0])) break; else ++idx;
            if (idx < mx) {
              els = users.get(idx).split("!");
              buf = ("Your PW:"+Authenticate.decrypt(els[1])).getBytes();                                       
            } else {
              if (els[1].charAt(0) == (char)0x03) buf = "You are banned.".getBytes();
              else buf = ("Unknown ID:"+uID).getBytes();
            }
            ds.send(new DatagramPacket(buf, buf.length, ia, port));
            return;
          }
          id = "u"+String.format("%08X", (int)(System.nanoTime() & 0xFFFFFFFF));
          uc = new uChatter(ia, port, id, X);
          while(locked) { // is locked?
            TimeUnit.MILLISECONDS.sleep(50);
          }
          locked = true; // lock it
          // single profile: only user per computer
          // multiple profile: multiple users per computer
          if (data.charAt(1) == (char)0x03) { // register -> existed or banned ?
            for (String user:users) if (user.indexOf(els[0]) == 0 || !soft && user.indexOf(els[2]) > 0) {
              locked = false; // unlocked
              buf = ((char)0x02+uID+" existed or banned.").getBytes();
              ds.send(new DatagramPacket(buf, buf.length, ia, port));
              return;
            }
            users.add(uc.uID);
            // and save new chatter's ID + PW (both in encrypted format)
            FileOutputStream fou = new FileOutputStream(ufile, true); // extended
            fou.write((uc.uID+"\n").getBytes());
            fou.flush();
            fou.close();
          } else { // login
            boolean no = true;
            for (String user:users) if (user.equals(X)) {
              no = false;
              break;
            }
            if (no) {
              locked = false;
              buf = ((char)0x02+uID+" is banned or inavlid ID/Password.").getBytes();
              ds.send(new DatagramPacket(buf, buf.length, ia, port));
              return;
            }
          }
          locked = false;
          synchronized(this) {
            chatters.add(id);
            cList.put(id, uc);
            model.addElement(id);
            ipList.put(ip_port, id);
          }
          String hello = "Welcome "+uID+" with ID: "+id;
          updateList(((char)0x01+hello+userList()).getBytes());
          synchronized(taLog) {
            taLog.append(hello+"\n");
          }
          return;
        }
        id = ipList.get(ip_port);
        if (id == null) { // should not happen
          ds.send(new DatagramPacket(new byte[] {(byte)0x02}, 1, ia, port));
          return;
        } else uc = cList.get(id);
        if (data.charAt(0) == (char)0x02) {
          synchronized(this) {
            ipList.remove(ip_port);
            model.removeElement(id);
            chatters.remove(id);
            cList.remove(id);
          }
          updateList(userList().getBytes());
          synchronized(taLog) {
            taLog.append(id+" quitted.\n");
          }
          return;
        }
        int p = data.indexOf("!");
        String who = data.substring(0, p);
        data = data.replace(who+"!", "");
        if ("ALL".equals(who)) {
          synchronized(taLog) {
            taLog.append("To everyone: "+data+"\n");
          }
          if (chatters.size() > 0) {
            buf = ("@everyone: "+data).getBytes();
            while(locked) { // is locked?
              TimeUnit.MILLISECONDS.sleep(50);
            }
            locked = true; // lock it
            for (String chatter:chatters) {
              uc = cList.get(chatter);
              ds = new DatagramSocket();
              ds.send(new DatagramPacket(buf, buf.length, uc.ip, uc.port));
            }
            locked = false;
          }
        } else {
          synchronized(taLog) {
            taLog.append("From "+id+" to "+who+": "+data+"\n");
          }
          uc = cList.get(who);
          buf = ("<"+id+"> "+data).getBytes();
          while(locked) { // is locked?
            TimeUnit.MILLISECONDS.sleep(50);
          }
          locked = true; // lock it
          ds.send(new DatagramPacket(buf, buf.length, uc.ip, uc.port));
          locked = false;
        }
      } catch (Exception e) {
        //e.printStackTrace();
      }
      locked = false;
    }
    //
    private void updateList(byte[] buf) throws Exception {
      // send updated userList
      while(locked) { // is locked?
        TimeUnit.MILLISECONDS.sleep(50);
      }
      locked = true; // lock it
      for (String chatter:chatters) {
        uChatter uc = cList.get(chatter);
        (new DatagramSocket()).send(new DatagramPacket(buf, buf.length, uc.ip, uc.port));
      }
      locked = false;
      return;
    }
    //
    private synchronized String userList() {
      StringBuilder sb = new StringBuilder("<>ALL");
      if (chatters.size() > 0) for (String chatter:chatters) {
        uChatter uc = cList.get(chatter);
        if (uc != null) sb.append("!"+uc.id);
      }
      return sb.toString();
    }
    // reply to newcomer
    public void actionPerformed(ActionEvent a) {
        String s = line.getText().trim();
        if (s.length() == 0) return;
        try {
            String user = (String) jcb.getSelectedItem();
            if ("ALL".equals(user)) {
              if (chatters.size() > 0) {
                byte[] buf = ("@all: "+s).getBytes();
                for (String chatter:chatters) {
                  uChatter uc = cList.get(chatter);
                  DatagramSocket ds = new DatagramSocket();
                  ds.send(new DatagramPacket(buf, buf.length, uc.ip, uc.port));
                }
              }
              synchronized(taLog) {
                taLog.append("UDPForum to everyone: "+s+"\n");
              }
            } else {
              uChatter uc = cList.get(user);
              synchronized(taLog) {
                taLog.append("To "+user+": "+s+"\n");
              }
              byte buf[] = ("UDPForum to "+user+": "+s+"\n").getBytes();
              DatagramSocket ds = new DatagramSocket();
              ds.send(new DatagramPacket(buf, buf.length, uc.ip, uc.port));
            }
            jcb.removeActionListener(me);
            jcb.setSelectedIndex(0);
            jcb.addActionListener(me);
            line.setText("");
        } catch (Exception e) { }
    }
    //----------------------------------------------------------------------
    public void shutdown( ) {
        closed = true; //shutdown the server
        if (chatters.size() > 0) try {
          byte buf[] = { (byte)0x02 };
          for (String chatter:chatters) {
            uChatter u = cList.get(chatter);
            DatagramSocket ds = new DatagramSocket();
            ds.send(new DatagramPacket(buf, buf.length, u.ip, u.port));
          }
          TimeUnit.MILLISECONDS.sleep(50);
        } catch (Exception ex) { }
        if (udpServer != null) try {
            udpServer.close( );
            TimeUnit.MILLISECONDS.sleep(50);
        } catch (Exception e) { }
        pool.shutdownNow();
        System.exit(0);
    }
    //
    private class uChatter {
        public uChatter(InetAddress ip, int port, String id, String uID) {
            this.port = port;
            this.uID = uID;
            this.id = id;
            this.ip = ip;
        }
        //
        public InetAddress ip;
        public String id, uID;
        public int port;
    }
    //
    public static void main(String[] a) {
      if (a.length != 1) {
        JOptionPane.showMessageDialog( null, "Usage: java UDPForum HostName/IP:Port"); 
        System.exit(0);
      } else try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
            int port = Integer.parseInt(a[0]);
            UDPForum srv = new UDPForum("Joe's UDPForum for UDPClients", port);
      } catch (Exception e) {
           JOptionPane.showMessageDialog( null, "Can't start NimbusLookAndFeel or invalid port:"+a[0]);
           System.exit(0);
      }
    }
}