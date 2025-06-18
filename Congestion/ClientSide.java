// ============================
// ✅ ClientSide.java (TCP Tahoe + Reno Selection)
// ============================
import java.io.*;
import java.net.*;
import java.util.*;

public class ClientSide {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 3002;

    // Common interface for congestion control algorithms
    interface CongestionControl {
        void onAck(int ackNumber, boolean isNewAck);
        void onTimeout();
        void reset();
        int getCwnd();
        int getSsthresh();
        String getCurrentState();
        String getAlgorithmName();
        void writeToCSV(String filename);
        void printStatistics();
    }

    // Wrapper for Reno to implement the interface
    static class RenoWrapper implements CongestionControl {
        private Reno reno;

        public RenoWrapper(int initialSsthresh) {
            this.reno = new Reno(initialSsthresh);
        }

        @Override
        public void onAck(int ackNumber, boolean isNewAck) {
            reno.onAck(ackNumber, isNewAck);
        }

        @Override
        public void onTimeout() {
            reno.onTimeout();
        }

        @Override
        public void reset() {
            reno.reset();
        }

        @Override
        public int getCwnd() {
            return reno.getCwnd();
        }

        @Override
        public int getSsthresh() {
            return reno.getSsthresh();
        }

        @Override
        public String getCurrentState() {
            return reno.getCurrentState();
        }

        @Override
        public String getAlgorithmName() {
            return "TCP Reno";
        }

        @Override
        public void writeToCSV(String filename) {
            // Reno doesn't have built-in CSV export, so we'll create a simple one
            System.out.println("📁 TCP Reno CSV export not implemented in original - data saved in separate files");
        }

        @Override
        public void printStatistics() {
            System.out.println("📊 TCP Reno final state: " + getCurrentState());
        }
    }

    // Wrapper for Tahoe to implement the interface
    static class TahoeWrapper implements CongestionControl {
        private TCPTahoe tahoe;

        public TahoeWrapper(int initialSsthresh) {
            this.tahoe = new TCPTahoe(initialSsthresh);
        }

        @Override
        public void onAck(int ackNumber, boolean isNewAck) {
            tahoe.onAck(ackNumber, isNewAck);
        }

        @Override
        public void onTimeout() {
            tahoe.onTimeout();
        }

        @Override
        public void reset() {
            tahoe.reset();
        }

        @Override
        public int getCwnd() {
            return tahoe.getCwnd();
        }

        @Override
        public int getSsthresh() {
            return tahoe.getSsthresh();
        }

        @Override
        public String getCurrentState() {
            return tahoe.getCurrentState();
        }

        @Override
        public String getAlgorithmName() {
            return "TCP Tahoe";
        }

        @Override
        public void writeToCSV(String filename) {
            tahoe.writeToCSV(filename);
        }

        @Override
        public void printStatistics() {
            tahoe.printStatistics();
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        boolean continueRunning = true;
        String initialFileName = args.length > 0 ? args[0] : null;

        // Choose congestion control algorithm
        CongestionControl congestionControl = chooseCongestionControlAlgorithm(scanner);

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT);
            System.out.println("Using: " + congestionControl.getAlgorithmName());

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

            int round = 1;

