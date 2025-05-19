import java.io.*;
import java.net.*;
import java.util.*;

public class Dclient {
    private static final String SERVER_ADDRESS = "10.33.27.28";
    private static final int SERVER_PORT = 8888;
    private static final int INITIAL_TIMEOUT = 5000; // in milliseconds
    private static final double ALPHA = 0.125;
    private static final double BETA = 0.25;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\nEnter file name to send (or 'exit' to quit): ");
            String fileName = scanner.nextLine().trim();
            if (fileName.equalsIgnoreCase("exit")) break;

            File file = new File(fileName);
            if (!file.exists() || !file.isFile()) {
                System.out.println("File not found: " + fileName);
                continue;
            }

            sendFile(file);
        }

        scanner.close();
    }

    private static void sendFile(File file) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream());
             FileInputStream fileInput = new FileInputStream(file)) {

            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT);

            // Send file name
            output.writeUTF(file.getName());
            output.flush();

            // Receive advertised window size
            int windowSize = input.readInt();
            System.out.println("Server advertised window size: " + windowSize + " bytes");

            byte[] buffer = new byte[windowSize];
            Map<Integer, byte[]> packetBuffer = new HashMap<>();
            Map<Integer, Integer> ackCount = new HashMap<>();

            int sequenceNumber = 0; // byte offset
            int bytesRead;

            long estimatedRTT = INITIAL_TIMEOUT;
            long devRTT = 0;
            long timeoutInterval = INITIAL_TIMEOUT;

            socket.setSoTimeout((int) timeoutInterval);

            while ((bytesRead = fileInput.read(buffer)) != -1) {
                byte[] packet = Arrays.copyOf(buffer, bytesRead);
                packetBuffer.put(sequenceNumber, packet);

                boolean acknowledged = false;

                while (!acknowledged) {
                    try {
                        long sendTime = System.currentTimeMillis();

                        // Send seq number, length, and data
                        output.writeInt(sequenceNumber);
                        output.writeInt(bytesRead);
                        output.write(packet, 0, bytesRead);
                        output.flush();

                        System.out.println("Sent packet [seq=" + sequenceNumber + ", len=" + bytesRead + "]");

                        // Wait for ACK
                        int ack = input.readInt();
                        long recvTime = System.currentTimeMillis();

                        if (ack == sequenceNumber + bytesRead) {
                            long sampleRTT = recvTime - sendTime;

                            // EWMA RTT update
                            estimatedRTT = (long) ((1 - ALPHA) * estimatedRTT + ALPHA * sampleRTT);
                            devRTT = (long) ((1 - BETA) * devRTT + BETA * Math.abs(sampleRTT - estimatedRTT));
                            timeoutInterval = estimatedRTT + 4 * devRTT;

                            socket.setSoTimeout((int) timeoutInterval);

                            System.out.println("ACK received: " + ack + " | SampleRTT=" + sampleRTT + "ms | New timeout=" + timeoutInterval + "ms");

                            acknowledged = true;
                            sequenceNumber = ack;
                        } else {
                            ackCount.put(ack, ackCount.getOrDefault(ack, 0) + 1);
                            if (ackCount.get(ack) >= 3) {
                                System.out.println("Triple duplicate ACK for byte " + ack + ". Fast retransmit.");
                                sequenceNumber = ack;
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout for packet starting at byte " + sequenceNumber + ", retransmitting...");
                    }
                }
            }

            // Send end-of-transmission signal
            output.writeInt(-1);
            output.writeInt(0);
            output.flush();

            int finalAck = input.readInt();
            if (finalAck == -1) {
                System.out.println("Server acknowledged end of transmission.");
            }

            System.out.println("File transfer complete.");

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }
}
