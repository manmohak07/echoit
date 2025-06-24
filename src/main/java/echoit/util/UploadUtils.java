package echoit.util;

import java.util.Random;

public class UploadUtils {

    // Generates unique code
    public static int generateUniqueCode() {
        // Unused ports
        int STARTING_PORT_NO = 49152;
        int ENDING_PORT_NO = 65535; // Fixed the max port number (was 85535)

        Random random = new Random();
        return STARTING_PORT_NO + random.nextInt(ENDING_PORT_NO - STARTING_PORT_NO + 1); // Fixed the random range calculation
    }
}
