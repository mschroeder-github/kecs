package de.dfki.sds.hephaistos;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;

/**
 *
 * 
 */
public class Counter {

    private File folder;

    public Counter(File folder) {
        this.folder = folder;
        folder.mkdirs();
    }

    public int getIncreased(String name) {
        synchronized (this) {
            try {
                File counterFile = new File(folder, name + ".txt");
                
                //init with 1
                if (!counterFile.exists()) {
                    FileUtils.writeStringToFile(counterFile, "1", StandardCharsets.UTF_8);
                }
                
                //get from file
                int count = Integer.parseInt(FileUtils.readFileToString(counterFile, StandardCharsets.UTF_8).trim());
                
                //increase and save
                int inc = count + 1;
                FileUtils.writeStringToFile(counterFile, String.valueOf(inc), StandardCharsets.UTF_8);
                
                //return the old value
                return count;
                
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

}
