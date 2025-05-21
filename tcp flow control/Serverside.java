import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ServerSide {
    private static final int port = 8888;
    private static final int WINDOW_SIZE = 10; // Server's receive window size
    private static final double PACKET_LOSS_PROBABILITY = 0.2; // 20% probability of packet loss
    
    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server started on port " + port);
            System.out.println("Receive window size: " + WINDOW_SIZE + " bytes");
            System.out.println("Packet loss probability: " + (PACKET_LOSS_PROBABILITY * 100) + "%");
            
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                    
                    clientSocket.setReceiveBufferSize(WINDOW_SIZE);
                    
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    clientHandler.start();
                } catch (Exception e) {
                    System.out.println("Error accepting client connection: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }
    
    private static class ClientHandler extends Thread {
        private Socket clientSocket;
        private DataInputStream input;
        private DataOutputStream output;
        private int expectedSeq = 0;
        private int highestInOrderSeq = -1; // For cumulative ACK
        private Map<Integer, byte[]> bufferedPackets = new HashMap<>(); // Buffer out-of-order packets
        
        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }
        
        @Override
        public void run() {
            try {
                input = new DataInputStream(clientSocket.getInputStream());
                output = new DataOutputStream(clientSocket.getOutputStream());
                
                String fileName = input.readUTF();
                System.out.println("Client sending file: " + fileName);
                
                output.writeInt(WINDOW_SIZE);
                output.flush();
                System.out.println("Sent window size: " + WINDOW_SIZE + " bytes to client");
                
                receiveFile(fileName);
                
            } catch (Exception e) {
                System.out.println("Error in client handler: " + e.getMessage());
            } finally {
                try {
                    if (input != null) input.close();
                    if (output != null) output.close();
                    if (clientSocket != null) clientSocket.close();
                    System.out.println("Client disconnected");
                } catch (IOException e) {
                    System.out.println("Error closing connection: " + e.getMessage());
                }
            }
        }
        
        private void receiveFile(String fileName) throws IOException {
            File file = new File(fileName);
            String actualFileName = fileName;
            int counter = 1;
            
            while (file.exists()) {
                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex > 0) {
                    actualFileName = fileName.substring(0, dotIndex) + "_" + counter + 
                                     fileName.substring(dotIndex);
                } else {
                    actualFileName = fileName + "_" + counter;
                }
                file = new File(actualFileName);
                counter++;
            }
            
            try (FileOutputStream fileOutput = new FileOutputStream(file)) {
                byte[] buffer = new byte[WINDOW_SIZE];
                long totalBytesReceived = 0;
                
                while (true) {
                    int sequenceNumber = input.readInt();
                    
                    if (sequenceNumber == -1) {
                        System.out.println("End of transmission received");
                        sendAck(-1); // Final ACK
                        break;
                    }
                    
                    int dataLength = input.readInt();
                    
                    // Read data
                    int bytesRead = 0;
                    while (bytesRead < dataLength) {
                        int remaining = dataLength - bytesRead;
                        int chunkSize = Math.min(remaining, WINDOW_SIZE);
                        int read = input.read(buffer, bytesRead, chunkSize);
                        if (read == -1) break;
                        bytesRead += read;
                    }
                    
                    // Randomly simulate packet loss
                    boolean packetLost = ThreadLocalRandom.current().nextDouble() < PACKET_LOSS_PROBABILITY;
                    
                    if (packetLost) {
                        System.out.println("Simulated packet loss for seq: " + sequenceNumber);
                        // Don't send ACK - simulate packet loss
                        continue;
                    }
                    
                    System.out.println("Received packet with seq: " + sequenceNumber + 
                                      ", Data len: " + dataLength + " bytes");
                    
                    if (sequenceNumber == expectedSeq) {
                        // Process in-order packet
                        fileOutput.write(buffer, 0, dataLength);
                        totalBytesReceived += dataLength;
                        highestInOrderSeq = sequenceNumber;
                        expectedSeq++;
                        
                        // Process any buffered packets that are now in order
                        while (bufferedPackets.containsKey(expectedSeq)) {
                            byte[] bufferedData = bufferedPackets.remove(expectedSeq);
                            fileOutput.write(bufferedData);
                            totalBytesReceived += bufferedData.length;
                            highestInOrderSeq = expectedSeq;
                            expectedSeq++;
                        }
                        
                        // Send cumulative ACK for highest in-order packet
                        sendAck(highestInOrderSeq);
                        System.out.println("Sent cumulative ACK for seq: " + highestInOrderSeq);
                    } else if (sequenceNumber > expectedSeq) {
                        // Buffer out-of-order packet
                        byte[] packetData = new byte[dataLength];
                        System.arraycopy(buffer, 0, packetData, 0, dataLength);
                        bufferedPackets.put(sequenceNumber, packetData);
                        
                        // Send duplicate ACK for the last in-order packet received
                        sendAck(highestInOrderSeq);
                        System.out.println("Out of order packet. Sent duplicate ACK for seq: " + highestInOrderSeq);
                    } else {
                        // Duplicate packet - already received
                        sendAck(highestInOrderSeq);
                        System.out.println("Duplicate packet. Sent ACK for seq: " + highestInOrderSeq);
                    }
                }
                
                System.out.println("File transfer complete. Total bytes received: " + totalBytesReceived);
                System.out.println("File saved as: " + actualFileName);
            }
        }
        
        private void sendAck(int ackNumber) throws IOException {
            output.writeInt(ackNumber);
            output.flush();
        }
    }
}