import java.util.*;
import java.io.*;

public class Reno {
    private int cwnd;
    private int ssthresh;
    private final int initialSsthresh;
    private int duplicateAckCount;
    private int lastAckReceived;
    private boolean inFastRecovery;
    private String csvFilename; // Auto-save filename
    
    // History tracking for CSV export
    private List<Integer> rounds;
    private List<Integer> cwndValues;
    private List<Integer> ssthreshValues;
    private List<String> phases;
    private List<String> events;
    private List<Boolean> fastRecoveryStatus;
    private int roundNum;

    public Reno(int initialSsthresh) {
        this(initialSsthresh, "tcp_reno_simulation.csv"); // Default filename
    }
    
    public Reno(int initialSsthresh, String csvFilename) {
        this.initialSsthresh = initialSsthresh;
        this.csvFilename = csvFilename;
        this.cwnd = 1; // Initial congestion window
        this.ssthresh = initialSsthresh;
        this.duplicateAckCount = 0;
        this.lastAckReceived = -1;
        this.inFastRecovery = false;
        this.roundNum = 0;
        
        // Initialize history lists
        this.rounds = new ArrayList<>();
        this.cwndValues = new ArrayList<>();
        this.ssthreshValues = new ArrayList<>();
        this.phases = new ArrayList<>();
        this.events = new ArrayList<>();
        this.fastRecoveryStatus = new ArrayList<>();
        
        // Create initial CSV file with headers
        initializeCSVFile();
    }

    public int getCwnd() {
        return cwnd;
    }

    public int getSsthresh() {
        return ssthresh;
    }

    public boolean isInFastRecovery() {
        return inFastRecovery;
    }
    
   
    public void setCSVFilename(String filename) {
        this.csvFilename = filename;
    }

    
    public void onAck(int ackNumber, boolean isNewAck) {
        if (!isNewAck) {
            // Duplicate ACK received
            if (ackNumber == lastAckReceived) {
                duplicateAckCount++;
                System.out.println("Duplicate ACK #" + duplicateAckCount + " for seq: " + ackNumber);
                
                if (duplicateAckCount == 3 && !inFastRecovery) {
                    // Triple duplicate ACK - trigger fast retransmit/fast recovery
                    roundNum++;
                    System.out.println(" Triple duplicate ACKs — Fast Retransmit triggered.");
                    ssthresh = Math.max(cwnd / 2, 1);
                    cwnd = ssthresh + 3; // Fast recovery: ssthresh + 3 (for the 3 dup ACKs)
                    inFastRecovery = true;
                    System.out.println("Updated ssthresh: " + ssthresh + ", cwnd set to: " + cwnd + " (Fast Recovery)");
                    
                    // Record history and auto-save to CSV
                    recordHistory("Fast Recovery", "Triple Dup ACK");
                } else if (inFastRecovery) {
                    // Additional duplicate ACK during fast recovery
                    roundNum++;
                    cwnd++;
                    System.out.println("Fast Recovery: Additional dup ACK, cwnd -> " + cwnd);
                    
                    // Record history and auto-save to CSV
                    recordHistory("Fast Recovery", "Additional Dup ACK");
                }
            }
        } else {
            // New ACK received
            roundNum++;
            lastAckReceived = ackNumber;
            
            if (inFastRecovery) {
                // Exiting fast recovery
                cwnd = ssthresh;
                inFastRecovery = false;
                duplicateAckCount = 0;
                System.out.println(" Exiting Fast Recovery: cwnd -> " + cwnd);
                
                // Record history and auto-save to CSV
                recordHistory("Congestion Avoidance", "Exit Fast Recovery");
            } else {
                // Normal ACK processing
                duplicateAckCount = 0;
                String phase;
                String event = "ACK Received";
                
                if (cwnd < ssthresh) {
                    // Slow start (exponential growth) - double every RTT
                    cwnd = cwnd * 2;
                    phase = "Slow Start";
                    System.out.println("Slow Start: cwnd -> " + cwnd);
                } else {
                    // Congestion avoidance (linear growth) - increase by 1 every RTT
                    cwnd = cwnd + 1;
                    phase = "Congestion Avoidance";
                    System.out.println("Congestion Avoidance: cwnd -> " + cwnd);
                }
                
                // Record history and auto-save to CSV
                recordHistory(phase, event);
            }
        }
    }

