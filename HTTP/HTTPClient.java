import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Scanner;

class Clientside {
    public static final String SERVER_URL = "http://localhost:8080";
    public static final String DOWNLOAD_DIR = "Clientsdownloads";

    public void uploadFile(String filePath) {
        File file = new File(DOWNLOAD_DIR, filePath); // File is expected in Clientsdownloads/
        System.out.println("Looking for: " + file.getAbsolutePath());

        if (!file.exists() || !file.isFile()) {
            System.out.println("File doesn't exist.");
            return;
        }

        try {
            URI uri = URI.create(SERVER_URL + "/upload");
            URL url = uri.toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setFixedLengthStreamingMode(file.length());

            try (
                OutputStream os = connection.getOutputStream();
                FileInputStream fis = new FileInputStream(file)
            ) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    System.out.println("Upload successful. Server response: " + br.readLine());
                }
            } else {
                System.out.println("Upload failed. HTTP " + responseCode);
            }

        } catch (IOException e) {
            System.out.println("Error during upload: " + e.getMessage());
        }
    }

    public void downloadFile(String filename) {
        try {
            URI uri = new URI("http", "localhost:8080", "/download", "filename=" + filename, null);
            URL url = uri.toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == 404) {
                System.out.println("Error 404: File not found on server.");
                return;
            } else if (responseCode != 200) {
                System.out.println("Download failed. HTTP " + responseCode);
                return;
            }

            new File(DOWNLOAD_DIR).mkdirs(); // Ensure Clientsdownloads exists
            File outFile = new File(DOWNLOAD_DIR, filename);

            try (
                InputStream is = connection.getInputStream();
                FileOutputStream fos = new FileOutputStream(outFile)
            ) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("Downloaded to: " + outFile.getAbsolutePath());

        } catch (Exception e) {
            System.out.println("Error during download: " + e.getMessage());
        }
    }
}

public class HTTPClient {
    Clientside cs = new Clientside();
    public static final String DOWNLOAD_DIR = "Clientsdownloads";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        HTTPClient client = new HTTPClient();
        new File(DOWNLOAD_DIR).mkdirs(); // Ensure download folder exists

        while (true) {
            System.out.println("\n1. Upload File (POST)");
            System.out.println("2. Download File (GET)");
            System.out.println("3. Exit");
            System.out.print("> ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    System.out.print("Enter file name to upload from Clientsdownloads/: ");
                    client.cs.uploadFile(scanner.nextLine().trim());
                    break;
                case "2":
                    System.out.print("Enter filename to download: ");
                    client.cs.downloadFile(scanner.nextLine().trim());
                    break;
                case "3":
                    System.out.println("Exiting...");
                    return;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }
}
