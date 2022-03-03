package de.dfki.sds.stringanalyzer.string;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;
import org.json.JSONObject;

/**
 * A string with additional meta data (e.g. ID).
 * Has 7 pointers (possible null).
 * 
 */
public class StringEntity implements Serializable {

    //to identify the string
    protected String id;
    
    //(optional) how id and value(s) are related
    //useful when working with RDF (so metadata)
    protected String propertyId;
    
    //the actual string (usually only one, but multi value is allowed)
    protected List<String> values;
    
    //(optional) meta data of the string in form of RDF
    //the string's resource is identified with it's id
    protected transient Model rdfmetadata;
    
    //(optional) meta data of the string in form of JSON
    protected JSONObject jsonmetadata;
    
    //tree information
    //used when an entity is coming from anothor one (e.g. tokenizing)
    //or used when being an annotation
    //we have a list of parents because we want to know all parents of a named entity
    protected List<StringEntity> parents;
    protected List<StringEntity> children;
    
    //when we use asSwingTreeNodes the created treeNode is stored temporarily
    protected transient DefaultMutableTreeNode treeNode;
    
    //<editor-fold desc="constructors and creators">
    
    //for jabsorb
    public StringEntity() {
        
    }
    
    public StringEntity(String value) {
        setId(null);
        //we do not want a null value in the values list
        //hasValue would not work
        //that means 'null' value => hasValue is false
        if(value != null) {
            initValues();
            getValues().add(value);
        }
    }

    public StringEntity(String id, String value) {
        this(value);
        setId(id);
    }

    public StringEntity(String id, String value, Model rdfmetadata) {
        this(id, value);
        this.rdfmetadata = rdfmetadata;
    }
    
    public StringEntity(String id, String label, String comment) {
        this(id, label);
        this.propertyId = RDFS.label.getURI();
        this.rdfmetadata = ModelFactory.createDefaultModel();
        Resource thisRes = asResource();
        rdfmetadata.add(thisRes, RDFS.label, label);
        rdfmetadata.add(thisRes, RDFS.comment, comment);
    }
    
    @Deprecated
    public StringEntity(StringEntity stringEntity) {
        setId(stringEntity.getId());
        setValues(stringEntity.getValues());
        //TODO parent children
    }
    
    public static StringEntity withRandomUUID(String value) {
        return new StringEntity(UUID.randomUUID().toString(), value);
    }
    
    public static StringEntity withRandomString(int len, String value) {
        return new StringEntity(RandomStringUtils.randomAlphabetic(len), value);
    }
    
    public static StringEntity withRandomString(String value) {
        return withRandomString(6, value);
    }
    
    public static List<StringEntity> annoToSimple(List<StringEntityAnnotation> annos) {
        return annos.stream().map(anno -> (StringEntity)anno).collect(toList());
    }
    
    public static StringEntity fromCSVRecord(CSVRecord record, String valueColumnName) {
        StringEntity se = new StringEntity("" + record.getRecordNumber(), record.get(valueColumnName));
        JSONObject meta = se.getOrCreateJsonObject("csv");
        for(String name : record.getParser().getHeaderNames()) {
            if(valueColumnName.equals(name))
                continue;
            
            String val = record.get(name);
            Object obj = val;
            try {
                obj = Double.parseDouble(val);
            } catch(NumberFormatException e1) {
                if(val.equals("true") || val.equals("false")) {
                    obj = Boolean.parseBoolean(val);
                } else {
                    obj = val;
                }
            }
            
            meta.put(name, obj);
        }
        return se;
    }
    
    //</editor-fold>
    
    //<editor-fold desc="equality">
    
    @Override
    public int hashCode() {
        if (getId() == null) {
            int hash = 3;
            hash = 17 * hash + Objects.hashCode(this.getValues());
            return hash;
        } else {
            int hash = 5;
            hash = 71 * hash + Objects.hashCode(this.getId());
            return hash;
        }
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
        final StringEntity other = (StringEntity) obj;

        if (getId() == null) {
            if (!Objects.equals(this.getValues(), other.getValues())) {
                return false;
            }
        } else {
            if (!Objects.equals(this.getId(), other.getId())) {
                return false;
            }
        }
        return true;
    }

    //</editor-fold>
    
    //<editor-fold desc="has-er & is-er & getter & setter">
    
    public boolean hasId() {
        return getId() != null;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }

    
    public boolean hasPropertyId() {
        return propertyId != null;
    }
    
    public String getPropertyId() {
        return propertyId;
    }
    
    public void setPropertyId(String propertyId) {
        this.propertyId = propertyId;
    }
    
    
    
    private void initValues() {
        if(getValues() == null)
            initValueArray();
    }
    
    private void initValueArray() {
        setValues(new ArrayList<>());
    }
    
    public boolean hasValue() {
        return getValues() != null && !getValues().isEmpty();
    }
    
    public boolean isMultiValued() {
        return hasValue() && getValues().size() > 1;
    }
    
