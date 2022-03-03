
package de.dfki.sds.hephaistos.storage.assertion;

import java.util.List;
import org.apache.jena.rdf.model.Resource;

/**
 * 
 */
public class Concept {

    private String uri;
    
    private Assertion conceptAssertion;
    
    private String prefLabel;
    
    private List<Assertion> types;
    private List<Assertion> prefLabels;
    private List<Assertion> hiddenLabels;
    private List<Assertion> isTopicOfs;

    public String getURI() {
        return uri;
    }
    
    public Resource getResource() {
        return conceptAssertion.getSubject();
    }

    public void setURI(String uri) {
        this.uri = uri;
    }

    public Assertion getConceptAssertion() {
        return conceptAssertion;
    }

    public void setConceptAssertion(Assertion conceptAssertion) {
        this.conceptAssertion = conceptAssertion;
    }

    public List<Assertion> getTypes() {
        return types;
    }

    public void setTypes(List<Assertion> types) {
        this.types = types;
    }

    public List<Assertion> getPrefLabels() {
        return prefLabels;
    }

    public void setPrefLabels(List<Assertion> prefLabels) {
        this.prefLabels = prefLabels;
    }

    public List<Assertion> getHiddenLabels() {
        return hiddenLabels;
    }

    public void setHiddenLabels(List<Assertion> hiddenLabels) {
        this.hiddenLabels = hiddenLabels;
    }

    public List<Assertion> getIsTopicOfs() {
        return isTopicOfs;
    }

    public void setIsTopicOfs(List<Assertion> isTopicOfs) {
        this.isTopicOfs = isTopicOfs;
    }

    public String getPrefLabel() {
        return prefLabel;
    }

    public void setPrefLabel(String prefLabel) {
        this.prefLabel = prefLabel;
    }
    
}
