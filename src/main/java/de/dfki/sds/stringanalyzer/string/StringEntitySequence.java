package de.dfki.sds.stringanalyzer.string;

import java.util.ArrayList;
import java.util.List;
import static java.util.stream.Collectors.toList;

/**
 * Use this to work with sequences.
 * The sequence member are 'not' children of this StringEntity.
 * 
 */
public class StringEntitySequence extends StringEntity {

    private List<StringEntity> sequence;
    
    public StringEntitySequence() {
        this.sequence = new ArrayList<>();
    }
    
    public StringEntitySequence(List<StringEntity> sequence) {
        this.sequence = new ArrayList<>(sequence);
    }
    
    @Override
    public boolean isSequence() {
        return true;
    }

    public List<StringEntity> getSequenceMembers() {
        return sequence;
    }
    
    public List<String> getSequenceValues() {
        return getSequenceMembers().stream().map(se -> se.getValue()).collect(toList());
    }

    public int lengthOfSequence() {
        return sequence.size();
    }
    
    public boolean isEmptySequence() {
        return sequence.isEmpty();
    }
    
    public List<String> getValueList() {
        return sequence.stream().map(se -> se.getValue()).collect(toList());
    }
}