    public String getValue() {
        return getValues().get(0);
    }

    public boolean isEmpty() {
        return getValue().isEmpty();
    }
    
    public boolean isBlank() {
        return getValue().trim().isEmpty();
    }
    
    public void setValue(String value) {
        setValues(new ArrayList<>(Arrays.asList(value)));
    }

    public List<String> getValues() {
        return values;
    }
    
    public void addValue(String value) {
        getValues().add(value);
    }

    public void setValues(List<String> values) {
        if(values == null)
            this.values = null;
        else 
            this.values = new ArrayList<>(values);
    }
    
    public void removeValuesIf(Predicate<String> filter) {
        values.removeIf(filter);
    }
    
    public void trimValues() {
        for(int i = 0; i < values.size(); i++) {
            values.set(i, values.get(i).trim());
        }
    }
    
    public void replaceValues(String target, String replacement) {
        for(int i = 0; i < values.size(); i++) {
            values.set(i, values.get(i).replace(target, replacement));
        }
    }
    
    public void lowercaseValues() {
        for(int i = 0; i < values.size(); i++) {
            values.set(i, values.get(i).toLowerCase());
        }
    }
    
    public void uppercaseValues() {
        for(int i = 0; i < values.size(); i++) {
            values.set(i, values.get(i).toUpperCase());
        }
    }
    
    public boolean hasRdfMetadata() {
        return rdfmetadata != null;
    }
    
    public Model getRdfMetadata() {
        return rdfmetadata;
    }

    public void setRdfMetadata(Model metadata) {
        this.rdfmetadata = metadata;
    }
    
    
    public boolean hasJsonMetadata() {
        return getJsonMetadata() != null;
    }

    public JSONObject getJsonMetadata() {
        return jsonmetadata;
    }
    
    private void initJsonMetadata() {
        if(getJsonMetadata() == null) {
            jsonmetadata = new JSONObject();
        }
    }

    public void setJsonMetadata(JSONObject jsonmetadata) {
        this.jsonmetadata = jsonmetadata;
    }
    
    public JSONObject getOrCreateJsonObject(String key) {
        
        JSONObject meta;
        if(!hasJsonMetadata()) {
            meta = new JSONObject();
            setJsonMetadata(meta);
        } else {
            meta = getJsonMetadata();
        }
        
        JSONObject obj;
        if(!meta.has(key)) {
            obj = new JSONObject();
            meta.put(key, obj);
        } else {
            obj = meta.getJSONObject(key);
        }
        
        return obj;
    }
    
    public JSONObject getJsonObject(String key) {
        return getJsonMetadata().getJSONObject(key);
    }
    
    public boolean hasJsonKey(String key) {
        return hasJsonMetadata() && getJsonMetadata().has(key);
    }

    public char getChar() {
        return getValue().charAt(0);
    }
    
    
    public int length() {
        return getValue().length();
    }
    
    //</editor-fold>
    
    //<editor-fold desc="to-er or is-er for extended classes">

    public boolean isSequence() {
        return false;
    }
    
    public StringEntitySequence toSequence() {
        if(!isSequence())
            throw new RuntimeException("not an sequence StringEntity");
        
        return (StringEntitySequence) this;
    }
    
    //</editor-fold>
    
    //<editor-fold desc="tree">
    
    private void initParents() {
        if(parents == null){
            parents = new ArrayList<>();
        }
    }
    
    private void initChildren() {
        if(children == null){
            children = new ArrayList<>();
        }
    }
    
    /**
     * Checks if child with id exists, if not, adds given one.
     * @param child
     * @param addParentToChild
     * @param index
     * @return 
     */
    public StringEntity getOrAddChild(StringEntity child, boolean addParentToChild, int index) {
        Map<String, StringEntity> map = getChildrenMap();
        
        if(map.containsKey(child.getId())) {
            return map.get(child.getId());
        }
        
        insertChildren(index, Arrays.asList(child), addParentToChild);
        
        return child;
    }
    
    public StringEntity getOrAddChild(StringEntity child) {
        return getOrAddChild(child, true, getChildCount());
    }
    
    public void getOrAddChildren(Collection<StringEntity> children) {
        children.forEach(child -> getOrAddChild(child, true, getChildCount()));
    }
    
    /**
     * Tries to get the child by id, if not found, useses creator and sets given id.
     * @param id the child's id
     * @param creator returns child if it has to be created
     * @param addParentToChild
     * @param index
     * @return the got or created child
     */
    public StringEntity getOrCreateChild(String id, Supplier<StringEntity> creator, boolean addParentToChild, int index) {
        Map<String, StringEntity> map = getChildrenMap();
        
        if(map.containsKey(id)) {
            return map.get(id);
        }
        
        StringEntity se = creator.get();
        se.setId(id);
        insertChildren(index, Arrays.asList(se), addParentToChild);
        
        return se;
    }
    
