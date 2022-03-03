package de.dfki.sds.kecs.modules;

import de.dfki.sds.hephaistos.storage.StorageItem;
import de.dfki.sds.hephaistos.storage.assertion.Assertion;
import de.dfki.sds.hephaistos.storage.assertion.AssertionPool;
import de.dfki.sds.hephaistos.storage.assertion.Concept;
import de.dfki.sds.hephaistos.storage.assertion.Intelligence;
import de.dfki.sds.hephaistos.storage.assertion.Phase;
import de.dfki.sds.hephaistos.storage.assertion.Rating;
import de.dfki.sds.hephaistos.storage.file.FileInfo;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import de.dfki.sds.kecs.KecsApp;
import de.dfki.sds.kecs.KecsSettings;
import de.dfki.sds.kecs.server.KecsHumlServer;
import de.dfki.sds.kecs.util.ExceptionUtility;
import de.dfki.sds.kecs.util.FileInfoSearchResult;
import de.dfki.sds.kecs.util.KecsUtils;
import de.dfki.sds.kecs.vocab.KECS;
import de.dfki.sds.mschroeder.commons.lang.RegexUtility;
import de.dfki.sds.mschroeder.commons.lang.SetUtility;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.json.JSONObject;

/**
 *
 */
public class DomainTerminologyExtraction extends Module {

    private final Phase aiPhase = Phase.DomainTerminologyExtraction;
    private final String aiName = "DomainTerminologyExtraction";

    private Set<String> stopwords;

    private Map<Resource, String> file2basename;
    
    private FileInfoStorage fileInfoStorage;

    public DomainTerminologyExtraction() {
        
    }

    @Override
    public void init(FileInfoStorage fileInfoStorage, AssertionPool pool, KecsSettings settings) {
        this.settings = settings;
        
        try {
            stopwords = new HashSet<>(IOUtils.readLines(
                    DomainTerminologyExtraction.class.getResourceAsStream("/de/dfki/sds/kecs/auxiliary/stopword_"+ settings.getLanguage().name() +".txt"),
                    StandardCharsets.UTF_8)
            );
        } catch (IOException ex) {
            ExceptionUtility.save(ex);
            throw new RuntimeException(ex);
        }
        
        this.fileInfoStorage = fileInfoStorage;
        initCache(fileInfoStorage);
    }

    //this is used for faster find method in generalizePositiveTerms
    private void initCache(FileInfoStorage fileInfoStorage) {
        long begin, end;

        file2basename = new HashMap<>();

        begin = System.currentTimeMillis();
        for (StorageItem storageItem : fileInfoStorage.getTreeIter(fileInfoStorage.getRoot())) {

            FileInfo fileInfo = (FileInfo) storageItem;
            JSONObject meta = new JSONObject(fileInfo.getMeta());
            String basename = meta.getString("basename");
            String uri = meta.getString("uri");
            Resource fileResource = KecsApp.creator.createResource(uri);

            file2basename.put(fileResource, basename);
        }
        end = System.currentTimeMillis();

        System.out.println("DomainTerminologyExtraction file2basename init cache with " + file2basename.size() + " files took " + (end - begin) + " ms");
    }

    @Override
    public void bootstrap(FileInfoStorage fileInfoStorage, AssertionPool pool, KecsSettings settings) {
        bootstrapV1(fileInfoStorage, pool);
    }

