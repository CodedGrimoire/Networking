import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ServerSide {
    private static final int PORT = 3002;
    private static final int WINDOW_SIZE = 200;
    private static final double PACKET_LOSS_PROBABILITY = 0.2;
    private static final double TRIPLE_DUP_ACK_PROBABILITY = 0.2;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            System.out.println("Receive window size: " + WINDOW_SIZE + " bytes");

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                    new ClientHandler(clientSocket).start();
                } catch (IOException e) {
                    System.out.println("Error accepting client connection: " + e.getMessage());
                    // Continue accepting other connections
                }
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket clientSocket;
        private final Set<Integer> droppedPackets = new HashSet<>();
        private final Set<Integer> fakeDupACKs = new HashSet<>();
        private DataInputStream input;
        private DataOutputStream output;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                // Initialize streams
                input = new DataInputStream(clientSocket.getInputStream());
                output = new DataOutputStream(clientSocket.getOutputStream());
                
                System.out.println("Client handler initialized for: " + clientSocket.getInetAddress().getHostAddress());

                while (isConnectionActive()) {
                    try {
                        String fileName = input.readUTF();

                        if ("__EXIT__".equals(fileName)) {
                            System.out.println("Client requested disconnection.");
                            sendAck(-1); // Acknowledge the exit request
                            break;
                        }

                        // Validate filename before processing
                        if (fileName == null || fileName.trim().isEmpty()) {
                            System.out.println(" Received empty filename from client. Sending error response.");
                            // Send error response to client (negative window size indicates error)
                            output.writeInt(-1);
                            output.flush();
                            continue;
                        }

                        fileName = fileName.trim();
                        System.out.println(" Client sending file: '" + fileName + "'");
                        
                        // Send window size
                        if (!sendWindowSize()) {
                            System.out.println("Failed to send window size. Connection may be lost.");
                            break;
                        }

                        // Handle file transfer with isolated error handling
                        boolean transferSuccess = handleFileTransfer(fileName);
                        
                        if (!transferSuccess) {
                            System.out.println(" File transfer failed, but keeping connection alive for retry...");
                            // Don't break - allow client to retry or send another file
                        }

                    } catch (SocketTimeoutException e) {
                        System.out.println("Socket timeout waiting for client data: " + e.getMessage());
                        if (!isConnectionActive()) {
                            break;
                        }
                        // Continue waiting for more data
                        
                    } catch (EOFException e) {
                        System.out.println(" Client disconnected (EOF): " + e.getMessage());
                        break;
                        
                    } catch (SocketException e) {
                        System.out.println("Socket error: " + e.getMessage());
                        if (e.getMessage().contains("Connection reset") || 
                            e.getMessage().contains("Socket closed") ||
                            e.getMessage().contains("Broken pipe")) {
                            System.out.println(" Connection lost with client");
                            break;
                        }
                        // For other socket errors, try to continue
                        
                    } catch (IOException e) {
                        System.out.println("IO error in main client loop: " + e.getMessage());
                        if (!isConnectionActive()) {
                            break;
                        }
                        // For other IO errors, try to continue
                    }
                }

            } catch (IOException e) {
                System.out.println("Critical error in client handler initialization: " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        private boolean isConnectionActive() {
            return clientSocket != null && 
                   !clientSocket.isClosed() && 
                   clientSocket.isConnected() && 
                   !clientSocket.isInputShutdown() && 
                   !clientSocket.isOutputShutdown();
        }

        private boolean sendWindowSize() {
            try {
                if (!isConnectionActive()) {
                    return false;
                }
                output.writeInt(WINDOW_SIZE);
                output.flush();
                return true;
            } catch (IOException e) {
                System.out.println(" Error sending window size: " + e.getMessage());
                return false;
            }
        }

        private boolean handleFileTransfer(String fileName) {
            System.out.println("Starting file transfer for: " + fileName);
            
            try {
                receiveFile(fileName);
                System.out.println("File transfer completed successfully: " + fileName);
                return true;
                
            } catch (FileNotFoundException e) {
                System.out.println(" File creation error: " + e.getMessage());
                return false;
                
            } catch (SocketTimeoutException e) {
                System.out.println(" Timeout during file transfer: " + e.getMessage());
                return false;
                
            } catch (EOFException e) {
                System.out.println(" Unexpected end of stream during file transfer: " + e.getMessage());
                return false;
                
            } catch (SocketException e) {
                System.out.println(" Socket error during file transfer: " + e.getMessage());
                return false;
                
            } catch (IOException e) {
                System.out.println("IO error during file transfer: " + e.getMessage());
                return false;
                
            } catch (Exception e) {
                System.out.println("Unexpected error during file transfer: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }

        private void receiveFile(String fileName) throws IOException {
            File file = new File(fileName);
            String actualFileName = fileName;
            int counter = 1;

            // Generate unique filename if file exists
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
                int packetsReceived = 0;
                long transferStartTime = System.currentTimeMillis();

                System.out.println("Created file: " + actualFileName);

                while (isConnectionActive()) {
                    // Read sequence number
                    int sequenceNumber = input.readInt();
                    
                    if (sequenceNumber == -1) {
                        System.out.println("End of transmission received");
                        if (!sendAck(-1)) {
                            System.out.println("Failed to send final ACK");
                        }
                        break;
                    }

                    // Read data length
                    int dataLength = input.readInt();
                    
                    if (dataLength < 0 || dataLength > WINDOW_SIZE) {
                        System.out.println("Invalid data length: " + dataLength);
                        continue;
                    }

                    // Read packet data
                    int bytesRead = 0;
                    while (bytesRead < dataLength && isConnectionActive()) {
                        int read = input.read(buffer, bytesRead, dataLength - bytesRead);
                        if (read == -1) {
                            throw new EOFException("Unexpected end of stream while reading packet data");
                        }
                        bytesRead += read;
                    }

                    packetsReceived++;
                    System.out.println("[" + packetsReceived + "] Received packet - Seq: " + sequenceNumber + 
                                     ", Data: " + dataLength + " bytes, Expected: " + expectedSeq);

                    // Simulate packet loss only on first transmission
                    boolean shouldDrop = false;
                    if (!droppedPackets.contains(sequenceNumber)) {
                        if (ThreadLocalRandom.current().nextDouble() < PACKET_LOSS_PROBABILITY) {
                            droppedPackets.add(sequenceNumber);
                            shouldDrop = true;
                            System.out.println("[SIMULATION] Packet loss for seq: " + sequenceNumber + " (first transmission)");
                        }
                    } else {
                        // This is a retransmission - remove from dropped set and process normally
                        droppedPackets.remove(sequenceNumber);
                        System.out.println("[RETRANSMISSION] Processing retransmitted seq: " + sequenceNumber);
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
                        System.out.println("[SIMULATION] Triggering triple duplicate ACKs for seq: " + sequenceNumber);
                        
                        // Send 3 duplicate ACKs with the last valid ACK number
                        for (int i = 0; i < 3; i++) {
                            if (!sendAck(highestInOrderSeq)) {
                                System.out.println("Failed to send duplicate ACK " + (i+1));
                                break;
                            }
                            System.out.println("↩[FAKE DUP ACK " + (i+1) + "/3] Sent duplicate ACK: " + highestInOrderSeq);
                            
                            // Small delay between duplicate ACKs
                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        
                        
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

                        if (!sendAck(highestInOrderSeq)) {
                            System.out.println("Failed to send cumulative ACK");
                            break;
                        }
                        System.out.println("Sent cumulative ACK for seq: " + highestInOrderSeq);
                        
                    } else if (sequenceNumber > expectedSeq) {
                        // Out-of-order packet - buffer it
                        byte[] packetData = Arrays.copyOf(buffer, dataLength);
                        bufferedPackets.put(sequenceNumber, packetData);
                        
                        if (!sendAck(highestInOrderSeq)) {
                            System.out.println("Failed to send duplicate ACK for out-of-order packet");
                            break;
                        }
                        System.out.println("Out-of-order packet (seq: " + sequenceNumber + 
                                         ", expected: " + expectedSeq + "). Sent duplicate ACK for seq: " + highestInOrderSeq);
                        
                    } else {
                        // Duplicate packet (sequenceNumber < expectedSeq)
                        if (!sendAck(highestInOrderSeq)) {
                            System.out.println("Failed to send ACK for duplicate packet");
                            break;
                        }
                        System.out.println("Duplicate packet (seq: " + sequenceNumber + 
                                         ", expected: " + expectedSeq + "). Sent ACK for seq: " + highestInOrderSeq);
                    }
                }

                long transferEndTime = System.currentTimeMillis();
                long transferDuration = transferEndTime - transferStartTime;

                System.out.println("\n" + "=".repeat(50));
                System.out.println("FILE TRANSFER STATISTICS");
                System.out.println("=".repeat(50));
                System.out.println("File saved as: " + actualFileName);
                System.out.println("Total bytes received: " + totalBytesReceived);
                System.out.println("Total packets received: " + packetsReceived);
                System.out.println("Transfer duration: " + transferDuration + " ms");
                System.out.println("Average throughput: " + (totalBytesReceived * 1000.0 / transferDuration) + " bytes/sec");
                System.out.println("Buffered packets remaining: " + bufferedPackets.size());
                System.out.println("=".repeat(50));
            }
        }

        private boolean sendAck(int ackNumber) {
            try {
                if (!isConnectionActive()) {
                    System.out.println("Connection not active. Cannot send ACK.");
                    return false;
                }
                
                output.writeInt(ackNumber);
                output.flush();
                return true;
                
            } catch (IOException e) {
                System.out.println("Error sending ACK " + ackNumber + ": " + e.getMessage());
                return false;
            }
        }

        private void cleanup() {
            System.out.println("Cleaning up client handler...");
            
            try {
                if (output != null) {
                    output.close();
                }
            } catch (IOException e) {
                System.out.println("Error closing output stream: " + e.getMessage());
            }

            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException e) {
                System.out.println("Error closing input stream: " + e.getMessage());
            }

            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.out.println("Error closing client socket: " + e.getMessage());
            }

            // Clear simulation state for potential reconnection
            droppedPackets.clear();
            fakeDupACKs.clear();
            
            System.out.println("Client handler cleanup completed");
        }
    }
}