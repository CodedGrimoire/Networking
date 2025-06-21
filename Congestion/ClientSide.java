import java.io.*;
import java.net.*;
import java.util.*;

public class ClientSide {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 3002;

    // TCP Tahoe Implementation
    static class TCPTahoe {
        private double cwnd = 1.0;
        private double ssthresh;
        private int roundNum = 0;
        private String csvFile;
        private PrintWriter csvWriter;

        public TCPTahoe(double initialSsthresh, String csvFile) {
            this.ssthresh = initialSsthresh;
            this.csvFile = csvFile;
            initCSV();
        }

        private void initCSV() {
            try {
                csvWriter = new PrintWriter(new FileWriter(csvFile));
                csvWriter.println("Round,CWND,SSThresh,Phase,Event");
                csvWriter.flush();
            } catch (IOException e) {
                System.err.println("Error creating Tahoe CSV: " + e.getMessage());
            }
        }

        public void onAck(boolean isNewAck) {
            if (isNewAck) {
                roundNum++;
                String phase = (cwnd < ssthresh) ? "Slow Start" : "Congestion Avoidance";
                
                if (cwnd < ssthresh) {
                    cwnd *= 2.0; // Slow start - exponential
                } else {
                    cwnd += 1.0; // Congestion avoidance - linear
                }
                
                recordData(phase, "ACK Received");
                System.out.println("Tahoe " + phase + ": cwnd -> " + getCwnd());
            }
        }

        public void onPacketLoss() {
            roundNum++;
            ssthresh = Math.max(cwnd / 2.0, 2.0);
            cwnd = 1.0;
            recordData("Slow Start", "Packet Loss");
            System.out.println("Tahoe Loss: ssthresh -> " + getSsthresh() + ", cwnd -> 1");
        }

        private void recordData(String phase, String event) {
            if (csvWriter != null) {
                csvWriter.printf("%d,%d,%d,%s,%s\n", roundNum, getCwnd(), getSsthresh(), phase, event);
                csvWriter.flush();
            }
        }

        public int getCwnd() { return (int) Math.ceil(cwnd); }
        public int getSsthresh() { return (int) Math.ceil(ssthresh); }
        
        public void reset() {
            cwnd = 1.0;
            ssthresh = 8.0;
            roundNum = 0;
            initCSV();
        }

        public void close() {
            if (csvWriter != null) csvWriter.close();
        }
    }

    // TCP Reno Implementation
    static class TCPReno {
        private int cwnd = 1;
        private int ssthresh;
        private int dupAckCount = 0;
        private boolean inFastRecovery = false;
        private int roundNum = 0;
        private String csvFile;
        private PrintWriter csvWriter;

        public TCPReno(int initialSsthresh, String csvFile) {
            this.ssthresh = initialSsthresh;
            this.csvFile = csvFile;
            initCSV();
        }

        private void initCSV() {
            try {
                csvWriter = new PrintWriter(new FileWriter(csvFile));
                csvWriter.println("Round,CWND,SSThresh,Phase,Event,FastRecovery,DupACKs");
                csvWriter.flush();
            } catch (IOException e) {
                System.err.println("Error creating Reno CSV: " + e.getMessage());
            }
        }

        public void onAck(boolean isNewAck) {
            roundNum++;
            
            if (!isNewAck) {
                // Duplicate ACK
                dupAckCount++;
                if (dupAckCount == 3 && !inFastRecovery) {
                    // Fast retransmit/recovery
                    ssthresh = Math.max(cwnd / 2, 2);
                    cwnd = ssthresh + 3;
                    inFastRecovery = true;
                    recordData("Fast Recovery", "Triple Dup ACK");
                    System.out.println("Reno Fast Recovery: ssthresh -> " + ssthresh + ", cwnd -> " + cwnd);
                } else if (inFastRecovery) {
                    cwnd++;
                    recordData("Fast Recovery", "Additional Dup ACK");
                    System.out.println("Reno Fast Recovery: cwnd -> " + cwnd);
                }
            } else {
                // New ACK
                dupAckCount = 0;
                
                if (inFastRecovery) {
                    // Exit fast recovery
                    cwnd = ssthresh;
                    inFastRecovery = false;
                    recordData("Congestion Avoidance", "Exit Fast Recovery");
                    System.out.println("Reno Exit Fast Recovery: cwnd -> " + cwnd);
                } else {
                    String phase = (cwnd < ssthresh) ? "Slow Start" : "Congestion Avoidance";
                    
                    if (cwnd < ssthresh) {
                        cwnd *= 2; // Slow start - exponential
                    } else {
                        cwnd += 1; // Congestion avoidance - linear
                    }
                    
                    recordData(phase, "ACK Received");
                    System.out.println("Reno " + phase + ": cwnd -> " + cwnd);
                }
            }
        }

