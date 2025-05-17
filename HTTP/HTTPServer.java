import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;

public class HTTPServer {
    private static final int PORT = 8080;
    private static final String FILE_DIR = "HTTP_server_files";

    public static void main(String[] args) {
        new File(FILE_DIR).mkdirs();
        try (ServerSocket sock = new ServerSocket(PORT)) {
            System.out.println("HTTP Server running on port: " + PORT);
            while (true) {
                Socket client = sock.accept();
                new Thread(() -> handleClient(client)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket client) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            OutputStream out = client.getOutputStream()
        ) {
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            System.out.println("Received: " + requestLine);
            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String path = parts[1];

            if (method.equals("GET") && path.startsWith("/download?filename=")) {
                String filename = path.substring("/download?filename=".length());
                File file = new File(FILE_DIR, filename);

                if (file.exists() && file.isFile()) {
                    byte[] data = Files.readAllBytes(file.toPath());
                    out.write(("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/octet-stream\r\n" +
                            "Content-Length: " + data.length + "\r\n" +
                            "Content-Disposition: attachment; filename=\"" + file.getName() + "\"\r\n\r\n").getBytes());
                    out.write(data);
                } else {
                    send404(out);
                }

            } else if (method.equals("POST") && path.equals("/upload")) {
                String uploadedFilename = "upload_" + System.currentTimeMillis();
                File file = new File(FILE_DIR, uploadedFilename);

                InputStream in = client.getInputStream();
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        if (len < buffer.length) break;
                    }
                }

                String response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\nFile uploaded as " + uploadedFilename;
                out.write(response.getBytes());

            } else {
                send404(out);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void send404(OutputStream out) throws IOException {
        String msg = "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\n\r\nFile not found";
        out.write(msg.getBytes());
    }
}
