package de.dfki.sds.stringanalyzer.string;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A string entity used for annotation.
 * The parent is the annotated text.
 * Its children are the possible named entities.
 * It needs two ints for begin and end, and the rest of the StringEntity (pointers).
 * 
 */
public class StringEntityAnnotation extends StringEntity {

    public static final String ANNOTATION = "annotation";
    public static final String GAP = "gap";
    
    //just adds begin and end
    private int begin;
    private int end;
    
    //use json metadata to store confidence and type
    //private float confidence = 0.5f;
    //type of the annotation
    //private String type = null;
    

    //should never be called from outside
    public StringEntityAnnotation() {
        
    }
    
    private void init() {
        setId(UUID.randomUUID().toString());
        //every annotation entity has this type in json meta data
        getOrCreateJsonObject(ANNOTATION);
    }
    
    public StringEntityAnnotation(StringEntity parent, int begin, int end) {
        init();
        if(!parent.hasValue()) {
            throw new IllegalArgumentException("parent needs to have a value");
        }
        parent.addChild(this);
        this.begin = begin;
        this.end = end;
    }
    
    public StringEntityAnnotation(StringEntity parent, int begin, int end, StringEntity namedEntity) {
        this(parent, begin, end);
        addChild(namedEntity);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 23 * hash + this.getBegin();
        hash = 23 * hash + this.getEnd();
        hash = 23 * hash + this.getParent().hashCode();
        //if(hasChild()) {
        //    hash = 23 * hash + this.getNamedEntity().hashCode();
        //}
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final StringEntityAnnotation other = (StringEntityAnnotation)obj;
        if (this.getBegin() != other.getBegin()) {
            return false;
        }
        if (this.getEnd() != other.getEnd()) {
            return false;
        }
        if (!Objects.equals(this.getParent(), other.getParent())) {
            return false;
        }
        //if (this.children != null && other.children != null && this.children.size() > 0 && other.children.size() > 0 && !Objects.equals(this.getNamedEntity(), other.getNamedEntity())) {
        //    return false;
        //}
        return true;
    }
    
    @Override
    public String getValue() {
        if(values == null || values.isEmpty()) {
            //never materialize
            return this.getParents().get(0).getValue().substring(getBegin(), getEnd()+1);
        }
        return super.getValue();
    }

    @Override
    public boolean hasValue() {
        //have always a value
        return true;
    }

    @Override
    public List<String> getValues() {
        if(values == null) {
            //an annotation covers only one value
            return Arrays.asList(getValue());
        }
        return super.getValues();
    }
    
    public StringEntity getTextEntity() {
        return getParents().get(0);
    }
    
    public List<StringEntity> getTextEntities() {
        return getParents();
    }
    
    public StringEntity getNamedEntity() {
        return getChildren().get(0);
    }
    
    public List<StringEntity> getNamedEntities() {
        return getChildren();
    }
    
    public int getBegin() {
        return begin;
    }

    public int getEnd() {
        return end;
    }
    
    @Override
    public boolean isAnnotation() {
        return true;
    }

    @Override
    public String toString() {
        return "StringEntityAnnotation{" + 
                "pos=(" + getBegin() + "," + getEnd() + ")" + 
                ", value=\""+ getValue() +"\"" + 
                ", #textEntity=" + getParents().size() + 
                (hasChildren() ? ", #namedEntity=" + getNamedEntities().size() : "") + 
                (hasJsonMetadata()? ", json=" + getJsonMetadata().toString() : "") + 
                '}';
    }
    
    public String toStringAnnotated() {
        StringBuilder sb = new StringBuilder();
            sb.append(getParent().getValue());
            sb.append("\n");
            
            sb.append(StringUtils.repeat(' ', getBegin()));
                
            if(getLength() == 0) {
                sb.append("_");
            } else {
                sb.append("[");
                sb.append(StringUtils.repeat('-', getEnd() - 1 - getBegin()));
                sb.append("]");
            }
            sb.append(getChildCount());
            sb.append(" (");
            sb.append(getBegin());
            sb.append(",");
            sb.append(getEnd());
            sb.append(")");
            return sb.toString();
    }
    
    public String toHtmlTitle() {
        return getChildCount() + " (" + getBegin() + ", " + getEnd() + ")";
    }
    
    public String toHtmlAnnotated() {
        String title = toHtmlTitle();
        StringBuilder sb = new StringBuilder();
        sb.append(getParent().getValue());
        sb.insert(getEnd()+1, "</mark>");
        sb.insert(getBegin(), "<mark title=\""+title+"\">");
        return sb.toString();
    }
    
    public String toHtmlTableData() {
        String[] split = split();
        return "<td style=\"padding: 0px; text-align:right;\"><code>" + StringEscapeUtils.escapeHtml4(split[0]) + "</code></td>" +
               "<td style=\"padding: 0px;\">" + "<mark title=\""+ toHtmlTitle() +"\"><code>" + StringEscapeUtils.escapeHtml4(split[1]) + "</code></mark></td>" +
               "<td style=\"padding: 0px;\"><code>" + StringEscapeUtils.escapeHtml4(split[2]) + "</code></td>" + 
               "<td style=\"padding-left: 5px;\">"+ getSpanScore() +"</td>" +
               "<td style=\"padding-left: 5px;\"><code>"+ getParent().getJsonMetadata().toString() +"</code></td>" +
               "<td style=\"padding-left: 5px;\"><code>"+ StringEscapeUtils.escapeHtml4(getParent().getId()) +"</code></td>";
    }

