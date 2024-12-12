import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
// Joe T. Schwarz
public class Authenticate extends JDialog {
  private static final long serialVersionUID = 32L;

  private boolean authenticated;
  private JPasswordField userPW;
  private JTextField userID;
  private int bType = 0;
  public Authenticate(JFrame me) {
    super(me, true);
    authenticated = false;
    setTitle("Authentication by Joe T. Schwarz");
    
    JPanel settingsPanel = new JPanel();
    settingsPanel.setBorder(BorderFactory.createTitledBorder("iForum Registrtation/Login"));
    GridBagLayout layout = new GridBagLayout();
    settingsPanel.setLayout(layout);
    
    JLabel msg = new JLabel("Case Sensitive", JLabel.CENTER);
    msg.setFont(new Font("Times",Font.BOLD,12));
    GridBagConstraints constraintE = new GridBagConstraints();
    constraintE.anchor = GridBagConstraints.CENTER;
    constraintE.gridwidth = GridBagConstraints.REMAINDER;
    layout.setConstraints(msg, constraintE); 
    settingsPanel.add(msg);
    
    JLabel usernameLabel = new JLabel("UserID ");
    constraintE = new GridBagConstraints();
    constraintE.anchor = GridBagConstraints.EAST;
    layout.setConstraints(usernameLabel, constraintE);
    settingsPanel.add(usernameLabel);
    
    userID = new JTextField(20);
    GridBagConstraints constraintW = new GridBagConstraints();
    constraintW.anchor = GridBagConstraints.WEST;
    constraintW.fill = GridBagConstraints.HORIZONTAL;
    constraintW.gridwidth = GridBagConstraints.REMAINDER;
    layout.setConstraints(userID, constraintW);
    settingsPanel.add(userID);

    JLabel passwordLabel = new JLabel("UserPassword ");
    layout.setConstraints(passwordLabel, constraintE);
    settingsPanel.add(passwordLabel);
    userPW = new JPasswordField(20);
    userPW.addActionListener(a -> {
      bType = 1;
      getData();
    });
    layout.setConstraints(userPW, constraintW);
    settingsPanel.add(userPW);
    // Setup buttons panel.
    JPanel buttonsPanel = new JPanel();
    JButton regBut = new JButton("REGISTER");
    regBut.setFont(new Font("Times",Font.BOLD,12));
    regBut.addActionListener(e->{
      getData();
    });
    regBut.setFocusPainted(false);
    JButton logBut = new JButton("LOGIN");
    logBut.setFont(new Font("Times",Font.BOLD,12));
    logBut.addActionListener(e->{
      bType = 1;
      getData();
    });
    logBut.setFocusPainted(false);
    JButton forBut = new JButton("FORGET PW");
    forBut.setFont(new Font("Times",Font.BOLD,12));
    forBut.addActionListener(e->{
      bType = 2;
      getData();
    });
    logBut.setFocusPainted(false);
    buttonsPanel.add(regBut);
    buttonsPanel.add(logBut);
    buttonsPanel.add(forBut);
    //
    addWindowListener(
        new WindowAdapter() {
          public void windowClosing(WindowEvent we){
            authenticated = false;
            bType = 3;
            dispose();
          }
        }
    );
    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(settingsPanel, BorderLayout.CENTER);
    getContentPane().add(buttonsPanel, BorderLayout.SOUTH);
    pack(); // Width/depth
    setVisible(true);
  }
  private String logID, logPW;
  //
  public void getData( ) {
  	logID = userID.getText( );
  	logPW = new String(userPW.getPassword( ));
    if (bType == 2) logPW = "??";
  	authenticated = true;
    dispose();
  }
  public boolean isRegis() {
  	  	return bType == 0;
  }
  public boolean isLogin() {
  	  	return bType == 1;
  }
  public boolean isForget() {
  	  	return bType == 2;
  }
  public boolean isCanceled() {
  	  	return bType == 3;
  }
  public boolean isAuthenticated( ){
  	  	return authenticated;
  }
  public String getRawID( ) {
  	  return logID;
  }
  public String getRawPW( ) {
  	  return logPW;
  }
  public String getEncryptedID( ) {
  	  return encrypt(logID);
  }
  public String getEncryptedPW( ) {
  	  return encrypt(logPW);
  }
  public String getEncryptedDID( ) {
  	  return encrypt(getDiskID());
  }
  // Simple encryption by adding to i+1ength
  public static String encrypt(String s) {
  	  if (s == null) return null;
  	  byte[] b = s.getBytes( );
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < b.length; ++i) sb.append(String.format("%02X", (int)(b[i]+b.length+i)));
      return sb.toString();
  }
  // Simple decryption by subtracting to i+1ength
  public static String decrypt(String s) {
  	  if (s == null) return null;
      byte[] b = new byte[s.length()/2];
      for (int i = 0, j = 0; i < b.length; ++i, j +=2) {
        b[i] = (byte)(parseHex(s.substring(j, j+2))-b.length-i);
      }
  	  return new String(b);
  }
  private static int parseHex(String hex) {
    byte[] h = hex.getBytes();
    return ((h[0] < (byte)'A'? 0:9)+(h[0] & 0x0F))*16+(h[1] < (byte)'A'? 0:9)+(h[1] & 0x0F);
  }
  // as an unique id for a computer
  // Window: wmic command for cpu id : wmic cpu get ProcessorID
  // Linux (e.g.UBUNTU): hdparm-I / dev / sda | grep Serial
  public static String getDiskID() {
    try {
      Process p = (System.getProperty("os.name").equals("Linux"))?
        Runtime.getRuntime().exec ("hdparm-I / dev / sda | grep Serial"):
        Runtime.getRuntime().exec(new String[] { "wmic", "bios", "get", "serialnumber" });
      p.getOutputStream().close();
      java.util.Scanner sc = new java.util.Scanner(p.getInputStream());
      String property = sc.next();
      String diskID = sc.next();
      if (diskID != null) return diskID;
    } catch (Exception e) { }
    return "??";
  }
}
