package de.dfki.sds.kecs.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;


/**
 * 
 */
public class ExceptionUtility {
    
    public static File exceptionFolder = new File("exceptions");
    
    public static void save(Throwable ex) {
        if(!exceptionFolder.exists()) {
            exceptionFolder.mkdirs();
        }
        
        String stackTrace = ExceptionUtils.getStackTrace(ex);
        File f = new File(exceptionFolder, LocalDateTime.now().toString() + ".txt");
        try {
            FileUtils.writeStringToFile(f, stackTrace, StandardCharsets.UTF_8);
        } catch (IOException e) {
            //ignore
        }
    }
}
