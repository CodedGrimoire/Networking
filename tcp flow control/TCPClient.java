import java.io.*;
import java.net.*;
import java.util.Scanner;

public class TCPClient {
    private static final String Server = "10.33.27.28";
    private static final int port = 6000;
    private static final String DOWNLOAD = "Clientsdownloads";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        File directory = new File(DOWNLOAD);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        try (Socket sock = new Socket(Server, port)) {
            System.out.println("Connected to server at " + Server + ":" + port);
            DataInputStream dataInput = new DataInputStream(sock.getInputStream());
            DataOutputStream dataOutput = new DataOutputStream(sock.getOutputStream());
            BufferedReader textInput = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            BufferedWriter textOutput = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));

            boolean connected = true;
            while (connected) {
                // Read server prompt for list or filename
                
                System.out.println("Enter file name in format: file:<filename>");
                textOutput.write("Enter file name in format: file:<filename>\n");
                textOutput.flush();
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
                } else {
                    // If user requested file list
                    if (userInput.equalsIgnoreCase("/list")) {
                        // Read and display the file list
                        String line;
                        while (!(line = textInput.readLine()).contains("Enter the file name")) {
                            System.out.println(line);
                        }
                        System.out.println(line); // Print the last line containing "Enter the file name"
                        continue;
                    }

                    else {
                        String[] parts = userInput.split(":", 2);
                        if (parts.length == 2 && parts[0].equalsIgnoreCase("file")) {
                            String filename = parts[1];
                            System.out.println("Filename: " + filename);
                        }

                        File file = new File("Clientsdownloads/" + userInput);
                        if (!file.exists()) {
                            System.out.println("File not found on client side.");
                            continue;
                        }

                        // Receive window size from server (in bytes)
                        int windowSize = dataInput.readInt();
                        int remainingWindow = windowSize;

                        System.out.println("Server window size: " + windowSize + " bytes");

                        // Start sending file
                        FileInputStream fis = new FileInputStream(file);
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        int seqNo = 0;

                        while ((bytesRead = fis.read(buffer)) != -1) {
                            if (bytesRead > remainingWindow) {
                                System.out.println(
                                        "Waiting for window update (remaining window = " + remainingWindow + ")");

                                remainingWindow = dataInput.readInt();
                            }

                            dataOutput.writeInt(seqNo);

                            dataOutput.writeInt(bytesRead);

                            dataOutput.write(buffer, 0, bytesRead);
                            dataOutput.flush();

                            // Wait for ACK
                            int ack = dataInput.readInt();
                            if (ack != seqNo) {
                                System.out.println("ACK mismatch: expected " + seqNo + ", got " + ack);
                                break;
                            } else {
                                System.out.println("ACK received for packet " + ack);
                                seqNo++;
                                remainingWindow -= bytesRead; // reduce window by sent chunk size
                            }
                        }

                        fis.close();
                        System.out.println("File upload completed.");
                        scanner.close();
                    }
                }
            }

        }

        catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

}
