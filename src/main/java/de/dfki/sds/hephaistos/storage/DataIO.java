package de.dfki.sds.hephaistos.storage;

import de.dfki.sds.hephaistos.DataStoreDescription;
import de.dfki.sds.hephaistos.Preference;
import de.dfki.sds.mschroeder.commons.lang.swing.LoadingListener;

/**
 * Data Input/Output reads {@link DataStoreDescription} and can import or
 * export data between {@link InternalStorage} of the tool.This is also used to preview data.
 * 
 * @param <S> Supported Storage
 * @param <P> Type of Preview (table, tree, etc.)
 */
public abstract class DataIO<S extends InternalStorage, P> {
    
    /**
     * Create a correct internal storage for importing.
     * @param storageManager
     * @return 
     */
    public abstract InternalStorage createInternalStorage(StorageManager storageManager);
    
    /**
     * Exports data from an internal storage.
     * @param from
     * @param to 
     * @param listener 
     */
    public abstract void exporting(S from, DataStoreDescription to, LoadingListener listener) throws Exception;
    
    /**
     * Imports the described data to an internal storage.
     * @param from
     * @param to 
     * @param listener 
     */
    public abstract void importing(DataStoreDescription from, S to, LoadingListener listener) throws Exception;
    
    /**
     * Give a brief preview of the data.
     * @param from
     * @return 
     */
    public abstract P preview(DataStoreDescription from);
 
    /**
     * Returns special preferences for the {@link DataStoreDescription}. 
     * @return 
     */
    public abstract Preference getPreference();

    /**
     * The name that is shown to the user when {@link DataStoreDescription} is
     * completed in a dialog.
     * @return 
     */
    public abstract String getName();
    
    /**
     * The basic data model of the data store.
     * @return 
     */
    public abstract DataModel getDataModel();

    @Override
    public String toString() {
        return getName();
    }
    
}
