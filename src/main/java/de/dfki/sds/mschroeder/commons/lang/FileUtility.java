package de.dfki.sds.mschroeder.commons.lang;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 *
 * 
 */
public class FileUtility {

    //public becaues also used by FilesystemLoader
    public static class FileDepth {

        public File file;
        public File parent;
        public int depth;

        public FileDepth(File file, File parent, int depth) {
            this.file = file;
            this.parent = parent;
            this.depth = depth;
        }
    }
    
    // https://stackoverflow.com/questions/453018/number-of-lines-in-a-file-in-java
    public static int countLines(File file) {
        if (file.isDirectory()) {
            return 0;
        }

        InputStream is = null;
        try {
            is = new FileInputStream(file);
            if(file.getName().endsWith("gz")) {
                is = new GZIPInputStream(is);
            }
            is = new BufferedInputStream(is);
            byte[] c = new byte[1024];

            int readChars = is.read(c);
            if (readChars == -1) {
                // bail out if nothing to read
                return 0;
            }

            // make it easy for the optimizer to tune this loop
            int count = 0;
            while (readChars == 1024) {
                for (int i = 0; i < 1024;) {
                    if (c[i++] == '\n') {
                        ++count;
                    }
                }
                readChars = is.read(c);
            }

            // count remaining characters
            while (readChars != -1) {
                //System.out.println(readChars);
                for (int i = 0; i < readChars; ++i) {
                    if (c[i] == '\n') {
                        ++count;
                    }
                }
                readChars = is.read(c);
            }

            return count == 0 ? 1 : count;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

    }
}
