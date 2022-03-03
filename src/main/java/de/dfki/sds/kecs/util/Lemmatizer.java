package de.dfki.sds.kecs.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * 
 * @version there is also a similar texana lemmatizer impl
 */
public class Lemmatizer {

    //static to share it between
    private static Map<Long, String> word2lemma;
    
    //to check how often it happens that one word has multiple lemmas
    private static Map<String, List<String>> word2lemmaList;
    
    //file for cache
    File tmpFile = new File(System.getProperty("java.io.tmpdir"), "lemma.bin.gz");
    
    private File lemmatableTxtGz;
    
    public Lemmatizer(File lemmatableTxtGz) {
        this.lemmatableTxtGz = lemmatableTxtGz;
        load();
    }

    //from resource
    public Lemmatizer(String resourcePath) {
        try(ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(Lemmatizer.class.getResourceAsStream(resourcePath)))) {
            word2lemma = (Map<Long, String>) ois.readObject();
        } catch (IOException ex) {
            //ignore
        } catch (ClassNotFoundException ex) {
            //ignore
        }
    }
                
    private void rawToHashMap() {
        word2lemma = new HashMap<>();
        
        word2lemmaList = new HashMap<>();
        
        //long begin = System.currentTimeMillis();
        
        //lemmatable from https://github.com/languagetool-org/german-pos-dict
        try(BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(lemmatableTxtGz)), "UTF-8"))) { //new FileInputStream(new File("langres/lemmatable.txt")), "UTF-8")
            String line;
            while((line = br.readLine()) != null) {
                
                String[] split = line.split("\t");
                
                long h = hash(split[0].toLowerCase());
                
                if(!word2lemma.containsKey(h)) {
                    word2lemma.put(h, split[1]);
                }
                
                /*
                String lemma = split[1].toLowerCase();
                List<String> list = word2lemmaList.computeIfAbsent(split[0].toLowerCase(), l -> new ArrayList<>());
                if(!list.contains(lemma)) {
                    list.add(lemma);
                }
                */
            }
        } catch (IOException ex) {
            ExceptionUtility.save(ex);
            throw new RuntimeException(ex);
        }
        
        /*
        int count = 0;
        for(Entry<String, List<String>> e : word2lemmaList.entrySet()) {
            if(e.getValue().size() > 1) {
                System.out.println(e);
                count++;
            }
        }
        System.out.println("multi-lemma: " + count);
        e.g. 
        entpuderndsten=[entpuderndste, entpudernd]
        unwirschste=[unwirschste, unwirsch]
        rationierendsten=[rationierendste, rationierend]
        blauäugigere=[blauäugigere, blauäugig]
        ausbauenden=[ausbauende, ausbauend]
        abgescheuerteste=[abgescheuerteste, abgescheuert]
        abgebundensten=[abgebundenste, abgebunden]
        totschlagenden=[totschlagende, totschlagend]
        plottend=[plottend, plotten]
        unsittlichste=[unsittlichste, unsittlich]
        gewachten=[gewachte, gewacht]
        multi-lemma: 92917
        */
        
        //long end = System.currentTimeMillis();
        
        //App.settings.debug(word2lemma.size() + " lemmas in " + (end - begin) + " ms");
    }
    
    private void save() {
        save(tmpFile);
    }
    
    public void save(File binFile) {
        try(ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(binFile)))) {
            oos.writeObject(word2lemma);
        } catch (IOException ex) {
            ExceptionUtility.save(ex);
            throw new RuntimeException(ex);
        }
        //App.settings.debug("save to " + tmpFile);
    }
    
    private void load() {
        //no load necessary
        if(word2lemma != null)
            return;
        
        if(tmpFile.exists()) {
            try(ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(new FileInputStream(tmpFile)))) {
                //App.settings.debug("load from temp file");
                word2lemma = (Map<Long, String>) ois.readObject();
            } catch (Exception ex) {
                //App.settings.debug("exception fallback: load from raw");
                rawToHashMap();
                save();
            }
        } else {
            //App.settings.debug("no temp file fallback: load from raw");
            rawToHashMap();
            save();
        }
    }
    
    public static long hash(String string) {
        long h = 1125899906842597L; // prime
        int len = string.length();

        for (int i = 0; i < len; i++) {
            h = 31 * h + string.charAt(i);
        }
        return h;
    }
    
    public String lookup(String word) {
        //String lc = word.toLowerCase();
        String lc = StringUtils.lowerCase(word);
        long hash = hash(lc);
        return word2lemma.get(hash);
    }
    
    public String lookupOrWord(String word) {
        String lemma = word2lemma.get(hash(StringUtils.lowerCase(word)));
        if(lemma != null)
            return lemma;
        return word;
    }
    
    public String lookupOr(String word, Function<String, String> transformer) {
        String lemma = word2lemma.get(hash(StringUtils.lowerCase(word)));
        if(lemma != null)
            return lemma;
        return transformer.apply(word);
    }
    
    public String lookupOrLowercaseWord(String word) {
        String lemma = word2lemma.get(hash(StringUtils.lowerCase(word)));
        if(lemma != null)
            return lemma;
        return word.toLowerCase();
    }
    
    
    
}
