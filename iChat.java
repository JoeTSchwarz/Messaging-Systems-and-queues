import java.io.*;
import java.awt.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import java.awt.event.*;
// Joe T. Schwarz
public class iChat extends JFrame implements ActionListener {
  private static final long serialVersionUID = 30L;
  private iChat me = this;
  private Socket soc;
  private JTextField line;
  private JTextArea taLog;
  private JComboBox<String> talkers;
  private volatile boolean go = true;
  private String isMe;
  private InputStream inp;
  private OutputStream out;
  //
  public iChat(String title, String hostPort) {
    super(title);
    JPanel pnl = new JPanel();
    pnl.setBackground(Color.pink);    
    GridBagLayout gbs = new GridBagLayout();
    pnl.setLayout(new GridBagLayout());
    GridBagConstraints cCenter = new GridBagConstraints();
    cCenter.anchor  = GridBagConstraints.CENTER;
    gbs.setConstraints(pnl,cCenter);
    
    JLabel jlab = new JLabel("  iChat ");
    line = new JTextField();
    line.setEditable(true);
    line.setBackground(Color.green);
    JPanel lPanel = new JPanel();
    lPanel.setLayout(new BorderLayout());
    lPanel.add(jlab, BorderLayout.WEST);
    lPanel.add(line, BorderLayout.CENTER);
    
    taLog = new JTextArea("You speak to a participant or ALL:\n"+
          "1. Write your message then\n"+
          "2. Just pick the ID you want to chat with\n"+
          "3. or ALL to everyone online.\n");
    taLog.setBackground(Color.lightGray);
    taLog.setEditable(false);
    JPanel logPanel = new JPanel();
    JScrollPane jTextArea = new JScrollPane(taLog); 
    logPanel.setBorder(BorderFactory.createTitledBorder("Discussion Protocol..."));
    taLog.setWrapStyleWord(true);
    jTextArea.setAutoscrolls(true);
    taLog.setLineWrap(true);

    logPanel.setLayout(new BorderLayout());
    logPanel.add(jTextArea, BorderLayout.CENTER);
    
    talkers = new JComboBox<>();
    talkers.setBackground(Color.pink);
    talkers.setFont(new Font("Times",Font.BOLD, 11));
    talkers.addActionListener(this);
    try {  // login and get iForum-ID
      int p = hostPort.indexOf(':');
      String host = hostPort.substring(0, p);
      int port = Integer.parseInt(hostPort.substring(p+1, hostPort.length()));
      soc = new Socket(host, port);
      out = soc.getOutputStream( );
      inp = soc.getInputStream( );
      // verify ID & Password 
      boolean closed = false;
      byte[] bb = new byte[512];
      Authenticate au = new Authenticate(this);
      if (au.isCanceled()) System.exit(0);
      if (au.isLogin())  { // login
        out.write(((char)0x02+au.getEncryptedID()+"!"+au.getEncryptedPW()+"!"+au.getEncryptedDID()).getBytes());
      } else if (au.isRegis()) { // register
        out.write(((char)0x03+au.getEncryptedID()+"!"+au.getEncryptedPW()+"!"+au.getEncryptedDID()).getBytes());
      } else { // forget PW
        out.write(((char)0x04+au.getEncryptedID()+"!"+au.getEncryptedPW()+"!"+au.getEncryptedDID()).getBytes());
      }
      out.flush();
      byte[] rep = new byte[128]; 
      int n = inp.read(rep);
      if (n < 0 || rep[0] == (byte)0x02) {
        actionExit(new String(rep, 1, n));
      }
      String msg = new String(rep, 0, n);
      isMe = msg.substring(msg.indexOf(" ID:")+4);
      (new Listener( )).start();  
      synchronized(taLog) {
        taLog.append(msg+"\n");
      }
    } catch (Exception y) {
      JOptionPane.showMessageDialog(this, "Unable to find iForum "+hostPort); 
      System.exit(0);
    }
    addWindowListener(
      new WindowAdapter() {
        public void windowClosing(WindowEvent we){
          actionExit(null);
        }
      }
    );
    //
    pnl.add(new JLabel("iChat with"));
    pnl.add(talkers);
           
    add("North",lPanel);
    add("Center",logPanel);
    add("South",pnl);
    // start Listener
    setSize(550, 400);
    setVisible(true);    
  }
  public void actionExit(String msg) {
     go = false;
     try {
       if (msg == null) {
        out.write(0x02); // say Goodbye
        out.flush( );
      } else JOptionPane.showMessageDialog(this, msg); 
      inp.close( );
      out.close( );
      soc.close( );
    } catch (Exception e) { }
    System.exit(0);
  }
  private class Listener extends Thread {
     public Listener( ) { }
     public void run( ) {
       try {
         while (go) {
          byte[] rep = new byte[65536];  
           int n = inp.read(rep);
          if (n < 0) {
            actionExit("Forum is probably closed!");
            return;
          }
          if (rep[0] == (byte)0x01) {
            // new iForum Chatter List
            SwingUtilities.invokeLater(() -> {
              talkers.removeAllItems( );
              talkers.removeActionListener(me);
              String[] u = (new String(rep,1,n)).trim().replace(isMe+"!","").split("!");
              for (int i = 0; i < u.length; ++i) {
                if (u[i].length() > 0) talkers.addItem(u[i]);
              }
              talkers.setSelectedIndex(0);
              talkers.addActionListener(me);
            });
          } else {
            String msg = new String(rep, 0, n);
            if (msg.charAt(0) == (char)0x02) actionExit( msg.substring(1));
            synchronized(taLog) {
              taLog.append(msg+"\n");
            }
          }
        }
      } catch (Exception x) {
        //x.printStackTrace();
        actionExit("Forum is probably closed!");
      }
     }
  }
  public void actionPerformed(ActionEvent ev) {
       String talk = line.getText( ).trim( );
      if (talk.length() > 0 && talkers.getSelectedIndex() > 0) try {
         String whom = (String)talkers.getSelectedItem();
        out.write(("<"+whom+">"+talk).getBytes());
        out.flush( );
        //
        synchronized(taLog) {
          taLog.append((whom.charAt(0) != 'A'? "iChat with "+whom+": \"":
                                               "iChat with everyone: \"")+
                        talk+"\"\n");
        }
        talkers.removeActionListener(me);
        talkers.setSelectedIndex(0);
        talkers.addActionListener(me);
      } catch (Exception ex) { }
       line.setText("");
  }
  public static void main(String... args) throws Exception {
    UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
    if (args.length == 1) {
      new iChat("Joe's iChat", args[0]);
    } else {
      JOptionPane.showMessageDialog( null, "Usage: java iChat HostName:Port"); 
      System.exit(0);
    }
  }
}
