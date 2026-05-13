package echoit.util;

public class FileSession {
    private String fileName;
    private int port;
    private int pin;
    private int retryCount;

    public FileSession(String fileName, int retryCount, int port, int pin) {
        this.fileName = fileName;
        this.port = port;
        this.pin = pin;
        this.retryCount = retryCount;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getPin() {
        return pin;
    }

    public void setPin(int pin) {
        this.pin = pin;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
}
