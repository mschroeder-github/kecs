
package de.dfki.sds.kecs.modules;

import de.dfki.sds.hephaistos.storage.assertion.Assertion;
import de.dfki.sds.hephaistos.storage.assertion.AssertionListener;
import de.dfki.sds.hephaistos.storage.assertion.AssertionPool;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import de.dfki.sds.kecs.KecsSettings;
import de.dfki.sds.kecs.ml.DefaultItemEmbedding;
import de.dfki.sds.kecs.util.KecsUtils;
import de.dfki.sds.mschroeder.commons.lang.math.MinAvgMaxSdDouble;
import de.dfki.sds.stringanalyzer.helper.GermaNet;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 
 */
public abstract class Module implements AssertionListener {
    
    protected DefaultItemEmbedding embedding = null;
    protected GermaNet germaNet = null;
    
    protected KecsSettings settings;
    
    protected boolean embeddingIsLoaded() {
        return embedding != null;
    }
    
    protected boolean languageResourceIsLoaded() {
        return germaNet != null;
    }
    
    public abstract void bootstrap(FileInfoStorage fileInfoStorage, AssertionPool pool, KecsSettings settings);
    
    public void init(FileInfoStorage fileInfoStorage, AssertionPool pool, KecsSettings settings) {
        //empty
    }

    protected void print(List<Assertion> changes) {
        System.out.println();
        System.out.println(this.getClass().getSimpleName() + " updateOnChanges");
        changes.forEach(a -> System.out.println("\t" + a));
        //System.out.println();
    }
   
    protected Map<String, MinAvgMaxSdDouble> statMap = new HashMap<>();
    
    protected void timeStat(String name, Runnable runnable) {
        MinAvgMaxSdDouble stat = statMap.computeIfAbsent(name, nm -> new MinAvgMaxSdDouble());
        long begin = System.currentTimeMillis();
        runnable.run();
        long end = System.currentTimeMillis();
        stat.add(end - begin);
    }
    
    protected void printTimeStat() {
        List<Entry<String, MinAvgMaxSdDouble>> entryList = new ArrayList<>(statMap.entrySet());
        entryList.sort((a,b) -> Double.compare(b.getValue().getAvg(), a.getValue().getAvg()));
        
        System.out.println();
        for(Entry<String, MinAvgMaxSdDouble> entry : entryList) {
            System.out.println(entry.getKey() + " takes " + entry.getValue().toStringInt());
        }
    }
    
    protected void saveTimeStat() {
        List<Entry<String, MinAvgMaxSdDouble>> entryList = new ArrayList<>(statMap.entrySet());
        entryList.sort((a,b) -> Double.compare(b.getValue().getAvg(), a.getValue().getAvg()));
        
        StringWriter sw = new StringWriter();
        sw.append("\n");
        sw.append(LocalDateTime.now().toString() + "\n");
        for(Entry<String, MinAvgMaxSdDouble> entry : entryList) {
            sw.append(entry.getKey() + " takes " + entry.getValue().toStringInt() + "\n");
        }
        
        KecsUtils.saveTime(settings.getOutputFolder(), sw.toString());
    }

    public void setEmbedding(DefaultItemEmbedding embedding) {
        this.embedding = embedding;
    }

    public void setGermaNet(GermaNet germaNet) {
        this.germaNet = germaNet;
    }
    
}
