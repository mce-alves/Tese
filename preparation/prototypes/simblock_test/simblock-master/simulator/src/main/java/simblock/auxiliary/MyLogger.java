package simblock.auxiliary;

import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class MyLogger {

    public static final Logger TEST_LOGGER = Logger.getLogger("test_logger");


    public static void setupHandler() {
        Handler fileHandler = null;
        SimpleFormatter txtFormatter = null;
        try {
            fileHandler = new FileHandler("../test_output.txt");
            txtFormatter = new SimpleFormatter();
            fileHandler.setFormatter(txtFormatter);
        } catch(Exception e) {
            e.printStackTrace();
        }
        TEST_LOGGER.addHandler(fileHandler);
    }

    public static void log(String m) {
        TEST_LOGGER.info(m);
    }

}
