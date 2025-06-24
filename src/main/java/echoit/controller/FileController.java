package echoit.controller;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import echoit.service.FileSharer;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileController {
    private final FileSharer fileSharer;
    private final HttpServer httpServer;
    private final String uploadDirectory;
    private final ExecutorService executorService;

    public FileController(int port) throws IOException {
        this.fileSharer = new FileSharer();
        this.httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        this.uploadDirectory = System.getProperty("java.io.tmpdir") + File.separator + "echoit-uploads";
        this.executorService = Executors.newFixedThreadPool(50);

        File uploadFile = new File(uploadDirectory);
        if (!uploadFile.exists()) {
            uploadFile.mkdirs();
        }

         httpServer.createContext("/upload", new UploadHandler());
         httpServer.createContext("/download", new DownloadHandler());
         httpServer.createContext("/", new CORSHandler());
         httpServer.setExecutor(executorService);
    }

    public void start() {
        httpServer.start();
        System.out.println("Server started on port " + httpServer.getAddress().getPort());
    }

    public void stop() {
        httpServer.stop(0);
        executorService.shutdown();
        System.out.println("Server stopped");
    }

    public class CORSHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Making sure that GET, POST methods are not handled by CORS
            String response = "NOT FOUND";
            exchange.sendResponseHeaders(404, response.getBytes().length);

            try(OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response.getBytes());
                // NO NEED TO CLOSE IT, AS TRY WILL DO IT ON ITS OWN, AS SOON AS THE SCOPE ENDS
                // outputStream.close();
            }
        }
    }

    private class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");

            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                String response = "Method not allowed";
                exchange.sendResponseHeaders(405, response.getBytes().length);

                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(response.getBytes());
                }
                return;
            }

            Headers requestHeaders = exchange.getRequestHeaders();
            String contentType = requestHeaders.getFirst("Content-Type");

            if (contentType == null || contentType.startsWith("multipart/form-data")) {
                String response = "Bad request: Content-Type must be multipart/form-data";
                exchange.sendResponseHeaders(400, response.getBytes().length);

                try(OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(response.getBytes());
                }
                return;
            }

            try {
                String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                IOUtils.copy(exchange.getRequestBody(), byteArrayOutputStream);
                byte[] requestData = byteArrayOutputStream.toByteArray();

                MultiParser multiParser = new MultiParser(requestData, boundary);
                MultiParser.ParseResult result = multiParser.parse();

                if (result == null) {
                    String response = "Could not parse the file content";
                    exchange.sendResponseHeaders(400, response.getBytes().length);

                    try(OutputStream outputStream = exchange.getResponseBody()) {
                        outputStream.write(response.getBytes());
                    }
                    return;
                }

                String fileName = result.fileName;
                if (fileName == null || fileName.trim().isEmpty()) {
                    fileName = "unnamed-file";
                }
                String uniqueFileName = UUID.randomUUID().toString() + "-" + new File(fileName).getName();
                String filePath = uploadDirectory + File.separator + uniqueFileName;

                try (FileOutputStream fileOutputStream = new FileOutputStream(filePath)) {
                    fileOutputStream.write(result.fileContent);
                }

                int port = fileSharer.offerFile(filePath);
                new Thread(() -> fileSharer.startFileServer(port));

                String jsonResponse = "{\"port\": " + port + "}";
                headers.add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);

                try(OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(jsonResponse.getBytes());
                }

            } catch (Exception e) {
                System.err.println("Error processing the upload " + e.getMessage());
                String response = "Server error " + e.getMessage();
                exchange.sendResponseHeaders(500, response.getBytes().length);

                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(response.getBytes());
                }
            }
        }
    }

    private static class MultiParser {
        private final byte[] data;
        private final String boundary;

        public MultiParser(byte[] data, String boundary) {
            this.data = data;
            this.boundary = boundary;
        }

        public ParseResult parse() {
            try {
                String stringData = new String(data);
                String fileNameMarker = "filename=\"";

                int start = stringData.indexOf(fileNameMarker);
                if (start == -1) {
                    return null;
                }

                int end = stringData.indexOf("\"", start);
                String fileName = stringData.substring(start, end);

                String contentTypeMarker = "Content-Type: ";
                String contentType = "application/octet-stream";
                int contentTypeStart = stringData.indexOf(contentTypeMarker, end);
                if (contentTypeStart != -1) {
                    contentTypeStart += contentTypeMarker.length();
                    int contentTypeEnd = stringData.indexOf("\r\n", contentTypeStart);
                    contentType = stringData.substring(contentTypeStart, contentTypeEnd);
                }

                String headerEndMarker = "\r\n\r\n";

                int headerEnd = stringData.indexOf(headerEndMarker);
                if (headerEnd == -1) {
                    return null;
                }

                int contentStart = headerEnd + headerEndMarker.length();

                byte[] boundaryBytes = ("\r\n--" + boundary + "--").getBytes();

                int contentEnd = findSequence(data, boundaryBytes, contentStart);
                if (contentEnd == -1) {
                    boundaryBytes = ("\r\n--" + boundary).getBytes();
                    contentEnd = findSequence(data, boundaryBytes, contentStart);
                }

                if (contentEnd == -1 || contentEnd <= contentStart) {
                    return null;
                }


                byte[] fileContent = new byte[contentEnd - contentStart];
                System.arraycopy(data, contentStart, fileContent, 0, fileContent.length);

                return new ParseResult(fileName, fileContent, contentType);


            } catch (Exception e) {
                System.err.println("Error parsing " + e.getMessage());
                return null;
            }
        }

        public static class ParseResult {
            public final String fileName;
            public final byte[] fileContent;
            public final String contentType;

            public ParseResult(String fileName, byte[] fileContent, String contentType) {
                this.fileName = fileName;
                this.fileContent = fileContent;
                this.contentType = contentType;
            }


        }

        private static int findSequence(byte[] data, byte[] boundaryBytes, int startPos) {
            int bound = data.length - boundaryBytes.length;
            outer:
            for (int i = startPos; i <= bound; i++) {
                for (int j = 0; j < boundaryBytes.length; j++) {
                    if (data[i + j] != boundaryBytes[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }
    }

}
