import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerSide {
    private static final int PORT = 6001;
    private static final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private static final String CLIENT_FILE = "clients.txt";
    private static final String AUTH_FILE = "clientAuth.txt";
    private static final String TRANSACTION_FILE = "transactions.txt";
    
    // Track which accounts are currently in use (card number -> client id)
    private static final Map<String, String> accountInUse = new ConcurrentHashMap<>();
    
    // Transaction counter for generating transaction IDs
    private static AtomicInteger transactionCounter = new AtomicInteger(1000);
    
    // Store completed transactions to prevent duplicate processing
    private static final Set<String> completedTransactions = ConcurrentHashMap.newKeySet();
    // Store transaction responses for retries
    private static final Map<String, String> transactionResponses = new ConcurrentHashMap<>();

    // Lock for synchronizing file access
    private static final Object fileLock = new Object();
    private static final Object transactionFileLock = new Object();

    public static void main(String[] args) {
        System.out.println("Bank Server started on port " + PORT);
        
        // Initialize transaction counter from log file
        initializeTransactionCounter();
        
        try (ServerSocket serverSocket = new ServerSocket(PORT);
             Scanner scanner = new Scanner(System.in)) {

            // Admin command thread
            new Thread(() -> {
                while (true) {
                    try {
                        System.out.print("\n[Bank Admin Command] > ");
                        String input = scanner.nextLine();

                        if (input.equals("exit")) System.exit(0);
                        if (input.equals("list")) {
                            System.out.println("Connected clients: " + clients.size());
                            for (ClientHandler client : clients) {
                                System.out.println("- Client[" + client.getClientId() + "] from " + client.getIp() + 
                                                  (client.getCardNumber() != null ? " using card " + client.getCardNumber() : ""));
                            }
                            
                            System.out.println("\nLocked accounts: " + accountInUse.size());
                            for (Map.Entry<String, String> entry : accountInUse.entrySet()) {
                                System.out.println("- Card " + entry.getKey() + " used by client " + entry.getValue());
                            }
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
                System.out.println("ATM client connected from " + clientSocket.getInetAddress().getHostAddress());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // Initialize transaction counter by reading the last transaction ID from the log file
    private static void initializeTransactionCounter() {
        synchronized (transactionFileLock) {
            File file = new File(TRANSACTION_FILE);
            if (!file.exists()) {
                System.out.println("Transaction log file does not exist. Creating with initial counter value.");
                return;
            }
            
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                String lastLine = null;
                
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        lastLine = line;
                    }
                }
                
                if (lastLine != null) {
                    // Extract transaction ID from the last line
                    // Format: timestamp,cardNo,transactionType,amount,transactionId,status
                    String[] parts = lastLine.split(",");
                    if (parts.length >= 5) {
                        String lastTxnId = parts[4];
                        if (lastTxnId.startsWith("TXN")) {
                            try {
                                int lastId = Integer.parseInt(lastTxnId.substring(3));
                                transactionCounter.set(lastId + 1);
                                System.out.println("Initialized transaction counter to " + transactionCounter.get());
                            } catch (NumberFormatException e) {
                                System.out.println("Could not parse last transaction ID: " + lastTxnId);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Error reading transaction log file: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    // Generate a new transaction ID
    public static String generateTransactionId() {
        return "TXN" + transactionCounter.getAndIncrement();
    }
    
    // Log a transaction to the transaction file
    public static void logTransaction(String cardNo, String transactionType, double amount, String transactionId, String status) {
        synchronized (transactionFileLock) {
            try (FileWriter fw = new FileWriter(TRANSACTION_FILE, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String timestamp = sdf.format(new Date());
                String logEntry = timestamp + "," + cardNo + "," + transactionType + "," + amount + "," + transactionId + "," + status;

                out.println(logEntry);
                System.out.println("Transaction logged: " + logEntry);
            } catch (IOException e) {
                System.out.println("Could not log transaction to file.");
                e.printStackTrace();
            }
        }
    }
    
    // Try to lock an account for exclusive access
    public static boolean lockAccount(String cardNo, String clientId) {
        // Check if account is already in use by another client
        String existingClient = accountInUse.putIfAbsent(cardNo, clientId);
        if (existingClient != null && !existingClient.equals(clientId)) {
            System.out.println("Account " + cardNo + " is already in use by client " + existingClient);
            return false;
        }
        System.out.println("Account " + cardNo + " locked by client " + clientId);
        return true;
    }
    
    // Release the lock on an account
    public static void unlockAccount(String cardNo, String clientId) {
        // Only remove if this client owns the lock
        accountInUse.remove(cardNo, clientId);
        System.out.println("Account " + cardNo + " unlocked by client " + clientId);
    }

    // Method to authenticate a client against the auth file
    public static boolean authenticateClient(String cardNo, String pin) {
        synchronized (fileLock) {
            try (BufferedReader reader = new BufferedReader(new FileReader(AUTH_FILE))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    // Skip empty lines and comments
                    if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                        continue;
                    }
                
                    // Expected format in file: cardNo:pin:balance
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
        }
    
        return false; // Authentication failed
    }

    public static String getAccountBalance(String cardNo, String pin) {
        synchronized (fileLock) {
            try (BufferedReader reader = new BufferedReader(new FileReader(AUTH_FILE))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Skip empty lines and comments
                    if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                        continue;
                    }
                
                    // Expected format in file: cardNo:pin:balance
                    String[] credentials = line.split(":", 3);
                    if (credentials.length == 3) {
                        String fileCardNo = credentials[0].trim();
                        String filePin = credentials[1].trim();
                        String balance = credentials[2].trim();
                    
                        if (fileCardNo.equals(cardNo) && filePin.equals(pin)) {
                            return balance;
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Error reading authentication file: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return "0"; 
    }   

    public static String processWithdrawal(String cardNo, String pin, double withdrawAmount, String transactionId) {
        // Check if this is a duplicate transaction
        if (completedTransactions.contains(transactionId)) {
            System.out.println("Duplicate transaction detected: " + transactionId);
            return transactionResponses.getOrDefault(transactionId, "ERROR:Unknown transaction");
        }
        
        // Validate withdrawal amount
        if (withdrawAmount <= 0) {
            String result = "ERROR:INVALID_AMOUNT:Withdrawal amount must be positive:" + transactionId;
            transactionResponses.put(transactionId, result);
            logTransaction(cardNo, "WITHDRAW", withdrawAmount, transactionId, "FAILED:INVALID_AMOUNT");
            return result;
        }
        
        boolean found = false;
        String result = "INSUFFICIENT_FUNDS:" + transactionId;
        List<String> fileContent = new ArrayList<>();
    
        // Synchronize file access to prevent concurrent modification
        synchronized (fileLock) {
            try (BufferedReader reader = new BufferedReader(new FileReader(AUTH_FILE))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    fileContent.add(line);
                }
            } catch (IOException e) {
                System.out.println("Error reading file: " + e.getMessage());
                e.printStackTrace();
                logTransaction(cardNo, "WITHDRAW", withdrawAmount, transactionId, "FAILED:FILE_READ_ERROR");
                return "ERROR:FILE_READ:" + e.getMessage() + ":" + transactionId;
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
                    System.out.println("Current balance: " + balance);
                
                    if (fileCardNo.equals(cardNo) && filePin.equals(pin)) {
                        found = true;
                        try {
                            if (balance >= withdrawAmount) {
                                double newBalance = balance - withdrawAmount;
                                System.out.println("New balance after withdrawal: " + newBalance);
                                fileContent.set(i, fileCardNo + ":" + filePin + ":" + newBalance);
                                result = "WITHDRAW_OK:" + transactionId;
                            }
                        } catch (NumberFormatException e) {
                            result = "ERROR:AMOUNT_FORMAT:" + e.getMessage() + ":" + transactionId;
                            logTransaction(cardNo, "WITHDRAW", withdrawAmount, transactionId, "FAILED:AMOUNT_FORMAT_ERROR");
                        }
                        break; 
                    }
                }
            }
        
            if (found && result.startsWith("WITHDRAW_OK")) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(AUTH_FILE))) {
                    for (String line : fileContent) {
                        System.out.println("Writing line: " + line);
                        writer.write(line);
                        writer.newLine();
                    }
                    logTransaction(cardNo, "WITHDRAW", withdrawAmount, transactionId, "SUCCESS");
                } catch (IOException e) {
                    System.out.println("Error writing to file: " + e.getMessage());
                    e.printStackTrace();
                    logTransaction(cardNo, "WITHDRAW", withdrawAmount, transactionId, "FAILED:FILE_WRITE_ERROR");
                    return "ERROR:FILE_WRITE:" + e.getMessage() + ":" + transactionId;
                }
            } else if (!found) {
                result = "ERROR:ACCOUNT_NOT_FOUND:" + transactionId;
                logTransaction(cardNo, "WITHDRAW", withdrawAmount, transactionId, "FAILED:ACCOUNT_NOT_FOUND");
            } else {
                logTransaction(cardNo, "WITHDRAW", withdrawAmount, transactionId, "FAILED:INSUFFICIENT_FUNDS");
            }
        }
        
        // Store transaction result for potential retries
        transactionResponses.put(transactionId, result);
        return result;
    }

    public static void removeClient(ClientHandler handler) {
        clients.remove(handler);
        
        // Release account lock if this client had one
        if (handler.getCardNumber() != null) {
            unlockAccount(handler.getCardNumber(), handler.getClientId());
        }
        
        System.out.println("ATM client [" + handler.getClientId() + "] disconnected.");
    }

    public static synchronized void saveClientInfo(ClientHandler client) {
        try (FileWriter fw = new FileWriter(CLIENT_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            String line = "clientId=" + client.getClientId()
                        + ", ip=" + client.getIp()
                        + ", connectedAt=" + client.getTimestamp();

            out.println(line);
            System.out.println("ATM client info saved: " + line);

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
        
        // Store authenticated user credentials
        private String cardNumber = null;
        private String pin = null;
        private boolean isAuthenticated = false;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.clientId = String.valueOf(socket.getPort());
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            this.ip = socket.getInetAddress().getHostAddress();
            this.port = socket.getPort();

            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());

            System.out.println("New ATM client connected as Client[" + clientId + "]");
        }

        public String getClientId() { return clientId; }
        public String getTimestamp() { return timestamp; }
        public String getIp() { return ip; }
        public int getPort() { return port; }
        public String getCardNumber() { return cardNumber; }

        public void sendMessage(String msg) {
            try {
                output.writeUTF(msg);
                output.flush();
                System.out.println("Sent to ATM[" + clientId + "]: " + msg);
            } catch (IOException e) {
                System.out.println("Failed to send message to ATM[" + clientId + "]");
            }
        }

        @Override
        public void run() {
            try {
                String message;
                while ((message = input.readUTF()) != null) {
                    System.out.println("From ATM[" + clientId + "]: " + message);
                    
                    // Process message based on protocol
                    String[] parts = message.split(":");
                    String command = parts[0];
                    
                    // Handle ACK messages
                    if (command.equals("ACK")) {
                        if (parts.length >= 3) {
                            String ackedTxnType = parts[1];
                            String ackedTxnId = parts[2];
                            System.out.println("Received ACK for " + ackedTxnType + " transaction " + ackedTxnId);
                            completedTransactions.add(ackedTxnId);
                        }
                        continue;
                    }
                    
                    // Handle AUTH command
                    if (command.equals("AUTH")) {
                        if (parts.length >= 4) {
                            String cardNo = parts[1];
                            String userPin = parts[2];
                            // Generate transaction ID if client sent REQUEST placeholder
                            String transactionId = "REQUEST".equals(parts[3]) ? generateTransactionId() : parts[3];
                            
                            // Check if this account is already in use
                            if (!lockAccount(cardNo, clientId)) {
                                sendMessage("AUTH_FAIL:ACCOUNT_IN_USE:This card is already being used at another ATM:" + transactionId);
                                logTransaction(cardNo, "AUTH", 0, transactionId, "FAILED:ACCOUNT_IN_USE");
                                System.out.println("Authentication failed for ATM[" + clientId + "]: Account in use");
                                continue;
                            }
                            
                            if (authenticateClient(cardNo, userPin)) {
                                this.cardNumber = cardNo;
                                this.pin = userPin;
                                this.isAuthenticated = true;
                                sendMessage("AUTH_OK:" + transactionId);
                                logTransaction(cardNo, "AUTH", 0, transactionId, "SUCCESS");
                                System.out.println("Authentication successful for ATM[" + clientId + "]");
                            } else {
                                // Release the lock since auth failed
                                unlockAccount(cardNo, clientId);
                                sendMessage("AUTH_FAIL:INVALID_CREDENTIALS:" + transactionId);
                                logTransaction(cardNo, "AUTH", 0, transactionId, "FAILED:INVALID_CREDENTIALS");
                                System.out.println("Authentication failed for ATM[" + clientId + "]");
                            }
                        } else {
                            sendMessage("ERROR:PROTOCOL:Invalid AUTH format");
                        }
                    }
                    // Handle commands that require authentication
                    else if (isAuthenticated) {
                        if (command.equals("BALANCE")) {
                            // Generate transaction ID if client sent REQUEST placeholder
                            String transactionId = parts.length > 1 && "REQUEST".equals(parts[1]) 
                                ? generateTransactionId() : parts[1];
                            
                            String balance = getAccountBalance(cardNumber, pin);
                            sendMessage("BALANCE_RESPONSE:" + balance + ":" + transactionId);
                            logTransaction(cardNumber, "BALANCE", 0, transactionId, "SUCCESS");
                        }
                        else if (command.equals("WITHDRAW")) {
                            if (parts.length >= 3) {
                                double amount;
                                try {
                                    amount = Double.parseDouble(parts[1]);
                                    // Generate transaction ID if client sent REQUEST placeholder
                                    String transactionId = "REQUEST".equals(parts[2]) 
                                        ? generateTransactionId() : parts[2];
                                    
                                    // Validate amount is positive
                                    if (amount <= 0) {
                                        sendMessage("ERROR:INVALID_AMOUNT:Withdrawal amount must be positive:" + transactionId);
                                        logTransaction(cardNumber, "WITHDRAW", amount, transactionId, "FAILED:INVALID_AMOUNT");
                                        continue;
                                    }
                                    
                                    String result = processWithdrawal(cardNumber, pin, amount, transactionId);
                                    sendMessage(result);
                                } catch (NumberFormatException e) {
                                    String transactionId = generateTransactionId();
                                    sendMessage("ERROR:AMOUNT_FORMAT:Invalid withdrawal amount:" + transactionId);
                                    logTransaction(cardNumber, "WITHDRAW", 0, transactionId, "FAILED:AMOUNT_FORMAT");
                                }
                            } else {
                                String transactionId = generateTransactionId();
                                sendMessage("ERROR:PROTOCOL:Invalid WITHDRAW format:" + transactionId);
                                logTransaction(cardNumber, "WITHDRAW", 0, transactionId, "FAILED:PROTOCOL_ERROR");
                            }
                        }
                    } else {
                        String transactionId = generateTransactionId();
                        sendMessage("ERROR:AUTH_REQUIRED:Please authenticate first:" + transactionId);
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection error with ATM[" + clientId + "]: " + e.getMessage());
            } finally {
                ServerSide.removeClient(this);
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

