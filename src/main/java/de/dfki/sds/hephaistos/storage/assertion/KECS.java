
package de.dfki.sds.hephaistos.storage.assertion;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * 
 */
public class KECS {

    public static final String NS = "https://www.dfki.uni-kl.de/~mschroeder/ld/kecs#";
    
    //Entity -> Entity
    //public static final Property number = ResourceFactory.createProperty(NS + "number");
    public static final Property containsDomainTerm = ResourceFactory.createProperty(NS + "containsDomainTerm");
    
}
