
package de.dfki.sds.kecs.ml;

import de.dfki.sds.stringanalyzer.SAGazetteerVector;
import de.dfki.sds.stringanalyzer.string.StringEntity;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * 
 */
public class DefaultItemEmbedding extends ItemEmbedding {

    private SAGazetteerVector gazvec;
    
    public DefaultItemEmbedding(File gazetteerFile, boolean lowercase) {
        init(gazetteerFile, lowercase);
    }
    
    public DefaultItemEmbedding(String gazetteerResourcePath, boolean lowercase) {
        init(gazetteerResourcePath, lowercase);
    }
    
    private void init(File gazetteerFile, boolean lowercase) {
        gazvec = new SAGazetteerVector();
        gazvec.setLowercase(lowercase);
        
        try {
            CSVParser csv = CSVFormat.DEFAULT.parse(new InputStreamReader(new GZIPInputStream(new FileInputStream(gazetteerFile)), StandardCharsets.UTF_8));
        
            Iterator<CSVRecord> iter = csv.iterator();
            while(iter.hasNext()) {
                CSVRecord record = iter.next();
                gazvec.register(Arrays.asList(record.get(0)), record.get(1));
            }
            csv.close();
            
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        
        gazvec.build();
    }
    
    private void init(String resourcePath, boolean lowercase) {
        gazvec = new SAGazetteerVector();
        gazvec.setLowercase(lowercase);
        
        try {
            CSVParser csv = CSVFormat.DEFAULT.parse(new InputStreamReader(new GZIPInputStream(DefaultItemEmbedding.class.getResourceAsStream(resourcePath)), StandardCharsets.UTF_8));
        
            Iterator<CSVRecord> iter = csv.iterator();
            while(iter.hasNext()) {
                CSVRecord record = iter.next();
                gazvec.register(Arrays.asList(record.get(0)), record.get(1));
            }
            csv.close();
            
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        
        gazvec.build();
    }
    
    @Override
    public List<String> getFeatures() {
        return gazvec.getClassLabels();
    }

    @Override
    public double[] getVector(StringEntity item) {
        return gazvec.getEntityVector().get(item);
    }
    
    @Override
    public void add(StringEntity item) {
        gazvec.add(item);
    }

    @Override
    public void update() {
        
    }

}
