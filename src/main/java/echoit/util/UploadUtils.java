package echoit.util;

import java.util.Random;

public class UploadUtils {

    // Generates unique code
    public static int generateUniqueCode() {
        // Unused ports
        int STARTING_PORT_NO = 49152;
        int ENDING_PORT_NO = 85535;

        Random random = new Random();
        return random.nextInt((ENDING_PORT_NO - STARTING_PORT_NO) + STARTING_PORT_NO);
    }
}
