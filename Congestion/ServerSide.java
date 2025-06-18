// =============================
// ✅ ServerSide.java (Deadlock Fixed)
// =============================
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ServerSide {
    private static final int PORT = 3002;
    private static final int WINDOW_SIZE = 200;
    private static final double PACKET_LOSS_PROBABILITY = 0.1;
    private static final double TRIPLE_DUP_ACK_PROBABILITY = 0.1;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            System.out.println("Receive window size: " + WINDOW_SIZE + " bytes");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket clientSocket;
        private final Set<Integer> droppedPackets = new HashSet<>();
        private final Set<Integer> fakeDupACKs = new HashSet<>();

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream input = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream())) {

                while (true) {
                    String fileName = input.readUTF();

                    if ("__EXIT__".equals(fileName)) {
                        System.out.println("Client requested disconnection.");
                        break;
                    }

                    System.out.println("Client sending file: " + fileName);
                    output.writeInt(WINDOW_SIZE);
                    output.flush();

                    receiveFile(input, output, fileName);
                }

            } catch (IOException e) {
                System.out.println("Error in client handler: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                    System.out.println("Client disconnected");
                } catch (IOException e) {
                    System.out.println("Error closing socket: " + e.getMessage());
                }
            }
        }

        private void receiveFile(DataInputStream input, DataOutputStream output, String fileName) throws IOException {
            File file = new File(fileName);
            String actualFileName = fileName;
            int counter = 1;

            while (file.exists()) {
                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex > 0) {
                    actualFileName = fileName.substring(0, dotIndex) + "_" + counter + fileName.substring(dotIndex);
                } else {
                    actualFileName = fileName + "_" + counter;
                }
                file = new File(actualFileName);
                counter++;
            }

            try (FileOutputStream fileOutput = new FileOutputStream(file)) {
                byte[] buffer = new byte[WINDOW_SIZE];
                long totalBytesReceived = 0;
                int expectedSeq = 0;
                int highestInOrderSeq = -1;
                Map<Integer, byte[]> bufferedPackets = new HashMap<>();

                while (true) {
                    int sequenceNumber = input.readInt();
                    if (sequenceNumber == -1) {
                        System.out.println("End of transmission received");
                        sendAck(output, -1);
                        break;
                    }

                    int dataLength = input.readInt();
                    int bytesRead = 0;
                    while (bytesRead < dataLength) {
                        int read = input.read(buffer, bytesRead, dataLength - bytesRead);
                        if (read == -1) break;
                        bytesRead += read;
                    }

                    System.out.println("📥 Received packet with seq: " + sequenceNumber + ", Data len: " + dataLength + " bytes");

                    // Simulate packet loss only on first transmission
                    boolean shouldDrop = false;
                    if (!droppedPackets.contains(sequenceNumber)) {
                        if (ThreadLocalRandom.current().nextDouble() < PACKET_LOSS_PROBABILITY) {
                            droppedPackets.add(sequenceNumber);
                            shouldDrop = true;
                            System.out.println("❌ Simulated packet loss for seq: " + sequenceNumber + " (first transmission)");
                        }
                    } else {
                        // This is a retransmission - remove from dropped set and process normally
                        droppedPackets.remove(sequenceNumber);
                        System.out.println("🔄 Processing retransmission for seq: " + sequenceNumber);
                    }
                    
                    if (shouldDrop) {
                        continue; // Skip processing this packet
                    }

                    // Simulate triple duplicate ACKs only under specific conditions
                    if (sequenceNumber == expectedSeq && 
                        highestInOrderSeq >= 0 && 
                        sequenceNumber > 0 &&  // Don't do this for the very first packet
                        !fakeDupACKs.contains(sequenceNumber) &&
                        ThreadLocalRandom.current().nextDouble() < TRIPLE_DUP_ACK_PROBABILITY) {
                        
                        fakeDupACKs.add(sequenceNumber);
                        System.out.println("⚠️ Simulating triple duplicate ACKs for seq: " + sequenceNumber);
                        
                        // Send 3 duplicate ACKs with the last valid ACK number
                        for (int i = 0; i < 3; i++) {
                            sendAck(output, highestInOrderSeq);
                            System.out.println("↩️ [FAKE DUP ACK " + (i+1) + "/3] Sent duplicate ACK: " + highestInOrderSeq);
                            
                            // Small delay between duplicate ACKs
                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        
                        // After sending fake duplicate ACKs, process the packet normally
                        // (This simulates the packet finally getting through after triggering fast retransmit)
                    }

                    // Process the packet normally
                    if (sequenceNumber == expectedSeq) {
                        // In-order packet
                        fileOutput.write(buffer, 0, dataLength);
                        totalBytesReceived += dataLength;
                        highestInOrderSeq = sequenceNumber;
                        expectedSeq++;

                        // Process any buffered out-of-order packets that are now in order
                        while (bufferedPackets.containsKey(expectedSeq)) {
                            byte[] bufferedData = bufferedPackets.remove(expectedSeq);
                            fileOutput.write(bufferedData);
                            totalBytesReceived += bufferedData.length;
                            highestInOrderSeq = expectedSeq;
                            expectedSeq++;
                        }

                        sendAck(output, highestInOrderSeq);
                        System.out.println("✅ Sent cumulative ACK for seq: " + highestInOrderSeq);
                        
                    } else if (sequenceNumber > expectedSeq) {
                        // Out-of-order packet - buffer it
                        byte[] packetData = Arrays.copyOf(buffer, dataLength);
                        bufferedPackets.put(sequenceNumber, packetData);
                        sendAck(output, highestInOrderSeq);
                        System.out.println("📦 Out-of-order packet (seq: " + sequenceNumber + ", expected: " + expectedSeq + "). Sent duplicate ACK for seq: " + highestInOrderSeq);
                        
                    } else {
                        // Duplicate packet (sequenceNumber < expectedSeq)
                        sendAck(output, highestInOrderSeq);
                        System.out.println("🔁 Duplicate packet (seq: " + sequenceNumber + ", expected: " + expectedSeq + "). Sent ACK for seq: " + highestInOrderSeq);
                    }
                }

                System.out.println("📁 File transfer complete. Total bytes received: " + totalBytesReceived);
                System.out.println("📥 File saved as: " + actualFileName);
            }
        }

        private void sendAck(DataOutputStream output, int ackNumber) throws IOException {
            output.writeInt(ackNumber);
            output.flush();
        }
    }
}