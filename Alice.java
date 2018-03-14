import java.util.Arrays;
import java.util.Scanner;
import java.util.zip.CRC32;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;




class Alice {
  private int seqNum = 0;
  private DatagramSocket socket;
  int indexTracker = 492;
  int lastSent = 0;
  Long lengthOfFile;
  Boolean isLastPacket = false;
  
  public static void main(String[] args) throws Exception {
    // Do not modify this method
    if (args.length != 2) {
      System.out.println("Usage: java Alice <host> <unreliNetPort>");
      System.exit(1);
    }
    InetAddress address = InetAddress.getByName(args[0]);
    new Alice(address, Integer.parseInt(args[1]));
  }
  
  public Alice(InetAddress address, int port) throws Exception {
    // Do not modify this method
    socket = new DatagramSocket();
    socket.setSoTimeout(100);
    
    Scanner sc = new Scanner(System.in);
    while (sc.hasNextLine()) {
      String line = sc.nextLine();
      handleLine(line, socket, address, port);
      // Sleep a bit. Otherwise (if we type very very fast)
      // sunfire might get so busy that it actually drops UDP packets.
      Thread.sleep(20);
    }
    socket.close();
  }
  
  public void handleLine(String line, DatagramSocket socket, InetAddress address, int port) throws Exception {
    // Do not modify this method
    if (line.startsWith("/send ")) {
      String path = line.substring("/send ".length());
      System.err.println("Sending file: " + path);
      try {
        File file = new File(path);
        if (!(file.isFile() && file.canRead())) {
          System.out.println("Path is not a file or not readable: " + path);
          return;
        }
      } catch (Exception e) {
        System.out.println("Could not read " + path);
        return;
      }
      sendFile(path, socket, address, port);
      System.err.println("Sent file.");
    } else {
      if (line.length() > 450) {
        System.out.println("Your message is too long to be sent in a single packet. Rejected.");
        return;
      }
      sendMessage(line, socket, address, port);
    }
  }
  
  public void sendFile(String path, DatagramSocket socket, InetAddress address, int port) throws Exception {
    double type = -1.0;
    File file = new File(path);
    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
    lengthOfFile = file.length();
    isLastPacket = false;
    while(!isLastPacket) {
      if(lengthOfFile < 492 || indexTracker > lengthOfFile) { 
        type = lengthOfFile - lastSent;
        byte[] finalPacket = new byte[492];  
        int bytesRead = bis.read(finalPacket);
        ByteBuffer byteBuffer = ByteBuffer.allocate(512);
        byteBuffer.putDouble(type);
        byteBuffer.putInt(seqNum);
        byteBuffer.put(finalPacket);
        long checkSum = checkSum(byteBuffer.array());
        ByteBuffer finalBuffer  = ByteBuffer.allocate(512);
        finalBuffer.putLong(checkSum);
        finalBuffer.putDouble(type);
        finalBuffer.putInt(seqNum);
        finalBuffer.put(finalPacket);
        byte[] fileData = finalBuffer.array();
        DatagramPacket sentPacket = new DatagramPacket(fileData, fileData.length, address, port);
        socket.send(sentPacket);
        ACK(sentPacket, socket, address, port, type);
        indexTracker = 492;
        lastSent = 0;
        isLastPacket = true;
      }
      else {
        byte[] componentData = new byte[492];
        int bytesRead = bis.read(componentData);
        
        ByteBuffer byteBuffer = ByteBuffer.allocate(512);
        byteBuffer.putDouble(type);
        byteBuffer.putInt(seqNum);
        byteBuffer.put(componentData);
        long checkSum = checkSum(byteBuffer.array());
        ByteBuffer finalBuffer  = ByteBuffer.allocate(512);
        finalBuffer.putLong(checkSum);
        finalBuffer.putDouble(type);
        finalBuffer.putInt(seqNum);
        finalBuffer.put(componentData);
        byte[] fileData = finalBuffer.array();
        DatagramPacket sentPacket = new DatagramPacket(fileData, fileData.length, address, port);
        socket.send(sentPacket);
        ACK(sentPacket, socket, address, port, type);
      }
    }
    
  }                          
  
  
  
  
  public void sendMessage(String message, DatagramSocket socket, InetAddress address, int port) throws Exception {
    double type = -2.0;
    ByteBuffer byteBuffer = ByteBuffer.allocate(512);
    byteBuffer.putDouble(type);
    byteBuffer.putInt(seqNum);
    byteBuffer.put(message.getBytes());
    long checkSum = checkSum(byteBuffer.array());
    ByteBuffer finalBuffer  = ByteBuffer.allocate(512);
    finalBuffer.putLong(checkSum);
    finalBuffer.putDouble(type);
    finalBuffer.putInt(seqNum);
    finalBuffer.put(message.getBytes());
    byte[] messageData = finalBuffer.array();
    DatagramPacket sentPacket = new DatagramPacket(messageData, messageData.length, address, port);
    socket.send(sentPacket);
    ACK(sentPacket, socket, address, port, type);
  }
  
  public void ACK(DatagramPacket sentPacket, DatagramSocket socket, InetAddress address, int port, double type) throws Exception {
    try{
      socket.setSoTimeout(100);
      byte[] rcvBuffer = new byte[512];
      DatagramPacket receivedPacket = new DatagramPacket(rcvBuffer, rcvBuffer.length);
      socket.receive(receivedPacket);
      ByteBuffer byteBuffer = ByteBuffer.wrap(receivedPacket.getData());
      long checkSum = byteBuffer.getLong(); 
      int ackSeqNum = byteBuffer.getInt(); 
      ByteBuffer forSeqNum = ByteBuffer.allocate(4);
      forSeqNum.putInt(ackSeqNum);  
      long receivedCheckSum = checkSum(forSeqNum.array()); 
      if(checkSum == receivedCheckSum) {
        if(seqNum == ackSeqNum) {            
          if(seqNum == 0)                
            seqNum = 1;
          else
            seqNum = 0;
          
          if(type != -2.0) {
            indexTracker += 492;
            lastSent += 492;
          }
        }
        else {                   
          if(type != -2.0) {
            socket.send(sentPacket);
            ACK(sentPacket, socket, address, port, type);
          }
          else {
            socket.send(sentPacket);
            ACK(sentPacket, socket, address, port, type);
          }
        }
      }
      else {  
        if(type != -2.0) {
          socket.send(sentPacket);
          ACK(sentPacket, socket, address, port, type);
        }
        else {
          socket.send(sentPacket);
          ACK(sentPacket, socket, address, port, type);
        }
      }
    } catch(Exception e) {
      if(type != -2.0 ) {
        socket.send(sentPacket);
        ACK(sentPacket, socket, address, port, type);
      }
      else {
        socket.send(sentPacket);
        ACK(sentPacket, socket, address, port, type);
      }
    } 
  }
  
  public long checkSum(byte[] packet) {
    CRC32 crc = new CRC32();
    crc.update(packet);
    return crc.getValue();
  }
}
