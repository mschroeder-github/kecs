package de.dfki.sds.mschroeder.commons.lang;

/**
 *
 * 
 */
public class StringUtility {
    
    public static String toProperCase(String s) {
        if (s == null) {
            return null;
        }

        if (s.length() == 0) {
            return s;
        }

        if (s.length() == 1) {
            return s.toUpperCase();
        }

        return s.substring(0, 1).toUpperCase()
                + s.substring(1).toLowerCase();
    }
    
    public static boolean isCamelCase(String s) {
        //V1
        //return s.matches("([A-Z])?[a-z]+([A-Z]+[a-z0-9]*)+");
        
        //V2
        return s.matches("([A-Z]|[a-z])+((\\-|[0-9]+)?[A-Z]+[a-z0-9]*)+");
    }
}
