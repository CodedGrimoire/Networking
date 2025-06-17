import java.util.*;
import java.io.*;

/**
 * TCP Tahoe Congestion Control Algorithm Implementation - WITH CSV EXPORT
 * 
 * TCP Tahoe characteristics:
 * - Slow Start: Exponential growth (CWND doubles every RTT when cwnd < ssthresh)
 * - Congestion Avoidance: Linear growth (CWND += 1 every RTT when cwnd >= ssthresh)
 * - Packet Loss Recovery: Always returns to CWND = 1 and ssthresh = cwnd/2
 */
public class TCPTahoe {
    
    // Algorithm state variables
    private double cwnd;
    private double ssthresh;
    private int roundNum;
    private final double initialCwnd;
    private final double initialSsthresh;
    
    // History tracking
    private List<Integer> rounds;
    private List<Double> cwndValues;
    private List<Double> ssthreshValues;
    private List<String> phases;
    private List<String> events;
    
    /**
     * Constructor with default values
     */
    public TCPTahoe() {
        this(1.0, 16.0);
    }
    
    /**
     * Constructor with custom initial values
     * 
     * @param initialCwnd Initial congestion window size
     * @param initialSsthresh Initial slow start threshold
     */
    public TCPTahoe(double initialCwnd, double initialSsthresh) {
        this.initialCwnd = initialCwnd;
        this.initialSsthresh = initialSsthresh;
        
        // Initialize history lists
        this.rounds = new ArrayList<>();
        this.cwndValues = new ArrayList<>();
        this.ssthreshValues = new ArrayList<>();
        this.phases = new ArrayList<>();
        this.events = new ArrayList<>();
        
        reset();
    }
    
    /**
     * Reset the algorithm to initial state
     */
    public void reset() {
        this.cwnd = initialCwnd;
        this.ssthresh = initialSsthresh;
        this.roundNum = 0;
        
        // Clear history
        rounds.clear();
        cwndValues.clear();
        ssthreshValues.clear();
        phases.clear();
        events.clear();
    }
    
    /**
     * Get current phase based on CWND and ssthresh
     * 
     * @return Current phase as string
     */
    public String getCurrentPhase() {
        return (cwnd < ssthresh) ? "Slow Start" : "Congestion Avoidance";
    }
    
    /**
     * Process a single transmission round
     * 
     * @param packetLoss Whether packet loss occurred this round
     * @return RoundInfo object with round details
     */
    public RoundInfo processRound(boolean packetLoss) {
        roundNum++;
        
        String phase;
        String event;
        
        if (packetLoss) {
            // TCP Tahoe packet loss handling:
            // 1. Set ssthresh to half of current CWND (minimum 2)
            ssthresh = Math.max(cwnd / 2.0, 2.0);
            // 2. Reset CWND to 1 (always, regardless of loss type)
            cwnd = 1.0;
            phase = "Slow Start";  // Always return to slow start after loss
            event = "Packet Loss";
        } else {
            // Normal operation - determine phase and grow accordingly
            if (cwnd < ssthresh) {
                // Slow Start: Exponential growth (double every RTT)
                cwnd *= 2.0;
                phase = "Slow Start";
            } else {
                // Congestion Avoidance: Linear growth (add 1 every RTT)
                cwnd += 1.0;
                phase = "Congestion Avoidance";
            }
            event = "ACK Received";
        }
        
        // Record history
        rounds.add(roundNum);
        cwndValues.add(cwnd);
        ssthreshValues.add(ssthresh);
        phases.add(phase);
        events.add(event);
        
        return new RoundInfo(roundNum, cwnd, ssthresh, phase, event);
    }
    
    /**
     * Simulate TCP Tahoe for multiple rounds
     * 
     * @param numRounds Number of rounds to simulate
     * @param packetLossRounds List of rounds where packet loss occurs
     * @return SimulationHistory object with complete history
     */
    public SimulationHistory simulate(int numRounds, List<Integer> packetLossRounds) {
        if (packetLossRounds == null) {
            packetLossRounds = new ArrayList<>();
        }
        
        reset();
        
        for (int i = 1; i <= numRounds; i++) {
            boolean packetLoss = packetLossRounds.contains(i);
            processRound(packetLoss);
        }
        
        return getHistory();
    }
    