    //v1 is based on splitByCharacterTypeCamelCase and heuristics
    private void bootstrapV1(FileInfoStorage fileInfoStorage, AssertionPool pool) {
        String symbols = ModuleUtils.getSymbols();

        Iterable<StorageItem> iter = fileInfoStorage.getTreeIter(fileInfoStorage.getRoot());

        for (StorageItem node : iter) {

            FileInfo fileInfo = (FileInfo) node;

            JSONObject meta = new JSONObject(fileInfo.getMeta());

            //basename already removed the extension
            String prefLabel = meta.getString("basename");
            //boolean isFile = !fileInfo.isDirectory();
            Resource resource = KecsApp.creator.createResource(meta.getString("uri"));

            List<String> tokenList = new ArrayList<>(Arrays.asList(StringUtils.splitByCharacterTypeCamelCase(prefLabel)));
            Set<String> tokenSet = new HashSet<>(tokenList);

            //remove file extension
            //because it is basename we do not have to remove it again
            /*
            if (isFile) {
                String ext = FilenameUtils.getExtension(prefLabel);
                tokenSet.remove(ext);
            }
            */
            
            for (String token : tokenSet) {

                //never create a token that is a separator
                if (StringUtils.containsOnly(token, " -_")) {
                    continue;
                }

                Rating rating = Rating.Undecided;
                double conf = 1.0;

                //single letter or symbols
                if (rating == Rating.Undecided && token.length() <= 1) {
                    rating = Rating.Negative;
                }

                //year
                if (rating == Rating.Undecided) {
                    try {
                        int year = Integer.parseInt(token);
                        if (year >= 1980 && year <= 2030) {
                            rating = Rating.Positive;
                        }
                    } catch (Exception e) {
                        //ignore
                    }
                }

                //stopword
                if (rating == Rating.Undecided && stopwords.contains(token.toLowerCase())) {
                    rating = Rating.Negative;
                }

                //alphabetic (also german)
                if (rating == Rating.Undecided && token.matches("[a-zA-ZüöäÜÖÄß]+")) {
                    rating = Rating.Positive;
                }

                //number which is not a year
                if (rating == Rating.Undecided && token.matches("\\d+")) {
                    rating = Rating.Negative;
                }

                //only symbols, like "__" or "$|--._"
                if (rating == Rating.Undecided && StringUtils.containsOnly(token, symbols)) {
                    rating = Rating.Negative;
                }

                pool.assertStatement(resource, KECS.containsDomainTerm, token,
                        Phase.DomainTerminologyExtraction, Intelligence.AI, "DomainTerminologyExtraction", rating, conf);
            }

            //if(pool.getCommitBufferSize() > 100000) {
            //    pool.commit();
            //}
        }

        pool.commit();
    }

    @Override
    public void updateOnChanges(FileInfoStorage fileInfoStorage, AssertionPool pool, List<Assertion> changes) {
        timeStat("DomainTerminologyExtraction", () -> {

            Map<Resource, Set<String>> file2terms = new HashMap<>();
            
            //positive case
            for (Assertion changedAssertion : AssertionPool.filter(changes, null, KECS.containsDomainTerm, null, aiPhase, null, null, Rating.Positive, 0)) {

                Resource node = changedAssertion.getStatement().getSubject();
                String changedDomainTerm = changedAssertion.getStatement().getString();

                timeStat("positiveTermNegativeSubstrings", () -> {
                    positiveTermNegativeSubstrings(node, changedDomainTerm, pool);
                });
                
                if (changedAssertion.getIntelligence() == Intelligence.NI) {
                    file2terms.computeIfAbsent(node, n -> new HashSet<>()).add(changedDomainTerm);
                }
            }
            
            //do also a find when hidden label is filled ("rdf-spreadsheet-editor" case)
            //the null file is ok
            for (Assertion assertion : AssertionPool.filter(changes, null, SKOS.hiddenLabel, null, Phase.ConceptDiscovery, Intelligence.NI, null, null, 0)) {
                file2terms.computeIfAbsent(null, n -> new HashSet<>()).add(assertion.getStatement().getString());
            }
            
            //could be many, we just want to call it once
            timeStat("generalizePositiveTerms", () -> {
                generalizePositiveTerms(file2terms, pool);
            });
            
        });
        
        saveTimeStat();
    }

