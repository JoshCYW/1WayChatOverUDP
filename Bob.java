import java.net.*;
import java.nio.*;
import java.io.*;
import java.util.zip.CRC32;


class Bob {
  private int seqNum = 1;
  DatagramSocket socket;
  
  public static void main(String[] args) throws Exception {
    // Do not modify this method
    if (args.length != 1) {
      System.out.println("Usage: java Bob <port>");
      System.exit(1);
    }
    new Bob(Integer.parseInt(args[0]));
  }
  
  public Bob(int port) throws Exception {
    socket = new DatagramSocket(port);
    BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream("output", false));
    
    while(true) {
      byte[] buffer = new byte[512];
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      socket.receive(packet);
      ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData());
      
      long receivedCheckSum = byteBuffer.getLong(0);
      double type = byteBuffer.getDouble(8);
      int rcvSeqNum = byteBuffer.getInt(16);
      byte[] data = new byte[byteBuffer.remaining() - 20];
      for(int i = 0; i < byteBuffer.remaining() - 20; i++) {
        data[i] = byteBuffer.get(i+20); 
      }
      ByteBuffer finalBuffer = ByteBuffer.allocate(data.length + 20);
      finalBuffer.putDouble(type);
      finalBuffer.putInt(rcvSeqNum);
      finalBuffer.put(data);
      long checkSum = checkSum(finalBuffer.array()); 
      if(receivedCheckSum == checkSum) {
        if(rcvSeqNum != seqNum) { 
          seqNum = rcvSeqNum;
          if (type != -1.0 && type != -2.0 && type > 0) {
            byte[] finalData = new byte[(int)type];
            for(int i = 0; i < type ; i++) {
              finalData[i] = byteBuffer.get(i+20);
            }
            try {
              output.write(finalData,0,finalData.length);
              output.flush();
              output.close();
            }
            catch (IOException e) {
              output = new BufferedOutputStream(new FileOutputStream("output", false));
              output.write(finalData,0,finalData.length);
              output.flush();
              output.close();
            }
          }
          else if(type == -2.0) {
            String message = new String(data);
            printMessage(message);
          }
          else if(type == -1.0) {
            try {
              output.write(data,0,data.length);
              output.flush();
            }
            catch (IOException e) {
              output = new BufferedOutputStream(new FileOutputStream("output", false));
              output.write(data,0,data.length);
              output.flush();
            }
          }
        }      
      }
      ACK(socket, packet.getAddress(), packet.getPort());
    }
  }
  
  
  public void printMessage(String message) {
    // Do not modify this method
    // Call me to print out the messages!
    System.out.println(message);
  }
  
  public long checkSum(byte[] packet) {
    CRC32 crc = new CRC32();
    crc.update(packet);
    return crc.getValue();
  }
  
  public void ACK(DatagramSocket socket, InetAddress address, int port) throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(512);
    ByteBuffer seqNumber = ByteBuffer.allocate(4);
    seqNumber.putInt(seqNum);
    byteBuffer.putLong(checkSum(seqNumber.array()));
    byteBuffer.putInt(seqNum);
    byte[] data = byteBuffer.array();
    DatagramPacket ack = new DatagramPacket(data, data.length, address, port);
    socket.send(ack);
  }
}