    public String toHtmlSwingLabel() {
        String[] split = split();
        return "<html>" + split[0] + 
               "<span style=\"background-color: #d9edf7\">" + split[1] + "</span>" + 
                split[2] + "</html>";
    }
    
    /**
     * Splits annotated string in left, annotated, right.
     * @return 
     */
    public String[] split() {
        if(getParentCount() == 0) {
            return new String[] { "", "", "" }; //TODO maybe return better
        }
        
        String v = getParent().getValue();
        return new String[] {
            v.substring(0, getBegin()),
            v.substring(getBegin(), getEnd()+1),
            v.substring(getEnd()+1, v.length()),
        };
    }
    
    public float getSpanScore() {
        if(getParentCount() == 0)
            return Float.NaN;
        
        return (getLeftSpanScore() + getRightSpanScore()) / 2.0f;
    }
    
    public float getLeftSpanScore() {
        //at the beginning: nice
        if(getBegin() == 0) 
            return 1.0f;
        
        String[] split = split();
        String left = split[0];
        String middle = split[1];
        
        String leftLast = left.substring(left.length()-1);
        String middleFirst = middle.substring(0, 1);
        
        return getDisconnectness(middleFirst.charAt(0), leftLast.charAt(0), true);
    }
    
    public float getRightSpanScore() {
        //at the end: nice
        if(getEnd()+1 == getParent().length()) 
            return 1.0f;
        
        String[] split = split();
        String middle = split[1];
        String right = split[2];
        
        String middleLast = middle.substring(middle.length()-1);
        String rightFirst = right.substring(0,1);
        
        return getDisconnectness(middleLast.charAt(0), rightFirst.charAt(0), false);
    }
    
    public int getSpanLength() {
        return (getEnd() - getBegin())+1;
    }
    
    public int getLength() {
        return (getEnd() - getBegin());
    }
    
    public float getCoverage() {
        return getSpanLength() / (float) getParent().length();
    }
    
    private float getDisconnectness(char internal, char external, boolean isFromLeft) {
        if(Character.isWhitespace(external) || external == '\n' || external == '\r' || external == '\t') {
            return 1.0f;
        } 
        if(external == '_' || external == '.' || external == ',') {
            return 0.9f;
        }
        if(external == '-') {
            return 0.75f;
        }
        if(Character.isDigit(external)) {
            return 0.8f;
        }
        if(StringUtils.containsAny(String.valueOf(external), "/*!@#$%^&*()\"{}_[]|\\?/<>")) {
            return 0.8f;
        }
        
        
        //ThisIsCamelCase
        //   externalInternal
        if(isFromLeft && Character.isLowerCase(external) && Character.isUpperCase(internal)) {
            return 0.8f;
        }
        if(!isFromLeft && Character.isLowerCase(internal) && Character.isUpperCase(external)) {
            return 0.8f;
        }
        
        //no go is for example 'Leipzig'er oder Bär'wald'e
        return 0.0f;
    }
    
    //TODO maybe remove special type method
    public String getType() {
        return getJsonMetadata().getString("type");
    }
    public StringEntityAnnotation setType(String type) {
        if(!hasJsonMetadata())
            setJsonMetadata(new JSONObject());
        getJsonMetadata().put("type", type);
        return this;
    }

    @Override
    public JSONObject toJson(boolean withChildren, boolean withParents) throws JSONException {
        JSONObject json = super.toJson(withChildren, withParents);
        json.put("begin", getBegin());
        json.put("end", getEnd());
        return json;
    }
    
    /**
     * Flat maps from text entities all annotations.
     * @param textEntities
     * @return 
     */
    public static List<StringEntityAnnotation> getAnnotations(List<StringEntity> textEntities) {
        return textEntities.stream().flatMap(te -> te.getChildrenAnnotation().stream()).collect(toList());
    }
    
    /**
     * Flat maps from text entities all named entities via annotations (will contain duplicates).
     * @param textEntities
     * @return 
     */
    public static List<StringEntity> getNamedEntitiesList(List<StringEntity> textEntities) {
        return textEntities.stream().flatMap(te -> te.getChildrenAnnotation().stream()).flatMap(te -> te.getChildren().stream()).collect(toList());
    }
    
    /**
     * Flat maps from text entities all named entities via annotations.
     * @param textEntities
     * @return 
     */
    public static Set<StringEntity> getNamedEntitiesSet(List<StringEntity> textEntities) {
        return textEntities.stream().flatMap(te -> te.getChildrenAnnotation().stream()).flatMap(te -> te.getChildren().stream()).distinct().collect(toSet());
    }
 
    public static float getSpanScore(String text, int begin, int end) {
        StringEntity se = StringEntity.withRandomUUID(text);
        StringEntityAnnotation anno = se.getOrCreateAnnotation(begin, end);
        return anno.getSpanScore();
    }
}
