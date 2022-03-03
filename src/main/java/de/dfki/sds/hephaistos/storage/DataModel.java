package de.dfki.sds.hephaistos.storage;

/**
 * All data models supported by this tool.
 * 
 */
public enum DataModel {
    /**
     * Only a document with unstructured text.
     */
    Flat,
    
    /**
     * Relations tables: it is structured with a schema.
     */
    Relational,
    
    /**
     * A tree structure with nodes that are relational.
     */
    Hierarchical,
    
    /**
     * Arbitrary connected nodes with labelled edges.
     */
    Graph
}
