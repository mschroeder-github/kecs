
package de.dfki.sds.kecs.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * NFO - Nepomuk File Ontology .
 */
public class NFO {

    public static final String NS = "http://www.semanticdesktop.org/ontologies/2007/03/22/nfo#";
    
    public static final Resource LocalFileDataObject = ResourceFactory.createResource(NS + "LocalFileDataObject");
    public static final Resource Folder = ResourceFactory.createResource(NS + "Folder");
    
    public static final Property fileName = ResourceFactory.createProperty(NS + "fileName");
    public static final Property fileSize = ResourceFactory.createProperty(NS + "fileSize");
    public static final Property belongsToContainer = ResourceFactory.createProperty(NS + "belongsToContainer");
    
}