    private void positiveTermNegativeSubstrings(Resource node, String changedDomainTerm, AssertionPool pool) {
        //if positively rated, all substrings in the rated note are automatically negatively rated
        //"SensAI" positive => "Sens" "AI" negative
        //already done by GUI, so human already marks substrings negative, but this could also be triggered by AI

        //TODO correct cut in positiveTermNegativeSubstrings
        //it can happen that a domain term is "hühnerstallbaumPFLANZE" and the correct split is "hühnerstall baumPFLANZE"
        //when "baumpflanze" is marked positive, we rate negative "PFLANZE" but "hühnerstallbaum" is still incorrectly split
        //the system needs to detect "hühnerstallbaum" as negative and then create a new positive "hühnerstall" term 
        //if(node.equals(ResourceFactory.createResource("urn:file:776"))) {
        //    int a = 0;
        //}
        for (Assertion assertion : pool.getAssertions(node, KECS.containsDomainTerm, null, aiPhase, null, null, Rating.Positive, 0)) {
            String domainTerm = assertion.getStatement().getString();

            if (!changedDomainTerm.equals(domainTerm) && changedDomainTerm.contains(domainTerm)) {
                pool.assertStatement(assertion.getStatement(), aiPhase, Intelligence.AI, aiName, Rating.Negative, 1.0);
            }
        }
    }

    private class Term {
        String text;
        Set<String> variations;
        Resource file;
        List<Pattern> patterns;
        Set<String> found;
        boolean isRegex;
        Map<Resource, Set<String>> resource2terms;
        Resource matchedConcept;
        
        public Term() {
            found = new HashSet<>();
            variations = new HashSet<>();
            resource2terms = new HashMap<>();
        }
        
        public void initRegex() {
            //if 5 characters or low it has to be case sensitive to avoid mismatches
            int caseSensitiveLengthThreshold = 5;
            

            Set<String> varCs = new HashSet<>();
            Set<String> varCi = new HashSet<>();

            //based on length we force case sensitive
            List<Set<String>> varList = Arrays.asList(varCs, varCi);
            variations.forEach(v -> {
                if(isRegex) {
                    varCs.add(v);
                    varCi.add(v);
                } else {
                    if (v.length() <= caseSensitiveLengthThreshold) {
                        varCs.add(v);
                    } else {
                        varCi.add(v);
                    }
                }
            });

            //so we have two patterns: case sensitive and insensitive
            patterns = new ArrayList<>();
            for(int i = 0; i < varList.size(); i++) {

                Set<String> vars = varList.get(i);

                if(vars.isEmpty()) {
                    patterns.add(null);
                    continue;
                }

                String glue = "([a-zA-ZÜÖÄüöä])?";

                StringBuilder patternSB = new StringBuilder();
                //left glue (group 1)
                if(!isRegex) {
                    patternSB.append(glue);
                }
                
                //found term (group 2)
                StringJoiner sj = new StringJoiner("|", "(", ")");
                vars.forEach(v -> sj.add(isRegex ? v : RegexUtility.quote(v)));
                patternSB.append(sj.toString());

                //right glue (group 3)
                if(!isRegex) {
                    patternSB.append(glue);
                }
                
                if(i == 0) {
                    patterns.add(Pattern.compile(patternSB.toString()));
                } else {
                    patterns.add(Pattern.compile(patternSB.toString(), Pattern.CASE_INSENSITIVE));
                }
            }
        }
    }

    private void generalizePositiveTerms(Map<Resource, Set<String>> file2posTerms, AssertionPool pool) {
        if(file2posTerms.isEmpty())
            return;
        
        List<Term> terms = new ArrayList<>();
        
        //extend with variations
        for(Resource file : file2posTerms.keySet()) {
            for(String posTerm : file2posTerms.get(file)) {
                
                Term term = new Term();
                term.text = posTerm;
                term.variations = ModuleUtils.variations(posTerm);
                term.file = file;
                //this text is already accepted by user so we pretend we found it
                term.found.add(term.text);
                
                term.initRegex();
                
                terms.add(term);
            }
        }
        
        //in terms we have all file that also contain a variation of the term
        timeStat("generalizePositiveTerms.find", () -> {
            find(terms);
        });
        
        timeStat("generalizePositiveTerms.getOrCreateConcept", () -> {
            getOrCreateConcept(terms, pool);
        });
        
        timeStat("generalizePositiveTerms.linkToConcept", () -> {
            linkToConcept(terms, pool, Intelligence.AI, aiName, false);
        });
        
        timeStat("generalizePositiveTerms.explicitLink", () -> {
            explicitLink(terms, pool);
        });
    }
    
