
package de.dfki.sds.kecs.util;

import de.dfki.sds.hephaistos.storage.assertion.Intelligence;
import org.apache.jena.rdf.model.Resource;

/**
 * 
 */
public class TypeWithIntel {

    private Resource type;
    private Intelligence intel;

    public TypeWithIntel(Resource type, Intelligence intel) {
        this.type = type;
        this.intel = intel;
    }

    public Resource getType() {
        return type;
    }

    public void setType(Resource type) {
        this.type = type;
    }

    public Intelligence getIntel() {
        return intel;
    }

    public void setIntel(Intelligence intel) {
        this.intel = intel;
    }
    
}