    public StringEntity getOrCreateChild(String id, Supplier<StringEntity> creator) {
        return getOrCreateChild(id, creator, true, getChildCount());
    }
    
    /**
     * Creates a child by value and uses value2id to check if it already exists.
     * Json creator can be used to fill the fresh string entity with meta data.
     * @param value
     * @param value2id
     * @param jsonCreator
     * @return 
     */
    public StringEntity getOrCreateChild(String value, Function<String, String> value2id, Consumer<JSONObject> jsonCreator) {
        String id = value2id.apply(value);
        return getOrCreateChild(id, () -> {
            StringEntity se = new StringEntity(value);
            se.initJsonMetadata();
            jsonCreator.accept(se.getJsonMetadata());
            return se;
        });
    }
    
    public StringEntity addChild(StringEntity stringEntity) {
        return addChild(stringEntity, true);
    }
    
    public StringEntity addChild(StringEntity stringEntity, boolean addParentToChild) {
        return addChildren(Arrays.asList(stringEntity), addParentToChild);
    }
    
    public StringEntity addChildren(Collection<StringEntity> stringEntity) {
        return addChildren(stringEntity, true);
    }
    
    public StringEntity addChildren(Collection<StringEntity> stringEntity, boolean addParentToChild) {
        return insertChildren(getChildren() == null ? 0 : getChildren().size(), stringEntity, addParentToChild);
    }
    
    public StringEntity insertChild(int index, StringEntity stringEntity) {
        return insertChildren(index, Arrays.asList(stringEntity), true);
    }
    
    public StringEntity insertChildren(int index, Collection<StringEntity> givenChildren, boolean addParentToChild) {
        initChildren();
        this.children.addAll(index, givenChildren);
        if(addParentToChild)
            givenChildren.forEach(se -> { se.addParent(this, false); });
        return this;
    }
    
    public StringEntity addParent(StringEntity parent) {
        return addParent(parent, true);
    }
    
    public StringEntity addParent(StringEntity parent, boolean addChildToParent) {
        return addParents(Arrays.asList(parent), addChildToParent);
    }
    
    public StringEntity addParents(Collection<StringEntity> parents) {
        return addParents(parents, true);
    }
    
    public StringEntity addParents(Collection<StringEntity> givenParents, boolean addChildToParent) {
        initParents();
        this.parents.addAll(givenParents);
        if(addChildToParent)
            givenParents.forEach(se -> { se.addChild(this, false); });
        return this;
    }
    
    /**
     * Removes this string entity from its parents and removes it from parents children.
     * @return this
     */
    public StringEntity removeFromParents() {
        for(StringEntity parent : getParents()) {
            parent.getChildren().remove(this);
        }
        getParents().clear();
        return this;
    }
    
    public StringEntity removeChild(StringEntity stringEntity) {
        return removeChild(stringEntity, true);
    }
    
    public StringEntity removeChild(StringEntity stringEntity, boolean removeParentFromChild) {
        if(hasChildren())
            this.children.remove(stringEntity);
        
        if(removeParentFromChild)
            stringEntity.removeParent(this, false);
        
        return this;
    }
    
    public StringEntity removeParent(StringEntity stringEntity) {
        return removeParent(stringEntity, true);
    }
    
    public StringEntity removeParent(StringEntity stringEntity, boolean removeChildFromParent) {
        if(hasParents())
            this.getParents().remove(stringEntity);
        
        if(removeChildFromParent)
            stringEntity.removeChild(this, false);
        
        return this;
    }
    
    public StringEntity removeChildrenIf(Predicate<StringEntity> childPredicate) {
        if(children == null)
            return this;
        
        children.removeIf(childPredicate);
        return this;
    }
    
    public StringEntity removeParentsIf(Predicate<StringEntity> parentPredicate) {
        if(parents == null)
            return this;
        
        parents.removeIf(parentPredicate);
        return this;
    }
    
    public boolean hasParent() {
        return getParents() != null && !getParents().isEmpty();
    }
    
    public boolean hasChild() {
        return getChildren() != null && !getChildren().isEmpty();
    }
    
    public boolean hasParents() {
        return parents != null;
    }
    
    public boolean hasChildren() {
        return children != null;
    }
    
    //for compatibility reason (get first one)
    public StringEntity getParent() {
        return getParents().get(0);
    }

    //for compatibility reason set exact one parent
    public StringEntity setParent(StringEntity origin) {
        this.parents = null;
        initParents();
        getParents().add(origin);
        origin.addChild(this);
        return this;
    }
    
    public List<StringEntity> getChildren() {
        if(!hasChildren())
            return new ArrayList<>();
        return new ArrayList<>(children);
    }

    /**
     * A map from id to entity.
     * @return 
     */
    public Map<String, StringEntity> getChildrenMap() {
        Map<String, StringEntity> m = new HashMap<>();
        if(!hasChildren()) {
            return m;
        }
        
        for(StringEntity child : getChildren()) {
            m.put(child.getId(), child);
        }
        
        return m;
    }
    
