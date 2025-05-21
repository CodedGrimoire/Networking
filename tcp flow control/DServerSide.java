import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DServerSide {
    private static final int PORT = 8888;
    private static final int MAX_WINDOW_SIZE = 1024;
    private static final double PACKET_LOSS_PROBABILITY = 0.2;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
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
        private final Socket socket;
        private DataInputStream input;
        private DataOutputStream output;
        private int expectedByte = 0;
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

                sendWindowSize(); // Initial window

                receiveFile(fileName);

            } catch (IOException e) {
                System.out.println("Client handler error: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                    System.out.println("Client disconnected.\n");
                } catch (IOException e) {
                    System.out.println("Error closing socket: " + e.getMessage());
                }
            }
        }

        private void receiveFile(String originalFileName) throws IOException {
            File file = new File(resolveUniqueFilename(originalFileName));
            try (FileOutputStream fos = new FileOutputStream(file)) {
                long totalReceived = 0;

                while (true) {
                    int seq = input.readInt();
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
                        System.out.println("Simulated packet loss for seq " + seq);
                        continue;
                    }

                    System.out.println("Received packet [seq=" + seq + ", len=" + length + "]");

                    if (seq == expectedByte) {
                        fos.write(data);
                        totalReceived += length;
                        expectedByte += length;

                        while (bufferedPackets.containsKey(expectedByte)) {
                            byte[] nextData = bufferedPackets.remove(expectedByte);
                            fos.write(nextData);
                            totalReceived += nextData.length;
                            expectedByte += nextData.length;
                        }

                        sendAck(expectedByte);
                        sendWindowSize();

                    } else if (seq > expectedByte) {
                        bufferedPackets.put(seq, data);
                        sendAck(expectedByte); // duplicate ACK
                        sendWindowSize();
                    } else {
                        sendAck(expectedByte); // duplicate packet
                        sendWindowSize();
                    }
                }

                System.out.println("File received: " + file.getName());
                System.out.println("Total bytes received: " + totalReceived);
            }
        }

        private void sendAck(int ackByte) throws IOException {
            output.writeInt(ackByte);
            output.flush();
            System.out.println("Sent ACK: " + ackByte);
        }

        private void sendWindowSize() throws IOException {
            int dynamicSize = ThreadLocalRandom.current().nextInt(64, MAX_WINDOW_SIZE + 1);
            output.writeInt(dynamicSize);
            output.flush();
            System.out.println("Advertised new window size: " + dynamicSize + " bytes");
        }

        private String resolveUniqueFilename(String baseName) {
            File file = new File(baseName);
            int counter = 1;
            String newName = baseName;

            while (file.exists()) {
                int dot = baseName.lastIndexOf('.');
                newName = (dot != -1) ? baseName.substring(0, dot) + "_" + counter + baseName.substring(dot)
                                      : baseName + "_" + counter;
                file = new File(newName);
                counter++;
            }

            return newName;
        }
    }
}
