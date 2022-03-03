package de.dfki.sds.hephaistos.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

/**
 * A storage summary can calculate metrics that gives insights in the storage's
 * content.
 * 
 */
public abstract class StorageSummary {
    
    protected StorageSummaryCache cache;

    public StorageSummary() {
        this(new StorageSummaryCache());
    }
    
    public StorageSummary(StorageSummaryCache cache) {
        this.cache = cache;
    }
    
    /**
     * This calculates selected metrics and returns a sorted list.
     * Implementations override this.
     * @return  If empty list returned, metrics are sorted alphabetically.
     */
    public List<String> prepare() {
        return Arrays.asList();
    }
    
    /**
     * Based on {@link #prepare() } creates a table model.
     * @return 
     */
    public final TableModel asTableModel() {
        Vector data = new Vector();
        
        List<String> rest = new ArrayList<>(cache.keySet());
        
        List<String> metrics = prepare();
        for(String metric : metrics) {
            Vector row = new Vector();
            row.add(metric);
            row.add(cache.get(metric));
            data.add(row);
            
            rest.remove(metric);
        }
        
        //rest ordered
        if(!rest.isEmpty()) {
            rest.sort((o1, o2) -> {
                return o1.compareTo(o2);
            });
            
            for(String metric : rest) {
                if(!cache.containsKey(metric))
                    continue;
                
                Vector row = new Vector();
                row.add(metric);
                row.add(cache.get(metric));
                data.add(row);
            }
        }
        
        Vector columns = new Vector();
        columns.add("Metric");
        columns.add("Value");
        
        return new DefaultTableModel(data, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }
    
    public void clearCache() {
        cache.clear();
    }
    
}
