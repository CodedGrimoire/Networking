import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClientSide {
    private static final String SERVER_ADDRESS = "10.33.27.28";
    private static final int SERVER_PORT = 8888;
    private static final int TIMEOUT = 3000; // 3 seconds timeout
    
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        boolean continueRunning = true;
        
        // Initial file name from command line arguments if provided
        String initialFileName = args.length > 0 ? args[0] : null;
        
        while (continueRunning) {
            String fileName;
            
            // Use initial values from command line if available on first run
            if (initialFileName != null) {
                fileName = initialFileName;
                initialFileName = null; // Clear for next iterations
            } else {
                // Prompt user for file name
                System.out.print("\nEnter file name to send (or 'exit' to quit): ");
                fileName = scanner.nextLine().trim();
                
                if (fileName.equalsIgnoreCase("exit")) {
                    System.out.println("Exiting client...");
                    break;
                }
            }
            
            // Verify file exists
            File file = new File(fileName);
            if (!file.exists() || !file.isFile()) {
                System.out.println("File doesn't exist: " + fileName);
                continue;
            }
            
            System.out.println("\nSending file: " + fileName);
            
            // Send the file
            sendFile(file);
        }
        
        scanner.close();
    }
    
    private static void sendFile(File file) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream());
             FileInputStream fileInput = new FileInputStream(file)) {
            
            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT);
            socket.setSoTimeout(TIMEOUT);
            
            // Send file name to server
            output.writeUTF(file.getName());
            output.flush();
            
            // Receive window size from server
            int serverWindowSize = input.readInt();
            System.out.println("Server window size: " + serverWindowSize + " bytes");
            
            // Prepare for sending file
            byte[] buffer = new byte[serverWindowSize];
            int sequenceNumber = 0;
            long totalBytesSent = 0;
            
            int bytesRead;
            while ((bytesRead = fileInput.read(buffer, 0, serverWindowSize)) != -1) {
                boolean packetAcknowledged = false;
                
                while (!packetAcknowledged) {
                    try {
                        // Send sequence number
                        output.writeInt(sequenceNumber);
                        
                        // Send data length
                        output.writeInt(bytesRead);
                        
                        // Send data
                        output.write(buffer, 0, bytesRead);
                        output.flush();
                        
                        System.out.println("Sent packet with seq: " + sequenceNumber + ", Data len: " + bytesRead + " bytes");
                        
                        // Wait for acknowledgment
                        int ack = input.readInt();
                        System.out.println("Received ACK: " + ack);
                        
                        if (ack == sequenceNumber) {
                            // Packet acknowledged
                            packetAcknowledged = true;
                            sequenceNumber++;
                            totalBytesSent += bytesRead;
                        } else {
                            System.out.println("Incorrect ACK, resending packet");
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout, resending packet with seq: " + sequenceNumber);
                    }
                }
            }
            
            // Send end of transmission signal
            output.writeInt(-1);
            output.writeInt(0);
            output.flush();
            System.out.println("Sent end of transmission signal");
            
            // Wait for final acknowledgment
            int finalAck = input.readInt();
            if (finalAck == -1) {
                System.out.println("Server acknowledged end of transmission");
            }
            
            System.out.println("File transfer complete. Total bytes sent: " + totalBytesSent);
            
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }
} 