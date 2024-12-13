import java.io.*;
import java.awt.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import java.awt.event.*;
// Joe T. Schwarz
public class uChat extends JFrame implements ActionListener {
	private static final long serialVersionUID = 30L;
  private uChat me = this;
	private UDPSocket soc;
	private JTextField line;
	private JTextArea taLog;
	private JComboBox<String> talkers;
  private volatile boolean go = true;
  private String isMe;
  private InputStream inp;
  private OutputStream out;
  //
	public uChat(String title, String hostPort) {
		super(title);
		JPanel pnl = new JPanel();
		pnl.setBackground(Color.pink);		
		GridBagLayout gbs = new GridBagLayout();
		pnl.setLayout(new GridBagLayout());
		GridBagConstraints cCenter = new GridBagConstraints();
		cCenter.anchor  = GridBagConstraints.CENTER;
		gbs.setConstraints(pnl,cCenter);
		
		JLabel jlab = new JLabel("  uChat ");
		line = new JTextField(30);
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
		try {	// login and get uForum-ID
      Authenticate au = new Authenticate(this);
			if (au.isCanceled()) System.exit(0);
	  	int p = hostPort.indexOf(':');
			boolean closed = false;
      soc = new UDPSocket(Integer.parseInt(hostPort.substring(p+1, hostPort.length())),
                          hostPort.substring(0, p)
                         );
      if (au.isLogin())  { // login
        soc.send((char)0x02+au.getEncryptedID()+"!"+au.getEncryptedPW()+"!"+au.getEncryptedDID());
			} else if (au.isRegis()) { // register
        soc.send((char)0x03+au.getEncryptedID()+"!"+au.getEncryptedPW()+"!"+au.getEncryptedDID());
      } else { // forget PW
        soc.send((char)0x04+au.getEncryptedID()+"!"+au.getEncryptedPW()+"!"+au.getEncryptedDID());
      }
      byte[] rep = soc.read();
      String msg = new String(rep, 0, rep.length);
      if (rep[0] == (byte)0x02) {
        actionExit(msg.substring(1));
      }
      isMe = msg.substring(msg.indexOf(" ID:")+4);
			(new Listener( )).start();	
		  synchronized(taLog) {
				taLog.append(msg+"\n");
			}
		} catch (Exception y) {
      y.printStackTrace();
			JOptionPane.showMessageDialog(this, "Unable to find uForum "+hostPort); 
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
    pnl.add(new JLabel("uChat with"));
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
      if (msg == null) soc.send(""+(char)0x02); // say Goodbye
      else JOptionPane.showMessageDialog(this, msg); 
      soc.close( );
    } catch (Exception e) { 
      e.printStackTrace();
    }    
		System.exit(0);
  }
  private class Listener extends Thread {
   	public Listener( ) { }
   	public void run( ) {
   		try {
   			while (go) {
          byte[] rep = soc.read();
					if (rep.length == 0) { // just for sure
						actionExit("Forum is probably closed!");
						return;
					}
					if (rep[0] == (byte)0x01) {
            // new iForum Chatter List
            SwingUtilities.invokeLater(() -> {
              talkers.removeAllItems( );
              talkers.removeActionListener(me);
              String[] u = (new String(rep, 1, rep.length-1)).trim().replace(isMe+"!","").split("!");
              for (int i = 0; i < u.length; ++i) {
                if (u[i].length() > 0) talkers.addItem(u[i]);
              }
              talkers.setSelectedIndex(0);
              talkers.addActionListener(me);
            });
					} else {
            String msg = new String(rep, 0, rep.length);
            if (rep[0] == (byte)0x02) actionExit(msg.substring(1));
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
        soc.send("<"+whom+">"+talk);
        //
        synchronized(taLog) {
          taLog.append((whom.charAt(0) != 'A'? "uChat with "+whom+": \"":
                                               "uChat with everyone: \"")+
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
      new uChat("Joe's uChat", args[0]);
		} else {
			JOptionPane.showMessageDialog( null, "Usage: java uChat HostName:Port"); 
			System.exit(0);
		}
	}
}
