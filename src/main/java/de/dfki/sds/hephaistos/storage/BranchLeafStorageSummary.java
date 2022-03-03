package de.dfki.sds.hephaistos.storage;

import java.util.Arrays;
import java.util.List;

/**
 * A default summary for all branch leaf storages.
 * 
 */
public class BranchLeafStorageSummary extends StorageSummary {
    
    private BranchLeafStorage storage;

    public BranchLeafStorageSummary(BranchLeafStorage storage, StorageSummaryCache cache) {
        super(cache);
        this.storage = storage;
    }

    @Override
    public List<String> prepare() {
        getNodeCount();
        return Arrays.asList();
    }
    
    public long getNodeCount() {
        return cache.computeLongIfAbsent("node count", s -> storage.size());
    }
    
}
