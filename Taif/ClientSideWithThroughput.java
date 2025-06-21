import java.io.*;
import java.net.*;
import java.util.*;

public class ClientSideWithThroughput {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 3002;

    // Throughput tracking class
    static class ThroughputTracker {
        private long totalBytesSent = 0;
        private long totalBytesAcked = 0;
        private long retransmissionBytes = 0;
        private long startTime;
        private long endTime;
        private List<Long> instantaneousRates = new ArrayList<>();
        private List<Long> timestamps = new ArrayList<>();
        private int MSS = 1460; // Maximum Segment Size (typical Ethernet)
        
        public void start() {
            startTime = System.currentTimeMillis();
        }
        
        public void end() {
            endTime = System.currentTimeMillis();
        }
        
        public void recordSent(int bytes) {
            totalBytesSent += bytes;
        }
        
        public void recordAcked(int bytes) {
            totalBytesAcked += bytes;
            recordInstantaneousRate(bytes);
        }
        
        public void recordRetransmission(int bytes) {
            retransmissionBytes += bytes;
        }
        
        private void recordInstantaneousRate(int bytes) {
            long currentTime = System.currentTimeMillis();
            timestamps.add(currentTime);
            
            // Calculate instantaneous rate over last 1 second
            if (timestamps.size() > 1) {
                long windowStart = currentTime - 1000; // 1 second window
                long bytesInWindow = 0;
                
                for (int i = timestamps.size() - 1; i >= 0; i--) {
                    if (timestamps.get(i) >= windowStart) {
                        bytesInWindow += bytes; // Simplified - in real implementation track bytes per timestamp
                    } else {
                        break;
                    }
                }
                
                if (bytesInWindow > 0) {
                    instantaneousRates.add(bytesInWindow * 8); // Convert to bits per second
                }
            }
        }
        
        // Calculate overall throughput in bps
        public double getOverallThroughput() {
            if (endTime <= startTime) return 0;
            double timeSeconds = (endTime - startTime) / 1000.0;
            return (totalBytesAcked * 8) / timeSeconds; // bits per second
        }
        
        // Calculate effective throughput (excluding retransmissions)
        public double getEffectiveThroughput() {
            if (endTime <= startTime) return 0;
            double timeSeconds = (endTime - startTime) / 1000.0;
            return ((totalBytesAcked - retransmissionBytes) * 8) / timeSeconds;
        }
        
        // Calculate goodput (application layer throughput)
        public double getGoodput() {
            return getEffectiveThroughput(); // Same as effective for our simple case
        }
        
        // Calculate theoretical throughput using current CWND and RTT
        public double getTheoreticalThroughput(int cwnd, double rtt) {
            if (rtt <= 0) return 0;
            return (MSS * cwnd * 8) / (rtt / 1000.0); // bits per second
        }
        
        // Get average instantaneous rate
        public double getAverageInstantaneousRate() {
            if (instantaneousRates.isEmpty()) return 0;
            return instantaneousRates.stream().mapToLong(Long::longValue).average().orElse(0);
        }
        
        // Calculate efficiency (how much of sent data was useful)
        public double getEfficiency() {
            if (totalBytesSent == 0) return 0;
            return ((double) (totalBytesSent - retransmissionBytes) / totalBytesSent) * 100;
        }
        
        public void printStatistics(String algorithm, int finalCwnd, double avgRtt) {
            System.out.println("\n=== " + algorithm + " THROUGHPUT STATISTICS ===");
            System.out.printf("Overall Throughput: %.2f Kbps (%.2f Mbps)\n", 
                getOverallThroughput() / 1000, getOverallThroughput() / 1_000_000);
            System.out.printf("Effective Throughput: %.2f Kbps (%.2f Mbps)\n", 
                getEffectiveThroughput() / 1000, getEffectiveThroughput() / 1_000_000);
            System.out.printf("Theoretical Max (CWND=%d, RTT=%.1fms): %.2f Kbps\n", 
                finalCwnd, avgRtt, getTheoreticalThroughput(finalCwnd, avgRtt) / 1000);
            System.out.printf("Transmission Efficiency: %.1f%%\n", getEfficiency());
            System.out.printf("Total Data Sent: %d bytes\n", totalBytesSent);
            System.out.printf("Total Data ACKed: %d bytes\n", totalBytesAcked);
            System.out.printf("Retransmissions: %d bytes\n", retransmissionBytes);
            System.out.printf("Transmission Time: %.2f seconds\n", (endTime - startTime) / 1000.0);
        }
        