    public List<StringEntity> getParents() {
        if(!hasParents())
            return new ArrayList<>();
        return new ArrayList<>(parents);
    }

    /**
     * Returns the path of parents (from root to this element) but without this
     * element at the end.
     * Throws RuntimeException if parent is ambigous.
     * @return 
     */
    public List<StringEntity> getParentPath() {
        return getParentPath(true);
    }
    
    /**
     * Returns the path of parents (from root to this element) but without this
     * element at the end.
     * @param ambigousCheck if true throws RuntimeException if parent is ambigous.
     * @return 
     */
    public List<StringEntity> getParentPath(boolean ambigousCheck) {
        List<StringEntity> path = new ArrayList<>();
        
        StringEntity cur = this;
        while(cur.hasParent()) {
            if(ambigousCheck && cur.getParentCount() > 1) {
                throw new RuntimeException("parent path is not possible because this string entity has more than one parent: " + cur);
            }
            
            StringEntity parent = cur.getParent();
            path.add(parent);
            cur = parent;
        }
        Collections.reverse(path);
        return path;
    }
    
    public Set<StringEntity> getNeighbors() {
        Set<StringEntity> neighbors = new HashSet<>();
        if(hasParents()) {
            neighbors.addAll(getParents());
        }
        if(hasChildren()) {
            neighbors.addAll(getChildren());
        }
        return neighbors;
    }
    
    /**
     * Returns children which match the predicate on their json meta data.
     * Children without meta data are not returned.
     * @param metaFilter
     * @return 
     */
    public List<StringEntity> getChildren(Predicate<JSONObject> metaFilter) {
        return getChildren().stream().filter(p -> p.hasJsonMetadata() ? metaFilter.test(p.getJsonMetadata()) : false).collect(toList());
    }
    
    /**
     * Returns parents which match the predicate on their json meta data.
     * Parents without meta data are not returned.
     * @param metaFilter
     * @return 
     */
    public List<StringEntity> getParents(Predicate<JSONObject> metaFilter) {
        return getParents().stream().filter(p -> p.hasJsonMetadata() ? metaFilter.test(p.getJsonMetadata()) : false).collect(toList());
    }
    
    
    
    public int getChildCount() {
        if(hasChildren())
            return getChildren().size();
        return 0;
    }
    
    public int getParentCount() {
        if(hasParents())
            return getParents().size();
        return 0;
    }
    
    /**
     * this and all children (plus children of children).
     * @return 
     */
    public List<StringEntity> descendants() {
        List<StringEntity> l = new ArrayList<>();
        l.add(this);
        if(hasChildren()) {
            for(StringEntity child : getChildren()) {
                l.addAll(child.descendants());
            }
        }
        return l;
    }
    
    /**
     * All children (plus children of children), but not 'this'.
     * @return 
     */
    public List<StringEntity> descendantsWithoutThis() {
        List<StringEntity> l = new ArrayList<>();
        if(hasChildren()) {
            for(StringEntity child : getChildren()) {
                l.addAll(child.descendants());
            }
        }
        return l;
    }
    
    /**
     * this and all children (plus children of children) but filters all.
     * @param metaFilter a filter while walking.
     * @return 
     */
    public List<StringEntity> descendants(Predicate<JSONObject> metaFilter) {
        List<StringEntity> l = new ArrayList<>();
        l.add(this);
        if(hasChildren()) {
            for(StringEntity child : getChildren(metaFilter)) {
                l.addAll(child.descendants(metaFilter));
            }
        }
        return l;
    }
    
    /**
     * this and all children (plus children of children). Walks all descendants but
     * only collects those which are filtered.
     * @param metaFilter a filter while collecting.
     * @return 
     */
    public List<StringEntity> descendantsCollect(Predicate<JSONObject> metaFilter) {
        List<StringEntity> l = new ArrayList<>();
        
        if(metaFilter != null) {
            if(this.hasJsonMetadata() && metaFilter.test(this.getJsonMetadata()))
                l.add(this);
        } else {
            l.add(this);
        }
        
        if(hasChildren()) {
            for(StringEntity child : getChildren()) {
                l.addAll(child.descendantsCollect(metaFilter));
            }
        }
        return l;
    }
    
    /**
     * this and all parents (plus parents of parents). Walks all ancestors but
     * only collects those which are filtered.
     * @param metaFilter a filter while collecting.
     * @return 
     */
    public List<StringEntity> ancestorsCollect(Predicate<JSONObject> metaFilter) {
        List<StringEntity> l = new ArrayList<>();
        
        if(metaFilter != null) {
            if(this.hasJsonMetadata() && metaFilter.test(this.getJsonMetadata()))
                l.add(this);
        } else {
            l.add(this);
        }
        
        if(hasParents()) {
            for(StringEntity parent : getParents()) {
                l.addAll(parent.ancestorsCollect(metaFilter));
            }
        }
        return l;
    }
    