    public List<FileInfoSearchResult> search(String search, boolean regex, Resource folder) {
        
        List<FileInfoSearchResult> result = new ArrayList<>();
            
        if(search.isEmpty()) {
            return result;
        }
        
        Term term = new Term();
        term.text = search;
        term.variations = regex ? new HashSet<>(Arrays.asList(search)) : ModuleUtils.variations(search);
        //this text is already accepted by user so we pretend we found it
        if(!regex)
            term.found.add(term.text);

        term.isRegex = regex;

        term.initRegex();

        timeStat("search", () -> {
            find(Arrays.asList(term));
        });

        for(Entry<Resource, Set<String>> entry : term.resource2terms.entrySet()) {

            FileInfo fi = (FileInfo) fileInfoStorage.get(KecsUtils.getId(entry.getKey().getURI()));

            FileInfoSearchResult searchResult = new FileInfoSearchResult();
            searchResult.setFileInfo(fi);
            searchResult.setTerms(entry.getValue());
            searchResult.setVariations(term.variations);
           
            String nm = entry.getValue().iterator().next();
            
            String lbl = fi.getName();
            
            int i = lbl.indexOf(nm);
            
            searchResult.setLeft(lbl.substring(0, i));
            searchResult.setMiddle(lbl.substring(i, i + nm.length()));
            searchResult.setRight(lbl.substring(i + nm.length(), lbl.length()));
            
            result.add(searchResult);
        }
        
        result.sort((a,b) -> a.getFileInfo().getName().compareToIgnoreCase(b.getFileInfo().getName()));

        return result;
    }
    
    public void createFromSearch(List<Object[]> fileTermList, Resource type, AssertionPool pool, String username, boolean separately) {
        if(fileTermList.isEmpty())
            return;
        
        List<Term> terms = new ArrayList<>();
        
        //when using regex we do them separately
        if(separately) {
            
            //avoid create same text multiple times
            Map<String, Term> text2term = new HashMap<>();
            
            for(Object[] entry : fileTermList) {
                
                String text = (String) entry[1];
                
                //for exact text use same term instance
                Term term = text2term.get(text);
                if(term == null) {
                    term = new Term();
                    text2term.put(text, term);
                    terms.add(term);
                }

                term.text = text;
                term.found.add(term.text);
                term.variations.add(term.text);
                term.resource2terms.computeIfAbsent((Resource) entry[0], r -> new HashSet<>()).add(text);

            }
        } else {
            //when without regex this will be one thing
            Term term = new Term();
            //no specific
            term.file = null;
                
            for(Object[] entry : fileTermList) {
                term.text = (String) entry[1];
                term.found.add(term.text);
                term.variations.add(term.text);
                term.resource2terms.computeIfAbsent((Resource) entry[0], r -> new HashSet<>()).add((String) entry[1]);
            }
            
            //just create one
            terms.add(term);
        }
        
        if(type != null) {
            getOrCreateConcept(terms, pool);

            typeConcept(terms, type, Intelligence.NI, username, pool);

            linkToConcept(terms, pool, Intelligence.AI, aiName, true);

            explicitLink(terms, pool);
        } else {
            
            //just add containsDomainTerm
            //linkToConcept will skip assertStatement when term.matchedConcept is null
            linkToConcept(terms, pool, Intelligence.AI, aiName, true);
        }
        
        //first persist the inital changes
        pool.commit();
        
        //this also commits at the end
        pool.notifyListenersRecursively(fileInfoStorage);
    }
    
