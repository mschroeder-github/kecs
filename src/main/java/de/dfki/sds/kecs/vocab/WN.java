
package de.dfki.sds.kecs.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Wordnet.
 */
public class WN {

    public static final String NS = "http://wordnet-rdf.princeton.edu/ontology#";
    
    public static final String Ontolex = "http://www.w3.org/ns/lemon/ontolex#";
    
    public static final Resource noun = ResourceFactory.createResource(NS + "noun");
    public static final Resource LexicalSense = ResourceFactory.createResource(Ontolex + "LexicalSense");
    
    public static final Property partOfSpeech = ResourceFactory.createProperty(NS + "partOfSpeech");
    public static final Property hypernym = ResourceFactory.createProperty(NS + "hypernym");
    public static final Property hyponym = ResourceFactory.createProperty(NS + "hyponym");
    public static final Property writtenRep = ResourceFactory.createProperty(Ontolex + "writtenRep");
    public static final Property canonicalForm = ResourceFactory.createProperty(Ontolex + "canonicalForm");
    
}
