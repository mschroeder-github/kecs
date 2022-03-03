package de.dfki.sds.hephaistos.storage;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A relational storage simply adds, removes and lists items.
 * 
 * @param <T>
 * @param <S>
 */
public abstract class RelationalStorage<T extends StorageItem, S extends StorageSummary> extends InternalStorage<T, S, ResultSet> {

    public RelationalStorage(InternalStorageMetaData metaData) {
        super(metaData);
    }
    
    public void add(T item) {
        addBulk(Arrays.asList(item));
    }
    
    public abstract void addBulk(Collection<T> items);
    
    public void remove(T item) {
        removeBulk(Arrays.asList(item));
    }
    
    public abstract void removeBulk(Collection<T> items);
    
    public abstract Iterable<T> getListIter();
    
    public List<T> getList(){
        List<T> result = new ArrayList<>();
        getListIter().forEach(result::add);
        return result;
    }
}
