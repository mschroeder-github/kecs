
package de.dfki.sds.kecs.util;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 
 */
public class JsonUtility {

    public static void forceLinkedHashMap(JSONObject json) {
        try {
            Field map = json.getClass().getDeclaredField("map");
            map.setAccessible(true);
            map.set(json, new LinkedHashMap<>());
            map.setAccessible(false);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public static List<Object> getList(JSONArray array) {
        try {
            Field list = array.getClass().getDeclaredField("myArrayList");
            list.setAccessible(true);
            List<Object> l = (List<Object>) list.get(array);
            list.setAccessible(false);
            return l;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public static <T> List<T> getList(JSONArray array, Class<T> type) {
        try {
            Field list = array.getClass().getDeclaredField("myArrayList");
            list.setAccessible(true);
            List<T> l = (List<T>) list.get(array);
            list.setAccessible(false);
            return l;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    
}
