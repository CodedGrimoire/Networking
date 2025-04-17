import java.io.*;
import java.net.*;

public class Client {
    public static void main(String[] args) {

        try {
            Socket sock = new Socket("192.168.116.164", 6001);
            DataOutputStream output = new DataOutputStream(sock.getOutputStream());
            DataInputStream input = new DataInputStream(sock.getInputStream());

            System.out.println("Client Connected to server at port " + sock.getPort());
            System.out.println("Client communication port: " + sock.getLocalPort());
            System.out.println("Type 'stop' to end the chat.\n");

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            Thread receiverThread = new Thread(() -> {
                try {
                    String msgFromServer;
                    while ((msgFromServer = input.readUTF()) != null) {
                        if (msgFromServer.equalsIgnoreCase("stop")) {
                            System.out.println("Server stopped the chat.");
                            break;
                        }
                        System.out.println("Server: " + msgFromServer);
                    }
                } catch (SocketException se) {
                    System.out.println("Connection closed.");
                } catch (IOException e) {
                    System.out.println("Error reading from server: " + e.getMessage());
                }
            });

            receiverThread.start();

            String msgToSend;
            while (true) {
                System.out.print("Client: ");
                msgToSend = reader.readLine();

                output.writeUTF(msgToSend);
                output.flush();

                if (msgToSend.equalsIgnoreCase("stop")) {
                    break;
                }
            }

            receiverThread.join();
            output.close();
            input.close();
            sock.close();
            reader.close();

        } catch (ConnectException ce) {
            System.out.println("Unable to connect to the server.");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
