package de.dfki.sds.stringanalyzer.helper;


import de.dfki.sds.stringanalyzer.string.StringEntity;
import de.dfki.sds.stringanalyzer.string.StringEntitySequence;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * 
 */
public class GermaNet {

    public static final String GERMANET = "germanet";
    
    private File relationsFile; //gn_relations.xml
    private List<File> wiktionaryParaphrasesFiles; //e.g. wiktionaryParaphrases-nomen.xml
    private List<File> synsetFiles; //e.g. nomen.Zeit.xml

    private Map<String, SynSet> id2synset;
    private Map<String, LexUnit> id2lexunit;
    private Map<String, List<SynSet>> word2synsets;

    private List<SynSet> roots;
    private List<StringEntity> rootsStringEntity;
    private Map<SynSet, StringEntity> synset2se;

    public GermaNet(List<File> synsetFiles, List<File> wiktionaryParaphrasesFiles, File relationsFile) {
        this.relationsFile = relationsFile;
        this.wiktionaryParaphrasesFiles = wiktionaryParaphrasesFiles;
        this.synsetFiles = synsetFiles;

        id2synset = new HashMap<>();
        id2lexunit = new HashMap<>();
        word2synsets = new HashMap<>();

        //parse();
    }

    //load
    public GermaNet(InputStream inputStream) {
        id2synset = new HashMap<>();
        id2lexunit = new HashMap<>();
        word2synsets = new HashMap<>();
        
        load(inputStream);
    }
    
    /* XML parse version, load() loads the saved json version
    private void parse() {
        try {
            for (File synsetFile : synsetFiles) {
                Document synsets = Jsoup.parse(new FileInputStream(synsetFile), "UTF-8", "", Parser.xmlParser());
                for (Element synset : synsets.getElementsByTag("synset")) {
                    String id = synset.attr("id");
                    String category = synset.attr("category");
                    String clazz = synset.attr("class");

                    SynSet ss = new SynSet(id, category, clazz);
                    id2synset.put(id, ss);

                    for (Element lexUnit : synset.getElementsByTag("lexUnit")) {

                        String luId = lexUnit.attr("id");
                        String orthForm = lexUnit.getElementsByTag("orthForm").first().text();

                        //get or create
                        LexUnit lu;
                        if (id2lexunit.containsKey(luId)) {
                            lu = id2lexunit.get(luId);
                        } else {
                            lu = new LexUnit(luId, orthForm);
                            id2lexunit.put(luId, lu);
                        }

                        //know each other
                        ss.lexUnits.add(lu);
                        lu.synSets.add(ss);

                        //from word to synsets
                        MapUtility.fillKey2ListMap(orthForm.toLowerCase(), ss, word2synsets);
                    }

                }
            }

            for (File paraFile : wiktionaryParaphrasesFiles) {
                Document paraphrases = Jsoup.parse(new FileInputStream(paraFile), "UTF-8", "", Parser.xmlParser());
                //<wiktionaryParaphrase lexUnitId="l45646" wiktionaryId="w70154" wiktionarySenseId="1" wiktionarySense="Schule: Note 4" edited="no" />
                for (Element paraphrase : paraphrases.getElementsByTag("wiktionaryParaphrase")) {
                    String lexUnitId = paraphrase.attr("lexUnitId");
                    String wiktionarySense = paraphrase.attr("wiktionarySense");
                    //String wiktionaryId = paraphrase.attr("wiktionaryId");

                    if (id2lexunit.containsKey(lexUnitId)) {
                        LexUnit lu = id2lexunit.get(lexUnitId);
                        lu.paraphrase = wiktionarySense;
                    }
                }
            }

            //<con_rel name="has_hypernym" from="s0" to="s51001" dir="revert" inv="has_hyponym" />
            //names = [entails, has_component_meronym, causes, has_portion_meronym, has_substance_meronym, is_related_to, has_hypernym, has_member_meronym]
            //dirs = [one, revert, both]
            //inv = [, has_hyponym, has_member_holonym, has_portion_holonym, has_substance_holonym, is_entailed_by, is_related_to, has_component_holonym]
            Document relations = Jsoup.parse(new FileInputStream(relationsFile), "UTF-8", "", Parser.xmlParser());
            for (Element relation : relations.getElementsByTag("con_rel")) {
                String name = relation.attr("name");
                String fromId = relation.attr("from");
                String toId = relation.attr("to");
                String dir = relation.attr("dir");
                String inv = relation.attr("inv");

                SynSet from = id2synset.get(fromId);
                SynSet to = id2synset.get(toId);

                if (from != null && to != null) {
                    if (name.equals("has_hypernym")) {
                        from.generalizations.add(to);

                        if (dir.equals("revert")) {
                            to.specializations.add(from);
                        }
                    }
                    
                    //TODO is_related_to
                    //TODO has_component_meronym
                    //TODO has_substance_meronym
                    //TODO has_antonym
                    //TODO has_pertainym
                    //TODO entails
                }
            }

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        initTree();
        initStringEntityTree();
    }
    */
    
