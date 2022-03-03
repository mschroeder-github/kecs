
package de.dfki.sds.hephaistos.storage.assertion;

import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import java.util.List;

/**
 * 
 */
public interface AssertionListener {

    //the following assertions where changed
    void updateOnChanges(FileInfoStorage fileInfoStorage, AssertionPool pool, List<Assertion> changes);
    
}
