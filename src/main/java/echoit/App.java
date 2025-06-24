package echoit;

import echoit.controller.FileController;

import java.io.IOException;

/**
 * Hello world!
 */
import java.io.IOException;

public class App {
    public static void main(String[] args) {
        try {
            FileController fileController = new FileController(8080);
            fileController.start();

            System.out.println("Server started on port " + 8080);
            System.out.println("Press 'Escape' key to stop the server");

            // Add shutdown hook to handle graceful termination
            Runtime.getRuntime().addShutdownHook(
                    new Thread(() -> {
                        System.out.println("Shutting down the server");
                        fileController.stop();
                    })
            );

            new Thread(() -> {
                try {
                    while (true) {
                        int key = System.in.read();
                        if (key == 27) {
                            System.out.println("Escape key pressed. Stopping the server...");
                            System.exit(0);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

