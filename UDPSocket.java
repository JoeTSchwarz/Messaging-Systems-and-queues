import java.net.*;
import java.util.concurrent.TimeUnit;
// joe t. schwarz
public class UDPSocket extends DatagramSocket {
  /**
  Constructor - instantiated by Client app -
  @param port int Port number
  @param hostName String or IP
  @Exception Exception thrown by JAVA
  */
  public UDPSocket(int port, String hostName) throws Exception {
    super();
    calibrate(new DatagramPacket(new byte[256], 256, InetAddress.getByName(hostName), port));
  }
  /**
  Constructor - instantiated by UDPSocketServer accept -
  @param port int Port number
  @param iaddr InetAddress of this host server
  @Exception Exception thrown by JAVA
  */
  public UDPSocket(DatagramPacket pkt) throws Exception {
    super();
    port = pkt.getPort();
    ia = pkt.getAddress();
    send(pkt); // send this pkt to client   
    TimeUnit.MILLISECONDS.sleep(10); // and wait for draining...
  }
  /**
  send
  @param msg String. If length &gt, 65000 bytes will be truncated to 65000 bytes
  @Exception Exception thrown by JAVA
  */
  public void send(String msg) throws Exception {
    if (closed) throw new Exception("UDPSocket is closed.");
    send(new DatagramPacket(msg.getBytes(), msg.length(), ia, port));
    TimeUnit.MILLISECONDS.sleep(10); // wait for draining
  }
  /**
  send
  @param msg byte array. If length &gt, 65000 bytes will be truncated to 65000 bytes
  @Exception Exception thrown by JAVA
  */
  public void send(byte[] msg) throws Exception {
    if (closed) throw new Exception("UDPSocket is closed.");
    send(new DatagramPacket(msg, msg.length, ia, port));
    TimeUnit.MILLISECONDS.sleep(10);
  }
  /**
  send
  @param msg byte array.
  @param idx int, starting index
  @param len int, length. If length &gt, 65000 bytes will be truncated to 65000 bytes
  @Exception Exception thrown by JAVA
  */
  public void send(byte[] msg, int idx, int len) throws Exception {
    if (closed) throw new Exception("UDPSocket is closed.");
    if (len > 65000) len = 65000;
    byte[] bb = new byte[len];
    System.arraycopy(msg, idx, bb, 0, len);
    send(new DatagramPacket(bb, len, ia, port));
    TimeUnit.MILLISECONDS.sleep(10); // wait for draining
  }
  /**
  read
  @return byte array
  @Exception Exception thrown by JAVA
  */
  public byte[] read() throws Exception {
    DatagramPacket pkt = new DatagramPacket(new byte[65000], 65000);
    receive(pkt);
    byte buf[] = new byte[pkt.getLength()];
    System.arraycopy(pkt.getData(), 0, buf, 0, buf.length);
    return buf;
  }
  /**
  read
  @param buf byte array
  @return int Number of read bytes
  @Exception Exception thrown by JAVA
  */
  public int read(byte[] buf) throws Exception {
    DatagramPacket pkt = new DatagramPacket(new byte[65000], 65000);
    receive(pkt);
    int n = pkt.getLength();
    if (n > buf.length) n = buf.length;
    System.arraycopy(pkt.getData(), 0, buf, 0, n);
    return n;
  }
  /**
  read
  @param buf byte array
  @param off int starting offset
  @param len int length of bytes
  @return int Number of read bytes
  @Exception Exception thrown by JAVA
  */
  public int read(byte[] buf, int off, int len) throws Exception {
    if (off < 0 || off > buf.length) throw new Exception("Invalid off.");
    DatagramPacket pkt = new DatagramPacket(new byte[65000], 65000);
    receive(pkt);
    int n = pkt.getLength();
    if (n > len) n = len;
    System.arraycopy(pkt.getData(), 0, buf, off, n);
    return n;
  }
  /**
  readPacket
  @return DatagramPacket
  @Exception Exception thrown by JAVA
  */
  public DatagramPacket readPacket() throws Exception {
    DatagramPacket pkt = new DatagramPacket(new byte[65000], 65000);
    receive(pkt);
    int n = pkt.getLength();
    if (n >= 65000) return pkt;
    DatagramPacket p = new DatagramPacket(new byte[n], n);
    p.setData(pkt.getData());
    return p;
  }
  /**
  disconnect
  */
  public void disconnect() {
    super.disconnect();
    closed = true;
  }  
  /**
  close
  */
  public void close() {
    super.close();
    closed = true;
  }
  /**
  getInetAddress
  @return InetAddress of this UDPSocket
  */
  public InetAddress getInetAddress() {
    return ia;
  }
  /**
  getPort
  @return int port of this UDPSocket
  */
  public int getPort() {
    return port;
  }
  private void calibrate(DatagramPacket pkt) throws Exception {
    send(pkt); // ping the host to get real InetAddress and port
    TimeUnit.MILLISECONDS.sleep(10); // wait for draining
    // then wait for reply...
    receive(pkt);
    ia = pkt.getAddress();
    port = pkt.getPort();
  }  
  //
  private int port;
  private InetAddress ia;
  private UDPSocket me = this;
  private volatile boolean closed = false;
}