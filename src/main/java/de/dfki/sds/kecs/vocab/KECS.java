
package de.dfki.sds.kecs.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.SKOS;

/**
 * 
 */
public class KECS {

    public static final String NS = "https://www.dfki.uni-kl.de/~mschroeder/ld/kecs#";
    
    public static final Resource Document = ResourceFactory.createResource(NS + "Document");
    public static final Resource Folder = ResourceFactory.createResource(NS + "Folder");
    
    //Entity -> Entity
    public static final Property number = ResourceFactory.createProperty(NS + "number");
    public static final Property containsDomainTerm = ResourceFactory.createProperty(NS + "containsDomainTerm");
    
    
    public static final Property techLabel = ResourceFactory.createProperty(SKOS.uri + "techLabel");
    
}
