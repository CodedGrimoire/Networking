import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RealTCPClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 3002;
    private static final int MAX_PACKET_SIZE = 1024;
    private static final int TIMEOUT_MS = 1000;

    // Congestion control interface
    interface CongestionControl {
        void onAck(int ackNumber, boolean isNewAck);
        void onTimeout();
        void reset();
        int getCwnd();
        int getSsthresh();
        String getCurrentState();
        String getAlgorithmName();
        void printStatistics();
    }

    // Wrapper for Reno
    static class RenoWrapper implements CongestionControl {
        private Reno reno;

        public RenoWrapper(int initialSsthresh) {
            this.reno = new Reno(initialSsthresh);
        }

        @Override public void onAck(int ackNumber, boolean isNewAck) { reno.onAck(ackNumber, isNewAck); }
        @Override public void onTimeout() { reno.onTimeout(); }
        @Override public void reset() { reno.reset(); }
        @Override public int getCwnd() { return reno.getCwnd(); }
        @Override public int getSsthresh() { return reno.getSsthresh(); }
        @Override public String getCurrentState() { return reno.getCurrentState(); }
        @Override public String getAlgorithmName() { return "TCP Reno"; }
        @Override public void printStatistics() { reno.printStatistics(); }
    }

    // Packet class to represent TCP segments
    static class TCPPacket {
        int sequenceNumber;
        byte[] data;
        long sendTime;
        boolean acknowledged;
        int retransmissionCount;

        TCPPacket(int seqNum, byte[] data) {
            this.sequenceNumber = seqNum;
            this.data = Arrays.copyOf(data, data.length);
            this.sendTime = System.currentTimeMillis();
            this.acknowledged = false;
            this.retransmissionCount = 0;
        }
    }

    // TCP Connection state
    static class TCPConnection {
        CongestionControl congestionControl;
        Map<Integer, TCPPacket> unacknowledgedPackets;
        int nextSequenceNumber;
        int lastAckReceived;
        int duplicateAckCount;
        Socket socket;
        DataInputStream input;
        DataOutputStream output;

        TCPConnection(Socket socket, CongestionControl cc) throws IOException {
            this.socket = socket;
            this.input = new DataInputStream(socket.getInputStream());
            this.output = new DataOutputStream(socket.getOutputStream());
            this.congestionControl = cc;
            this.unacknowledgedPackets = new ConcurrentHashMap<>();
            this.nextSequenceNumber = 0;
            this.lastAckReceived = -1;
            this.duplicateAckCount = 0;
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT);
            
            // Choose congestion control algorithm
            CongestionControl congestionControl = chooseCongestionControlAlgorithm(scanner);
            TCPConnection connection = new TCPConnection(socket, congestionControl);
            
            System.out.println("Using: " + congestionControl.getAlgorithmName());

            while (true) {
                System.out.print("\nEnter file name to send (or 'exit' to quit): ");
                String fileName = scanner.nextLine().trim();

                if (fileName.equalsIgnoreCase("exit")) {
                    connection.output.writeUTF("__EXIT__");
                    connection.output.flush();
                    break;
                }

                File file = new File(fileName);
                if (!file.exists()) {
                    System.out.println("File doesn't exist: " + fileName);
                    continue;
                }

                sendFileWithRealTCP(connection, file);
            }

        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }

        scanner.close();
    }

    private static void sendFileWithRealTCP(TCPConnection conn, File file) throws IOException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("SENDING FILE WITH REAL TCP BEHAVIOR");
        System.out.println("File: " + file.getName());
        System.out.println("Algorithm: " + conn.congestionControl.getAlgorithmName());
        System.out.println("=".repeat(60));

        // Send filename
        conn.output.writeUTF(file.getName());
        conn.output.flush();

        // Get server window size
        int serverWindowSize = conn.input.readInt();
        System.out.println("Server window size: " + serverWindowSize + " bytes");

        // Reset congestion control
        conn.congestionControl.reset();
        conn.unacknowledgedPackets.clear();
        conn.nextSequenceNumber = 0;
        conn.lastAckReceived = -1;
        conn.duplicateAckCount = 0;

        // Read file into packets
        List<byte[]> filePackets = new ArrayList<>();
        try (FileInputStream fileInput = new FileInputStream(file)) {
            byte[] buffer = new byte[Math.min(MAX_PACKET_SIZE, serverWindowSize)];
            int bytesRead;
            while ((bytesRead = fileInput.read(buffer)) != -1) {
                filePackets.add(Arrays.copyOf(buffer, bytesRead));
            }
        }

        System.out.println("File divided into " + filePackets.size() + " packets");

        // Start transmission with real TCP windowing
        int packetsToSend = filePackets.size();
        int packetsSent = 0;
        long transmissionStart = System.currentTimeMillis();

        while (packetsSent < packetsToSend || !conn.unacknowledgedPackets.isEmpty()) {
            
            // PHASE 1: SEND NEW PACKETS (up to congestion window)
            int cwnd = conn.congestionControl.getCwnd();
            int packetsInFlight = conn.unacknowledgedPackets.size();
            int availableWindow = cwnd - packetsInFlight;

            System.out.println("\n--- Transmission Round ---");
            System.out.println("CWND: " + cwnd + ", In Flight: " + packetsInFlight + 
                             ", Available: " + availableWindow + ", " + conn.congestionControl.getCurrentState());

            // Send new packets up to window limit
            while (availableWindow > 0 && packetsSent < packetsToSend) {
                byte[] packetData = filePackets.get(packetsSent);
                TCPPacket packet = new TCPPacket(packetsSent, packetData);
                
                sendPacket(conn, packet);
                conn.unacknowledgedPackets.put(packetsSent, packet);
                
                System.out.println("Sent packet " + packetsSent + " (" + packetData.length + " bytes)");
                
                packetsSent++;
                availableWindow--;
            }

            // PHASE 2: PROCESS ACKS (with timeout)
            boolean receivedAck = processAcks(conn, TIMEOUT_MS);
            
            if (!receivedAck) {
                // Timeout occurred - retransmit oldest unacknowledged packet
                handleTimeout(conn);
            }

            // PHASE 3: CHECK FOR RETRANSMISSIONS
            checkForRetransmissions(conn);
        }

        // Send end-of-transmission signal
        conn.output.writeInt(-1);
        conn.output.writeInt(0);
        conn.output.flush();

        // Wait for final ACK
        try {
            int finalAck = conn.input.readInt();
            if (finalAck == -1) {
                System.out.println("Server acknowledged end of transmission");
            }
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout waiting for final ACK");
        }

        long transmissionEnd = System.currentTimeMillis();
        long duration = transmissionEnd - transmissionStart;

        System.out.println("\n" + "=".repeat(60));
        System.out.println("TRANSMISSION COMPLETE");
        System.out.println("Duration: " + duration + " ms");
        System.out.println("Final CWND: " + conn.congestionControl.getCwnd());
        System.out.println("Final SSThresh: " + conn.congestionControl.getSsthresh());
        conn.congestionControl.printStatistics();
        System.out.println("=".repeat(60));
    }

    private static void sendPacket(TCPConnection conn, TCPPacket packet) throws IOException {
        conn.output.writeInt(packet.sequenceNumber);
        conn.output.writeInt(packet.data.length);
        conn.output.write(packet.data);
        conn.output.flush();
        packet.sendTime = System.currentTimeMillis();
    }

    private static boolean processAcks(TCPConnection conn, int timeoutMs) {
        try {
            conn.socket.setSoTimeout(timeoutMs);
            
            while (true) {
                int ack = conn.input.readInt();
                System.out.println("Received ACK: " + ack);
                
                if (ack > conn.lastAckReceived) {
                    // NEW ACK - cumulative acknowledgment
                    int newlyAckedPackets = 0;
                    
                    // Remove all packets up to and including this ACK
                    Iterator<Map.Entry<Integer, TCPPacket>> iter = conn.unacknowledgedPackets.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<Integer, TCPPacket> entry = iter.next();
                        if (entry.getKey() <= ack) {
                            iter.remove();
                            newlyAckedPackets++;
                        }
                    }
                    
                    // Update TCP state
                    conn.lastAckReceived = ack;
                    conn.duplicateAckCount = 0;
                    
                    // Call congestion control for each newly acknowledged packet
                    for (int i = 0; i < newlyAckedPackets; i++) {
                        conn.congestionControl.onAck(ack, true);
                    }
                    
                    System.out.println("New ACK " + ack + " acknowledged " + newlyAckedPackets + " packets");
                    
                } else if (ack == conn.lastAckReceived) {
                    // DUPLICATE ACK
                    conn.duplicateAckCount++;
                    conn.congestionControl.onAck(ack, false);
                    
                    System.out.println("Duplicate ACK " + ack + " (count: " + conn.duplicateAckCount + ")");
                    
                    if (conn.duplicateAckCount == 3) {
                        System.out.println("🚨 TRIPLE DUPLICATE ACK - Fast Retransmit triggered");
                        // Fast retransmit - retransmit the next expected packet
                        retransmitPacket(conn, ack + 1);
                    }
                }
                
                // If no more packets in flight, break
                if (conn.unacknowledgedPackets.isEmpty()) {
                    return true;
                }
                
                // Continue processing more ACKs if available
                if (conn.input.available() == 0) {
                    // No more ACKs immediately available
                    return true;
                }
            }
            
        } catch (SocketTimeoutException e) {
            // No ACK received within timeout
            return false;
        } catch (IOException e) {
            System.out.println("Error processing ACKs: " + e.getMessage());
            return false;
        }
    }

    private static void handleTimeout(TCPConnection conn) {
        System.out.println("\nTIMEOUT OCCURRED");
        System.out.println("Before timeout - CWND: " + conn.congestionControl.getCwnd() + 
                         ", SSThresh: " + conn.congestionControl.getSsthresh());
        
        conn.congestionControl.onTimeout();
        conn.duplicateAckCount = 0;
        
        System.out.println("After timeout  - CWND: " + conn.congestionControl.getCwnd() + 
                         ", SSThresh: " + conn.congestionControl.getSsthresh());
        
        // Retransmit the oldest unacknowledged packet
        if (!conn.unacknowledgedPackets.isEmpty()) {
            int oldestSeq = Collections.min(conn.unacknowledgedPackets.keySet());
            retransmitPacket(conn, oldestSeq);
        }
    }

    private static void retransmitPacket(TCPConnection conn, int sequenceNumber) {
        TCPPacket packet = conn.unacknowledgedPackets.get(sequenceNumber);
        if (packet != null) {
            try {
                packet.retransmissionCount++;
                sendPacket(conn, packet);
                System.out.println("Retransmitted packet " + sequenceNumber + 
                                 " (attempt #" + packet.retransmissionCount + ")");
            } catch (IOException e) {
                System.out.println("Error retransmitting packet " + sequenceNumber + ": " + e.getMessage());
            }
        }
    }

    private static void checkForRetransmissions(TCPConnection conn) {
        long currentTime = System.currentTimeMillis();
        for (TCPPacket packet : conn.unacknowledgedPackets.values()) {
            if (currentTime - packet.sendTime > TIMEOUT_MS && packet.retransmissionCount == 0) {
                // This packet might need retransmission due to individual timeout
                // But we'll let the main timeout mechanism handle it
            }
        }
    }

    private static CongestionControl chooseCongestionControlAlgorithm(Scanner scanner) {
        while (true) {
            System.out.println("\n" + "=".repeat(50));
            System.out.println("CHOOSE CONGESTION CONTROL ALGORITHM");
            System.out.println("=".repeat(50));
            System.out.println("1. TCP Reno (Fast retransmit & fast recovery)");
            System.out.print("Enter your choice (1): ");
            
            String choice = scanner.nextLine().trim();
            
            if (choice.equals("1") || choice.isEmpty()) {
                System.out.println("Selected: TCP Reno");
                return new RenoWrapper(8); // Initial ssthresh of 8
            } else {
                System.out.println("Invalid choice. Please enter 1.");
            }
        }
    }
}