    private void initTree() {
        roots = id2synset.values().stream().filter(synset -> synset.generalizations.isEmpty()).collect(toList());
    }
    
    private void initStringEntityTree() {
        
        rootsStringEntity = new ArrayList<>();
        
        synset2se = new HashMap<>();
        
        //for building rootsStringEntity
        for(SynSet root : roots) {
            Queue<SynSet> q = new LinkedList<>();
            q.add(root);
            
            while(!q.isEmpty()) {
                SynSet cur = q.poll();
                
                StringEntity se;
                if(synset2se.containsKey(cur)) {
                    se = synset2se.get(cur);
                } else {
                    se = cur.toStringEntity();
                    synset2se.put(cur, se);
                }
                
                if(cur == root) {
                    rootsStringEntity.add(se);
                }

                for(SynSet child : cur.specializations) {
                    
                    StringEntity seChild;
                    if(synset2se.containsKey(child)) {
                        seChild = synset2se.get(child);
                    } else {
                        seChild = child.toStringEntity();
                        synset2se.put(child, seChild);
                    }
                    
                    se.addChild(seChild);
                    
                    q.add(child);
                }
            }
        }
    }

    public class SynSet {

        private String id;
        private String category;
        private String clazz;
        private List<LexUnit> lexUnits;

        //relations
        //names = [entails, has_component_meronym, causes, has_portion_meronym, has_substance_meronym, is_related_to, has_hypernym, has_member_meronym]
        //dirs = [one, revert, both]
        //inv = [, has_hyponym, has_member_holonym, has_portion_holonym, has_substance_holonym, is_entailed_by, is_related_to, has_component_holonym]
        //hypernym = generalization
        private List<SynSet> generalizations;

        //has_hyponym
        private List<SynSet> specializations;

        public SynSet(String id, String category, String clazz) {
            this.id = id;
            this.category = category;
            this.clazz = clazz;
            this.lexUnits = new ArrayList<>();
            this.generalizations = new ArrayList<>();
            this.specializations = new ArrayList<>();
        }

        public String getLexSimple() {
            return lexUnits.stream().map(lu -> lu.orthForm).collect(joining(", "));
        }
        
        public String getLex() {
            return "["+lexUnits.stream().map(lu -> lu.orthForm).collect(joining(",")) + "]";
        }
        
        @Override
        public String toString() {
            return "SynSet{" + "id=" + id + ", category=" + category + ", clazz=" + clazz + ", lexUnits=" + getLex() + '}';
        }

        public String getId() {
            return id;
        }
        
        public String toStringTree() {
            StringBuilder sb = new StringBuilder();
            toStringTree("", true, sb);
            return sb.toString();
        }

        private void toStringTree(String prefix, boolean isTail, StringBuilder sb) {
            sb.append(prefix).append(isTail ? "└── " : "├── ").append(toString()).append("\n");
            for (int i = 0; i < specializations.size() - 1; i++) {
                specializations.get(i).toStringTree(prefix + (isTail ? "    " : "│   "), false, sb);
            }
            if (specializations.size() > 0) {
                specializations.get(specializations.size() - 1)
                        .toStringTree(prefix + (isTail ? "    " : "│   "), true, sb);
            }
        }

