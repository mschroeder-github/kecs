
package de.dfki.sds.kecs.ml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import weka.core.Instance;

/**
 * 
 */
public class Datasets {

    private List<Object[]> trainSet;
    private List<Object[]> testSet;
    private List<String> classLabels;
    
    //get the associated resources for a given record
    private Map<Object[], Set<Resource>> record2resources;
    private Map<Instance, Set<Resource>> instance2resources;
    
    private Map<Object[], Statement> record2stmt;
    private Map<Instance, Statement> instance2stmt;

    public Datasets() {
        trainSet = new ArrayList<>();
        testSet = new ArrayList<>();
        
        record2resources = new HashMap<>();
        instance2resources = new HashMap<>();
        
        record2stmt = new HashMap<>();
        instance2stmt = new HashMap<>();
    }

    public Map<Object[], Statement> getRecord2stmt() {
        return record2stmt;
    }

    public Map<Instance, Statement> getInstance2stmt() {
        return instance2stmt;
    }

    public List<Object[]> getTrainSet() {
        return trainSet;
    }

    public void setTrainSet(List<Object[]> trainSet) {
        this.trainSet = trainSet;
    }

    public List<Object[]> getTestSet() {
        return testSet;
    }

    public void setTestSet(List<Object[]> testSet) {
        this.testSet = testSet;
    }

    public List<String> getClassLabels() {
        return classLabels;
    }

    public void setClassLabels(List<String> classLabels) {
        this.classLabels = classLabels;
    }
    
    public static double[] toDoubleArray(Object[] record) {
        
        int len = 0;
        for(Object o : record) {
            if(o instanceof Double)
                len++;
        }
        
        double[] d = new double[len];
        int j = 0;
        for(int i = 0; i < record.length; i++) {
            if(record[i] instanceof Double) {
                d[j++] = (double) record[i];
            }
        }
        
        return d;
    }
}