    /**
     * Uses descendantsCollect to collect all descendants by metaFilter and create
     * a id to string entity map.
     * @param metaFilter
     * @return 
     */
    public Map<String, StringEntity> descendantsIdMap(Predicate<JSONObject> metaFilter) {
        List<StringEntity> descendants = descendantsCollect(metaFilter);
        Map<String, StringEntity> m = new HashMap<>();
        for(StringEntity descendant : descendants) {
            m.put(descendant.getId(), descendant);
        }
        return m;
    }
    
    public Set<StringEntity> breadthFirstSearch() {
        return breadthFirstSearch(null);
    }
    
    /**
     * Performs breadth first search from this StringEntity.
     * @param consumer you can apply a consumer to get only specific ones
     * @return all visited string entities in a set
     */
    public Set<StringEntity> breadthFirstSearch(Consumer<StringEntity> consumer) {
        Queue<StringEntity> q = new LinkedList<>();
        q.add(this);
        
        Set<StringEntity> visited = new HashSet<>();
        
        while(!q.isEmpty()) {
            StringEntity cur = q.poll();
            
            if(visited.contains(cur)) {
                continue;
            }
            
            if(consumer != null) {
                consumer.accept(cur);
            }
            
            visited.add(cur);
            
            for(StringEntity neighbor : cur.getNeighbors()) {
                if(!visited.contains(neighbor)) {
                    q.add(neighbor);
                }
            }
        }
        
        return visited;
    }
    
    public StringEntity breadthFirstSearchFor(Predicate<StringEntity> predicate) {
        Queue<StringEntity> q = new LinkedList<>();
        q.add(this);
        
        Set<StringEntity> visited = new HashSet<>();
        
        while(!q.isEmpty()) {
            StringEntity cur = q.poll();
            
            if(visited.contains(cur)) {
                continue;
            }
            
            if(predicate.test(cur)) {
                return cur;
            }
            
            visited.add(cur);
            
            for(StringEntity neighbor : cur.getNeighbors()) {
                if(!visited.contains(neighbor)) {
                    q.add(neighbor);
                }
            }
        }
        
        return null;
    }
    
    
    /**
     * Converts this string entity (and recursivly its children) to swing's tree node.
     * @param metaFilter filter json meta data
     * @param childSorter sorts children
     * @param maxDepth maximal depth (inclusive)
     * @param curDepth current depth
     * @param oneChildLayer if true it also adds children after finishing (metaFilter returns false) or maxDepth is reached. the children are not filtered.
     * @return 
     */
    public TreeNode asSwingTreeNodes(Predicate<JSONObject> metaFilter, Comparator<StringEntity> childSorter, Integer maxDepth, int curDepth, boolean oneChildLayer) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(this);
        this.treeNode = treeNode;
        
        if(maxDepth != null && curDepth >= maxDepth) {
            
            //one extra layer
            if(oneChildLayer && hasChildren()) {
                for(StringEntity child : getChildren()) {
                    treeNode.add((MutableTreeNode) child.asSwingTreeNode());
                }
            }
            
            return treeNode;
        }
        
