package de.dfki.sds.hephaistos.storage;

/**
 * Holds meta data for an internal storage like id or class name.
 * 
 */
public class InternalStorageMetaData {
    
    private String id;
    private String className;
    private StorageSummaryCache summaryCache;

    public InternalStorageMetaData(String id, String className) {
        this.id = id;
        this.className = className;
        this.summaryCache = new StorageSummaryCache();
    }

    public String getId() {
        return id;
    }
    
    public String getClassName() {
        return className;
    }

    public void setClassName(String classname) {
        this.className = classname;
    }

    public StorageSummaryCache getSummaryCache() {
        return summaryCache;
    }

    @Override
    public String toString() {
        return "InternalStorageMetaData{" + "id=" + id + ", className=" + className + '}';
    }
    
}
