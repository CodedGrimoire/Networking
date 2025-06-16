import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ServerSide {
    private static final int PORT = 3002;
    private static final int WINDOW_SIZE = 10;
    private static final double PACKET_LOSS_PROBABILITY = 0.2;

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

                    boolean packetLost = ThreadLocalRandom.current().nextDouble() < PACKET_LOSS_PROBABILITY;
                    if (packetLost) {
                        System.out.println("Simulated packet loss for seq: " + sequenceNumber);
                        continue;
                    }

                    System.out.println("Received packet with seq: " + sequenceNumber + ", Data len: " + dataLength + " bytes");

                    if (sequenceNumber == expectedSeq) {
                        fileOutput.write(buffer, 0, dataLength);
                        totalBytesReceived += dataLength;
                        highestInOrderSeq = sequenceNumber;
                        expectedSeq++;

                        while (bufferedPackets.containsKey(expectedSeq)) {
                            byte[] bufferedData = bufferedPackets.remove(expectedSeq);
                            fileOutput.write(bufferedData);
                            totalBytesReceived += bufferedData.length;
                            highestInOrderSeq = expectedSeq;
                            expectedSeq++;
                        }

                        sendAck(output, highestInOrderSeq);
                        System.out.println("Sent cumulative ACK for seq: " + highestInOrderSeq);
                    } else if (sequenceNumber > expectedSeq) {
                        byte[] packetData = Arrays.copyOf(buffer, dataLength);
                        bufferedPackets.put(sequenceNumber, packetData);
                        sendAck(output, highestInOrderSeq);
                        System.out.println("Out of order packet. Sent duplicate ACK for seq: " + highestInOrderSeq);
                    } else {
                        sendAck(output, highestInOrderSeq);
                        System.out.println("Duplicate packet. Sent ACK for seq: " + highestInOrderSeq);
                    }
                }

                System.out.println("File transfer complete. Total bytes received: " + totalBytesReceived);
                System.out.println("File saved as: " + actualFileName);

                System.out.println("Generating RTT plot:");
                Process process = null;
                process = new ProcessBuilder("python3", "generate_rtt_plot.py").start();
                int exitCode = -1;
                try {
                    exitCode = process.waitFor();
                } catch (InterruptedException e) {
                    System.out.println("Process was interrupted: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
                if (exitCode == 0) {
                    System.out.println("RTT plot generated successfully.");
                } else {
                    System.out.println("Failed to generate RTT plot. Exit code: " + exitCode);
                }

            }
        }

        private void sendAck(DataOutputStream output, int ackNumber) throws IOException {
            output.writeInt(ackNumber);
            output.flush();
        }
    }
}