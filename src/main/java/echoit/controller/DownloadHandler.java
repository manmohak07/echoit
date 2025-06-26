package echoit.controller;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.source.doctree.SystemPropertyTree;
import jdk.jfr.ContentType;

import java.io.*;
import java.net.Socket;

public class DownloadHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            String response = "Method not allowed";
            exchange.sendResponseHeaders(405, response.getBytes().length);

            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response.getBytes());
            }
            return;
        }

        String path = exchange.getRequestURI().getPath();
        // String portString = path.substring(path.lastIndexOf('/') + 1);
        String portString = path.substring(path.lastIndexOf('/') + 1);

        try {
            int port = Integer.parseInt(portString);

            try (Socket socket = new Socket("127.0.0.1", port)) {
                InputStream socketInput = socket.getInputStream();
                File temp = File.createTempFile("download", ".tmp");
                String fileName = "downloaded-file";

                try(FileOutputStream fileOutputStream = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[10000];
                    int byteRead;
                    int b;
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                    while((b = socketInput.read()) != -1) {
                        if (b == '\n') {
                            break;
                        }

                        byteArrayOutputStream.write(b);
                        String header = byteArrayOutputStream.toString().trim();

                        if (header.startsWith("Filename: ")) {
                            fileName = header.substring("Filename: ".length());
                            //fileOutputStream.write(buffer, 0, byteRead);
                        }

                        // *IMPROVISATION* CAN USE A VARIABLE TO CHECK IF WE HAVE REACHED THE LIMIT OF 10K
                        // AND THEN ERASE THE BUFFER SO THAT FILE EXCEEDING MORE THAN 10K IN SIZE CAN BE WRITTEN
                        while ((byteRead = socketInput.read(buffer)) != -1) {
                            fileOutputStream.write(buffer, 0, byteRead);
                        }
                    }
                    headers.add("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                    headers.add("Content-Type", "application/octet-stream");
                    exchange.sendResponseHeaders(200, temp.length());

                    try(OutputStream outputStream = exchange.getResponseBody()) {
                        FileInputStream fileInputStream = new FileInputStream(temp);
                        byte[] newBuffer = new byte[10000];
                        int bytesRead;

                        while ((bytesRead = fileInputStream.read(newBuffer)) != -1) {
                            outputStream.write(newBuffer, 0, bytesRead);
                        }
                    }
                    temp.delete();
                }
            }

        } catch (Exception e) {
            System.err.println("Not able to download the file " + e.getMessage());
            String response = "Error downloading the file " + e.getMessage();
            headers.add("Content-type", "text/plain");
            exchange.sendResponseHeaders(400, response.getBytes().length);

            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response.getBytes());
            }
        }

    }
}
