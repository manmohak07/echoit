package echoit.controller;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import echoit.service.FileSharer;

import java.io.*;
import java.net.Socket;

public class DownloadHandler implements HttpHandler {
    private final FileSharer fileSharer;

    public DownloadHandler(FileSharer fileSharer) {
        this.fileSharer = fileSharer;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type");

        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            String response = "Method not allowed";
            exchange.sendResponseHeaders(405, response.getBytes().length);

            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response.getBytes());
            }
            return;
        }

        // Parse port from path: /download/54321
        String path = exchange.getRequestURI().getPath();
        String portString = path.substring(path.lastIndexOf('/') + 1);
        int port;
        try {
            port = Integer.parseInt(portString);
        } catch (NumberFormatException e) {
            sendJson(exchange, 400, "{\"error\": \"Invalid port\"}");
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        int pin = -1;

        if (query != null) {
            for (String param:  query.split("&")) {
                String[] kv = param.split("=");
                if (kv.length == 2 && kv[0].equals("pin")) {
                    try {
                        pin = Integer.parseInt(kv[1]);
                    } catch (NumberFormatException ignored) {

                    }
                }
            }
        }

        if (pin == -1) {
            sendJson(exchange, 400, "{\"error\": \"PIN is required\"}");
            return;
        }

        FileSharer.PinResult result = fileSharer.validatePin(port, pin);

        switch (result) {
            case NOT_FOUND:
                sendJson(exchange, 404, "{\"error\": \"Session not found or expired\"}");
                return;
            case LOCKED_OUT:
                sendJson(exchange, 403, "{\"error\": \"Too many incorrect attempts. Session has been terminated.\"}");
                return;
            case WRONG_PIN:
                int retriesLeft = fileSharer.getFileSession(port).getRetryCount();
                sendJson(exchange, 401, "{\"error\": \"Incorrect PIN\", \"retriesLeft\": " + retriesLeft + "}");
                return;
            case CORRECT:
                break; // fall through to download
        }

        try (Socket socket = new Socket("127.0.0.1", port)) {
            InputStream socketInput = socket.getInputStream();
            File temp = File.createTempFile("download", ".tmp");
            String fileName = "downloaded-file";

            try(FileOutputStream fileOutputStream = new FileOutputStream(temp)) {
                byte[] buffer = new byte[10000];
                int byteRead;
                int b;
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                while ((b = socketInput.read()) != -1) {
                    if (b == '\n') break;
                    byteArrayOutputStream.write(b);
                }

                String header = byteArrayOutputStream.toString().trim();
                if (header.startsWith("Filename: ")) {
                    fileName = header.substring("Filename: ".length());
                    //fileOutputStream.write(buffer, 0, byteRead);
                }

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

        } catch (Exception e) {
            System.err.println("Not able to download the file " + e.getMessage());
            sendJson(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
            // String response = "Error downloading the file " + e.getMessage();
            // headers.add("Content-type", "text/plain");
            // exchange.sendResponseHeaders(400, response.getBytes().length);

            // try (OutputStream outputStream = exchange.getResponseBody()) {
            //    outputStream.write(response.getBytes());
            // }
        }
    }
    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = json.getBytes();
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
