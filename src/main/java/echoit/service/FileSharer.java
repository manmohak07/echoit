package echoit.service;

import echoit.util.FileSession;
import echoit.util.UploadUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// starts a thread
// maintains the history of all the files being served on a port
public class FileSharer {

    public enum PinResult {
        CORRECT,
        WRONG_PIN,
        LOCKED_OUT,
        NOT_FOUND,
    }

    private final HashMap<Integer, FileSession> availableFiles;
    private final HashMap<Integer, ServerSocket> activeSockets = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    public FileSharer() {
        availableFiles = new HashMap<>();
    }

    public FileSession getFileSession(int port) {
        return availableFiles.get(port);
    }

    public PinResult validatePin(int port, int pin) {
        FileSession fileSession = getFileSession(port);

        // Does not exist
        if (fileSession == null) return PinResult.NOT_FOUND;
        // Burnt all retries
        if (fileSession.getRetryCount() <= 0) return PinResult.LOCKED_OUT;
        // Correct pin
        if (fileSession.getPin() == pin) return PinResult.CORRECT;

        // In case of Wrong Pin
        fileSession.setRetryCount(fileSession.getRetryCount() - 1);
        if(fileSession.getRetryCount() <= 0) {
            terminateSession(port);
            return PinResult.LOCKED_OUT;
        }
        return PinResult.WRONG_PIN;

    }

    public void terminateSession(int port) {
        FileSession fileSession = availableFiles.remove(port);

        if (fileSession != null) {
            new File(fileSession.getFileName()).delete();
            System.out.println("Session terminated for port -> " + port);
        }
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
                int pin = UploadUtils.generateRandomPin();
                availableFiles.put(portNo, new FileSession(fileName, 2, portNo, pin));
            }
            return portNo;
        }
    }

    public void startFileServer(int portNo) {
        FileSession fileSession = availableFiles.get(portNo);
        String file1 = fileSession.getFileName();

        // Checking for file existence
        if (file1 == null) {
            System.out.println("No file found at " + portNo);
        }

        // If DNE, then start sending using a TC block
        try (ServerSocket serverSocket = new ServerSocket(portNo, 50)) {
            assert file1 != null;

            activeSockets.put(portNo, serverSocket);
            scheduler.schedule(() -> cleanUpSession(portNo), 60, TimeUnit.SECONDS);

            System.out.println("Serving file " + new File(file1).getName() + " on port " + portNo);
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client " + clientSocket.getInetAddress());
            new Thread(new FileSenderHandler(clientSocket, file1)).start();
        } catch (IOException e) {
            System.err.println("Error handling file on " + portNo + ": " + e.getMessage());
        }

    }

    private void cleanUpSession(int portNo) {
        // close the session
        if(activeSockets.containsKey(portNo)) {
            ServerSocket serverSocket = activeSockets.remove(portNo);

            if(serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.out.println("Error closing server socket on " + portNo + ": " + e.getMessage());
                }
            }
        }

        // delete the file
        FileSession fileSession = availableFiles.remove(portNo);
        if (fileSession != null) {
            new File(fileSession.getFileName()).delete();
            System.out.println("Cleaned up session on port " + portNo);
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
