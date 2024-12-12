# Messaging-Systems-and-queues
I will show you three different implementations of messaging systems and queues in the form of three different chat applications:
- TCP/IP with Socket and ServerSocket: P2P and PubSub.
- UDP/IP with DatagramSocket: P2P and PubSub.
- UDP/IP with self-implemented UDPSocket and UDPServerSocket: P2P and PubSub.

The implementations do not use a medium to store the messages, but an online area (here: JTextArea) to display the messages in real time.
- The message/queue servers iForum (ServerSocket), uForum (implemented UDPServerSocket) and UDPForum (as thread and DatagramSocket). All forums also serve as P2P or PubSub.
- Each participant can be either a P2P participant or a PubSub consumer/producer as iChat (Socket), uChat (implemented UDPSocket) and UDPChatter (DatagramSocket). As P2P, each participant just needs to select another participant from a list provided by the mentioned forums or as a publisher if the message is sent to ALL.
  Die Apps sind selbsterklärend.

The apps are self-explanatory.
Note: The difference between the SOFT and HARD option of iForum/uForum and UDPForum is:
- SOFT: If a participant is banned, they can "re-register" using a different user ID.
- HARD: If a participant is banned, their ID and computer are locked. They can only "re-register" using a different computer.