    /**
     * Simulate TCP Tahoe with random packet loss
     * 
     * @param numRounds Number of rounds to simulate
     * @param lossRate Probability of packet loss per round (0.0 to 1.0)
     * @param seed Random seed for reproducible results
     * @return SimulationHistory object with complete history
     */
    public SimulationHistory simulateWithRandomLoss(int numRounds, double lossRate, long seed) {
        Random random = new Random(seed);
        List<Integer> packetLossRounds = new ArrayList<>();
        
        for (int i = 1; i <= numRounds; i++) {
            if (random.nextDouble() < lossRate) {
                packetLossRounds.add(i);
            }
        }
        
        return simulate(numRounds, packetLossRounds);
    }
    
    /**
     * Get complete simulation history
     * 
     * @return SimulationHistory object
     */
    public SimulationHistory getHistory() {
        return new SimulationHistory(
            new ArrayList<>(rounds),
            new ArrayList<>(cwndValues),
            new ArrayList<>(ssthreshValues),
            new ArrayList<>(phases),
            new ArrayList<>(events)
        );
    }
    
    /**
     * Get current state of the algorithm
     * 
     * @return CurrentState object
     */
    public CurrentState getCurrentState() {
        return new CurrentState(roundNum, cwnd, ssthresh, getCurrentPhase());
    }
    
    /**
     * Get statistics about the simulation
     * 
     * @return Statistics object
     */
    public Statistics getStatistics() {
        if (rounds.isEmpty()) {
            return new Statistics();
        }
        
        double maxCwnd = Collections.max(cwndValues);
        double minCwnd = Collections.min(cwndValues);
        double avgCwnd = cwndValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        long slowStartRounds = phases.stream().mapToLong(p -> p.equals("Slow Start") ? 1 : 0).sum();
        long congestionAvoidanceRounds = phases.stream().mapToLong(p -> p.equals("Congestion Avoidance") ? 1 : 0).sum();
        long packetLossEvents = events.stream().mapToLong(e -> e.equals("Packet Loss") ? 1 : 0).sum();
        
        return new Statistics(
            rounds.size(),
            maxCwnd,
            minCwnd,
            avgCwnd,
            (int) slowStartRounds,
            (int) congestionAvoidanceRounds,
            (int) packetLossEvents
        );
    }
    
    /**
     * Export simulation data to CSV format
     * 
     * @return CSV formatted string
     */
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
    