        public void onTimeout() {
            roundNum++;
            ssthresh = Math.max(cwnd / 2, 2);
            cwnd = 1;
            inFastRecovery = false;
            dupAckCount = 0;
            recordData("Slow Start", "Timeout");
            System.out.println("Reno Timeout: ssthresh -> " + ssthresh + ", cwnd -> 1");
        }

        private void recordData(String phase, String event) {
            if (csvWriter != null) {
                csvWriter.printf("%d,%d,%d,%s,%s,%s,%d\n", 
                    roundNum, cwnd, ssthresh, phase, event, inFastRecovery, dupAckCount);
                csvWriter.flush();
            }
        }

        public int getCwnd() { return cwnd; }
        public int getSsthresh() { return ssthresh; }
        
        public void reset() {
            cwnd = 1;
            ssthresh = 8;
            dupAckCount = 0;
            inFastRecovery = false;
            roundNum = 0;
            initCSV();
        }

        public void close() {
            if (csvWriter != null) csvWriter.close();
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String initialFile = args.length > 0 ? args[0] : null;

        // Initialize both algorithms
        TCPTahoe tahoe = new TCPTahoe(8.0, "tcp_tahoe_simulation.csv");
        TCPReno reno = new TCPReno(8, "tcp_reno_simulation.csv");

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("Connected to server. Running TCP Tahoe vs Reno comparison.");
            
            // RTT estimation variables
            double estimatedRTT = 500, devRTT = 100;
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

                // Reset algorithms for new transmission
                tahoe.reset();
                reno.reset();

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
                                
                                output.writeInt(seqNum);
                                output.writeInt(bytesRead);
                                output.write(packet);
                                output.flush();

                                int ack = input.readInt();
                                long rtt = System.currentTimeMillis() - sendTime;

                                if (ack == seqNum) {
                                    // Expected ACK - both algorithms process as new ACK
                                    tahoe.onAck(true);
                                    reno.onAck(true);
                                    
                                    // Update RTT estimates
                                    estimatedRTT = 0.875 * estimatedRTT + 0.125 * rtt;
                                    devRTT = 0.75 * devRTT + 0.25 * Math.abs(rtt - estimatedRTT);
                                    timeoutInterval = Math.max((int)(estimatedRTT + 4 * devRTT), 100);
                                    socket.setSoTimeout(timeoutInterval);

                                    packetAcked = true;
                                    seqNum++;
                                    lastAck = ack;
                                    dupAckCount = 0;

                                } else if (ack == lastAck) {
                                    // Duplicate ACK
                                    dupAckCount++;
                                    tahoe.onAck(false); // Tahoe just notes the dup ACK
                                    reno.onAck(false);  // Reno handles fast retransmit/recovery
                                    
                                    if (dupAckCount == 3) {
                                        tahoe.onPacketLoss(); // Tahoe treats as loss
                                        System.out.println("Triple dup ACK detected - packet loss!");
                                    }
                                }

                            } catch (SocketTimeoutException e) {
                                // Timeout - both algorithms handle as packet loss
                                System.out.println("Timeout! Retransmitting seq " + seqNum);
                                tahoe.onPacketLoss();
                                reno.onTimeout();
                                dupAckCount = 0;
                            }
                        }
                    }

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

                    System.out.println("\n=== RESULTS ===");
                    System.out.println("Tahoe - Final CWND: " + tahoe.getCwnd() + ", SSThresh: " + tahoe.getSsthresh());
                    System.out.println("Reno  - Final CWND: " + reno.getCwnd() + ", SSThresh: " + reno.getSsthresh());
                    System.out.println("Data saved to tcp_tahoe_simulation.csv and tcp_reno_simulation.csv");
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