            while (continueRunning) {
                String fileName;

                if (initialFileName != null) {
                    fileName = initialFileName;
                    initialFileName = null;
                } else {
                    System.out.print("\nEnter file name to send (or 'exit' to quit, 'switch' to change algorithm): ");
                    fileName = scanner.nextLine().trim();
                }

                if (fileName.equalsIgnoreCase("exit")) {
                    output.writeUTF("__EXIT__");
                    output.flush();
                    System.out.println("Exiting client...");
                    break;
                } else if (fileName.equalsIgnoreCase("switch")) {
                    congestionControl = chooseCongestionControlAlgorithm(scanner);
                    System.out.println("Switched to: " + congestionControl.getAlgorithmName());
                    continue;
                }

                File file = new File(fileName);
                if (!file.exists() || !file.isFile()) {
                    System.out.println("File doesn't exist: " + fileName);
                    continue;
                }

                System.out.println("\nSending file: " + fileName);
                System.out.println("Using algorithm: " + congestionControl.getAlgorithmName());
                System.out.println("=".repeat(60));
                output.writeUTF(file.getName());
                output.flush();

                int serverWindowSize = input.readInt();
                System.out.println("Server window size: " + serverWindowSize + " bytes");

                // Reset congestion control for new file transfer
                congestionControl.reset();

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

                                // Process the ACK with the selected congestion control algorithm
                                if (ack == sequenceNumber) {
                                    // Expected ACK received - packet acknowledged
                                    congestionControl.onAck(ack, true); // New ACK
                                    
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
                                    cwndPerRound.add(congestionControl.getCwnd());
                                    round++;

                                } else if (ack == lastAckReceived) {
                                    // Duplicate ACK
                                    consecutiveDupAcks++;
                                    congestionControl.onAck(ack, false); // Duplicate ACK
                                    
                                    if (consecutiveDupAcks == 3) {
                                        // Triple duplicate ACK handling
                                        if (congestionControl instanceof TahoeWrapper) {
                                            // TCP Tahoe treats triple dup ACK as packet loss
                                            ((TahoeWrapper) congestionControl).tahoe.onTripleDupAck();
                                        }
                                        // For Reno, the onAck(false) call already handles this
                                        isRetransmission = true;
                                        eventLog.add("Round " + round + ": TRIPLE DUP ACK seq " + sequenceNumber + 
                                                   ", CWND=" + congestionControl.getCwnd());
                                    }
                                    
                                } else {
                                    // Unexpected ACK
                                    congestionControl.onAck(ack, true);
                                }

                            } catch (SocketTimeoutException e) {
                                congestionControl.onTimeout();
                                isRetransmission = true;
                                consecutiveDupAcks = 0;
                                
                                eventLog.add("Round " + round + ": TIMEOUT seq " + sequenceNumber + 
                                           ", CWND=" + congestionControl.getCwnd());
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

                    // Save detailed data based on algorithm
                    String algorithmPrefix = congestionControl.getAlgorithmName().toLowerCase().replace(" ", "_");
                    saveTransmissionData(sampleRTTs, estimatedRTTs, timeoutHistory, cwndPerRound, 
                                       eventLog, algorithmPrefix);
                    
                    // Save algorithm-specific data
                    congestionControl.writeToCSV(algorithmPrefix + "_detailed_data.csv");
                    
                    System.out.println("\n" + "=".repeat(60));
                    System.out.println("📊 TRANSMISSION COMPLETE");
                    System.out.println("Algorithm: " + congestionControl.getAlgorithmName());
                    System.out.println("Total bytes sent: " + totalBytesSent);
                    System.out.println("Total rounds: " + (round - 1));
                    System.out.println("Final CWND: " + congestionControl.getCwnd());
                    System.out.println("Final SSThresh: " + congestionControl.getSsthresh());
                    
                    // Print algorithm-specific statistics
                    congestionControl.printStatistics();
                    
                    System.out.println("=".repeat(60));
                }
            }

        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }

        scanner.close();
    }

    private static CongestionControl chooseCongestionControlAlgorithm(Scanner scanner) {
        while (true) {
            System.out.println("\n" + "=".repeat(50));
            System.out.println("🔧 CHOOSE CONGESTION CONTROL ALGORITHM");
            System.out.println("=".repeat(50));
            System.out.println("1. TCP Tahoe (Simple, no fast recovery)");
            System.out.println("2. TCP Reno (Fast retransmit & fast recovery)");
            System.out.print("Enter your choice (1 or 2): ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    System.out.println("✅ Selected: TCP Tahoe");
                    return new TahoeWrapper(8); // Initial ssthresh of 8
                case "2":
                    System.out.println("✅ Selected: TCP Reno");
                    return new RenoWrapper(8); // Initial ssthresh of 8
                default:
                    System.out.println("❌ Invalid choice. Please enter 1 or 2.");
                    break;
            }
        }
    }

    private static void saveTransmissionData(List<Long> sampleRTTs, List<Double> estimatedRTTs, 
                                           List<Integer> timeoutHistory, List<Integer> cwndPerRound,
                                           List<String> eventLog, String algorithmPrefix) {
        // Save RTT data
        try (PrintWriter writer = new PrintWriter(new File(algorithmPrefix + "_rtt_data.csv"))) {
            writer.println("TimeIndex,SampleRTT,EstimatedRTT,TimeoutInterval");
            for (int i = 0; i < sampleRTTs.size(); i++) {
                writer.printf("%d,%d,%.2f,%d\n", i + 1, sampleRTTs.get(i), 
                             estimatedRTTs.get(i), timeoutHistory.get(i));
            }
            System.out.println("📁 RTT data saved to " + algorithmPrefix + "_rtt_data.csv");
        } catch (IOException e) {
            System.out.println("Error saving RTT data: " + e.getMessage());
        }

        // Save CWND data
        try (PrintWriter cwndWriter = new PrintWriter(new File(algorithmPrefix + "_cwnd_data.csv"))) {
            cwndWriter.println("Round,CWND");
            for (int i = 0; i < cwndPerRound.size(); i++) {
                cwndWriter.printf("%d,%d\n", i + 1, cwndPerRound.get(i));
            }
            System.out.println("📁 CWND data saved to " + algorithmPrefix + "_cwnd_data.csv");
        } catch (IOException e) {
            System.out.println("Error saving CWND data: " + e.getMessage());
        }

        // Save event log
        try (PrintWriter eventWriter = new PrintWriter(new File(algorithmPrefix + "_events.log"))) {
            eventWriter.println("TCP Event Log - " + algorithmPrefix.toUpperCase().replace("_", " "));
            eventWriter.println("=".repeat(60));
            for (String event : eventLog) {
                eventWriter.println(event);
            }
            System.out.println("📁 Event log saved to " + algorithmPrefix + "_events.log");
        } catch (IOException e) {
            System.out.println("Error saving event log: " + e.getMessage());
        }
    }
}