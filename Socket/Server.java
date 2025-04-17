import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 6001;
    private static final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private static final String CLIENT_FILE = "clients.txt";

    public static void main(String[] args) {
        System.out.println("Server started on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT);
             Scanner scanner = new Scanner(System.in)) {

            // Thread to allow server to message specific client
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
                            target.get().sendMessage("Server: " + message);
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
                saveClientInfo(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

private static final String AUTH_FILE = "clientAuth.txt";

// Method to authenticate a client against the auth file
public static boolean authenticateClient(String cardNo, String pin) {
    try (BufferedReader reader = new BufferedReader(new FileReader(AUTH_FILE))) {
        String line;
        while ((line = reader.readLine()) != null) {
            // Skip empty lines and comments
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }
            
            // Expected format in file: cardNo:pin
            String[] credentials = line.split(":", 3);
            if (credentials.length == 3) {
                String fileCardNo = credentials[0].trim();
                String filePin = credentials[1].trim();

                
                if (fileCardNo.equals(cardNo) && filePin.equals(pin)) {
                    return true; // Authentication successful
                }
            }
        }
    } catch (IOException e) {
        System.out.println("Error reading authentication file: " + e.getMessage());
        e.printStackTrace();
    }
    
    return false; // Authentication failed
}

public static String clientAmount(String cardNo, String pin) {
    try (BufferedReader reader = new BufferedReader(new FileReader(AUTH_FILE))) {
        String line;
        while ((line = reader.readLine()) != null) {
            // Skip empty lines and comments
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }
            
            // Expected format in file: cardNo:pin
            String[] credentials = line.split(":", 3);
            if (credentials.length == 3) {
                String fileCardNo = credentials[0].trim();
                String filePin = credentials[1].trim();
                String amount = credentials[2].trim();
                
                if (fileCardNo.equals(cardNo) && filePin.equals(pin)) {
                    return amount; // Authentication successful
                }
            }
        }
    } catch (IOException e) {
        System.out.println("Error reading authentication file: " + e.getMessage());
        e.printStackTrace();
    }
    
    return "0"; 
}


public static String withdraw(String cardNo, String pin, double withdrawAmounts) {
    boolean found = false;
    String result = "Not enough balance";
    List<String> fileContent = new ArrayList<>();
    
    try (BufferedReader reader = new BufferedReader(new FileReader(AUTH_FILE))) {
        String line;
        while ((line = reader.readLine()) != null) {
            fileContent.add(line);
        }
    } catch (IOException e) {
        System.out.println("Error reading file: " + e.getMessage());
        e.printStackTrace();
        return "Error processing withdrawal";
    }
    
    for (int i = 0; i < fileContent.size(); i++) {
        String line = fileContent.get(i);
        
        if (line.trim().isEmpty() || line.trim().startsWith("#")) {
            continue;
        }
        
        String[] credentials = line.split(":", 3);
        if (credentials.length == 3) {
            String fileCardNo = credentials[0].trim();
            String filePin = credentials[1].trim();
            double balance = Double.parseDouble(credentials[2].trim());
            System.out.println(balance);
            
            if (fileCardNo.equals(cardNo) && filePin.equals(pin)) {
                found = true;
                try {
                    if (balance >= withdrawAmounts) {
                        double newBalance = balance - withdrawAmounts;
                        System.out.println(newBalance);
                        fileContent.set(i, fileCardNo + ":" + filePin + ":" + newBalance);
                        result = "Withdrawn successfully";
                    }
                } catch (NumberFormatException e) {
                    result = "Error processing amount";
                }
                break; 
            }
        }
    }
    

    if (found) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(AUTH_FILE))) {
            for (String line : fileContent) {
                System.out.println(line);
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
            e.printStackTrace();
            return "Error processing withdrawal";
        }
    } else {
        result = "Account not found";
    }
    
    return result;
}




    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage("Client[" + sender.getClientId() + "]: " + message);
            }
        }
    }

    public static void removeClient(ClientHandler handler) {
        clients.remove(handler);
        System.out.println("Client[" + handler.getClientId() + "] disconnected.");
    }

    public static synchronized void saveClientInfo(ClientHandler client) {
        try (FileWriter fw = new FileWriter(CLIENT_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            String line = "clientId=" + client.getClientId()
                        + ", ip=" + client.getIp()
                        + ", connectedAt=" + client.getTimestamp();

            out.println(line);
            System.out.println("Client info saved: " + line);

        } catch (IOException e) {
            System.out.println("Could not save client info to text file.");
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private final DataInputStream input;
        private final DataOutputStream output;
        private final String clientId;
        private final String timestamp;
        private final String ip;
        private final int port;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.clientId = String.valueOf(socket.getPort());
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            this.ip = socket.getInetAddress().getHostAddress();
            this.port = socket.getPort();

            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            sendMessage("Connected as Client[" + clientId + "]");
        }

        public String getClientId() { return clientId; }
        public String getTimestamp() { return timestamp; }
        public String getIp() { return ip; }
        public int getPort() { return port; }

        public void sendMessage(String msg) {
            try {
                output.writeUTF(msg);
            } catch (IOException e) {
                System.out.println("Failed to send to Client[" + clientId + "]");
            }
        }

        @Override
        public void run() {
            String msg;
            try {
                while ((msg = input.readUTF()) != null) {
                    if (msg.equalsIgnoreCase("stop")) break;
                    System.out.println("From Client[" + clientId + "]: " + msg);             
                    String[] parts = msg.split(":", 2);
                    String card_no = parts[0].trim();
                    String pin = parts[1].trim();

                    if(authenticateClient(card_no,pin)){
                        sendMessage("AUTH_OK");
                        System.out.println("Authentication successful for Client[" + clientId + "]");
                        while(true){
                            String msg1 = input.readUTF();
                        
                        if(msg1.equals("amount")){
                            output.writeUTF(clientAmount(card_no,pin));
                        }
                        if(msg1.equals("withdraw")){
                            output.writeUTF("Enter your amount to withdraw: ");
                            double withdrawAmount = Double.parseDouble(input.readUTF());

                            output.writeUTF(withdraw(card_no,pin,withdrawAmount));
                        }

                        if(msg1.equals("exit")){
                            System.exit(0);
                        }

                        }
                        
                    } else {
                        sendMessage("AUTH_FAILED");
                        System.out.println("Authentication failed for Client[" + clientId + "]");
                    }
                    Server.broadcast(msg, this);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                Server.removeClient(this);
                try {
                    socket.close();
                    input.close();
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