    private void find(List<Term> terms) {

        //if 9 characters or lower we need both non-glued
        //if 10 characters or more we need only one non-glued
        //-> be less restictive if length is long
        //TODO magic number
        int glueThreshold = 9;
        
        class RegexRunnable implements Runnable {

            Collection<Entry<Resource, String>> entries;
            //Map<Resource, Set<String>> resource2terms = new HashMap<>();
            
            public RegexRunnable(Collection<Entry<Resource, String>> entries) {
                this.entries = entries;
                //this.resource2terms = new HashMap<>();
            }
            
            @Override
            public void run() {
                for(Term term : terms) {
                    for(int i = 0; i < term.patterns.size(); i++) {
                        Pattern p = term.patterns.get(i);
                        if(p == null) {
                            continue;
                        }

                        for(Entry<Resource, String> entry : entries) {
                            Matcher matcher = p.matcher(entry.getValue());

                            while(matcher.find()) {

                                if(!term.isRegex) {
                                    boolean fulfilled;
                                    
                                    //because of \b it is empty
                                    boolean leftSep  = matcher.group(1) == null;
                                    //matching length
                                    int len = matcher.group(2).length();
                                    //because of \b it is empty
                                    boolean rightSep = matcher.group(3) == null;
                                    
                                    if (len <= glueThreshold) {
                                        fulfilled = leftSep && rightSep;
                                    } else {
                                        //be less restictive if length is long
                                        fulfilled = leftSep || rightSep;
                                    }
                                    
                                    if(fulfilled) {
                                        term.found.add(matcher.group(2));
                                        term.resource2terms.computeIfAbsent(entry.getKey(), n -> new HashSet<>()).add(matcher.group(2));
                                    }
                                    
                                } else {
                                    //if there is a group "(...)" use the first one
                                    String text;
                                    if(matcher.groupCount() > 1) {
                                        text = matcher.group(2);
                                    } else {
                                        text = matcher.group();//same as group(1)
                                    }
                                    if(!text.isEmpty()) {
                                        term.found.add(text);
                                        term.resource2terms.computeIfAbsent(entry.getKey(), n -> new HashSet<>()).add(text);
                                    }
                                }
                                
                            }
                        }
                    }
                }
            }
        }
        
        //seems to be not necessary for better optimization
        //maybe threads make sense when more terms are there, e.g. count >= 4
        //only use threads of set of files are large
        int numThread = file2basename.size() > 50000 ? 4 : 0;
        
        //List<RegexRunnable> regexRunnables = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        
        if(numThread > 1) {
            //around 112000 in one usecase
            List<Entry<Resource, String>> fileEntries = new ArrayList<>(file2basename.entrySet());
            
            //split fileEntries
            int splitSize = fileEntries.size() / numThread;
            
            for(int i = 0; i < numThread; i++) {
                List<Entry<Resource, String>> sublist = fileEntries.subList(
                                                                 i    * splitSize, 
                        i == numThread-1 ? fileEntries.size() : (i+1) * splitSize
                );
                
                RegexRunnable regexRunnable = new RegexRunnable(sublist);

                Thread thread = new Thread(regexRunnable);
                threads.add(thread);
                
                thread.start();
            }
            
            for(Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
            
        } else {
            RegexRunnable regexRunnable = new RegexRunnable(file2basename.entrySet());
            regexRunnable.run();
        }
        
        //1 term takes around 300 ms
        //5 terms take around 1000 ms (threading halfs this)
        //for(Term term : terms) {
        //    System.out.println(term.text + ", " + term.resource2terms.size() + " files found, numThread=" + numThread + ", took " + (end - begin) + " ms");
        //}
    }

    private void getOrCreateConcept(List<Term> terms, AssertionPool pool) {
        
        //1 of 10 words indicate to this concept (person problem)
        //TODO magic number
        double jaccardThreshold = 0.1;
        
        for(Term term : terms) {

            double maxJaccard = 0;
            Concept maxConcept = null;
            
            //get from allFoundTerms all possible concepts
            //use jaccard similarity
            //will be around 500 to 1000 concepts so it is no problem to loop here
            for (Concept concept : pool.getConcepts()) {

                Set<String> techLabelStrings = new HashSet<>();
                for (Assertion techLabel : concept.getHiddenLabels()) {
                    techLabelStrings.add(techLabel.getStatement().getString());
                }

                double jaccard = SetUtility.intersection(term.found, techLabelStrings).size() / (double) SetUtility.union(term.found, techLabelStrings).size();

                //System.out.println(term.found + " vs " + techLabelStrings + " => " + jaccard);
                
                if(jaccard > maxJaccard) {
                    maxJaccard = jaccard;
                    maxConcept = concept;
                    
                    //it will not get better
                    if(maxJaccard == 1.0) {
                        break;
                    }
                }
            }

            //best overlapping one
            if (maxConcept != null && maxJaccard >= jaccardThreshold) {
                term.matchedConcept = maxConcept.getResource();
                
            } else {
                //create one
                String prefLabel = ModuleUtils.toPrefLabel(term.text);

                Resource cpt = pool.createConcept();
                pool.assertStatement(cpt, RDF.type, ConceptDiscovery.DEFAULT_TYPE, Phase.ConceptDiscovery, Intelligence.AI, aiName, Rating.Positive, 1.0);
                pool.assertStatement(cpt, SKOS.prefLabel, prefLabel, Phase.ConceptDiscovery, Intelligence.AI, aiName, Rating.Positive, 1.0);

                term.found.add(prefLabel);
                List<String> allFoundTermsList = new ArrayList<>(term.found);
                allFoundTermsList.sort((a, b) -> a.compareToIgnoreCase(b));

                for (String techLabel : allFoundTermsList) {
                    pool.assertStatement(cpt, SKOS.hiddenLabel, techLabel, Phase.ConceptDiscovery, Intelligence.AI, aiName, Rating.Positive, 1.0);
                }

                term.matchedConcept = cpt;
            }
        }
    }
    
    private void typeConcept(List<Term> terms, Resource type, Intelligence intel, String username, AssertionPool pool) {
        
        if(type == null || type.equals(ConceptDiscovery.DEFAULT_TYPE))
            return;
        
        List<Assertion> assertions = new ArrayList<>();
        for(Term term : terms) {
            
            if(term.matchedConcept != null) {
                //TODO would be better a util method
                KecsHumlServer.addTypeAssertion(term.matchedConcept, type, Rating.Positive, username, assertions, pool);
            }
        }
        
        for(Assertion assertion : assertions) {
            pool.assertAssertion(assertion);
        }
    }
    
    private void linkToConcept(List<Term> terms, AssertionPool pool, Intelligence intel, String username, boolean forceContainsDomainTerm) {
        for(Term term : terms) {
            
            for(Resource file : term.resource2terms.keySet()) {
                
                //human already made the containsDomainTerm
                //term.file == null when hidden label was used (without file relation)
                if(forceContainsDomainTerm || term.file == null || !term.file.equals(file)) {
                    for(String foundOne : term.resource2terms.get(file)) {
                        pool.assertStatement(file, KECS.containsDomainTerm, foundOne, Phase.DomainTerminologyExtraction, intel, username, Rating.Positive, 0.75);
                    }
                }
                
                if(term.matchedConcept != null) {
                    pool.assertStatement(file, FOAF.topic, term.matchedConcept, Phase.ConceptDiscovery, intel, username, Rating.Positive, 0.75);
                }
            }
        }
    }
    
    private void explicitLink(List<Term> terms, AssertionPool pool) {
        for(Term term : terms) {
            
            List<RDFNode> objects = new ArrayList<>();
            for(String variation : SetUtility.union(term.variations, term.found)) {
                objects.add(KecsApp.creator.createLiteral(variation));
            }
            
            //get really fast all file contains term assertions of all variations
            List<Assertion> assertions = pool.getAssertionsIn(KECS.containsDomainTerm, objects, aiPhase);
            
            for(Assertion assertion : assertions) {
                if(assertion.getRating() != Rating.Positive)
                    continue;
                
                pool.assertStatement(assertion.getSubject(), FOAF.topic, term.matchedConcept, Phase.ConceptDiscovery, Intelligence.AI, aiName, Rating.Positive, 0.75);
            }
        }
    }
    
    //==============================================================
    
    /*
    public static void main(String[] args) {
        Pattern p = Pattern.compile("([a-zA-ZÜÖÄüöä])?(Grundsätze)([a-zA-ZÜÖÄüöä])?");
        Matcher m = p.matcher("aGrundsätze_Barth_120828");
        while(m.find()) {
            System.out.println(m);
            System.out.println(m.group(1));
            System.out.println(m.group(2));
            System.out.println(m.group(3));
        }
    }
    */
    
    /*
    public static void main(String[] args) {
        for(String token : Arrays.asList(StringUtils.splitByCharacterTypeCamelCase("WIP_#for2007-mainDiagram!(28)A.jpg"))) {
            System.out.print("\\adjustbox{cframe=green}{"+token+"}");
        }
        System.out.println();
    }
    */
}
