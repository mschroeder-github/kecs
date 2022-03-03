package de.dfki.sds.hephaistos.storage;

import de.dfki.sds.hephaistos.storage.assertion.AssertionPool;
import de.dfki.sds.hephaistos.storage.assertion.AssertionPoolSqlite;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * Manages the storages for the project to persist data, information and knowledge.
 * 
 */
public class StorageManager {
    
    //actually name of the dataset
    public static final String KG_RES_SEG = "knowledgeResource";
    
    /*package*/ transient File folder;
    private transient Connection connection;
    
    /**
     * States which storage ID was initialized for a certain class.
     */
    private Map<String, InternalStorageMetaData> id2metadata;

    public static final String DEFAULT_FILENAME = "data.sqlite";
    
    private String filename = DEFAULT_FILENAME;
    
    private StorageManager() {
        
    }
    
    /**
     * 
     * @param folder where the internal storages should be placed.
     */
    public StorageManager(File folder) {
        this.folder = folder;
        this.folder.mkdirs();
        this.id2metadata = new HashMap<>();
    }
    
    public void open() {
        try {
            connection = DriverManager.getConnection(getSqliteConnectionString());
        } catch(SQLException ex) {
            Utils.saveException(folder, ex);
            throw new RuntimeException(ex);
        }
    }
    
    public void close() {
        try {
            connection.close();
        } catch (SQLException ex) {
            Utils.saveException(folder, ex);
            throw new RuntimeException(ex);
        }
    }
    
    //methods for humans
    
    public FileInfoStorage getFileInfoStorage() {
        return getFileInfoStorage(generateId());
    }
    
    public FileInfoStorage getFileInfoStorage(String id) {
        return getStorage(id, FileInfoStorage.class);
    }
    
    public AssertionPool getAssertionPool() {
        return getAssertionPool(generateId());
    }
    
    public AssertionPool getAssertionPool(String id) {
        return getStorage(id, AssertionPool.class);
    }
    
    //EXTEND HERE FURTHER
    
    /**
     * Returns for an interface type the correct implementation.
     * @param <T>
     * @param id
     * @param type
     * @return 
     */
    public <T extends InternalStorage> T getStorage(String id, Class<T> type) {
        
        //get or create meta data
        InternalStorageMetaData metadata = id2metadata.computeIfAbsent(id, ident -> new InternalStorageMetaData(ident, type.getName()));
        
        if(type == FileInfoStorage.class) {
            return (T) new FileInfoStorage(metadata, connection, new File(folder, "cache"));
        }
        else if(type == AssertionPool.class) {
            return (T) new AssertionPoolSqlite(metadata, connection, folder);
        }
        else {
            id2metadata.remove(id);
            throw new RuntimeException("No storage implementation found for " + type);
        }
    }
    
    public <T extends InternalStorage> T getStorage(Class<T> type) {
        return getStorage(generateId(), type);
    }
    
    /**
     * Uses internal map to get the corresponding class for an id and calls {@link #getStorage(java.lang.String, java.lang.Class) }.
     * This is used by GUI.
     * @param id
     * @return 
     */
    public InternalStorage getStorage(String id) {
        String className = id2metadata.get(id).getClassName();
        Class<InternalStorage> clazz;
        try {
            clazz = (Class<InternalStorage>) StorageManager.class.getClassLoader().loadClass(className);
        } catch (ClassNotFoundException ex) {
            Utils.saveException(folder, ex);
            throw new RuntimeException(ex);
        }
        return getStorage(id, clazz);
    }
    
    public List<InternalStorage> getStorages(List<String> ids) {
        List<InternalStorage> storages = new ArrayList<>();
        for(String id : ids) {
            storages.add(getStorage(id));
        }
        return storages;
    }
    
    /**
     * This removes the storage completely.
     * @param id 
     */
    public void removeStorage(String id) {
        if(!id2metadata.containsKey(id)) {
            throw new RuntimeException("Internal storage does not exist with id " + id);
        }
        InternalStorage s = getStorage(id);
        try {
            s.remove();
        } catch(Exception e) {
            //TODO table locked exception
            //for now ignore
        }
        s.close();
    }
    
    public boolean containsStorage(String id) {
        try {
            ResultSet tables = connection.getMetaData().getTables(null, null, null, new String[] {"TABLE"});
            while(tables.next()) {
                if(tables.getString(3).startsWith(id + "_")) {
                    return true;
                }
            }
            return false;
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
    
    public File getSqliteFile() {
        return new File(folder, filename);
    }
    
    private String getSqliteConnectionString() {
        if(folder == null) {
            throw new RuntimeException("folder is null");
        }
        return "jdbc:sqlite:" + getSqliteFile().getAbsolutePath();
    }
    
    private String getTDB2Location() {
        File tdb2 = new File(folder, "tdb2");
        tdb2.mkdirs();
        return tdb2.getAbsolutePath();
    }
    
    private String generateId() {
        return RandomStringUtils.randomAlphabetic(6);
    }

}
