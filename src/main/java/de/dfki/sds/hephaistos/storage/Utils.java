
package de.dfki.sds.hephaistos.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 
 */
public class Utils {

    public static final String PROJECT_NAME = "Hephaistos";
    public static final String URI_SCHEME = "hephaistos";
    
    /**
     * <pre>yyyy-MM-dd_HH-mm-ss</pre>, for files
     */
    public static DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    
    public static final String ACQUISITION_PATH = "/de/dfki/sds/hephaistos/acquisition/";
    
    public static final String PROJECT_SEG = "project";
    public static final String DATA_DICT_SEG = "dataDictionary";
    public static final String REG_PERSONS_SEG = "registerOfPersons";
    public static final String EXPEDITION_SEG = "expedition";
    public static final String STORAGE_SEG = "storageManager";
    public static final String GRAPH_SEG = "graph";
    
    
    public static void saveException(File folder, Exception exception) {
        File exceptionFolder = new File(folder, "exceptions");
        exceptionFolder.mkdirs();
        File exceptionFile = new File(exceptionFolder, Utils.DATE_FORMAT.format(new Date()) + ".txt");
        
        String stackTrace = exceptionToString(exception);
        
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(exceptionFile);
            fos.write(stackTrace.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
    
    public static String exceptionToString(Exception exception) {
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();
        return stackTrace;
    }
}
