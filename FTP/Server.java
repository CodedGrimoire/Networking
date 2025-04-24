import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 6000;
    private static final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private static final String FILE_DIRECTORY = "server_files";
   

    public static void main(String[] args) {
        
        File directory = new File(FILE_DIRECTORY);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        
        System.out.println("File Server started on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT);
             Scanner scanner = new Scanner(System.in)) {

            new Thread(() -> {
                while (true) {
                    try {
                        System.out.print("\n[Server Command] clientId:message → ");
                        String input = scanner.nextLine();
                        if (input.equalsIgnoreCase("exit")) System.exit(0);
                        if (!input.contains(":")) continue;

                        String[] parts = input.split(":", 2);
                        String clientId = parts[0].trim();
                        String message = parts[1].trim();

                        Optional<ClientHandler> target = clients.stream()
                            .filter(c -> c.getClientId().equals(clientId))
                            .findFirst();

                        if (target.isPresent()) {
                            target.get().sendTextMessage("Server: " + message);
                        } else {
                            System.out.println("Client[" + clientId + "] not found.");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);
                
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<String> getAvailableFiles() {
        File directory = new File(FILE_DIRECTORY);
        String[] files = directory.list();
        return files != null ? Arrays.asList(files) : new ArrayList<>();
    }

    static class ClientHandler implements Runnable {
        private final Socket sock;
        private final DataInputStream input;
        private final DataOutputStream output;
        private final String clientId;
        private final String ip;
        private final int port;
        private final BufferedReader textInput;
        private final BufferedWriter textOutput;

        public ClientHandler(Socket sock) throws IOException {
            this.sock = sock;
            this.clientId = String.valueOf(sock.getPort());
            
            this.ip = sock.getInetAddress().getHostAddress();
            this.port = sock.getPort();

            input = new DataInputStream(sock.getInputStream());
            output = new DataOutputStream(sock.getOutputStream());
            textInput = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            textOutput = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
            
            System.out.println("Client[" + clientId + "] connected from " + ip + ":" + port);
            sendTextMessage("Connected as Client[" + clientId + "]");
        }

        public String getClientId() { 
            return clientId; 
        }
        
        public String getIp() { 
            return ip; 
        }
        
        public int getPort() { 
            return port; 
        }
        
        public void sendTextMessage(String msg) {
            try {
                textOutput.write(msg + "\n");
                textOutput.flush();
            } catch (IOException e) {
                System.out.println("Failed to send text message to Client[" + clientId + "]");
            }
        }

        private void sendFileList() {
            List<String> files = Server.getAvailableFiles();
            try {
                sendTextMessage("Available files:");
                if (files.isEmpty()) {
                    sendTextMessage("No files available.");
                } else {
                    for (int i = 0; i < files.size(); i++) {
                        sendTextMessage((i + 1) + ". " + files.get(i));
                    }
                }
            } catch (Exception e) {
                System.out.println("Error sending file list to Client[" + clientId + "]: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                boolean connected = true;
                
                while (connected) {
                    sendTextMessage("Enter /list to see available files, /exit to disconnect");
                    
                    String clientResponse = textInput.readLine();
                    
                    if (clientResponse == null || clientResponse.equalsIgnoreCase("/exit")) {
                        sendTextMessage("Goodbye!");
                        connected = false;
                        continue;
                    }
                    
                    if (clientResponse.equalsIgnoreCase("/list")) {
                        sendFileList();

                        sendTextMessage("Enter the file name you want to download:");
                        clientResponse = textInput.readLine();
                        
                        // Check again if client wants to exit
                        if (clientResponse == null || clientResponse.equalsIgnoreCase("/exit")) {
                            sendTextMessage("Goodbye!");
                            connected = false;
                            continue;
                        }
                    }
                    
                    // Process file download request
                    String fileName = clientResponse;
                    System.out.println("Client[" + clientId + "] requested file: " + fileName);
                    
                    File file = new File(FILE_DIRECTORY + File.separator + fileName);
                    
                    if (file.exists() && file.isFile()) {
                        sendTextMessage("FILE_FOUND");
                        
                        long fileSize = file.length();
                        output.writeLong(fileSize);
                        
                        try (FileInputStream fileInputStream = new FileInputStream(file)) {
                            byte[] buffer = new byte[1024];
                            
                            int bytesRead;
                            
                            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                                output.write(buffer, 0, bytesRead);
                            }
                            output.flush();
                            
                            System.out.println("File " + fileName + " sent to Client[" + clientId + "] successfully");
                            
                            sendTextMessage("File transfer complete. Press Enter to continue...");

                            textInput.readLine();
                        }
                    } else {
                        sendTextMessage("FILE_NOT_FOUND");
                        System.out.println("File " + fileName + " not found for Client[" + clientId + "]");
                    }
                }
            } catch (IOException e) {
                System.out.println("Error handling Client[" + clientId + "]: " + e.getMessage());
            } finally {
                clients.remove(this);
                System.out.println("Client[" + clientId + "] disconnected");
                
                try {
                    sock.close();
                    input.close();
                    output.close();
                    textInput.close();
                    textOutput.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