    /**
     * Write simulation data to CSV file
     * 
     * @param filename Name of the CSV file to write
     */
    public void writeToCSV(String filename) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.print(exportToCSV());
            System.out.println("CSV data written to " + filename);
        } catch (IOException e) {
            System.err.println("Error writing CSV file: " + e.getMessage());
        }
    }
    
    // Getters
    public double getCwnd() { return cwnd; }
    public double getSsthresh() { return ssthresh; }
    public int getRoundNum() { return roundNum; }
    
    /**
     * Inner class to hold round information
     */
    public static class RoundInfo {
        public final int round;
        public final double cwnd;
        public final double ssthresh;
        public final String phase;
        public final String event;
        
        public RoundInfo(int round, double cwnd, double ssthresh, String phase, String event) {
            this.round = round;
            this.cwnd = cwnd;
            this.ssthresh = ssthresh;
            this.phase = phase;
            this.event = event;
        }
        
        @Override
        public String toString() {
            return String.format("Round %d: CWND=%.1f, SSThresh=%.1f, Phase=%s, Event=%s",
                    round, cwnd, ssthresh, phase, event);
        }
    }
    
    /**
     * Inner class to hold simulation history
     */
    public static class SimulationHistory {
        public final List<Integer> rounds;
        public final List<Double> cwndValues;
        public final List<Double> ssthreshValues;
        public final List<String> phases;
        public final List<String> events;
        
        public SimulationHistory(List<Integer> rounds, List<Double> cwndValues,
                               List<Double> ssthreshValues, List<String> phases, List<String> events) {
            this.rounds = rounds;
            this.cwndValues = cwndValues;
            this.ssthreshValues = ssthreshValues;
            this.phases = phases;
            this.events = events;
        }
    }
    
    /**
     * Inner class to hold current state
     */
    public static class CurrentState {
        public final int round;
        public final double cwnd;
        public final double ssthresh;
        public final String phase;
        
        public CurrentState(int round, double cwnd, double ssthresh, String phase) {
            this.round = round;
            this.cwnd = cwnd;
            this.ssthresh = ssthresh;
            this.phase = phase;
        }
        
        @Override
        public String toString() {
            return String.format("Round %d: CWND=%.1f, SSThresh=%.1f, Phase=%s",
                    round, cwnd, ssthresh, phase);
        }
    }
    
    /**
     * Inner class to hold statistics
     */
    public static class Statistics {
        public final int totalRounds;
        public final double maxCwnd;
        public final double minCwnd;
        public final double avgCwnd;
        public final int slowStartRounds;
        public final int congestionAvoidanceRounds;
        public final int packetLossEvents;
        
        public Statistics() {
            this(0, 0, 0, 0, 0, 0, 0);
        }
        
        public Statistics(int totalRounds, double maxCwnd, double minCwnd, double avgCwnd,
                         int slowStartRounds, int congestionAvoidanceRounds, int packetLossEvents) {
            this.totalRounds = totalRounds;
            this.maxCwnd = maxCwnd;
            this.minCwnd = minCwnd;
            this.avgCwnd = avgCwnd;
            this.slowStartRounds = slowStartRounds;
            this.congestionAvoidanceRounds = congestionAvoidanceRounds;
            this.packetLossEvents = packetLossEvents;
        }
        
        @Override
        public String toString() {
            return String.format("Statistics: Total Rounds=%d, Max CWND=%.1f, Avg CWND=%.2f, " +
                    "Slow Start Rounds=%d, Congestion Avoidance Rounds=%d, Packet Loss Events=%d",
                    totalRounds, maxCwnd, avgCwnd, slowStartRounds, congestionAvoidanceRounds, packetLossEvents);
        }
    }
    
    /**
     * Example usage and testing
     */
    public static void main(String[] args) {
        // Create TCP Tahoe instance
        TCPTahoe tahoe = new TCPTahoe();
        
        System.out.println("TCP Tahoe Simulation with CSV Export:");
        System.out.println("=".repeat(50));
        
        // Run simulation with packet losses at strategic points
        List<Integer> packetLosses = Arrays.asList(15, 35, 60, 85);
        SimulationHistory history = tahoe.simulate(100, packetLosses);
        
        // Display statistics
        Statistics stats = tahoe.getStatistics();
        System.out.println("\nSimulation Results:");
        System.out.println(stats);
        
        // Export to CSV
        tahoe.writeToCSV("tcp_tahoe_simulation.csv");
        
        // Show some key events
        System.out.println("\nKey Events (Packet Losses and Phase Transitions):");
        for (int i = 0; i < history.events.size(); i++) {
            if (history.events.get(i).equals("Packet Loss") || 
                (i > 0 && !history.phases.get(i).equals(history.phases.get(i-1)))) {
                System.out.printf("Round %d: CWND=%.1f, SSThresh=%.1f, %s, %s\n",
                    history.rounds.get(i),
                    history.cwndValues.get(i),
                    history.ssthreshValues.get(i),
                    history.phases.get(i),
                    history.events.get(i));
            }
        }
        
        System.out.println("\nCSV file 'tcp_tahoe_simulation.csv' has been generated.");
        System.out.println("You can now run 'python graph2.py' to plot the results.");
    }
}