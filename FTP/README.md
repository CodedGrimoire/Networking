# File Transfer System

A simple Java-based file transfer system with a client-server architecture that allows clients to download files from the server.

## Features

- Server handles multiple clients simultaneously
- Client can request files from the server
- Optional file listing - client can decide whether to view available files
- Simple command-based interface (/list, /exit)
- Continuous connection allows multiple file downloads
- File transfer with progress indication

## Setup

1. Compile both files:
   ```
   javac Server.java
   javac Client.java
   ```

2. Create a `server_files` directory in the same location as the Server class to store files that can be served to clients:
   ```
   mkdir server_files
   ```

3. Add some files to the `server_files` directory that clients can download.

## Usage

### Starting the Server

```
java Server
```

The server will start and listen on port 6000. The console will display:
```
File Server started on port 6000
```

### Starting the Client

```
java Client
```

The client will connect to the server running on localhost:6000.

### Client Commands

When connected to the server, the client has the following commands:
- `/list` - Shows a list of available files on the server
- `/exit` - Disconnects from the server
- `filename` - Directly request a file for download

### Downloading Files

1. When the client connects, the server will ask if the client wants to see the list of available files
2. The client can enter `/list` to see available files or directly enter a filename
3. If the file exists, it will be downloaded and saved to the `downloads` directory
4. After each download, the client can choose another file or exit
5. Type `/exit` to disconnect from the server

## Server Commands

While the server is running, you can send messages to specific clients using the format:
```
clientId:message
```

For example:
```
12345:Hello client
```

To exit the server, type:
```
exit
```

## Notes

- Downloaded files are saved in the `downloads` directory
- The server hosts files from the `server_files` directory
- Both directories are created automatically if they don't exist 