        /**
         * Returns the path of parents (from root to this element) but without
         * this element at the end. Throws RuntimeException if parent is
         * ambigous.
         *
         * @return
         */
        public List<SynSet> getParentPath() {
            List<SynSet> path = new ArrayList<>();

            //path.add(this);
            
            SynSet cur = this;
            while (!cur.generalizations.isEmpty()) {
                if (cur.generalizations.size() > 1) {
                    throw new RuntimeException("parent path is not possible because this string entity has more than one parent: " + cur);
                }

                SynSet parent = cur.generalizations.get(0);
                path.add(parent);
                cur = parent;
            }
            Collections.reverse(path);
            return path;
        }
        
        /**
         * Returns the path of parents (from root to this element) but without
         * this element at the end. If parent is
         * ambigous takes first one.
         *
         * @return
         */
        public List<SynSet> getParentPathDisambig() {
            List<SynSet> path = new ArrayList<>();
            
            //path.add(this);

            SynSet cur = this;
            while (!cur.generalizations.isEmpty()) {
                SynSet parent = cur.generalizations.get(0);
                path.add(parent);
                cur = parent;
            }
            Collections.reverse(path);
            return path;
        }
        
        /**
         * Returns the path of parents (from root to this element) but without
         * this element at the end.If parent is
 ambigous takes first one.
         *
         * @param max maximal parent path length
         * @return
         */
        public List<SynSet> getParentPathDisambig(int max) {
            List<SynSet> path = new ArrayList<>();
            
            //path.add(this);

            SynSet cur = this;
            while (!cur.generalizations.isEmpty()) {
                SynSet parent = cur.generalizations.get(0);
                path.add(parent);
                cur = parent;
                
                if(path.size() >= max) {
                    break;
                }
            }
            Collections.reverse(path);
            return path;
        }

        public List<LexUnit> getLexUnits() {
            return lexUnits;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 83 * hash + Objects.hashCode(this.id);
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
            final SynSet other = (SynSet) obj;
            if (!Objects.equals(this.id, other.id)) {
                return false;
            }
            return true;
        }

        public String getCategory() {
            return category;
        }

        public String getClazz() {
            return clazz;
        }
        
        public StringEntity toStringEntity() {
            StringEntity se = new StringEntity();
            se.setId(id);
            se.setValues(getLexUnits().stream().map(lu -> lu.getOrthForm()).collect(toList()));
            JSONObject meta = se.getOrCreateJsonObject(GERMANET);
            meta.put("category", getCategory());
            meta.put("class", getClazz());
            meta.put("lex", getLex());
            return se;
        }
        
        public StringEntitySequence getParentPathStringEntitySequence() {
            StringEntitySequence seq = new StringEntitySequence();
            for(SynSet synset : getParentPathDisambig()) { //TODO disambig not good?
                seq.getSequenceMembers().add(synset.toStringEntity());
            }
            return seq;
        }
        
    }

    public class LexUnit {

        private String id;
        private String orthForm;
        private String paraphrase;
        private List<SynSet> synSets;

        public LexUnit(String id, String orthForm) {
            this.id = id;
            this.orthForm = orthForm;
            this.synSets = new ArrayList<>();
        }

        public String getOrthForm() {
            return orthForm;
        }
        
        @Override
        public String toString() {
            return "LexUnit{" + "id=" + id + ", orthForm=" + orthForm + ", paraphrase=" + paraphrase + '}';
        }

        public String getParaphrase() {
            return paraphrase;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 97 * hash + Objects.hashCode(this.id);
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
            final LexUnit other = (LexUnit) obj;
            if (!Objects.equals(this.id, other.id)) {
                return false;
            }
            return true;
        }
        
        
        
    }

    public static List<File> nomen(File germaNetFolder) {
        return Arrays.asList(germaNetFolder.listFiles(f -> f.getName().startsWith("nomen.")));
    }
    
    public static List<File> synsets(File germaNetFolder) {
        return Arrays.asList(germaNetFolder.listFiles(f -> 
                f.getName().startsWith("nomen.") || 
                f.getName().startsWith("verben.") || 
                f.getName().startsWith("adj.")
        ));
    }

    public static List<File> classes(File germaNetFolder, List<String> classes) {
        return synsets(germaNetFolder).stream().filter(f -> classes.contains(f.getName().split("\\.")[1])).collect(toList());
    }
    
    public static List<File> categories(File germaNetFolder, List<String> categories) {
        return synsets(germaNetFolder).stream().filter(f -> categories.contains(f.getName().split("\\.")[0])).collect(toList());
    }
    
    
    public int synsetCount() {
        return id2synset.size();
    }

