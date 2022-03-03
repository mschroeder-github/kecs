
package de.dfki.sds.kecs.util;

import org.apache.jena.rdf.model.Property;
import org.jgrapht.graph.DefaultEdge;

/**
 * 
 */
public class PropertyEdge extends DefaultEdge {

    private Property property;

    public PropertyEdge() {
    }

    public PropertyEdge(Property property) {
        this.property = property;
    }
    
    public Property getProperty() {
        return property;
    }

    public void setProperty(Property property) {
        this.property = property;
    }
 
    
}
