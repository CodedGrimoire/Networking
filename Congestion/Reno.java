public class Reno {
    private int cwnd;
    private int ssthresh;
    private final int initialSsthresh;

    public Reno(int initialSsthresh) {
        this.initialSsthresh = initialSsthresh;
        this.cwnd = 1; // Initial congestion window
        this.ssthresh = initialSsthresh;
    }

    public int getCwnd() {
        return cwnd;
    }

    public int getSsthresh() {
        return ssthresh;
    }

    public void onAck(boolean isDuplicate, int dupAckCount) {
        if (isDuplicate && dupAckCount == 3) {
            // Fast retransmit condition
            System.out.println("🚨 Triple duplicate ACKs — Fast Retransmit triggered.");
            ssthresh = Math.max(cwnd / 2, 1);
            cwnd = ssthresh;  // Fast recovery: cwnd not reset, unlike Tahoe
            System.out.println("Updated ssthresh: " + ssthresh + ", cwnd set to: " + cwnd + " (Fast Recovery)");
        } else {
            if (cwnd < ssthresh) {
                // Slow start (exponential growth)
                cwnd *= 2;
                System.out.println("📈 Slow Start: cwnd -> " + cwnd);
            } else {
                // Congestion avoidance (linear growth)
                cwnd += 1;
                System.out.println("📉 Congestion Avoidance: cwnd -> " + cwnd);
            }
        }
    }

    public void onTimeout() {
        System.out.println("⏰ Timeout occurred — treating as congestion signal.");
        ssthresh = Math.max(cwnd / 2, 1);
        cwnd = 1;  // Start over
        System.out.println("Updated ssthresh: " + ssthresh + ", cwnd reset to 1 (Slow Start)");
    }
}