    public int lexunitCount() {
        return id2lexunit.size();
    }

    public int wordCount() {
        return word2synsets.size();
    }

    public String getStatusLine() {
        return roots.size() + " roots, " +
                synsetCount() + " synsets, " +
                + lexunitCount() + " lexunits, "
                + wordCount() + " words";
    }

    public List<SynSet> lookup(String word) {
        List<SynSet> synsets = word2synsets.get(word.toLowerCase());
        if (synsets == null) {
            return Arrays.asList();
        }
        return synsets;
    }

    public List<StringEntity> lookupStringEntity(String word) {
        List<SynSet> synsets = word2synsets.get(word.toLowerCase());
        if (synsets == null) {
            return Arrays.asList();
        }
        return synsets.stream().map(ss -> synset2se.get(ss)).collect(toList());
    }

    public List<StringEntity> getRootsStringEntity() {
        return rootsStringEntity;
    }

    public void save(OutputStream outputStream) {
        JSONObject id2synsetObj = new JSONObject();
        
        for(Entry<String, SynSet> e : id2synset.entrySet()) {
            JSONObject synsetObj = new JSONObject();
            id2synsetObj.put(e.getKey(), synsetObj);
            
            SynSet synset = e.getValue();
            
            synsetObj.put("id", synset.id);
            synsetObj.put("category", synset.category);
            synsetObj.put("class", synset.clazz);
            
            JSONArray lexUnitArray = new JSONArray();
            synsetObj.put("lexUnits", lexUnitArray);
            
            for(LexUnit lu : synset.lexUnits) {
                
                JSONObject luObj = new JSONObject();
                luObj.put("id", lu.id);
                luObj.put("orthForm", lu.orthForm);
                luObj.put("paraphrase", lu.paraphrase);
                
                lexUnitArray.put(luObj);
            }
            
            JSONArray genArray = new JSONArray();
            synsetObj.put("gen", genArray);
            for(SynSet gen : synset.generalizations) {
                genArray.put(gen.id);
            }
            
            JSONArray specArray = new JSONArray();
            synsetObj.put("spec", specArray);
            for(SynSet spec : synset.specializations) {
                specArray.put(spec.id);
            }
        }
        
        try {
            outputStream.write(id2synsetObj.toString(1).getBytes(StandardCharsets.UTF_8));
            outputStream.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        
    }
    
    private void load(InputStream inputStream) {
        String content;
        try {
            content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            inputStream.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        
        JSONObject id2synsetObj = new JSONObject(content);
        
        for(String key : id2synsetObj.keySet()) {
            
            JSONObject synsetObj = id2synsetObj.getJSONObject(key);
            
            SynSet synset = new SynSet(synsetObj.getString("id"), synsetObj.getString("category"), synsetObj.getString("class"));
            
            JSONArray lexUnitArray = synsetObj.getJSONArray("lexUnits");
            for(int i = 0; i < lexUnitArray.length(); i++) {
                JSONObject luObj = lexUnitArray.getJSONObject(i);
                
                LexUnit lu = new LexUnit(luObj.getString("id"), luObj.getString("orthForm"));
                lu.paraphrase = luObj.optString("paraphrase", null);
                
                String k = lu.orthForm.toLowerCase();
                SynSet v = synset;
                if(word2synsets.containsKey(k)) {
                    word2synsets.get(k).add(v);
                } else {
                    word2synsets.put(k, new ArrayList<>(Arrays.asList(v)));
                }
                
                synset.lexUnits.add(lu);
            }
            
            id2synset.put(key, synset);
        }
        
        for(String key : id2synsetObj.keySet()) {
            
            JSONObject synsetObj = id2synsetObj.getJSONObject(key);
            
            SynSet synset = id2synset.get(key);
            
            JSONArray genArray = synsetObj.getJSONArray("gen");
            for(int i = 0; i < genArray.length(); i++) {
                synset.generalizations.add(id2synset.get(genArray.getString(i)));
            }
            
            JSONArray specArray = synsetObj.getJSONArray("spec");
            for(int i = 0; i < specArray.length(); i++) {
                synset.specializations.add(id2synset.get(specArray.getString(i)));
            }
        }
        
        initTree();
    }
    
}
