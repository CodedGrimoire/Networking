// ============================
// ✅ ClientSide.java (TCP Reno Fixed)
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
            List<String> eventLog = new ArrayList<>();

            Reno reno = new Reno(8); // Start with ssthresh of 8
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
                System.out.println("=".repeat(50));
                output.writeUTF(file.getName());
                output.flush();

                int serverWindowSize = input.readInt();
                System.out.println("Server window size: " + serverWindowSize + " bytes");

                // Reset Reno for new file transfer
                reno.reset();

                try (FileInputStream fileInput = new FileInputStream(file)) {
                    byte[] buffer = new byte[serverWindowSize];
                    int sequenceNumber = 0;
                    long totalBytesSent = 0;
                    int bytesRead;
                    int lastAckReceived = -1;
                    int consecutiveDupAcks = 0;

                    Map<Integer, byte[]> packetCache = new HashMap<>();
                    Map<Integer, Integer> packetSizes = new HashMap<>();

                    while ((bytesRead = fileInput.read(buffer)) != -1) {
                        byte[] packet = Arrays.copyOf(buffer, bytesRead);
                        packetCache.put(sequenceNumber, packet);
                        packetSizes.put(sequenceNumber, bytesRead);

                        boolean packetAcknowledged = false;
                        boolean isRetransmission = false;

                        while (!packetAcknowledged) {
                            try {
                                long sendTime = System.currentTimeMillis();

                                output.writeInt(sequenceNumber);
                                output.writeInt(bytesRead);
                                output.write(packet);
                                output.flush();

                                int ack = input.readInt();
                                long ackTime = System.currentTimeMillis();
                                long sampleRTT = ackTime - sendTime;

                                // Process the ACK with TCP Reno logic
                                if (ack == sequenceNumber) {
                                    // Expected ACK received - packet acknowledged
                                    reno.onAck(ack, true); // New ACK
                                    
                                    // Update RTT estimates
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
                                    lastAckReceived = ack;
                                    consecutiveDupAcks = 0;

                                    // Record CWND for this round
                                    cwndPerRound.add(reno.getCwnd());
                                    round++;

                                } else if (ack == lastAckReceived) {
                                    // Duplicate ACK
                                    consecutiveDupAcks++;
                                    reno.onAck(ack, false); // Duplicate ACK
                                    
                                    if (consecutiveDupAcks == 3) {
                                        isRetransmission = true;
                                    }
                                    
                                } else {
                                    // Unexpected ACK
                                    reno.onAck(ack, true);
                                }

                            } catch (SocketTimeoutException e) {
                                reno.onTimeout();
                                isRetransmission = true;
                                consecutiveDupAcks = 0;;
                                consecutiveDupAcks = 0;
                                
                                eventLog.add("Round " + round + ": TIMEOUT seq " + sequenceNumber + ", CWND=" + reno.getCwnd());
                                // Continue the loop to resend the packet
                            }
                        }
                    }

                    // Send end of transmission signal
                    output.writeInt(-1);
                    output.writeInt(0);
                    output.flush();
                    System.out.println("📡 Sent end of transmission signal");

                    // Handle final ACK
                    try {
                        int finalAck = input.readInt();
                        if (finalAck == -1) {
                            System.out.println("✅ Server acknowledged end of transmission");
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("⚠️ Timeout waiting for final ACK - assuming transmission complete");
                    }

                    // Save detailed data
                    saveTransmissionData(sampleRTTs, estimatedRTTs, timeoutHistory, cwndPerRound, eventLog);
                    
                    System.out.println("\n" + "=".repeat(50));
                    System.out.println("📊 TRANSMISSION COMPLETE");
                    System.out.println("Total bytes sent: " + totalBytesSent);
                    System.out.println("Total rounds: " + (round - 1));
                    System.out.println("Final CWND: " + reno.getCwnd());
                    System.out.println("Final SSThresh: " + reno.getSsthresh());
                    System.out.println("=".repeat(50));
                }
            }

        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }

        scanner.close();
    }

    private static void saveTransmissionData(List<Long> sampleRTTs, List<Double> estimatedRTTs, 
                                           List<Integer> timeoutHistory, List<Integer> cwndPerRound,
                                           List<String> eventLog) {
        // Save RTT data
        try (PrintWriter writer = new PrintWriter(new File("rtt_data.csv"))) {
            writer.println("TimeIndex,SampleRTT,EstimatedRTT,TimeoutInterval");
            for (int i = 0; i < sampleRTTs.size(); i++) {
                writer.printf("%d,%d,%.2f,%d\n", i + 1, sampleRTTs.get(i), 
                             estimatedRTTs.get(i), timeoutHistory.get(i));
            }
            System.out.println("📁 RTT data saved to rtt_data.csv");
        } catch (IOException e) {
            System.out.println("Error saving RTT data: " + e.getMessage());
        }

        // Save CWND data
        try (PrintWriter cwndWriter = new PrintWriter(new File("cwnd_data.csv"))) {
            cwndWriter.println("Round,CWND");
            for (int i = 0; i < cwndPerRound.size(); i++) {
                cwndWriter.printf("%d,%d\n", i + 1, cwndPerRound.get(i));
            }
            System.out.println("📁 CWND data saved to cwnd_data.csv");
        } catch (IOException e) {
            System.out.println("Error saving CWND data: " + e.getMessage());
        }

        // Save event log
        try (PrintWriter eventWriter = new PrintWriter(new File("tcp_events.log"))) {
            eventWriter.println("TCP Reno Event Log");
            eventWriter.println("==================");
            for (String event : eventLog) {
                eventWriter.println(event);
            }
            System.out.println("📁 Event log saved to tcp_events.log");
        } catch (IOException e) {
            System.out.println("Error saving event log: " + e.getMessage());
        }
    }
}