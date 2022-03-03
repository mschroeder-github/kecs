package de.dfki.sds.hephaistos.storage;

/**
 * Abstract class provides basic functionality to a storage in this tool.
 * An internal storage is made for a certain java bean.
 * 
 */
public abstract class InternalStorage
        <T extends StorageItem, //type
        S extends StorageSummary, //summary
        RS> //result set
{
 
    protected InternalStorageMetaData metaData;

    public InternalStorage(InternalStorageMetaData metaData) {
        this.metaData = metaData;
    }
    
    /**
     * Removes all items in the storage.
     */
    public abstract void clear();
    
    /**
     * Removes the whole storage.
     */
    public abstract void remove();

    /**
     * Returns a summary object that is used to summarize the
     * storage's content.
     * @return 
     */
    public abstract S summary();
    
    /**
     * Returns the size of the internal storage.
     * @return 
     */
    public abstract long size();
    
    /**
     * Closes the connection to the storage.
     */
    public abstract void close();

    public InternalStorageMetaData getMetaData() {
        return metaData;
    }
    
    public String getId() {
        return metaData.getId();
    }
    
    @Override
    public String toString() {
        return "InternalStorage{" + "id=" + getId() + ", type=" + getClass().getSimpleName() + '}';
    }
    
    //2020-02-01 currently a problem: could be ignored until really implemented
    //the add and remove methods are in subclasses
    
    /**
     * A keyword based search that returns sorted by relevance stored items.
     * The storage has to decide how and on what textual data the search is 
     * performed.
     * @param keywords
     * @return 
     */
    //public abstract List<T> search(String keywords);
    
    //not a good idea to return an open result set
    /**
     * Performs a structured query that returns a result set which depends
     * on the storage.
     * @param query
     * @return 
     */        
    //public abstract RS query(String query);
    
    /**
     * Performs an executable structured query on the storage.
     * @param query 
     */
    //public abstract void execute(String query);
    
}
