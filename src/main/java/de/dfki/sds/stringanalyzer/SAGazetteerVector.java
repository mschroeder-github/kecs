
package de.dfki.sds.stringanalyzer;

import de.dfki.sds.stringanalyzer.string.StringEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ahocorasick.trie.PayloadEmit;
import org.ahocorasick.trie.PayloadTrie;
import org.ahocorasick.trie.PayloadTrie.PayloadTrieBuilder;

/**
 * 
 */
public class SAGazetteerVector extends StringAnalyzerComponent {

    private PayloadTrie<String> trie;
    private PayloadTrieBuilder<String> builder;
    
    private Map<StringEntity, double[]> entityVector;
    
    private List<String> classLabels;
    private List<String> additionalClassLabels;
    
    private boolean lowercase;
    private boolean stringLengthWeight;
    
    public SAGazetteerVector() {
        clear();
        additionalClassLabels = new ArrayList<>(Arrays.asList(
                "_number",
                "_letter",
                "_lcletter",
                "_ucletter",
                "_space",
                "_braket",
                "_dot",
                "_separator",
                "_quote",
                "_slash",
                "_other"
        ));
        classLabels = new ArrayList<>();
        classLabels.addAll(additionalClassLabels);
        classLabels.remove("_lcletter");
        classLabels.remove("_ucletter");
        lowercase = true;
        stringLengthWeight = true;
        entityVector = new HashMap<>();
    }
    
    public final void clear() {
        builder = PayloadTrie.<String>builder();
        trie = null;
    }
    
    public void register(List<String> strings, String classLabel) {
        for(String str : strings) {
            if(lowercase) {
                str = str.toLowerCase();
            }
            //if already registered, first addKeyword counts
            builder.addKeyword(str, classLabel);
        }
        if(!classLabels.contains(classLabel))
            classLabels.add(classLabel);
    }
    
    public void build() {
        build(true);
    }
    
    public void build(boolean ignoreOverlaps) {
        if(ignoreOverlaps) {
            builder.ignoreOverlaps();
        }
        trie = builder.build();
    }
    
    @Override
    public void add(StringEntity entity) {
        if(trie == null) {
            throw new RuntimeException("build() first");
        }
        
        if(entityVector.containsKey(entity)) {
            return;
        }
        
        String val = entity.getValue();
        if(lowercase) {
            val = val.toLowerCase();
        }
        
        boolean[] covered = new boolean[val.length()];
        double[] vector = new double[classLabels.size()];
        //needs to be lowercase because the *.csv.gz is all lowercase
        for(PayloadEmit<String> emit : trie.parseText(val.toLowerCase())) {
            
            int index = classLabels.indexOf(emit.getPayload());
            
            if(stringLengthWeight) {
                vector[index] += emit.getKeyword().length() / (double) val.length();
            } else {
                vector[index] += 1.0;
            }
            
            for(int i = emit.getStart(); i <= emit.getEnd(); i++) {
                covered[i] = true;
            }
        }
        
        
        for(String lbl : additionalClassLabels) {
            int index = classLabels.indexOf(lbl);
            if(index < 0)
                continue;

            int count = 0;
            for(int i = 0; i < val.length(); i++) {
                if(covered[i])
                    continue;

                char c = val.charAt(i);

                int countBefore = count;
                
                if(lbl.equals("_number")) {
                    count += Character.isDigit(c) ? 1 : 0;
                } else if(lbl.equals("_space")) {
                    count += Character.isSpaceChar(c) ? 1 : 0;
                } else if(lbl.equals("_braket")) {
                    count += (c == '(' || c == ')' ||
                          c == '{' || c == '}' ||
                          c == '<' || c == '>' ||
                          c == '[' || c == ']') ? 1 : 0;
                } else if(lbl.equals("_dot")) {
                    count += (c == '(' || c == ')' ||
                          c == '.' || c == ',' ||
                          c == ';' || c == ':') ? 1 : 0;
                } else if(lbl.equals("_separator")) {
                    count += (c == '-' || c == '_' || c == '~') ? 1 : 0;
                } else if(lbl.equals("_quote")) {
                    count += (c == '\'' || c == '"') ? 1 : 0;
                } else if(lbl.equals("_slash")) {
                    count += (c == '\\' || c == '|' || c == '/') ? 1 : 0;
                } else if(lbl.equals("_letter")) {
                    count += Character.isLetter(c) ? 1 : 0;
                } else if(lbl.equals("_lcletter")) {
                    count += Character.isLowerCase(c) ? 1 : 0;
                } else if(lbl.equals("_ucletter")) {
                    count += Character.isUpperCase(c) ? 1 : 0;
                } else if(lbl.equals("_other")) {
                    count += 1;
                }
                
                if(count != countBefore) {
                    covered[i] = true;
                }
            }

            if(stringLengthWeight) {
                vector[index] = count / (double) val.length();
            } else {
                vector[index] = count;
            }
        }
        
        entityVector.put(entity, vector);
    }

    public Map<StringEntity, double[]> getEntityVector() {
        return entityVector;
    }

    public List<String> getClassLabels() {
        return classLabels;
    }

    public boolean isLowercase() {
        return lowercase;
    }

    public void setLowercase(boolean lowercase) {
        this.lowercase = lowercase;
        
        int i = classLabels.size() - 1;
        
        if(!lowercase) {
            classLabels.add(i, "_lcletter");
            classLabels.add(i, "_ucletter");
            classLabels.remove("_letter");
        } else {
            classLabels.remove("_lcletter");
            classLabels.remove("_ucletter");
            classLabels.add(i, "_letter");
        }
    }

    public boolean isStringLengthWeight() {
        return stringLengthWeight;
    }

    public void setStringLengthWeight(boolean stringLengthWeight) {
        this.stringLengthWeight = stringLengthWeight;
    }
    
}
