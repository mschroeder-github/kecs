
package de.dfki.sds.kecs.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * 
 */
public class DOT {

    public static final String NS = "https://w3id.org/dot#";
    
    public static final Property filePath = ResourceFactory.createProperty(NS + "filePath");
    
}
