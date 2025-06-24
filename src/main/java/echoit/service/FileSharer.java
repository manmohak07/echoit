package echoit.service;

import echoit.util.UploadUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

// starts a thread
// maintains the history of all the files being served on a port
public class FileSharer {
    private HashMap<Integer, String> availableFiles;

    public FileSharer() {
        availableFiles = new HashMap<>();
    }

    public int offerFile(String fileName) {
        // Free port no on the system
        int portNo;

        // Searches for a free port
        // *IMPROVISATION* CAN IMPLEMENT TIMEOUT FEATURE TO AVOID INF LOOP
        while(true) {
            portNo = UploadUtils.generateUniqueCode();

            // Skipping the port if already in use
            if (availableFiles.containsKey(portNo)) {
                continue;
            } else {
                // Reserving the port for the current file
                availableFiles.put(portNo, fileName);
            }
            return portNo;
        }
    }

    public void startFileServer(int portNo) {
        String file1 = availableFiles.get(portNo);

        // Checking for file existence
        if (file1 == null) {
            System.out.println("No file found at " + portNo);
        }

        // If DNE, then start sending using a TC block
        try (ServerSocket serverSocket = new ServerSocket(portNo, 50)) {
            assert file1 != null;
            System.out.println("Serving file " + new File(file1).getName() + " on port " + portNo);
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client " + clientSocket.getInetAddress());
            new Thread(new FileSenderHandler(clientSocket, file1)).start();
        } catch (IOException e) {
            System.err.println("Error handling file on " + portNo + ": " + e.getMessage());
        }

    }

    private static class FileSenderHandler implements Runnable{
        private final Socket clientSocket;
        private final String fileName;

        public FileSenderHandler(Socket clientSocket, String fileName) {
            this.clientSocket = clientSocket;
            this.fileName = fileName;
        }

        @Override
        public void run() {
            try(FileInputStream fis = new FileInputStream(fileName)) {
                OutputStream outputStream = clientSocket.getOutputStream();
                String file = new File(fileName).getName();
                String header = "Filename: " + file + "\n";
                outputStream.write(header.getBytes());

                byte[] buffer = new byte[10000];
                int byteRead;

                while((byteRead = fis.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, byteRead);
                }
                System.out.println(file + " has been sent to " + clientSocket.getInetAddress());
            } catch (Exception e) {
                System.err.println("Error sending file " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (Exception e) {
                    System.err.println("Error closing the socket" + e.getMessage());
                }
            }
        }
    }

}
