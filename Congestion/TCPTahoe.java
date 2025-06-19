import java.util.*;
import java.io.*;

public class TCPTahoe {
    private double cwnd;
    private double ssthresh;
    private final double initialSsthresh;
    private int lastAckReceived;
    private String csvFilename; // Auto-save filename
    
    // History tracking for CSV export
    private List<Integer> rounds;
    private List<Double> cwndValues;
    private List<Double> ssthreshValues;
    private List<String> phases;
    private List<String> events;
    private int roundNum;

    public TCPTahoe(double initialSsthresh) {
        this(initialSsthresh, "tcp_tahoe_simulation.csv"); // Default filename
    }
    
    public TCPTahoe(double initialSsthresh, String csvFilename) {
        this.initialSsthresh = initialSsthresh;
        this.csvFilename = csvFilename;
        this.cwnd = 1.0; // Initial congestion window
        this.ssthresh = initialSsthresh;
        this.lastAckReceived = -1;
        this.roundNum = 0;
        
        // Initialize history lists
        this.rounds = new ArrayList<>();
        this.cwndValues = new ArrayList<>();
        this.ssthreshValues = new ArrayList<>();
        this.phases = new ArrayList<>();
        this.events = new ArrayList<>();
        
        // Create initial CSV file with headers
        initializeCSVFile();
    }

    public int getCwnd() {
        return (int) Math.ceil(cwnd);
    }

    public int getSsthresh() {
        return (int) Math.ceil(ssthresh);
    }
    
    
    public void setCSVFilename(String filename) {
        this.csvFilename = filename;
    }

   
    public void onAck(int ackNumber, boolean isNewAck) {
        if (isNewAck) {
            // New ACK received - normal operation
            lastAckReceived = ackNumber;
            roundNum++;
            
            String phase;
            String event = "ACK Received";
            
            if (cwnd < ssthresh) {
                // Slow Start: Exponential growth (double every RTT)
                cwnd *= 2.0;
                phase = "Slow Start";
                System.out.println("Slow Start: cwnd -> " + getCwnd());
            } else {
                // Congestion Avoidance: Linear growth (add 1 every RTT)
                cwnd += 1.0;
                phase = "Congestion Avoidance";
                System.out.println("Congestion Avoidance: cwnd -> " + getCwnd());
            }
            
            // Record history and auto-save to CSV
            recordHistory(phase, event);
        } else {
            // Duplicate ACK received - TCP Tahoe treats any loss as congestion
            System.out.println("Duplicate ACK received for seq: " + ackNumber);
        }
    }

    
    public void onTimeout() {
        System.out.println(" Timeout occurred — TCP Tahoe congestion response.");
        roundNum++;
        
      
        ssthresh = Math.max(cwnd / 2.0, 2.0);
       
        cwnd = 1.0;
        
        System.out.println("Updated ssthresh: " + getSsthresh() + ", cwnd reset to 1 (Slow Start)");
        
        // Record history and auto-save to CSV
        recordHistory("Slow Start", "Packet Loss");
    }

   
    public void onTripleDupAck() {
        System.out.println(" Triple duplicate ACKs — TCP Tahoe treats as packet loss.");
        roundNum++;
        
        // TCP Tahoe response: same as timeout (no fast recovery)
        ssthresh = Math.max(cwnd / 2.0, 2.0);
        cwnd = 1.0;
        
        System.out.println("Updated ssthresh: " + getSsthresh() + ", cwnd reset to 1 (Slow Start)");
        
        // Record history and auto-save to CSV
        recordHistory("Slow Start", "Triple Dup ACK");
    }

    
    public void reset() {
        this.cwnd = 1.0;
        this.ssthresh = initialSsthresh;
        this.lastAckReceived = -1;
        this.roundNum = 0;
        
        // Clear history
        rounds.clear();
        cwndValues.clear();
        ssthreshValues.clear();
        phases.clear();
        events.clear();
        
        System.out.println(" TCP Tahoe reset: cwnd=1, ssthresh=" + getSsthresh());
        
        // Reinitialize CSV file
        initializeCSVFile();
    }
    
    
    public String getCurrentState() {
        String phase = (cwnd < ssthresh) ? "Slow Start" : "Congestion Avoidance";
        return String.format("CWND=%d, SSThresh=%d, Phase=%s", 
                           getCwnd(), getSsthresh(), phase);
    }
    
   
    public String getCurrentPhase() {
        return (cwnd < ssthresh) ? "Slow Start" : "Congestion Avoidance";
    }
    

    private void initializeCSVFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFilename))) {
            writer.println("Round,CWND,SSThresh,Phase,Event");
            System.out.println("Initialized CSV file: " + csvFilename);
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
        
        // Auto-save to CSV file immediately
        appendToCSV(roundNum, cwnd, ssthresh, phase, event);
        
        System.out.println("Round " + roundNum + " data saved to " + csvFilename);
    }
    
    private void appendToCSV(int round, double cwnd, double ssthresh, String phase, String event) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFilename, true))) {
            writer.printf("%d,%.1f,%.1f,%s,%s%n", round, cwnd, ssthresh, phase, event);
        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
    }
    
   
    public String exportToCSV() {
        StringBuilder csv = new StringBuilder();
        csv.append("Round,CWND,SSThresh,Phase,Event\n");
        
        for (int i = 0; i < rounds.size(); i++) {
            csv.append(String.format("%d,%.1f,%.1f,%s,%s\n",
                rounds.get(i),
                cwndValues.get(i),
                ssthreshValues.get(i),
                phases.get(i),
                events.get(i)));
        }
        
        return csv.toString();
    }
    
   
    public void writeToCSV(String filename) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.print(exportToCSV());
            System.out.println("Complete TCP Tahoe data written to " + filename);
        } catch (IOException e) {
            System.err.println("Error writing CSV file: " + e.getMessage());
        }
    }
    
    
    public void printStatistics() {
        if (rounds.isEmpty()) {
            System.out.println("No data to analyze yet.");
            return;
        }
        
        double maxCwnd = Collections.max(cwndValues);
        double minCwnd = Collections.min(cwndValues);
        double avgCwnd = cwndValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        long slowStartRounds = phases.stream().mapToLong(p -> p.equals("Slow Start") ? 1 : 0).sum();
        long congestionAvoidanceRounds = phases.stream().mapToLong(p -> p.equals("Congestion Avoidance") ? 1 : 0).sum();
        long packetLossEvents = events.stream().mapToLong(e -> e.contains("Loss") || e.contains("Dup ACK") ? 1 : 0).sum();
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println(" TCP TAHOE STATISTICS");
        System.out.println("=".repeat(50));
        System.out.println("Total Rounds: " + rounds.size());
        System.out.println("Max CWND: " + (int)maxCwnd);
        System.out.println("Average CWND: " + String.format("%.2f", avgCwnd));
        System.out.println("Slow Start Rounds: " + slowStartRounds);
        System.out.println("Congestion Avoidance Rounds: " + congestionAvoidanceRounds);
        System.out.println("Packet Loss Events: " + packetLossEvents);
        System.out.println("CSV File: " + csvFilename);
        System.out.println("=".repeat(50));
    }
    
   
    
}