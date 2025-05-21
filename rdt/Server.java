import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ThreadLocalRandom;

public class Server {
    private static final int PORT = 6000;
    private static final int DEFAULT_WINDOW_SIZE = 12; // fallback window size (bytes)
    private static final double PACKET_LOSS_PROB = 0.2; // 20% packet loss
    private static final long WINDOW_TIMEOUT_MS = 60_000; // 1 minute timeout

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private int expectedSeq = 0;
        private final Map<Integer, byte[]> buffer = new TreeMap<>();
        private int currentWindowSize = DEFAULT_WINDOW_SIZE;

        // For terminal input with timeout
        private final Scanner scanner = new Scanner(System.in);
        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())
            ) {
                while (true) {
                    String fileName = in.readUTF();
                    if (fileName.equalsIgnoreCase("exit")) {
                        System.out.println("Client requested exit. Closing connection.");
                        break;
                    }

                    System.out.println("Receiving file: " + fileName);

                    currentWindowSize = DEFAULT_WINDOW_SIZE;
                    out.writeInt(currentWindowSize); // send initial window size
                    out.flush();

                    File dir = new File("HTTP_server_files");
                    if (!dir.exists()) dir.mkdirs();

                    File outFile = new File(dir, uniqueFilename(fileName));
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {

                        expectedSeq = 0;
                        buffer.clear();

                        while (true) {
                            int seqNo = in.readInt();
                            if (seqNo == -1) {
                                System.out.println("End of transmission for file: " + outFile.getName());
                                break;
                            }

                            int length = in.readInt();
                            byte[] data = new byte[length];
                            in.readFully(data);

                            // Simulate packet loss
                            if (ThreadLocalRandom.current().nextDouble() < PACKET_LOSS_PROB) {
                                System.out.println("Dropped packet seq=" + seqNo);
                                // no ACK sent
                                continue;
                            }

                            if (seqNo == expectedSeq) {
                                fos.write(data);
                                expectedSeq += length;

                                while (buffer.containsKey(expectedSeq)) {
                                    byte[] buffered = buffer.remove(expectedSeq);
                                    fos.write(buffered);
                                    expectedSeq += buffered.length;
                                }
                            } else if (seqNo > expectedSeq) {
                                buffer.put(seqNo, data);
                            } else {
                                // duplicate packet, ignore
                            }

                            out.writeInt(expectedSeq); // cumulative ACK (next expected byte)
                            out.flush();

                            System.out.println("Received seq=" + seqNo + ", sent ACK=" + expectedSeq);

                            // Now prompt for new window size with timeout
                            Integer newWindow = readWindowSizeWithTimeout();

                            if (newWindow != null) {
                                currentWindowSize = newWindow;
                                System.out.println("Updated window size to: " + currentWindowSize);
                            } else {
                                System.out.println("No input within 1 minute, keeping window size: " + currentWindowSize);
                            }

                            // Send updated window size after ACK
                            out.writeInt(currentWindowSize);
                            out.flush();
                        }
                    }
                    System.out.println("File saved as: " + outFile.getAbsolutePath());
                }

                socket.close();
                executor.shutdownNow();
                System.out.println("Connection closed with client.");
            } catch (IOException e) {
                System.err.println("Client handler error: " + e.getMessage());
            }
        }

        // Reads integer from terminal with timeout. Returns null on timeout or invalid input.
        private Integer readWindowSizeWithTimeout() {
            Future<String> future = executor.submit(() -> {
                System.out.print("Enter new window size (or press Enter to keep current): ");
                return scanner.nextLine().trim();
            });

            try {
                String input = future.get(WINDOW_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (input.isEmpty()) return null;
                try {
                    int val = Integer.parseInt(input);
                    if (val > 0) return val;
                    System.out.println("Window size must be positive. Ignoring input.");
                    return null;
                } catch (NumberFormatException e) {
                    System.out.println("Invalid number format. Ignoring input.");
                    return null;
                }
            } catch (TimeoutException e) {
                future.cancel(true);
                return null; // timed out, no input
            } catch (Exception e) {
                System.out.println("Error reading input: " + e.getMessage());
                return null;
            }
        }

        private String uniqueFilename(String baseName) {
            File f = new File("HTTP_server_files", baseName);
            int count = 1;
            String name = baseName;
            while (f.exists()) {
                int dot = baseName.lastIndexOf('.');
                if (dot != -1)
                    name = baseName.substring(0, dot) + "_" + count + baseName.substring(dot);
                else
                    name = baseName + "_" + count;
                f = new File("HTTP_server_files", name);
                count++;
            }
            return name;
        }
    }
}
