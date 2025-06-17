public class Reno {
    private int cwnd;
    private int ssthresh;
    private final int initialSsthresh;
    private int duplicateAckCount;
    private int lastAckReceived;
    private boolean inFastRecovery;

    public Reno(int initialSsthresh) {
        this.initialSsthresh = initialSsthresh;
        this.cwnd = 1; // Initial congestion window
        this.ssthresh = initialSsthresh;
        this.duplicateAckCount = 0;
        this.lastAckReceived = -1;
        this.inFastRecovery = false;
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

    /**
     * Handle ACK reception - this should be called for each ACK received
     * @param ackNumber The ACK number received
     * @param isNewAck Whether this is a new ACK (advancing the window) or duplicate
     */
    public void onAck(int ackNumber, boolean isNewAck) {
        if (!isNewAck) {
            // Duplicate ACK received
            if (ackNumber == lastAckReceived) {
                duplicateAckCount++;
                System.out.println("Duplicate ACK #" + duplicateAckCount + " for seq: " + ackNumber);
                
                if (duplicateAckCount == 3 && !inFastRecovery) {
                    // Triple duplicate ACK - trigger fast retransmit/fast recovery
                    System.out.println("🚨 Triple duplicate ACKs — Fast Retransmit triggered.");
                    ssthresh = Math.max(cwnd / 2, 1);
                    cwnd = ssthresh + 3; // Fast recovery: ssthresh + 3 (for the 3 dup ACKs)
                    inFastRecovery = true;
                    System.out.println("Updated ssthresh: " + ssthresh + ", cwnd set to: " + cwnd + " (Fast Recovery)");
                } else if (inFastRecovery) {
                    // Additional duplicate ACK during fast recovery
                    cwnd++;
                    System.out.println("Fast Recovery: Additional dup ACK, cwnd -> " + cwnd);
                }
            }
        } else {
            // New ACK received
            lastAckReceived = ackNumber;
            
            if (inFastRecovery) {
                // Exiting fast recovery
                cwnd = ssthresh;
                inFastRecovery = false;
                duplicateAckCount = 0;
                System.out.println("🔄 Exiting Fast Recovery: cwnd -> " + cwnd);
            } else {
                // Normal ACK processing
                duplicateAckCount = 0;
                
                if (cwnd < ssthresh) {
                    // Slow start (exponential growth) - double every RTT
                    cwnd = cwnd * 2;
                    System.out.println("📈 Slow Start: cwnd -> " + cwnd);
                } else {
                    // Congestion avoidance (linear growth) - increase by 1 every RTT
                    cwnd = cwnd + 1;
                    System.out.println("📊 Congestion Avoidance: cwnd -> " + cwnd);
                }
            }
        }
    }

    public void onTimeout() {
        System.out.println("⏰ Timeout occurred — treating as congestion signal.");
        ssthresh = Math.max(cwnd / 2, 1);
        cwnd = 1;  // Start over with slow start
        inFastRecovery = false;
        duplicateAckCount = 0;
        System.out.println("Updated ssthresh: " + ssthresh + ", cwnd reset to 1 (Slow Start)");
    }

    /**
     * Reset the Reno state for a new connection
     */
    public void reset() {
        this.cwnd = 1;
        this.ssthresh = initialSsthresh;
        this.duplicateAckCount = 0;
        this.lastAckReceived = -1;
        this.inFastRecovery = false;
        System.out.println("🔄 TCP Reno reset: cwnd=1, ssthresh=" + ssthresh);
    }
    
    /**
     * Get current state information for debugging
     */
    public String getCurrentState() {
        String phase = inFastRecovery ? "Fast Recovery" : 
                      (cwnd < ssthresh ? "Slow Start" : "Congestion Avoidance");
        return String.format("CWND=%d, SSThresh=%d, Phase=%s, DupACKs=%d", 
                           cwnd, ssthresh, phase, duplicateAckCount);
    }
}