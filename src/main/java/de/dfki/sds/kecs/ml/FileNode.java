
package de.dfki.sds.kecs.ml;

import java.util.HashSet;
import java.util.Set;
import org.apache.jena.rdf.model.Resource;

/**
 * 
 */
public class FileNode {

    private String name;
    private Resource file;
    private Set<Resource> topics;
    
    public FileNode() {
        topics = new HashSet<>();
    }

    public Resource getFile() {
        return file;
    }

    public void setFile(Resource file) {
        this.file = file;
    }

    public Set<Resource> getTopics() {
        return topics;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "FileNode{" + "name=" + name + ", file=" + file + ", topics=" + topics.size() + '}';
    }
    
}