    public void onTimeout() {
        System.out.println("Timeout occurred — treating as congestion signal.");
        roundNum++;
        
        ssthresh = Math.max(cwnd / 2, 1);
        cwnd = 1;  // Start over with slow start
        inFastRecovery = false;
        duplicateAckCount = 0;
        System.out.println("Updated ssthresh: " + ssthresh + ", cwnd reset to 1 (Slow Start)");
        
        // Record history and auto-save to CSV
        recordHistory("Slow Start", "Timeout");
    }

    
    public void reset() {
        this.cwnd = 1;
        this.ssthresh = initialSsthresh;
        this.duplicateAckCount = 0;
        this.lastAckReceived = -1;
        this.inFastRecovery = false;
        this.roundNum = 0;
        
        // Clear history
        rounds.clear();
        cwndValues.clear();
        ssthreshValues.clear();
        phases.clear();
        events.clear();
        fastRecoveryStatus.clear();
        
        System.out.println("TCP Reno reset: cwnd=1, ssthresh=" + ssthresh);
        
        // Reinitialize CSV file
        initializeCSVFile();
    }
    
    
    public String getCurrentState() {
        String phase = inFastRecovery ? "Fast Recovery" : 
                      (cwnd < ssthresh ? "Slow Start" : "Congestion Avoidance");
        return String.format("CWND=%d, SSThresh=%d, Phase=%s, DupACKs=%d", 
                           cwnd, ssthresh, phase, duplicateAckCount);
    }
    
   
    public String getCurrentPhase() {
        if (inFastRecovery) return "Fast Recovery";
        return (cwnd < ssthresh) ? "Slow Start" : "Congestion Avoidance";
    }
    
    
    private void initializeCSVFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFilename))) {
            writer.println("Round,CWND,SSThresh,Phase,Event,FastRecovery,DupACKs");
            System.out.println(" Initialized CSV file: " + csvFilename);
        } catch (IOException e) {
            System.err.println("Error initializing CSV file: " + e.getMessage());
        }
    }
    
    
    private void recordHistory(String phase, String event) {
        // Add to memory lists
        rounds.add(roundNum);
        cwndValues.add(cwnd);
        ssthreshValues.add(ssthresh);
        phases.add(phase);
        events.add(event);
        fastRecoveryStatus.add(inFastRecovery);
        
        // Auto-save to CSV file immediately
        appendToCSV(roundNum, cwnd, ssthresh, phase, event, inFastRecovery, duplicateAckCount);
        
        System.out.println("Round " + roundNum + " data saved to " + csvFilename);
    }
    
   
    private void appendToCSV(int round, int cwnd, int ssthresh, String phase, String event, boolean fastRecovery, int dupAcks) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFilename, true))) {
            writer.printf("%d,%d,%d,%s,%s,%s,%d%n", round, cwnd, ssthresh, phase, event, fastRecovery, dupAcks);
        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
    }
    
    
    public String exportToCSV() {
        StringBuilder csv = new StringBuilder();
        csv.append("Round,CWND,SSThresh,Phase,Event,FastRecovery,DupACKs\n");
        
        for (int i = 0; i < rounds.size(); i++) {
            csv.append(String.format("%d,%d,%d,%s,%s,%s,%d\n",
                rounds.get(i),
                cwndValues.get(i),
                ssthreshValues.get(i),
                phases.get(i),
                events.get(i),
                fastRecoveryStatus.get(i),
                // Note: dupACKs at time of recording might not be current dupACKs
                0 // placeholder for dupACKs since it's dynamic
            ));
        }
        
        return csv.toString();
    }
    
    
    public void writeToCSV(String filename) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.print(exportToCSV());
            System.out.println("Complete TCP Reno data written to " + filename);
        } catch (IOException e) {
            System.err.println("Error writing CSV file: " + e.getMessage());
        }
    }
    
    
    public void printStatistics() {
        if (rounds.isEmpty()) {
            System.out.println("No data to analyze yet.");
            return;
        }
        
        int maxCwnd = Collections.max(cwndValues);
        int minCwnd = Collections.min(cwndValues);
        double avgCwnd = cwndValues.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        
        long slowStartRounds = phases.stream().mapToLong(p -> p.equals("Slow Start") ? 1 : 0).sum();
        long congestionAvoidanceRounds = phases.stream().mapToLong(p -> p.equals("Congestion Avoidance") ? 1 : 0).sum();
        long fastRecoveryRounds = phases.stream().mapToLong(p -> p.equals("Fast Recovery") ? 1 : 0).sum();
        long packetLossEvents = events.stream().mapToLong(e -> e.contains("Timeout") || e.contains("Triple Dup ACK") ? 1 : 0).sum();
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println("TCP RENO STATISTICS");
        System.out.println("=".repeat(50));
        System.out.println("Total Rounds: " + rounds.size());
        System.out.println("Max CWND: " + maxCwnd);
        System.out.println("Average CWND: " + String.format("%.2f", avgCwnd));
        System.out.println("Slow Start Rounds: " + slowStartRounds);
        System.out.println("Congestion Avoidance Rounds: " + congestionAvoidanceRounds);
        System.out.println("Fast Recovery Rounds: " + fastRecoveryRounds);
        System.out.println("Packet Loss Events: " + packetLossEvents);
        System.out.println("CSV File: " + csvFilename);
        System.out.println("=".repeat(50));
    }
    
    
    }