        public void saveToCSV(String filename) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                writer.println("Metric,Value,Unit");
                writer.printf("Overall_Throughput_Kbps,%.2f,Kbps\n", getOverallThroughput() / 1000);
                writer.printf("Effective_Throughput_Kbps,%.2f,Kbps\n", getEffectiveThroughput() / 1000);
                writer.printf("Efficiency_Percent,%.1f,%%\n", getEfficiency());
                writer.printf("Total_Bytes_Sent,%d,bytes\n", totalBytesSent);
                writer.printf("Total_Bytes_ACKed,%d,bytes\n", totalBytesAcked);
                writer.printf("Retransmission_Bytes,%d,bytes\n", retransmissionBytes);
                writer.printf("Transmission_Time_Seconds,%.2f,seconds\n", (endTime - startTime) / 1000.0);
            } catch (IOException e) {
                System.err.println("Error saving throughput data: " + e.getMessage());
            }
        }
    }

    // Enhanced TCP implementations with throughput tracking
    static class TCPTahoe {
        private double cwnd = 1.0;
        private double ssthresh;
        private int roundNum = 0;
        private PrintWriter csvWriter;
        private ThroughputTracker throughputTracker;

        public TCPTahoe(double initialSsthresh, String csvFile) {
            this.ssthresh = initialSsthresh;
            this.throughputTracker = new ThroughputTracker();
            initCSV(csvFile);
        }

        private void initCSV(String csvFile) {
            try {
                csvWriter = new PrintWriter(new FileWriter(csvFile));
                csvWriter.println("Round,CWND,SSThresh,Phase,Event,Theoretical_Throughput_Kbps");
                csvWriter.flush();
            } catch (IOException e) {
                System.err.println("Error creating Tahoe CSV: " + e.getMessage());
            }
        }

        public void onAck(boolean isNewAck, int bytes, double rtt) {
            if (isNewAck) {
                roundNum++;
                throughputTracker.recordAcked(bytes);
                
                String phase = (cwnd < ssthresh) ? "Slow Start" : "Congestion Avoidance";
                
                if (cwnd < ssthresh) {
                    cwnd *= 2.0;
                } else {
                    cwnd += 1.0;
                }
                
                double theoreticalThroughput = throughputTracker.getTheoreticalThroughput(getCwnd(), rtt);
                recordData(phase, "ACK Received", theoreticalThroughput);
                System.out.printf("Tahoe %s: cwnd -> %d (Theoretical: %.1f Kbps)\n", 
                    phase, getCwnd(), theoreticalThroughput / 1000);
            }
        }

        public void onPacketLoss(int bytes, double rtt) {
            roundNum++;
            throughputTracker.recordRetransmission(bytes);
            ssthresh = Math.max(cwnd / 2.0, 2.0);
            cwnd = 1.0;
            
            double theoreticalThroughput = throughputTracker.getTheoreticalThroughput(getCwnd(), rtt);
            recordData("Slow Start", "Packet Loss", theoreticalThroughput);
            System.out.println("Tahoe Loss: ssthresh -> " + getSsthresh() + ", cwnd -> 1");
        }
        
        public void recordSent(int bytes) {
            throughputTracker.recordSent(bytes);
        }

        private void recordData(String phase, String event, double theoreticalThroughput) {
            if (csvWriter != null) {
                csvWriter.printf("%d,%d,%d,%s,%s,%.2f\n", 
                    roundNum, getCwnd(), getSsthresh(), phase, event, theoreticalThroughput / 1000);
                csvWriter.flush();
            }
        }

        public int getCwnd() { return (int) Math.ceil(cwnd); }
        public int getSsthresh() { return (int) Math.ceil(ssthresh); }
        public ThroughputTracker getThroughputTracker() { return throughputTracker; }
        
        public void startTracking() { throughputTracker.start(); }
        public void endTracking() { throughputTracker.end(); }
        
        public void reset() {
            cwnd = 1.0;
            ssthresh = 8.0;
            roundNum = 0;
            throughputTracker = new ThroughputTracker();
        }

        public void close() {
            if (csvWriter != null) csvWriter.close();
        }
    }

    static class TCPReno {
        private int cwnd = 1;
        private int ssthresh;
        private int dupAckCount = 0;
        private boolean inFastRecovery = false;
        private int roundNum = 0;
        private PrintWriter csvWriter;
        private ThroughputTracker throughputTracker;

        public TCPReno(int initialSsthresh, String csvFile) {
            this.ssthresh = initialSsthresh;
            this.throughputTracker = new ThroughputTracker();
            initCSV(csvFile);
        }

        private void initCSV(String csvFile) {
            try {
                csvWriter = new PrintWriter(new FileWriter(csvFile));
                csvWriter.println("Round,CWND,SSThresh,Phase,Event,FastRecovery,DupACKs,Theoretical_Throughput_Kbps");
                csvWriter.flush();
            } catch (IOException e) {
                System.err.println("Error creating Reno CSV: " + e.getMessage());
            }
        }

        public void onAck(boolean isNewAck, int bytes, double rtt) {
            roundNum++;
            
            if (!isNewAck) {
                dupAckCount++;
                if (dupAckCount == 3 && !inFastRecovery) {
                    throughputTracker.recordRetransmission(bytes);
                    ssthresh = Math.max(cwnd / 2, 2);
                    cwnd = ssthresh + 3;
                    inFastRecovery = true;
                    
                    double theoreticalThroughput = throughputTracker.getTheoreticalThroughput(cwnd, rtt);
                    recordData("Fast Recovery", "Triple Dup ACK", theoreticalThroughput);
                    System.out.printf("Reno Fast Recovery: ssthresh -> %d, cwnd -> %d (Theoretical: %.1f Kbps)\n", 
                        ssthresh, cwnd, theoreticalThroughput / 1000);
                } else if (inFastRecovery) {
                    cwnd++;
                    double theoreticalThroughput = throughputTracker.getTheoreticalThroughput(cwnd, rtt);
                    recordData("Fast Recovery", "Additional Dup ACK", theoreticalThroughput);
                }
            } else {
                throughputTracker.recordAcked(bytes);
                dupAckCount = 0;
                
                if (inFastRecovery) {
                    cwnd = ssthresh;
                    inFastRecovery = false;
                    double theoreticalThroughput = throughputTracker.getTheoreticalThroughput(cwnd, rtt);
                    recordData("Congestion Avoidance", "Exit Fast Recovery", theoreticalThroughput);
                } else {
                    String phase = (cwnd < ssthresh) ? "Slow Start" : "Congestion Avoidance";
                    
                    if (cwnd < ssthresh) {
                        cwnd *= 2;
                    } else {
                        cwnd += 1;
                    }
                    
                    double theoreticalThroughput = throughputTracker.getTheoreticalThroughput(cwnd, rtt);
                    recordData(phase, "ACK Received", theoreticalThroughput);
                    System.out.printf("Reno %s: cwnd -> %d (Theoretical: %.1f Kbps)\n", 
                        phase, cwnd, theoreticalThroughput / 1000);
                }
            }
        }

        public void onTimeout(int bytes, double rtt) {
            roundNum++;
            throughputTracker.recordRetransmission(bytes);
            ssthresh = Math.max(cwnd / 2, 2);
            cwnd = 1;
            inFastRecovery = false;
            dupAckCount = 0;
            
            double theoreticalThroughput = throughputTracker.getTheoreticalThroughput(cwnd, rtt);
            recordData("Slow Start", "Timeout", theoreticalThroughput);
            System.out.println("Reno Timeout: ssthresh -> " + ssthresh + ", cwnd -> 1");
        }
        
        public void recordSent(int bytes) {
            throughputTracker.recordSent(bytes);
        }

        private void recordData(String phase, String event, double theoreticalThroughput) {
            if (csvWriter != null) {
                csvWriter.printf("%d,%d,%d,%s,%s,%s,%d,%.2f\n", 
                    roundNum, cwnd, ssthresh, phase, event, inFastRecovery, dupAckCount, theoreticalThroughput / 1000);
                csvWriter.flush();
            }
        }

        public int getCwnd() { return cwnd; }
        public int getSsthresh() { return ssthresh; }
        public ThroughputTracker getThroughputTracker() { return throughputTracker; }
        
        public void startTracking() { throughputTracker.start(); }
        public void endTracking() { throughputTracker.end(); }
        
        public void reset() {
            cwnd = 1;
            ssthresh = 8;
            dupAckCount = 0;
            inFastRecovery = false;
            roundNum = 0;
            throughputTracker = new ThroughputTracker();
        }

        public void close() {
            if (csvWriter != null) csvWriter.close();
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String initialFile = args.length > 0 ? args[0] : null;

        TCPTahoe tahoe = new TCPTahoe(8.0, "tcp_tahoe_simulation.csv");
        TCPReno reno = new TCPReno(8, "tcp_reno_simulation.csv");

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("Connected to server. Running TCP Tahoe vs Reno with Throughput Analysis.");
            
            double estimatedRTT = 500, devRTT = 100;
            List<Double> rttSamples = new ArrayList<>();
            int timeoutInterval = (int)(estimatedRTT + 4 * devRTT);
            socket.setSoTimeout(Math.max(timeoutInterval, 100));

            while (true) {
                String fileName = initialFile;
                if (fileName == null) {
                    System.out.print("\nEnter file name (or 'exit'): ");
                    fileName = scanner.nextLine().trim();
                } else {
                    initialFile = null;
                }

                if (fileName.equalsIgnoreCase("exit")) {
                    output.writeUTF("__EXIT__");
                    break;
                }

                File file = new File(fileName);
                if (!file.exists()) {
                    System.out.println("File not found: " + fileName);
                    continue;
                }

                tahoe.reset();
                reno.reset();
                tahoe.startTracking();
                reno.startTracking();
                rttSamples.clear();

                System.out.println("\n=== Sending: " + fileName + " ===");
                output.writeUTF(file.getName());
                
                int serverWindowSize = input.readInt();
                System.out.println("Server window size: " + serverWindowSize);

                try (FileInputStream fileInput = new FileInputStream(file)) {
                    byte[] buffer = new byte[serverWindowSize];
                    int seqNum = 0, lastAck = -1, dupAckCount = 0;
                    int bytesRead;

                    while ((bytesRead = fileInput.read(buffer)) != -1) {
                        byte[] packet = Arrays.copyOf(buffer, bytesRead);
                        boolean packetAcked = false;

                        while (!packetAcked) {
                            try {
                                long sendTime = System.currentTimeMillis();
                                
                                // Record bytes sent for both algorithms
                                tahoe.recordSent(bytesRead);
                                reno.recordSent(bytesRead);
                                
                                output.writeInt(seqNum);
                                output.writeInt(bytesRead);
                                output.write(packet);
                                output.flush();

                                int ack = input.readInt();
                                long rtt = System.currentTimeMillis() - sendTime;
                                rttSamples.add((double) rtt);

                                if (ack == seqNum) {
                                    tahoe.onAck(true, bytesRead, rtt);
                                    reno.onAck(true, bytesRead, rtt);
                                    
                                    estimatedRTT = 0.875 * estimatedRTT + 0.125 * rtt;
                                    devRTT = 0.75 * devRTT + 0.25 * Math.abs(rtt - estimatedRTT);
                                    timeoutInterval = Math.max((int)(estimatedRTT + 4 * devRTT), 100);
                                    socket.setSoTimeout(timeoutInterval);

                                    packetAcked = true;
                                    seqNum++;
                                    lastAck = ack;
                                    dupAckCount = 0;

                                } else if (ack == lastAck) {
                                    dupAckCount++;
                                    tahoe.onAck(false, 0, rtt);
                                    reno.onAck(false, 0, rtt);
                                    
                                    if (dupAckCount == 3) {
                                        tahoe.onPacketLoss(bytesRead, rtt);
                                        System.out.println("Triple dup ACK detected!");
                                    }
                                }

                            } catch (SocketTimeoutException e) {
                                System.out.println("Timeout! Retransmitting seq " + seqNum);
                                tahoe.onPacketLoss(bytesRead, estimatedRTT);
                                reno.onTimeout(bytesRead, estimatedRTT);
                                dupAckCount = 0;
                            }
                        }
                    }

                    // End tracking
                    tahoe.endTracking();
                    reno.endTracking();

                    // Send end signal
                    output.writeInt(-1);
                    output.writeInt(0);
                    output.flush();

                    try {
                        int finalAck = input.readInt();
                        if (finalAck == -1) {
                            System.out.println("Transmission complete!");
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Final ACK timeout - assuming complete");
                    }

                    // Calculate average RTT
                    double avgRTT = rttSamples.stream().mapToDouble(Double::doubleValue).average().orElse(estimatedRTT);

                    // Print throughput statistics
                    tahoe.getThroughputTracker().printStatistics("TCP TAHOE", tahoe.getCwnd(), avgRTT);
                    reno.getThroughputTracker().printStatistics("TCP RENO", reno.getCwnd(), avgRTT);
                    
                    // Save throughput data
                    tahoe.getThroughputTracker().saveToCSV("tcp_tahoe_throughput.csv");
                    reno.getThroughputTracker().saveToCSV("tcp_reno_throughput.csv");
                    
                    System.out.println("\n=== COMPARISON ===");
                    System.out.printf("Tahoe Throughput: %.2f Kbps\n", 
                        tahoe.getThroughputTracker().getOverallThroughput() / 1000);
                    System.out.printf("Reno Throughput:  %.2f Kbps\n", 
                        reno.getThroughputTracker().getOverallThroughput() / 1000);
                    
                    double improvement = ((reno.getThroughputTracker().getOverallThroughput() - 
                                          tahoe.getThroughputTracker().getOverallThroughput()) / 
                                          tahoe.getThroughputTracker().getOverallThroughput()) * 100;
                    System.out.printf("Reno vs Tahoe Improvement: %.1f%%\n", improvement);
                }
            }

        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        } finally {
            tahoe.close();
            reno.close();
            scanner.close();
        }
    }
}