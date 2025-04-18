import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 6001;
    private static Map<String, String> transactionCache = new HashMap<>();
    
    public static void main(String[] args) {
        try {
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            DataInputStream input = new DataInputStream(socket.getInputStream());
            
            System.out.println("ATM connected to bank server at port " + socket.getPort());
            System.out.println("Welcome to the ATM");
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            
            // Start a separate thread to handle responses from the server
            Thread responseThread = new Thread(() -> {
                try {
                    String response;
                    while ((response = input.readUTF()) != null) {
                        processServerResponse(response, output);
                    }
                } catch (SocketException se) {
                    System.out.println("Connection to bank server closed.");
                } catch (IOException e) {
                    System.out.println("Error reading from server: " + e.getMessage());
                }
            });
            responseThread.start();
            
            // Main interaction loop
            boolean authenticated = false;
            while (true) {
                if (!authenticated) {
                    System.out.println("\n--- ATM Login ---");
                    System.out.print("Enter card number: ");
                    String cardNumber = reader.readLine();
                    
                    System.out.print("Enter PIN: ");
                    String pin = reader.readLine();
                    
                    // Use server-generated transaction IDs
                    String authMessage = "AUTH:" + cardNumber + ":" + pin + ":REQUEST";
                    sendMessage(output, authMessage);
                    
                    // Wait for authentication response (handled by response thread)
                    Thread.sleep(1000);
                    
                    // Check for authentication success
                    boolean authSuccessful = false;
                    for (String cachedResponse : transactionCache.values()) {
                        if (cachedResponse.startsWith("AUTH_OK")) {
                            authSuccessful = true;
                            break;
                        }
                    }
                    
                    if (authSuccessful) {
                        authenticated = true;
                        // Clear cache after successful auth
                        transactionCache.clear();
                    }
                    
                    // Wait a bit to see response
                    Thread.sleep(500);
                } else {
                    displayMenu();
                    String choice = reader.readLine();
                    
                    switch (choice) {
                        case "1": // Check Balance
                            sendMessage(output, "BALANCE:REQUEST");
                            Thread.sleep(1000);
                            break;
                            
                        case "2": // Withdraw Money
                            System.out.print("Enter amount to withdraw: ");
                            String amount = reader.readLine();
                            
                            try {
                                // Validate amount is positive
                                double withdrawAmount = Double.parseDouble(amount);
                                if (withdrawAmount <= 0) {
                                    System.out.println("Error: Amount must be positive");
                                    continue;
                                }
                                
                                sendMessage(output, "WITHDRAW:" + amount + ":REQUEST");
                                Thread.sleep(1000);
                            } catch (NumberFormatException e) {
                                System.out.println("Error: Invalid amount format");
                            }
                            break;
                            
                        case "3": // Exit
                            System.out.println("Thank you for using the ATM. Goodbye!");
                            socket.close();
                            return;
                            
                        default:
                            System.out.println("Invalid choice. Please try again.");
                    }
                }
            }
        } catch (ConnectException ce) {
            System.out.println("Unable to connect to the bank server.");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private static void displayMenu() {
        System.out.println("\n--- ATM Menu ---");
        System.out.println("1. Check Balance");
        System.out.println("2. Withdraw Money");
        System.out.println("3. Exit");
        System.out.print("Enter your choice: ");
    }
    
    private static void sendMessage(DataOutputStream output, String message) {
        try {
            output.writeUTF(message);
            output.flush();
            System.out.println("Sent: " + message);
        } catch (IOException e) {
            System.out.println("Error sending message: " + e.getMessage());
        }
    }
    
    private static void processServerResponse(String response, DataOutputStream output) {
        System.out.println("Received: " + response);
        
        String[] parts = response.split(":");
        if (parts.length < 2) return;
        
        String messageType = parts[0];
        String transactionId = parts.length > 1 ? parts[parts.length - 1] : "";
        
        // Cache the response for this transaction
        transactionCache.put(transactionId, response);
        
        switch (messageType) {
            case "AUTH_OK":
                System.out.println("Authentication successful!");
                break;
                
            case "AUTH_FAIL":
                if (parts.length > 2) {
                    String reason = parts[1];
                    if ("ACCOUNT_IN_USE".equals(reason)) {
                        System.out.println("Authentication failed: This card is already being used at another ATM");
                    } else {
                        System.out.println("Authentication failed: " + (parts.length > 2 ? parts[2] : "Unknown reason"));
                    }
                } else {
                    System.out.println("Authentication failed: Unknown reason");
                }
                break;
                
            case "BALANCE_RESPONSE":
                if (parts.length > 2) {
                    String balance = parts[1];
                    System.out.println("Your current balance: $" + balance);
                    sendAcknowledgment(output, "BALANCE", transactionId);
                }
                break;
                
            case "WITHDRAW_OK":
                System.out.println("Withdrawal successful. Please take your cash.");
                sendAcknowledgment(output, "WITHDRAW", transactionId);
                break;
                
            case "INSUFFICIENT_FUNDS":
                System.out.println("Insufficient funds for this withdrawal.");
                sendAcknowledgment(output, "WITHDRAW", transactionId);
                break;
                
            case "ERROR":
                String errorMsg = parts.length > 2 ? parts[1] + ": " + parts[2] : "Unknown error";
                System.out.println("Error: " + errorMsg);
                
                // Additional handling for specific error types
                if (parts.length > 1 && parts[1].equals("INVALID_AMOUNT")) {
                    System.out.println("Please enter a positive amount for withdrawal.");
                }
                
                sendAcknowledgment(output, "ERROR", transactionId);
                break;
        }
    }
    
    private static void sendAcknowledgment(DataOutputStream output, String messageType, String transactionId) {
        try {
            String ack = "ACK:" + messageType + ":" + transactionId;
            output.writeUTF(ack);
            output.flush();
            System.out.println("Sent acknowledgment: " + ack);
        } catch (IOException e) {
            System.out.println("Error sending acknowledgment: " + e.getMessage());
        }
    }
}
