import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

class ServerSide {
    private static final int port = 8888;
    private static final int WINDOW_SIZE = 10; // advertised in bytes
    private static final double PACKET_LOSS_PROBABILITY = 0.2;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            System.out.println("Receive window size: " + WINDOW_SIZE + " bytes");
            System.out.println("Packet loss probability: " + (PACKET_LOSS_PROBABILITY * 100) + "%");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("\nNew client connected: " + clientSocket.getInetAddress().getHostAddress());

                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private DataInputStream input;
        private DataOutputStream output;
        private int expectedByte = 0;
        private int highestInOrderByte = -1;
        private final Map<Integer, byte[]> bufferedPackets = new TreeMap<>();

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                input = new DataInputStream(socket.getInputStream());
                output = new DataOutputStream(socket.getOutputStream());

                String fileName = input.readUTF();
                System.out.println("Receiving file: " + fileName);

                output.writeInt(WINDOW_SIZE); // advertise window size
                output.flush();

                receiveFile(fileName);

            } catch (IOException e) {
                System.out.println("Client handler error: " + e.getMessage());
            } finally {
                try {
                    if (input != null) input.close();
                    if (output != null) output.close();
                    if (socket != null) socket.close();
                    System.out.println("Client disconnected.\n");
                } catch (IOException e) {
                    System.out.println("Error closing connection: " + e.getMessage());
                }
            }
        }

        private void receiveFile(String originalFileName) throws IOException {
            File file = new File(resolveUniqueFilename(originalFileName));
            try (FileOutputStream fos = new FileOutputStream(file)) {
                long totalReceived = 0;

                while (true) {
                    int seq = input.readInt(); // byte offset

                    if (seq == -1) {
                        System.out.println("End of transmission received.");
                        sendAck(-1);
                        break;
                    }

                    int length = input.readInt();
                    byte[] data = new byte[length];
                    input.readFully(data);

                    boolean packetLost = ThreadLocalRandom.current().nextDouble() < PACKET_LOSS_PROBABILITY;

                    if (packetLost) {
                        System.out.println("Simulated packet loss for seq: " + seq);
                        continue;
                    }

                    System.out.println("Received packet [seq=" + seq + ", len=" + length + "]");

                    if (seq == expectedByte) {
                        fos.write(data);
                        totalReceived += length;
                        highestInOrderByte = seq + length;
                        expectedByte = highestInOrderByte;

                        // Process any buffered in-order packets
                        while (bufferedPackets.containsKey(expectedByte)) {
                            byte[] buffered = bufferedPackets.remove(expectedByte);
                            fos.write(buffered);
                            totalReceived += buffered.length;
                            highestInOrderByte = expectedByte + buffered.length;
                            expectedByte = highestInOrderByte;
                        }

                        sendAck(expectedByte);
                        System.out.println("Sent ACK: " + expectedByte);

                    } else if (seq > expectedByte) {
                        // Out-of-order: buffer it
                        bufferedPackets.put(seq, data);
                        sendAck(expectedByte); // duplicate ACK for last good byte
                        System.out.println("Out-of-order packet. Sent duplicate ACK: " + expectedByte);

                    } else {
                        // Duplicate or already received
                        sendAck(expectedByte);
                        System.out.println("Duplicate packet. Sent ACK: " + expectedByte);
                    }
                }

                System.out.println("File received: " + file.getName());
                System.out.println("Total bytes received: " + totalReceived);
            }
        }

        private void sendAck(int ackByte) throws IOException {
            output.writeInt(ackByte);
            output.flush();
        }

        private String resolveUniqueFilename(String baseName) {
            File file = new File(baseName);
            int counter = 1;
            String newName = baseName;

            while (file.exists()) {
                int dot = baseName.lastIndexOf('.');
                if (dot != -1) {
                    newName = baseName.substring(0, dot) + "_" + counter + baseName.substring(dot);
                } else {
                    newName = baseName + "_" + counter;
                }
                file = new File(newName);
                counter++;
            }

            return newName;
        }
    }
}
