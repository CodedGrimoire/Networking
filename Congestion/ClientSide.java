// ============================
// ✅ ClientSide.java (Deadlock Fixed)
// ============================
import java.io.*;
import java.net.*;
import java.util.*;

public class ClientSide {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 3002;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        boolean continueRunning = true;
        String initialFileName = args.length > 0 ? args[0] : null;

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT);

            double alpha = 0.125;
            double beta = 0.25;
            double estimatedRTT = 500;
            double devRTT = 100;
            int timeoutInterval = (int)(estimatedRTT + 4 * devRTT);

            socket.setSoTimeout(timeoutInterval);
            System.out.println("Initial timeout: " + timeoutInterval + " ms");
            
            // Set a minimum timeout to prevent too aggressive retransmissions
            int minTimeout = 100; // 100ms minimum

            List<Long> sampleRTTs = new ArrayList<>();
            List<Double> estimatedRTTs = new ArrayList<>();
            List<Integer> timeoutHistory = new ArrayList<>();
            List<Integer> cwndPerRound = new ArrayList<>();

            Reno reno = new Reno(8);
            int round = 1;

            while (continueRunning) {
                String fileName;

                if (initialFileName != null) {
                    fileName = initialFileName;
                    initialFileName = null;
                } else {
                    System.out.print("\nEnter file name to send (or 'exit' to quit): ");
                    fileName = scanner.nextLine().trim();
                }

                if (fileName.equalsIgnoreCase("exit")) {
                    output.writeUTF("__EXIT__");
                    output.flush();
                    System.out.println("Exiting client...");
                    break;
                }

                File file = new File(fileName);
                if (!file.exists() || !file.isFile()) {
                    System.out.println("File doesn't exist: " + fileName);
                    continue;
                }

                System.out.println("\nSending file: " + fileName);
                output.writeUTF(file.getName());
                output.flush();

                int serverWindowSize = input.readInt();
                System.out.println("Server window size: " + serverWindowSize + " bytes");

                try (FileInputStream fileInput = new FileInputStream(file)) {
                    byte[] buffer = new byte[serverWindowSize];
                    int sequenceNumber = 0;
                    long totalBytesSent = 0;
                    int bytesRead;
                    int lastAck = -1;
                    int dupAckCount = 0;

                    Map<Integer, byte[]> packetCache = new HashMap<>();
                    Map<Integer, Integer> packetSizes = new HashMap<>();

                    while ((bytesRead = fileInput.read(buffer)) != -1) {
                        byte[] packet = Arrays.copyOf(buffer, bytesRead);
                        packetCache.put(sequenceNumber, packet);
                        packetSizes.put(sequenceNumber, bytesRead);

                        boolean packetAcknowledged = false;

                        while (!packetAcknowledged) {
                            try {
                                long sendTime = System.currentTimeMillis();

                                output.writeInt(sequenceNumber);
                                output.writeInt(bytesRead);
                                output.write(packet);
                                output.flush();

                                System.out.println("📤 Sent packet with seq: " + sequenceNumber + ", Data len: " + bytesRead + " bytes" + 
                                    (dupAckCount > 0 ? " (retransmission)" : " (first attempt)"));

                                int ack = input.readInt();
                                long ackTime = System.currentTimeMillis();
                                long sampleRTT = ackTime - sendTime;

                                System.out.println("Received ACK: " + ack + " | SampleRTT: " + sampleRTT + " ms");

                                // FIXED: Handle duplicate ACKs first, regardless of expected ACK
                                if (ack == lastAck) {
                                    dupAckCount++;
                                    System.out.println("Duplicate ACK: " + ack + " (count=" + dupAckCount + ")");
                                    if (dupAckCount == 3) {
                                        System.out.println("🚨 Triple duplicate ACKs detected - Fast Retransmit triggered");
                                        reno.onAck(true, 3);
                                        dupAckCount = 0;
                                        
                                        // For fast retransmit, we continue with the current packet 
                                        // (which should be the lost packet that needs retransmitting)
                                        System.out.println("🔄 Fast retransmit: resending current packet seq: " + sequenceNumber);
                                        continue;
                                    }
                                } else {
                                    // FIXED: Reset duplicate count to 0 for new ACK, not 1
                                    dupAckCount = 0;
                                    lastAck = ack;
                                    reno.onAck(false, 1);
                                }

                                // Check if this is the expected ACK
                                if (ack == sequenceNumber) {
                                    estimatedRTT = (1 - alpha) * estimatedRTT + alpha * sampleRTT;
                                    devRTT = (1 - beta) * devRTT + beta * Math.abs(sampleRTT - estimatedRTT);
                                    timeoutInterval = Math.max((int)(estimatedRTT + 4 * devRTT), minTimeout);
                                    socket.setSoTimeout(timeoutInterval);

                                    sampleRTTs.add(sampleRTT);
                                    estimatedRTTs.add(estimatedRTT);
                                    timeoutHistory.add(timeoutInterval);

                                    packetAcknowledged = true;
                                    sequenceNumber++;
                                    totalBytesSent += bytesRead;

                                    cwndPerRound.add(reno.getCwnd());
                                    System.out.println("📊 End of Round " + round + " — CWND = " + reno.getCwnd());
                                    round++;
                                } else {
                                    System.out.println("Unexpected ACK: " + ack + ", waiting for ACK: " + sequenceNumber);
                                }

                            } catch (SocketTimeoutException e) {
                                System.out.println("⏰ Timeout triggered! Resending packet with seq: " + sequenceNumber + " (timeout: " + timeoutInterval + "ms)");
                                reno.onTimeout();
                                // Reset duplicate ACK count on timeout
                                dupAckCount = 0;
                                // Continue the loop to resend the packet
                            }
                        }
                    }

                    // Send end of transmission signal
                    output.writeInt(-1);
                    output.writeInt(0);
                    output.flush();
                    System.out.println("Sent end of transmission signal");

                    // FIXED: Add timeout handling for final ACK
                    try {
                        int finalAck = input.readInt();
                        if (finalAck == -1) {
                            System.out.println("Server acknowledged end of transmission");
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout waiting for final ACK - assuming transmission complete");
                    }

                    // Save RTT and CWND data
                    try (PrintWriter writer = new PrintWriter(new File("rtt_data.csv"))) {
                        writer.println("TimeIndex,SampleRTT,EstimatedRTT,TimeoutInterval");
                        for (int i = 0; i < sampleRTTs.size(); i++) {
                            writer.printf("%d,%d,%.2f,%d\n", i, sampleRTTs.get(i), estimatedRTTs.get(i), timeoutHistory.get(i));
                        }
                        System.out.println("RTT data saved to rtt_data.csv");
                    }

                    try (PrintWriter cwndWriter = new PrintWriter(new File("cwnd_data.csv"))) {
                        cwndWriter.println("Round,CWND");
                        for (int i = 0; i < cwndPerRound.size(); i++) {
                            cwndWriter.printf("%d,%d\n", i + 1, cwndPerRound.get(i));
                        }
                        System.out.println("CWND data saved to cwnd_data.csv");
                    }

                    System.out.println("File transfer complete. Total bytes sent: " + totalBytesSent);
                }
            }

        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }

        scanner.close();
    }
}