
package de.dfki.sds.kecs.util;

import de.dfki.sds.hephaistos.storage.assertion.Assertion;
import java.util.Set;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import weka.core.Instance;

/**
 * 
 */
public class Prediction {

    private String classLabel;
    private double confidence;
    private Set<Resource> resources;
    private Instance instance;
    private Statement statement;
    private double distance;
    private Assertion assertion;
    
    public Prediction() {
    }

    public Prediction(String classLabel) {
        this.classLabel = classLabel;
    }
    
    public Prediction(String classLabel, double confidence) {
        this.classLabel = classLabel;
        this.confidence = confidence;
    }
    
    

    public String getClassLabel() {
        return classLabel;
    }

    public void setClassLabel(String classLabel) {
        this.classLabel = classLabel;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    @Deprecated
    public Set<Resource> getResources() {
        return resources;
    }

    @Deprecated
    public void setResources(Set<Resource> resources) {
        this.resources = resources;
    }

    public Statement getStatement() {
        return statement;
    }

    public void setStatement(Statement statement) {
        this.statement = statement;
    }

    public Assertion getAssertion() {
        return assertion;
    }

    public void setAssertion(Assertion assertion) {
        this.assertion = assertion;
    }
    
    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }
    
    public Instance getInstance() {
        return instance;
    }

    public void setInstance(Instance instance) {
        this.instance = instance;
    }
    
    @Override
    public String toString() {
        return "Prediction{" + "classLabel=" + classLabel + ", confidence=" + confidence + ", distance=" + distance + ", assertion=" + assertion + '}';
    }
    
}
