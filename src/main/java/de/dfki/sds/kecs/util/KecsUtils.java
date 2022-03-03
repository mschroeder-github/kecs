
package de.dfki.sds.kecs.util;

import de.dfki.sds.hephaistos.storage.assertion.Assertion;
import de.dfki.sds.hephaistos.storage.assertion.Intelligence;
import de.dfki.sds.hephaistos.storage.assertion.Rating;
import de.dfki.sds.hephaistos.storage.file.FileInfo;
import de.dfki.sds.kecs.KecsApp;
import de.dfki.sds.kecs.ml.StatusManager;
import de.dfki.sds.kecs.modules.ConceptDiscovery;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.jena.rdf.model.Resource;
import org.json.JSONObject;

/**
 * 
 */
public class KecsUtils {
    
    public static int getId(String fileUri) {
        return Integer.parseInt(fileUri.split("\\:")[2]);
    }
    
    public static Resource getResource(FileInfo fi) {
        return KecsApp.creator.createResource(getURI(fi));
    }
    
    public static String getURI(FileInfo fi) {
        return new JSONObject(fi.getMeta()).getString("uri");
    }
    
    public static TypeWithIntel getType(List<Assertion> types) {
        //first positive type assertion (by default NamedIndividual)
        Resource maxType = ConceptDiscovery.DEFAULT_TYPE;
        Intelligence maxIntel = Intelligence.AI;
        double maxConf = 0;
        for(Assertion typeAssertion : types) {
            if(typeAssertion.getRating() == Rating.Positive) {
                
                //use always NI
                if(typeAssertion.getIntelligence() == Intelligence.NI) {
                    maxIntel = Intelligence.NI;
                    maxType = typeAssertion.getObject();
                    break;
                } else {
                    //in case of AI use highest confidence
                    if(typeAssertion.getConfidence() > maxConf) {
                        maxType = typeAssertion.getObject();
                        maxConf = typeAssertion.getConfidence();
                    }
                }
            }
        }
        return new TypeWithIntel(maxType, maxIntel);
    }
    
    public static void saveHistoryEntry(JSONObject entry, File folder) {
        File historyFile = new File(folder, "history.jsonl");
        if (!historyFile.exists()) {
            try {
                historyFile.createNewFile();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        try {
            FileWriterWithEncoding fw = new FileWriterWithEncoding(historyFile, StandardCharsets.UTF_8, true);
            fw.write(entry.toString() + "\n");
            fw.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private static final Object syncPointInteraction = new Object();
    
    public static void saveInteraction(JSONObject entry, File folder) {
        synchronized(syncPointInteraction) { 
            File file = new File(folder, "interactions.jsonl.gz");
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
            try {
                OutputStream os = new GZIPOutputStream(new FileOutputStream(file, true));
                String line = entry.toString() + "\n";
                os.write(line.getBytes(StandardCharsets.UTF_8));
                os.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
    
    private static final Object syncPointStatus = new Object();
    
    public static void saveStatus(StatusManager statusManager, File folder) {
        synchronized(syncPointStatus) { 
            JSONObject entry = new JSONObject();
            entry.put("when", LocalDateTime.now().toString());
            statusManager.fillJSON(entry);
            
            //do not save chart data
            entry.remove("data");

            File file = new File(folder, "status.jsonl.gz");
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
            try {
                OutputStream os = new GZIPOutputStream(new FileOutputStream(file, true));
                String line = entry.toString() + "\n";
                os.write(line.getBytes(StandardCharsets.UTF_8));
                os.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static List<JSONObject> loadHistoryEntries(File folder) {
        List<JSONObject> list = new ArrayList<>();
        File historyFile = new File(folder, "history.jsonl");
        if (!historyFile.exists()) {
            return list;
        }
        try {
            for (String line : FileUtils.readLines(historyFile, StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                list.add(new JSONObject(line));
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return list;
    }
    
    private static final Object syncPointTime = new Object();
    
    public static void saveTime(File folder, String timeInfo) {
        synchronized(syncPointTime) { 
            File file = new File(folder, "times.txt.gz");
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
            try {
                OutputStream os = new GZIPOutputStream(new FileOutputStream(file, true));
                os.write(timeInfo.getBytes(StandardCharsets.UTF_8));
                os.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
