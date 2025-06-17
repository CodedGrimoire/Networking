import java.util.*;

/**
 * TCP Tahoe Congestion Control Algorithm Implementation
 * 
 * TCP Tahoe characteristics:
 * - Slow Start: Exponential growth (CWND doubles every RTT)
 * - Congestion Avoidance: Linear growth (CWND += 1 every RTT)
 * - Packet Loss Recovery: Always returns to CWND = 1 (no fast recovery)
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
            // Handle packet loss (Tahoe behavior)
            // Set ssthresh to half of current CWND (minimum 2)
            ssthresh = Math.max(cwnd / 2.0, 2.0);
            // Reset CWND to 1 (key difference from Reno)
            cwnd = 1.0;
            phase = "Timeout Recovery";
            event = "Packet Loss";
        } else {
            // Normal operation - no packet loss
            if (cwnd < ssthresh) {
                // Slow Start: Exponential growth
                cwnd *= 2;
                phase = "Slow Start";
            } else {
                // Congestion Avoidance: Linear growth
                cwnd += 1;
                phase = "Congestion Avoidance";
            }
            event = "Normal Growth";
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
        long timeoutRecoveryRounds = phases.stream().mapToLong(p -> p.equals("Timeout Recovery") ? 1 : 0).sum();
        long packetLossEvents = events.stream().mapToLong(e -> e.equals("Packet Loss") ? 1 : 0).sum();
        
        return new Statistics(
            rounds.size(),
            maxCwnd,
            minCwnd,
            avgCwnd,
            (int) slowStartRounds,
            (int) congestionAvoidanceRounds,
            (int) timeoutRecoveryRounds,
            (int) packetLossEvents
        );
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
        public final int timeoutRecoveryRounds;
        public final int packetLossEvents;
        
        public Statistics() {
            this(0, 0, 0, 0, 0, 0, 0, 0);
        }
        
        public Statistics(int totalRounds, double maxCwnd, double minCwnd, double avgCwnd,
                         int slowStartRounds, int congestionAvoidanceRounds,
                         int timeoutRecoveryRounds, int packetLossEvents) {
            this.totalRounds = totalRounds;
            this.maxCwnd = maxCwnd;
            this.minCwnd = minCwnd;
            this.avgCwnd = avgCwnd;
            this.slowStartRounds = slowStartRounds;
            this.congestionAvoidanceRounds = congestionAvoidanceRounds;
            this.timeoutRecoveryRounds = timeoutRecoveryRounds;
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
        
        // Simulate with some packet losses
        List<Integer> packetLosses = Arrays.asList(25, 50, 75);
        SimulationHistory history = tahoe.simulate(100, packetLosses);
        
        // Print results
        System.out.println("TCP Tahoe Simulation Results:");
        System.out.println("=".repeat(40));
        
        Statistics stats = tahoe.getStatistics();
        System.out.println(stats);
        
        System.out.println("\nFirst 10 rounds:");
        for (int i = 0; i < Math.min(10, history.rounds.size()); i++) {
            System.out.printf("Round %d: CWND=%.1f, SSThresh=%.1f, Phase=%s%n",
                    history.rounds.get(i),
                    history.cwndValues.get(i),
                    history.ssthreshValues.get(i),
                    history.phases.get(i));
        }
        
        System.out.println("\nRounds with packet loss:");
        for (int i = 0; i < history.events.size(); i++) {
            if ("Packet Loss".equals(history.events.get(i))) {
                System.out.printf("Round %d: CWND reset to %.1f, SSThresh set to %.1f%n",
                        history.rounds.get(i),
                        history.cwndValues.get(i),
                        history.ssthreshValues.get(i));
            }
        }
    }
}