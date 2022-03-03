package de.dfki.sds.hephaistos.storage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * To cache the calculated summaries.
 * 
 */
public class StorageSummaryCache {

    private Map<String, Double> doubleCache;
    private Map<String, Long> longCache;
    private Map<String, String> stringCache;
    
    public StorageSummaryCache() {
        doubleCache = new HashMap<>();
        longCache = new HashMap<>();
        stringCache = new HashMap<>();
    }
    
    public long computeLongIfAbsent(String key, Function<String, Long> f) {
        return longCache.computeIfAbsent(key, f);
    }
    
    public double computeDoubleIfAbsent(String key, Function<String, Double> f) {
        return doubleCache.computeIfAbsent(key, f);
    }
    
    public String computeStringIfAbsent(String key, Function<String, String> f) {
        return stringCache.computeIfAbsent(key, f);
    }

    public Set<String> keySet() {
        Set<String> keys = new HashSet<>();
        keys.addAll(doubleCache.keySet());
        keys.addAll(longCache.keySet());
        keys.addAll(stringCache.keySet());
        return keys;
    }
    
    public boolean containsKey(String key) {
        return keySet().contains(key);
    }
    
    public void clear() {
        doubleCache.clear();
        longCache.clear();
        stringCache.clear();
    }
    
    public Object get(String key) {
        for(Map m : Arrays.asList(doubleCache, longCache, stringCache)) {
            if(m.containsKey(key))
                return m.get(key);
        }
        return null;
    }
    
}
