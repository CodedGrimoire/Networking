import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 6000;
    private static final String DOWNLOAD_DIRECTORY = "downloads";

    public static void main(String[] args) {
        // Create downloads directory if it doesn't exist
        File directory = new File(DOWNLOAD_DIRECTORY);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        Scanner scanner = new Scanner(System.in);
        
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT);
            
            // Set up streams for communication
            DataInputStream dataInput = new DataInputStream(socket.getInputStream());
            DataOutputStream dataOutput = new DataOutputStream(socket.getOutputStream());
            BufferedReader textInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter textOutput = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            
            // Read welcome message from server
            String serverMessage = textInput.readLine();
            System.out.println(serverMessage);
            
            boolean connected = true;
            
            while (connected) {
                // Read server prompt for list or filename
                String prompt = textInput.readLine();
                System.out.println(prompt);
                
                // Get user input
                System.out.print("> ");
                String userInput = scanner.nextLine();
                
                // Send user input to server
                textOutput.write(userInput + "\n");
                textOutput.flush();
                
                // If user wants to exit
                if (userInput.equalsIgnoreCase("/exit")) {
                    String goodbyeMsg = textInput.readLine(); // Read goodbye message
                    System.out.println(goodbyeMsg);
                    connected = false;
                    continue;
                }
                
                // If user requested file list
                if (userInput.equalsIgnoreCase("/list")) {
                    // Read and display the file list
                    String line;
                    while (!(line = textInput.readLine()).contains("Enter the file name")) {
                        System.out.println(line);
                    }
                    System.out.println(line); // Print prompt for file name
                    
                    // Get file name from user
                    System.out.print("> ");
                    userInput = scanner.nextLine();
                    
                    // Send file name to server
                    textOutput.write(userInput + "\n");
                    textOutput.flush();
                    
                    // Check if user wants to exit
                    if (userInput.equalsIgnoreCase("/exit")) {
                        String goodbyeMsg = textInput.readLine(); // Read goodbye message
                        System.out.println(goodbyeMsg);
                        connected = false;
                        continue;
                    }
                }
                
                // Read server response for file request
                String response = textInput.readLine();
                
                if ("FILE_FOUND".equals(response)) {
                    System.out.println("File found on server. Downloading...");
                    
                    // Read file size
                    long fileSize = dataInput.readLong();
                    System.out.println("File size: " + fileSize + " bytes");
                    
                    // Create file to save the downloaded content
                    File downloadedFile = new File(DOWNLOAD_DIRECTORY + File.separator + userInput);
                    
                    // Download file
                    try (FileOutputStream fileOutputStream = new FileOutputStream(downloadedFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long totalBytesRead = 0;
                        
                        while (totalBytesRead < fileSize && 
                               (bytesRead = dataInput.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                            fileOutputStream.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                            
                            // Print progress
                            double progress = (double) totalBytesRead / fileSize * 100;
                            System.out.printf("\rDownloading... %.1f%%", progress);
                        }
                        
                        System.out.println("\nFile downloaded successfully to " + downloadedFile.getAbsolutePath());
                    }
                    
                    // Read completion message
                    String completionMsg = textInput.readLine();
                    System.out.println(completionMsg);
                    
                    // Wait for user to press Enter to continue
                    System.out.println("Press Enter to continue...");
                    scanner.nextLine();
                    
                    // Send acknowledgment to server
                    textOutput.write("\n");
                    textOutput.flush();
                    
                } else if ("FILE_NOT_FOUND".equals(response)) {
                    System.out.println("File not found on server.");
                } else {
                    System.out.println("Unexpected response from server: " + response);
                }
            }
            
        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + SERVER_ADDRESS);
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
        }
    }
}
