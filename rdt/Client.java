import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;

public class Client {
    private static final String SERVER = "localhost";
    private static final int PORT = 6000;
    private static final String DOWNLOAD_DIR = "Clientsdownloads";

    private static final double ALPHA = 0.125;
    private static final double BETA = 0.25;

    private static double estimatedRTT = 100;
    private static double devRTT = 0;
    private static double timeout = 1000;

    private static Map<Integer, byte[]> sentPackets = new HashMap<>();
    private static Map<Integer, Long> sendTimes = new HashMap<>();
    private static Map<Integer, Integer> duplicateAcks = new HashMap<>();

    // Timeout in ms to wait for window size after ACK
    private static final int WINDOW_SIZE_WAIT_TIMEOUT = 60_000;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try (Socket sock = new Socket(SERVER, PORT);
             DataOutputStream out = new DataOutputStream(sock.getOutputStream());
             DataInputStream in = new DataInputStream(sock.getInputStream())) {

            while (true) {
                System.out.print("Enter filename to send: ");
                String fileName = scanner.nextLine().trim();

                if (fileName.equalsIgnoreCase("exit")) {
                    out.writeUTF("exit");
                    System.out.println("Exiting client.");
                    break;
                }

                File file = new File(DOWNLOAD_DIR + "/" + fileName);
                if (!file.exists()) {
                    System.err.println("File not found: " + file.getAbsolutePath());
                    continue;
                }

                sentPackets.clear();
                sendTimes.clear();
                duplicateAcks.clear();

                out.writeUTF(fileName);

                int windowSize = in.readInt();
                System.out.println("Initial window size: " + windowSize);

                byte[] fileBytes = Files.readAllBytes(file.toPath());
                int offset = 0;
                int seqNo = 0;

                while (offset < fileBytes.length) {
                    int packetSize = Math.min(windowSize, fileBytes.length - offset);
                    byte[] packet = Arrays.copyOfRange(fileBytes, offset, offset + packetSize);

                    sendPacket(out, seqNo, packet);
                    sentPackets.put(seqNo, packet);
                    sendTimes.put(seqNo, System.currentTimeMillis());

                    boolean acked = false;
                    while (!acked) {
                        try {
                            sock.setSoTimeout((int) timeout);
                            int ack = in.readInt();

                            if (ack == seqNo + packetSize) {
                                long sampleRTT = System.currentTimeMillis() - sendTimes.get(seqNo);
                                updateTimeout(sampleRTT);

                                // Try to read new window size with short timeout
                                try {
                                    sock.setSoTimeout(WINDOW_SIZE_WAIT_TIMEOUT);
                                    int newWindowSize = in.readInt();
                                    if (newWindowSize > 0) {
                                        windowSize = newWindowSize;
                                        System.out.println("Window size updated by server: " + windowSize);
                                    }
                                } catch (SocketTimeoutException ste) {
                                    // No new window size sent, keep old window size
                                }

                                offset += packetSize;
                                seqNo += packetSize;
                                acked = true;
                                duplicateAcks.clear();
                                System.out.println("ACK received: " + ack);
                            } else {
                                duplicateAcks.put(ack, duplicateAcks.getOrDefault(ack, 0) + 1);
                                if (duplicateAcks.get(ack) == 3) {
                                    int resendSeq = ack; // next expected seq
                                    System.out.println("Fast retransmit triggered for seq " + resendSeq);
                                    resendPacket(out, resendSeq);
                                    sendTimes.put(resendSeq, System.currentTimeMillis());
                                }
                            }
                        } catch (SocketTimeoutException e) {
                            System.out.println("Timeout! Resending seq " + seqNo);
                            resendPacket(out, seqNo);
                            sendTimes.put(seqNo, System.currentTimeMillis());
                        }
                    }
                }

                out.writeInt(-1); // end of file signal
                System.out.println("File transfer complete.");
            }

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

    private static void sendPacket(DataOutputStream out, int seqNo, byte[] data) throws IOException {
        out.writeInt(seqNo);
        out.writeInt(data.length);
        out.write(data);
        out.flush();
        System.out.println("Sent packet: seq=" + seqNo + ", size=" + data.length);
    }

    private static void resendPacket(DataOutputStream out, int seqNo) throws IOException {
        byte[] data = sentPackets.get(seqNo);
        if (data != null) {
            sendPacket(out, seqNo, data);
        }
    }

    private static void updateTimeout(long sampleRTT) {
        estimatedRTT = (1 - ALPHA) * estimatedRTT + ALPHA * sampleRTT;
        devRTT = (1 - BETA) * devRTT + BETA * Math.abs(sampleRTT - estimatedRTT);
        timeout = estimatedRTT + 4 * devRTT;
        System.out.printf("Timeout updated: %.2f ms (Sample RTT = %d ms)%n", timeout, sampleRTT);
    }
}
