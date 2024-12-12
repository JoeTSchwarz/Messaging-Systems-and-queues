import java.net.*;
// joe t. schwarz
public class UDPServerSocket extends DatagramSocket {
  /**
  Constructor
  @param port int Port number
  @Exception Exception thrown by JAVA
  */
  public UDPServerSocket(int port) throws Exception {
    super(port);
  }
  /**
  start accept to incomming message
  @return UDPSocket
  @Exception Exception thrown by JAVA or closed
  */
  public UDPSocket accept( ) throws Exception {
    if (closed) throw new Exception("UDPServerSocket is closed.");
    DatagramPacket pkt = new DatagramPacket(new byte[1024], 1024);
    this.receive(pkt); // wait for Client's message
    return new UDPSocket(pkt);
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
  //
  private volatile boolean closed = false;
}