import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClientSide {
    private static final String SERVER_ADDRESS = "10.33.27.28";
    private static final int SERVER_PORT = 8888;
    private static final int DEFAULT_WINDOW_SIZE = 1024; // Default window size
    private static final int TIMEOUT = 3000; // 3 seconds timeout
    
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        boolean continueRunning = true;
        
        // Initial file name from command line arguments if provided
        String initialFileName = args.length > 0 ? args[0] : null;
        int initialWindowSize = DEFAULT_WINDOW_SIZE;
        
        if (args.length >= 2) {
            try {
                initialWindowSize = Integer.parseInt(args[1]);
                if (initialWindowSize <= 0) {
                    System.out.println("Window size must be positive. Using default: " + DEFAULT_WINDOW_SIZE);
                    initialWindowSize = DEFAULT_WINDOW_SIZE;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid window size. Using default: " + DEFAULT_WINDOW_SIZE);
            }
        }
        
        while (continueRunning) {
            String fileName;
            int windowSize;
            
            // Use initial values from command line if available on first run
            if (initialFileName != null) {
                fileName = initialFileName;
                windowSize = initialWindowSize;
                initialFileName = null; // Clear for next iterations
            } else {
                // Prompt user for file name
                System.out.print("\nEnter file name to request (or 'exit' to quit): ");
                fileName = scanner.nextLine().trim();
                
                if (fileName.equalsIgnoreCase("exit")) {
                    System.out.println("Exiting client...");
                    break;
                }
                
                // Prompt user for window size
                System.out.print("Enter window size (press Enter for default " + DEFAULT_WINDOW_SIZE + "): ");
                String windowSizeInput = scanner.nextLine().trim();
                
                if (windowSizeInput.isEmpty()) {
                    windowSize = DEFAULT_WINDOW_SIZE;
                } else {
                    try {
                        windowSize = Integer.parseInt(windowSizeInput);
                        if (windowSize <= 0) {
                            System.out.println("Window size must be positive. Using default: " + DEFAULT_WINDOW_SIZE);
                            windowSize = DEFAULT_WINDOW_SIZE;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid window size. Using default: " + DEFAULT_WINDOW_SIZE);
                        windowSize = DEFAULT_WINDOW_SIZE;
                    }
                }
            }
            
            System.out.println("\nRequesting file: " + fileName);
            System.out.println("Window size: " + windowSize + " bytes");
            
            // Request the file
            requestFile(fileName, windowSize);
        }
        
        scanner.close();
    }
    
    private static void requestFile(String fileName, int windowSize) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream());
             FileOutputStream fileOutput = new FileOutputStream(new File("received_" + fileName))) {
            
            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT);
            socket.setSoTimeout(TIMEOUT);
            
            // Send file name to request
            output.writeUTF(fileName);
            
            // Send window size
            output.writeInt(windowSize);
            output.flush();
            
            // Prepare for receiving file
            byte[] buffer = new byte[windowSize];
            int expectedSeq = 0;
            long totalBytesReceived = 0;
            
            while (true) {
                // Read sequence number
                int sequenceNumber = input.readInt();
                
                if (sequenceNumber == -1) {
                    // End of transmission
                    System.out.println("End of transmission received");
                    sendAck(output, -1);
                    break;
                }
                
                // Read data length
                int dataLength = input.readInt();
                
                // Read data
                int bytesRead = 0;
                while (bytesRead < dataLength) {
                    int remaining = dataLength - bytesRead;
                    int chunkSize = Math.min(remaining, windowSize);
                    int read = input.read(buffer, 0, chunkSize);
                    if (read == -1) break;
                    bytesRead += read;
                }
                
                System.out.println("Received packet with seq: " + sequenceNumber + ", Data len: " + dataLength + " bytes");
                
                if (sequenceNumber == expectedSeq) {
                    // If packet is in order, write to file
                    fileOutput.write(buffer, 0, dataLength);
                    totalBytesReceived += dataLength;
                    expectedSeq++;
                    sendAck(output, sequenceNumber);
                    System.out.println("Sent ACK for seq: " + sequenceNumber);
                } else {
                    // Out of order packet, resend the last ACK
                    sendAck(output, expectedSeq - 1);
                    System.out.println("Out of order packet. Sent ACK for seq: " + (expectedSeq - 1));
                }
            }
            
            System.out.println("File transfer complete. Total bytes received: " + totalBytesReceived);
            System.out.println("File saved as: received_" + fileName);
            
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }
    
    private static void sendAck(DataOutputStream output, int ackNumber) throws IOException {
        output.writeInt(ackNumber);
        output.flush();
    }
} 