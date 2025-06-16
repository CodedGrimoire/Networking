import java.io.*;
import java.net.*;
import java.util.*;

public class ClientSide {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 3002;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        boolean continueRunning = true;
        String initialFileName = args.length > 0 ? args[0] : null;

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT);

            double alpha = 0.125;
            double beta = 0.25;
            double estimatedRTT = 1000;
            double devRTT = 500;
            int timeoutInterval = (int)(estimatedRTT + 4 * devRTT);

            socket.setSoTimeout(timeoutInterval);
            System.out.println("Initial timeout: " + timeoutInterval + " ms");

            List<Long> sampleRTTs = new ArrayList<>();
            List<Double> estimatedRTTs = new ArrayList<>();
            List<Integer> timeoutHistory = new ArrayList<>();

            while (continueRunning) {
                String fileName;

                if (initialFileName != null) {
                    fileName = initialFileName;
                    initialFileName = null;
                } else {
                    System.out.print("\nEnter file name to send (or 'exit' to quit): ");
                    fileName = scanner.nextLine().trim();
                }

                if (fileName.equalsIgnoreCase("exit")) {
                    output.writeUTF("__EXIT__");
                    output.flush();
                    System.out.println("Exiting client...");
                    break;
                }

                File file = new File(fileName);
                if (!file.exists() || !file.isFile()) {
                    System.out.println("File doesn't exist: " + fileName);
                    continue;
                }

                System.out.println("\nSending file: " + fileName);
                output.writeUTF(file.getName());
                output.flush();

                int serverWindowSize = input.readInt();
                System.out.println("Server window size: " + serverWindowSize + " bytes");

                try (FileInputStream fileInput = new FileInputStream(file)) {
                    byte[] buffer = new byte[serverWindowSize];
                    int sequenceNumber = 0;
                    long totalBytesSent = 0;
                    int bytesRead;
                    int lastAck = -1;
                    int dupAckCount = 0;

                    while ((bytesRead = fileInput.read(buffer, 0, serverWindowSize)) != -1) {
                        boolean packetAcknowledged = false;

                        while (!packetAcknowledged) {
                            try {
                                long sendTime = System.currentTimeMillis();

                                output.writeInt(sequenceNumber);
                                output.writeInt(bytesRead);
                                output.write(buffer, 0, bytesRead);
                                output.flush();

                                System.out.println("Sent packet with seq: " + sequenceNumber + ", Data len: " + bytesRead + " bytes");

                                int ack = input.readInt();
                                long ackTime = System.currentTimeMillis();
                                long sampleRTT = ackTime - sendTime;

                                System.out.println("Received ACK: " + ack + " | SampleRTT: " + sampleRTT + " ms");

                                if (ack == lastAck) {
                                    dupAckCount++;
                                    if (dupAckCount == 3) {
                                        System.out.println("Triple duplicate ACKs received — fast retransmitting packet with seq: " + sequenceNumber);
                                        dupAckCount = 0;
                                        continue;
                                    }
                                } else {
                                    lastAck = ack;
                                    dupAckCount = 1;
                                }

                                if (ack == sequenceNumber) {
                                    estimatedRTT = (1 - alpha) * estimatedRTT + alpha * sampleRTT;
                                    devRTT = (1 - beta) * devRTT + beta * Math.abs(sampleRTT - estimatedRTT);
                                    timeoutInterval = (int)(estimatedRTT + 4 * devRTT);
                                    socket.setSoTimeout(timeoutInterval);

                                    sampleRTTs.add(sampleRTT);
                                    estimatedRTTs.add(estimatedRTT);
                                    timeoutHistory.add(timeoutInterval);

                                    System.out.printf("Updated EstimatedRTT: %.2f ms, DevRTT: %.2f ms, Timeout: %d ms\n",
                                            estimatedRTT, devRTT, timeoutInterval);

                                    packetAcknowledged = true;
                                    sequenceNumber++;
                                    totalBytesSent += bytesRead;
                                } else {
                                    System.out.println("Unexpected ACK: " + ack + ", waiting for ACK: " + sequenceNumber);
                                }
                            } catch (SocketTimeoutException e) {
                                System.out.println("Timeout, resending packet with seq: " + sequenceNumber);
                            }
                        }
                    }

                    output.writeInt(-1);
                    output.writeInt(0);
                    output.flush();
                    System.out.println("Sent end of transmission signal");

                    int finalAck = input.readInt();
                    if (finalAck == -1) {
                        System.out.println("Server acknowledged end of transmission");
                    }

                    try (PrintWriter writer = new PrintWriter(new File("rtt_data.csv"))) {
                        writer.println("TimeIndex,SampleRTT,EstimatedRTT,TimeoutInterval");
                        for (int i = 0; i < sampleRTTs.size(); i++) {
                            writer.printf("%d,%d,%.2f,%d\n",
                                    i, sampleRTTs.get(i), estimatedRTTs.get(i), timeoutHistory.get(i));
                        }
                        System.out.println("RTT data saved to rtt_data.csv");
                    } catch (IOException e) {
                        System.out.println("Error writing RTT data: " + e.getMessage());
                    }

                    System.out.println("File transfer complete. Total bytes sent: " + totalBytesSent);
                }
            }

        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }

        scanner.close();
    }
}