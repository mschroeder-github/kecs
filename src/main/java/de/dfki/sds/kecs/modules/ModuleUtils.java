
package de.dfki.sds.kecs.modules;

import de.dfki.sds.kecs.util.Lemmatizer;
import de.dfki.sds.mschroeder.commons.lang.StringUtility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringJoiner;
import org.apache.commons.lang3.StringUtils;

/**
 * 
 */
public class ModuleUtils {
    
    private static Lemmatizer lemmatizer;
    
    static {
        lemmatizer = new Lemmatizer("/de/dfki/sds/kecs/auxiliary/lemma.bin.gz");
    }
    
    public static Set<String> variations(String term) {
        Set<String> variations = new HashSet<>();
        variations.add(term);

        String[] segments = StringUtils.splitByCharacterTypeCamelCase(term);
        
        List<String> segmentList = new ArrayList<>(Arrays.asList(segments));
        List<String> separators = Arrays.asList("", " ", "_", "-");
        String separatorsStr = " _-";

        //remove already existing separators
        segmentList.removeIf(seg -> StringUtils.containsOnly(seg, separatorsStr));

        //TODO magic number: threshold when making variations is too expensive
        if(segmentList.size() > 3) {
            return variations;
        }
        
        if (segmentList.size() > 1) {
            List<List<String>> separatorInput = new ArrayList<>();
            for (int i = 0; i < segmentList.size() - 1; i++) {
                separatorInput.add(separators);
            }

            List<List<String>> combinations = cartesianProductList(separatorInput);

            for (List<String> combination : combinations) {
                StringBuilder sb = new StringBuilder();

                for (int i = 0; i < segmentList.size(); i++) {
                    sb.append(segmentList.get(i));
                    if (i < combination.size()) {
                        sb.append(combination.get(i));
                    }
                }

                variations.add(sb.toString());
            }
        }

        Map<String, String> umlautMap = new HashMap<>();
        umlautMap.put("Ü", "Ue");
        umlautMap.put("Ö", "Oe");
        umlautMap.put("Ä", "Ae");
        umlautMap.put("ü", "ue");
        umlautMap.put("ö", "oe");
        umlautMap.put("ä", "ae");
        boolean containedUmlaut = false;
        for(Entry<String, String> entry : umlautMap.entrySet()) {
            if(term.contains(entry.getKey())) {
                containedUmlaut = true;
                term = term.replace(entry.getKey(), entry.getValue());
            }
        }
        if(containedUmlaut) {
            variations.addAll(variations(term));
        }
        
        return variations;
    }

    public static String toPrefLabel(String term) {
        String prefLabel = term;
        //"PRO-OPT", "ABC123" vs "ÜDS" vs "SensAI" vs "Grundsteuer" 
        if (!prefLabel.matches("[A-ZÜÖÄ0-9]+") && !StringUtility.isCamelCase(term)) {

            prefLabel = umlautFixV2(prefLabel);
            
            //often helpful
            prefLabel = prefLabel.replace("_", " ");

            //too many strange effects for three-letter words
            if (prefLabel.length() >= 4) {
                prefLabel = lemmatizer.lookupOr(prefLabel, lbl -> lbl);
            }

            //will also shrink multi spaces like "    "
            prefLabel = toProperCaseWords(prefLabel);
        }
        return prefLabel;
    }

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

        int start = 1;
        
        while(start < s.length()) {
            if(Character.isDigit(s.charAt(start - 1))) {
                start++;
            } else {
                break;
            }
        }
        
        return s.substring(0, start).toUpperCase()
                + s.substring(start).toLowerCase();
    }

    public static String toProperCaseWords(String s) {
        StringJoiner sj = new StringJoiner(" ");
        for (String word : s.split("[ ]+")) {
            sj.add(toProperCase(word));
        }
        return sj.toString();
    }

    public static String umlautFix(String s) {
        if (s == null) {
            return null;
        }

        return s
                .replace("Ue", "Ü")
                .replace("Oe", "Ö")
                .replace("Ae", "Ä")
                .replace("ue", "ü")
                .replace("oe", "ö")
                .replace("ae", "ä");
    }
    
    public static String umlautFixV2(String s) {
        if (s == null) {
            return null;
        }

        Map<String, String> umlautMap = new HashMap<>();
        umlautMap.put("Ue", "Ü");
        umlautMap.put("Oe", "Ö");
        umlautMap.put("Ae", "Ä");
        umlautMap.put("ue", "ü");
        umlautMap.put("oe", "ö");
        umlautMap.put("ae", "ä");
        
        for(Map.Entry<String, String> e : umlautMap.entrySet()) {
            
            String esc = e.getKey();
            String umlaut = e.getValue();
            
            int index = s.length();
            int i = 0;
            while((i = s.lastIndexOf(esc, index)) != -1) {
                
                boolean substitute = true;
                if(i > 0) {
                    char c = s.toLowerCase().charAt(i - 1);
                    
                    if(c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u') {
                        substitute = false;
                    }
                }
                
                if(substitute) {
                    s = s.substring(0, i) + umlaut + s.substring(i + 2, s.length());
                }
                
                index = i - 1;
                
                if(index < 0) {
                    break;
                }
            }
        }
        
        return s;
    }
    
    public static <T> List<List<T>> cartesianProductList(List<List<T>> lists) {
        if (lists.size() < 2) {
            List<List<T>> l = new ArrayList<>();

            for (T t : lists.get(0)) {
                l.add(new ArrayList<>(Arrays.asList(t)));
            }

            return l;
        }

        return _cartesianProductList(0, lists);
    }

    private static <T> List<List<T>> _cartesianProductList(int index, List<List<T>> lists) {
        List<List<T>> ret = new LinkedList<>();
        if (index == lists.size()) {
            ret.add(new LinkedList<>());
        } else {
            for (T obj : lists.get(index)) {
                for (List<T> set : _cartesianProductList(index + 1, lists)) {
                    set.add(obj);
                    ret.add(set);
                }
            }
        }
        return ret;
    }

    public static String getSymbols() {
        String str = "";
        for (int i = 33; i <= 126; i++) {
            char c = (char) i;
            if (!(Character.isLetter(i) || Character.isDigit(c))) {
                str += c;
            }
        }
        return str;
    }
    
}
