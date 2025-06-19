
import java.io.*;
import java.net.*;
import java.util.*;

public class ClientSide {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 3002;

  
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
            System.out.println("TCP Reno CSV export not implemented in original - data saved in separate files");
        }

        @Override
        public void printStatistics() {
            System.out.println("TCP Reno final state: " + getCurrentState());
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

        // Create both congestion control algorithms for comparison
        CongestionControl tahoeControl = new TahoeWrapper(8);
        CongestionControl renoControl = new RenoWrapper(8);

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT);
            System.out.println("Running DUAL ALGORITHM COMPARISON: TCP Tahoe vs TCP Reno");

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
            List<Integer> tahoeCwndPerRound = new ArrayList<>();
            List<Integer> renoCwndPerRound = new ArrayList<>();
            List<String> eventLog = new ArrayList<>();

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
                System.out.println("DUAL ALGORITHM COMPARISON: TCP Tahoe vs TCP Reno");
                System.out.println("=".repeat(60));
                output.writeUTF(file.getName());
                output.flush();

                int serverWindowSize = input.readInt();
                System.out.println("Server window size: " + serverWindowSize + " bytes");

                // Reset both congestion control algorithms for new file transfer
                tahoeControl.reset();
                renoControl.reset();

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

                                // Process the ACK with BOTH congestion control algorithms
                                if (ack == sequenceNumber) {
                                    // Expected ACK received - packet acknowledged
                                    tahoeControl.onAck(ack, true); // New ACK
                                    renoControl.onAck(ack, true);   // New ACK
                                    
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

                                    // Record CWND for both algorithms
                                    tahoeCwndPerRound.add(tahoeControl.getCwnd());
                                    renoCwndPerRound.add(renoControl.getCwnd());
                                    round++;

                                } else if (ack == lastAckReceived) {
                                    // Duplicate ACK
                                    consecutiveDupAcks++;
                                    tahoeControl.onAck(ack, false); // Duplicate ACK
                                    renoControl.onAck(ack, false);   // Duplicate ACK
                                    
                                    if (consecutiveDupAcks == 3) {
                                        // Triple duplicate ACK handling
                                        if (tahoeControl instanceof TahoeWrapper) {
                                            // TCP Tahoe treats triple dup ACK as packet loss
                                            ((TahoeWrapper) tahoeControl).tahoe.onTripleDupAck();
                                        }
                                        // For Reno, the onAck(false) call already handles this
                                        isRetransmission = true;
                                        eventLog.add("Round " + round + ": TRIPLE DUP ACK seq " + sequenceNumber + 
                                                   ", Tahoe CWND=" + tahoeControl.getCwnd() + 
                                                   ", Reno CWND=" + renoControl.getCwnd());
                                    }
                                    
                                } else {
                                    // Unexpected ACK
                                    tahoeControl.onAck(ack, true);
                                    renoControl.onAck(ack, true);
                                }

                            } catch (SocketTimeoutException e) {
                                tahoeControl.onTimeout();
                                renoControl.onTimeout();
                                isRetransmission = true;
                                consecutiveDupAcks = 0;
                                
                                eventLog.add("Round " + round + ": TIMEOUT seq " + sequenceNumber + 
                                           ", Tahoe CWND=" + tahoeControl.getCwnd() + 
                                           ", Reno CWND=" + renoControl.getCwnd());
                                // Continue the loop to resend the packet
                            }
                        }
                    }

                    // Send end of transmission signal
                    output.writeInt(-1);
                    output.writeInt(0);
                    output.flush();
                    System.out.println("Sent end of transmission signal");

                    // Handle final ACK
                    try {
                        int finalAck = input.readInt();
                        if (finalAck == -1) {
                            System.out.println("Server acknowledged end of transmission");
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout waiting for final ACK - assuming transmission complete");
                    }

                    // Save comparison data
                    saveComparisonData(sampleRTTs, estimatedRTTs, timeoutHistory, 
                                     tahoeCwndPerRound, renoCwndPerRound, eventLog, fileName);
                    
                    // Save algorithm-specific data for both algorithms
                    tahoeControl.writeToCSV("tcp_tahoe_detailed_data.csv");
                    renoControl.writeToCSV("tcp_reno_detailed_data.csv");
                    
                    System.out.println("\n" + "=".repeat(60));
                    System.out.println("TRANSMISSION COMPLETE - DUAL ALGORITHM COMPARISON");
                    System.out.println("File: " + fileName);
                    System.out.println("Total bytes sent: " + totalBytesSent);
                    System.out.println("Total rounds: " + (round - 1));
                    System.out.println();
                    System.out.println("TCP TAHOE RESULTS:");
                    System.out.println("  Final CWND: " + tahoeControl.getCwnd());
                    System.out.println("  Final SSThresh: " + tahoeControl.getSsthresh());
                    tahoeControl.printStatistics();
                    System.out.println();
                    System.out.println("TCP RENO RESULTS:");
                    System.out.println("  Final CWND: " + renoControl.getCwnd());
                    System.out.println("  Final SSThresh: " + renoControl.getSsthresh());
                    renoControl.printStatistics();
                    System.out.println("=".repeat(60));
                }
            }

        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }

        scanner.close();
    }

    private static void saveComparisonData(List<Long> sampleRTTs, List<Double> estimatedRTTs, 
                                         List<Integer> timeoutHistory, List<Integer> tahoeCwndPerRound,
                                         List<Integer> renoCwndPerRound, List<String> eventLog, 
                                         String fileName) {
        // Save RTT data
        try (PrintWriter writer = new PrintWriter(new File("comparison_rtt_data.csv"))) {
            writer.println("TimeIndex,SampleRTT,EstimatedRTT,TimeoutInterval");
            for (int i = 0; i < sampleRTTs.size(); i++) {
                writer.printf("%d,%d,%.2f,%d\n", i + 1, sampleRTTs.get(i), 
                             estimatedRTTs.get(i), timeoutHistory.get(i));
            }
            System.out.println("RTT data saved to comparison_rtt_data.csv");
        } catch (IOException e) {
            System.out.println("Error saving RTT data: " + e.getMessage());
        }

        // Save comparison CWND data (both algorithms in one file)
        try (PrintWriter cwndWriter = new PrintWriter(new File("comparison_cwnd_data.csv"))) {
            cwndWriter.println("Round,Tahoe_CWND,Reno_CWND");
            int maxRounds = Math.max(tahoeCwndPerRound.size(), renoCwndPerRound.size());
            for (int i = 0; i < maxRounds; i++) {
                int tahoeCwnd = i < tahoeCwndPerRound.size() ? tahoeCwndPerRound.get(i) : 0;
                int renoCwnd = i < renoCwndPerRound.size() ? renoCwndPerRound.get(i) : 0;
                cwndWriter.printf("%d,%d,%d\n", i + 1, tahoeCwnd, renoCwnd);
            }
            System.out.println("Comparison CWND data saved to comparison_cwnd_data.csv");
        } catch (IOException e) {
            System.out.println("Error saving comparison CWND data: " + e.getMessage());
        }

        // Save separate CWND files for individual algorithm analysis
        try (PrintWriter tahoeWriter = new PrintWriter(new File("tcp_tahoe_cwnd_data.csv"))) {
            tahoeWriter.println("Round,CWND");
            for (int i = 0; i < tahoeCwndPerRound.size(); i++) {
                tahoeWriter.printf("%d,%d\n", i + 1, tahoeCwndPerRound.get(i));
            }
            System.out.println("Tahoe CWND data saved to tcp_tahoe_cwnd_data.csv");
        } catch (IOException e) {
            System.out.println("Error saving Tahoe CWND data: " + e.getMessage());
        }

        try (PrintWriter renoWriter = new PrintWriter(new File("tcp_reno_cwnd_data.csv"))) {
            renoWriter.println("Round,CWND");
            for (int i = 0; i < renoCwndPerRound.size(); i++) {
                renoWriter.printf("%d,%d\n", i + 1, renoCwndPerRound.get(i));
            }
            System.out.println("Reno CWND data saved to tcp_reno_cwnd_data.csv");
        } catch (IOException e) {
            System.out.println("Error saving Reno CWND data: " + e.getMessage());
        }

        // Save event log with comparison data
        try (PrintWriter eventWriter = new PrintWriter(new File("comparison_events.log"))) {
            eventWriter.println("TCP COMPARISON EVENT LOG - TAHOE vs RENO");
            eventWriter.println("File: " + fileName);
            eventWriter.println("=".repeat(60));
            for (String event : eventLog) {
                eventWriter.println(event);
            }
            System.out.println("Comparison event log saved to comparison_events.log");
        } catch (IOException e) {
            System.out.println("Error saving event log: " + e.getMessage());
        }
    }
}