        if(hasChildren()) {
            Stream<StringEntity> s = getChildren().stream();
            if(childSorter != null) {
                s = s.sorted(childSorter);
            }
            for(StringEntity child : s.collect(toList())) {
                if(metaFilter != null && child.hasJsonMetadata() && !metaFilter.test(child.getJsonMetadata())) {
                    
                    //one extra layer
                    if(oneChildLayer) {
                        treeNode.add((MutableTreeNode) child.asSwingTreeNode());
                    }
                    
                    continue;
                }
                
                treeNode.add((MutableTreeNode) child.asSwingTreeNodes(metaFilter, childSorter, maxDepth, curDepth+1, oneChildLayer));
            }
        }
        return treeNode;
    }
    
    /**
     * Converts this string entity (and recursivly its children) to swing's tree node.
     * @param metaFilter filter json meta data
     * @param childSorter sorts children
     * @return 
     */
    public TreeNode asSwingTreeNodes(Predicate<JSONObject> metaFilter, Comparator<StringEntity> childSorter) {
        return asSwingTreeNodes(metaFilter, childSorter, null, 0, false);
    }
    
    /**
     * Converts this string entity to swing's tree node (without children).
     * @return 
     */
    public TreeNode asSwingTreeNode() {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(this);
        this.treeNode = treeNode;
        return treeNode;
    }
    
    /**
     * Converts this string entity (and recursivly its children) to swing's tree model.
     * @param metaFilter
     * @return 
     */
    public TreeModel asSwingTreeModel(Predicate<JSONObject> metaFilter) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) this.asSwingTreeNode();
        DefaultTreeModel dtm = new DefaultTreeModel(root);
        
        Queue<DefaultMutableTreeNode> q = new LinkedList<>();
        q.add(root);
        
        while(!q.isEmpty()) {
            DefaultMutableTreeNode tn = q.poll();
            StringEntity se = (StringEntity) tn.getUserObject();
            
            if(se.hasChildren()) {
                for(StringEntity child : se.getChildren()) {
                    
                    if(metaFilter != null && child.hasJsonMetadata() && !metaFilter.test(child.getJsonMetadata()))
                        continue;
                    
                    DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) child.asSwingTreeNode();
                    
                    tn.add(childNode);
                    
                    q.add(childNode);
                }
            }
        }
        
        return dtm;
    }
    
    public boolean hasTreeNode() {
        return treeNode != null;
    }

    public DefaultMutableTreeNode getTreeNode() {
        return treeNode;
    }
    
    //</editor-fold>
    
    //<editor-fold desc="drag & drop">
    
    /**
     * Moves the sources to the target.
     * @param source
     * @param sourceModel
     * @param target 
     * @param targetModel 
     * @param move 
     * @param insertFront 
     * @return the moved (source) string entities.
     */
    public static List<StringEntity> dragAndDrop(TreePath[] source, TreeModel sourceModel, TreePath target, TreeModel targetModel, boolean move, boolean insertFront) {
        if(target.getPathCount() == 0)
            return Arrays.asList();
        
        DefaultTreeModel sourceDtm = (DefaultTreeModel) sourceModel;
        DefaultTreeModel targetDtm = (DefaultTreeModel) targetModel;
        
        DefaultMutableTreeNode targetNode = (DefaultMutableTreeNode) target.getLastPathComponent();
        
        List<StringEntity> moved = new ArrayList<>();
        
        for(TreePath path : source) {
            if(path.getPathCount() <= 1)
                continue;
            
            //not possible
            if(path == target)
                continue;
            
            DefaultMutableTreeNode last       = (DefaultMutableTreeNode) path.getPathComponent(path.getPathCount()-1);
            DefaultMutableTreeNode nextToLast = (DefaultMutableTreeNode) path.getPathComponent(path.getPathCount()-2);

            if(move) {
                sourceDtm.removeNodeFromParent(last);
            }

            StringEntity lastSe       = (StringEntity) last.getUserObject(); //child
            StringEntity nextToLastSe = (StringEntity) nextToLast.getUserObject(); //parent

            if(move) {
                nextToLastSe.removeChild(lastSe);
            }

            //insert child node
            int insertIndexNode = insertFront ? 0 : targetNode.getChildCount();
            targetDtm.insertNodeInto(last, targetNode, insertIndexNode);
            
            //insert child string entity
            StringEntity targetSe = (StringEntity) targetNode.getUserObject();
            int insertIndexSe = insertFront ? 0 : targetSe.getChildCount();
            targetSe.insertChild(insertIndexSe, lastSe);
         
            moved.add(lastSe);
            
            targetDtm.nodeStructureChanged(nextToLast);
            targetDtm.nodeChanged(last);
        }
        
        return moved;
    }
    
    //</editor-fold>
    
    //<editor-fold desc="annotation">
    
    public boolean isAnnotation() {
        return false;
    }
    
    public StringEntityAnnotation toAnnotation() {
        if(!isAnnotation())
            throw new RuntimeException("not an annotation StringEntity");
        
        return (StringEntityAnnotation) this;
    }
    
    /**
     * Returns the annotations from the named entity's perspective.
     * @return 
     */
    public List<StringEntityAnnotation> getParentsAnnotation(){
        return getParents()
                .stream()
                .filter(se -> se.isAnnotation())
                .map(se -> se.toAnnotation())
                .collect(toList());
    }
    
    /**
     * Returns the annotations from the text entity's perspective.
     * @return 
     */
    public List<StringEntityAnnotation> getChildrenAnnotation(){
        return getChildren()
                .stream()
                .filter(se -> se.isAnnotation())
                .map(se -> se.toAnnotation())
                .collect(toList());
    }
    
    /**
     * Returns the annotations from the text entity's perspective.
     * @return 
     */
    public List<StringEntityAnnotation> getChildrenAnnotation(Predicate<JSONObject> metaFilter){
        return getChildren()
                .stream()
                .filter(se -> se.isAnnotation())
                .filter(p -> p.hasJsonMetadata() ? metaFilter.test(p.getJsonMetadata()) : false)
                .map(se -> se.toAnnotation())
                .collect(toList());
    }
    
    /**
     * creates a map, e.g. '2-5' to annotation.
     * @return 
     */
    public Map<String, StringEntityAnnotation> getAnnotationMap() {
        Map<String, StringEntityAnnotation> m = new HashMap<>();
        if(hasChildren()) {
            for(StringEntityAnnotation anno : getChildrenAnnotation()) {
                int b = anno.getBegin();
                int e = anno.getEnd();
                m.put(b + "-" + e, anno);
            }
        }
        return m;
    }
    
    /**
     * Removes this named entity from its annotation parents and removes it from the annotation's children.
     * If after that the annotation does not have any children, we remove the annotation, too.
     * @return this
     */
    public StringEntity removeFromAnnotations() {
        for(StringEntityAnnotation parent : getParentsAnnotation().toArray(new StringEntityAnnotation[0])) {
            parent.getChildren().remove(this);
            getParents().remove(parent);
            
            //if the StringEntityAnnotation does not have any children we can remove it from the text entity
            if(parent.getChildren().isEmpty()) {
                parent.removeFromParents();
            }
        }
        return this;
    }
    
    /**
     * Get annotation if the annotation exists already on begin-end position, or
     * generates a new one. id = random UUID.
     * @param begin begin position
     * @param end end position
     * @return retrieved or created one
     */
    public StringEntityAnnotation getOrCreateAnnotation(int begin, int end) {
        
        //check if already exists
        Map<String, StringEntityAnnotation> map = getAnnotationMap();
        String key = begin + "-" + end;
        if(map.containsKey(key)) {
            return map.get(key);
        }
        
        //create new one
        StringEntityAnnotation fresh = new StringEntityAnnotation(this, begin, end);
        return fresh;
    }
    
    public boolean hasAnnotationAt(int begin, int end) {
        return getAnnotationMap().containsKey(begin + "-" + end);
    } 
    
    //</editor-fold>
    
    //<editor-fold desc="semantic stuff">
    
    public Resource asResource() {
        if(!hasId())
            throw new RuntimeException("no id");
        
        if(hasRdfMetadata())
            return getRdfMetadata().createResource(getId());
        
        return ResourceFactory.createResource(getId());
    }
    
    public Property getProperty() {
        if(!hasPropertyId())
            throw new RuntimeException("no propery id");
        
        if(hasRdfMetadata())
            return getRdfMetadata().createProperty(propertyId);
        
        return ResourceFactory.createProperty(propertyId);
    }
    
    public Literal asLiteral() {
        if(!hasValue())
            throw new RuntimeException("no value");
        
        return ResourceFactory.createPlainLiteral(getValue());
    }
    
    public Literal asLiteral(String lang) {
        if(!hasValue())
            throw new RuntimeException("no value");
        
        return ResourceFactory.createLangLiteral(getValue(), lang);
    }
    
    public Statement asStatement() {
        Resource s = asResource();
        Property p = getProperty();
        Literal l = asLiteral();
        return ResourceFactory.createStatement(s, p, l);
    }
    
    //</editor-fold>
    
    //<editor-fold desc="to methods">
    
    private static final int ABBREV_MAX_LEN = 100;
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("StringEntity{");
        
        sb.append("hashCode=" + hashCode() + ", ");
        
        if (hasId()) {
            sb.append("id=" + getId() + ", ");
        }
        if (hasValue()) {
            sb.append("value='" + toShortValueString() + "' ("+ getValues().size() +"), ");
        }
        if(hasRdfMetadata()) {
            sb.append("#metadata_triple=" + rdfmetadata.size() + ", ");
        }
        if(hasChildren()) {
            sb.append("#children=" + getChildren().size()+ ", ");
        }
        if(hasParent()) {
            sb.append("#parent=" + getParents().size() + ", ");
        }
        if(hasJsonMetadata()) {
            sb.append("json=" + getJsonMetadata().toString()+ ", ");
        }
        
        sb.append("}");
        return sb.toString();
    }

    /**
     * Abbreviates String and shows '\n' '\r' '\t' in it.
     * @return 
     */
    public String toShortValueString() {
        return toShortValueString(ABBREV_MAX_LEN);
    }
    
    /**
     * Abbreviates String and shows '\n' '\r' '\t' in it.
     * @param maxWidth length when abbreviation should start
     * @return 
     */
    public String toShortValueString(int maxWidth) {
        String v = getValue();
        return StringUtils.abbreviate(v == null ? "(null)" : v, maxWidth).replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r");
    }
    
    public String toStringTree() {
        StringBuilder sb = new StringBuilder();
        toStringTree("", true, sb);
        return sb.toString();
    }

    private void toStringTree(String prefix, boolean isTail, StringBuilder sb) {
        sb.append(prefix).append(isTail ? "└── " : "├── ").append(toString()).append("\n");
        if(getChildren() != null) {
            for (int i = 0; i < getChildren().size() - 1; i++) {
                getChildren().get(i).toStringTree(prefix + (isTail ? "    " : "│   "), false, sb);
            }
            if (getChildren().size() > 0) {
                getChildren().get(getChildren().size() - 1)
                        .toStringTree(prefix + (isTail ?"    " : "│   "), true, sb);
            }
        }
    }
    
    public String toStringTreeParent() {
        StringBuilder sb = new StringBuilder();
        toStringTreeParent("", true, sb);
        return sb.toString();
    }

    private void toStringTreeParent(String prefix, boolean isTail, StringBuilder sb) {
        sb.append(prefix).append(isTail ? "└── " : "├── ").append(toString()).append("\n");
        if(getParents() != null) {
            for (int i = 0; i < getParents().size() - 1; i++) {
                getParents().get(i).toStringTreeParent(prefix + (isTail ? "    " : "│   "), false, sb);
            }
            if (getParents().size() > 0) {
                getParents().get(getParents().size() - 1)
                        .toStringTreeParent(prefix + (isTail ?"    " : "│   "), true, sb);
            }
        }
    }
    
    public String toHtml() {
        StringBuilder sb = new StringBuilder();
        
        if (hasId() && hasValue()) {
            sb.append("<a href=\""+ StringEscapeUtils.escapeHtml4(getId()) +"\">" + StringEscapeUtils.escapeHtml4(getValue()) + "</a>");
        }
        if(hasChildren()) {
            sb.append(" " + getChildCount()).append("C");
        }
        if(hasParent()) {
            sb.append(" " + getParentCount()).append("P");
        }
        if (hasJsonMetadata()) {
            sb.append(" <code>" + StringEscapeUtils.escapeHtml4(getJsonMetadata().toString()) + "</code>");
        }
        
        return sb.toString();
    }
    
    public String toHtmlSimple() {
        StringBuilder sb = new StringBuilder();
        
        if (hasValue()) {
            sb.append(StringEscapeUtils.escapeHtml4(getValue()));
        }
        if (hasId()) {
            sb.append(" ");
            sb.append(StringEscapeUtils.escapeHtml4(getId()));
        }
        if(hasChildren()) {
            sb.append(" " + getChildCount()).append("Children");
        }
        if(hasParent()) {
            sb.append(" " + getParentCount()).append("Parents");
        }
        if (hasJsonMetadata()) {
            sb.append(" <code>" + StringEscapeUtils.escapeHtml4(getJsonMetadata().toString()) + "</code>");
        }
        
        return sb.toString();
    }
    
    public JSONObject toJson() {
        return toJson(true, false);
    }
    
    public JSONObject toJson(boolean withChildren, boolean withParents) {
        JSONObject jo = new JSONObject();
        if(hasId())
            jo.put("id", getId());
        
        if(hasPropertyId())
            jo.put("prop", getPropertyId());
        
        if(hasValue())
            jo.put("value", getValue());
        
        if(hasJsonMetadata())
            jo.put("meta", getJsonMetadata());
        
        //TODO use org.json dependency here
        /*
        if(withChildren && hasChildren())
            jo.put("children", JSON.Array(getChildren(), c -> new JSON(c.toJson(true, false))).asJSONArray());
        
        if(withParents && hasParents())
            jo.put("parents", JSON.Array(getParents(), c -> new JSON(c.toJson(false, true))).asJSONArray());
        
        if(isMultiValued()) {
            jo.put("values", JSON.Array(getValues(), v -> new JSON(v)).asJSONArray());
        }
        */
        
        return jo;
    }
    
    //</editor-fold>
    
    //<editor-fold desc="save and load with kryo">
    
    //TODO kryo ref was removed
    
    //to print loading and saving stats
    //private static final boolean DEBUG_IO = true;
    
    /**
     * Saves the String Entity and all its connecting parents and children with
     * kryo to a given gzip compressed file.
     * If this is not working you may have to increase stack size (-Xss).
     * @param file target file
     * @param kryo kryo settings
     * @return { duration in ms, target file size in bytes }
     */
    /*
    public long[] save(File file, Kryo kryo) {
        Output output = null;
        long duration = 0;
        try {
            output = new Output(new GZIPOutputStream(new FileOutputStream(file)));
            long begin = System.currentTimeMillis();
            kryo.writeObject(output, this);
            duration = System.currentTimeMillis() - begin;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            output.close();
            if(DEBUG_IO) {
                System.out.println("save " + file.getName() + " " + duration + " ms " + MemoryUtility.humanReadableByteCount(file.length()));
            }
            return new long[] { duration, file.length() };
        }
    }
    */
    /**
     * Loads a String Entity and all its connecting parents and children with
     * kryo from a given gzip cormpessed file.
     * @param file source file
     * @param kryo kryo settings
     * @return deserialized string entity
     */
    /*
    public static StringEntity load(File file, Kryo kryo) {
        Input input = null;
        StringEntity se = null;
        long duration = 0;
        try {
            input = new Input(new GZIPInputStream(new FileInputStream(file)));
            long begin = System.currentTimeMillis();
            se = kryo.readObject(input, StringEntity.class);
            duration = System.currentTimeMillis() - begin;
            input.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            input.close();
            if(DEBUG_IO) {
                System.out.println("load " + file.getName() + " " + duration + " ms " + MemoryUtility.humanReadableByteCount(file.length()));
            }
            if(se == null) {
                throw new RuntimeException("loading returned null, could be that you have to set stack size -Xss100m");
            }
            return se;
        }
    }
    */
    
    //</editor-fold>
    
}
