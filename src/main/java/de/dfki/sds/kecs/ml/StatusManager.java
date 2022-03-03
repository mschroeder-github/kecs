
package de.dfki.sds.kecs.ml;

import de.dfki.sds.hephaistos.storage.assertion.Assertion;
import de.dfki.sds.hephaistos.storage.assertion.AssertionPool;
import de.dfki.sds.hephaistos.storage.assertion.AssertionPoolSqlite;
import de.dfki.sds.hephaistos.storage.assertion.Intelligence;
import de.dfki.sds.hephaistos.storage.assertion.KECS;
import de.dfki.sds.hephaistos.storage.assertion.Phase;
import de.dfki.sds.hephaistos.storage.assertion.Rating;
import de.dfki.sds.hephaistos.storage.file.FileInfo;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import de.dfki.sds.kecs.KecsSettings;
import de.dfki.sds.kecs.modules.ConceptDiscovery;
import de.dfki.sds.kecs.modules.OntologyPopulation;
import de.dfki.sds.kecs.util.KecsUtils;
import de.dfki.sds.kecs.util.PropertyEdge;
import de.dfki.sds.kecs.vocab.DOT;
import de.dfki.sds.kecs.vocab.NFO;
import de.dfki.sds.mschroeder.commons.lang.MemoryUtility;
import de.dfki.sds.mschroeder.commons.lang.SetUtility;
import de.dfki.sds.mschroeder.commons.lang.math.MinAvgMaxSdDouble;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.shortestpath.GraphMeasurer;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Calculates status information.
 */
public class StatusManager {    
    
    private FileInfoStorage fileInfoStorage;
    private AssertionPool assertionPool;
    private KecsSettings settings;
    
    private GraphManager graphManager;

    private long branchCount;
    private long leafCount;
    
    private double conceptDiscoveryMetric;
    private int positiveTopics;
    private int positiveTerms;
    private Resource conceptDiscoveryNextFileResource;
    private FileInfo conceptDiscoveryNextFile;
    
    private double ontologyPopulationMetric;
    private int positiveTypes;
    private int positiveConcepts;
    
    private double taxonomyMetric;
    private int taxComponents;
    private int taxVertices;
    private int taxConcepts;
    private int taxBroader;
    private double taxGeneralization;
    private double taxConnectedness;
    
    private double nonTaxonomicMetric;
    private int nonTaxComponents;
    private int nonTaxVertices;
    private double nonTaxConnectedness;
    private double nonTaxDiameter;
    private double nonTaxCloseness;
    
    private int effortClick;
    private int effortKeydown;
    private int effortInteractions;
    
    private long memoryUsed;
    private long memoryTotal;
    private long memoryMax;
    
    private double overallRating;
    
    private long duration;
    
    private JSONObject chartData;
    
    private double productivity;
    
    private final Object syncPoint = new Object();
    
    public StatusManager(FileInfoStorage fileInfoStorage, AssertionPool assertionPool, KecsSettings settings) {
        this.fileInfoStorage = fileInfoStorage;
        this.assertionPool = assertionPool;
        this.settings = settings;
        
        this.graphManager = new GraphManager();
        
        //query only once
        fileInfoStatus();
    }

    //static
    
    public final void fileInfoStatus() {
        //just query it once
        if(branchCount == 0 && leafCount == 0) {
            branchCount = fileInfoStorage.getBranchCount();
            leafCount = fileInfoStorage.getLeafCount();
        }
    }

    //dynamic
    
    public void calculateAll(boolean fastTopicStatistics) {
        synchronized(syncPoint) {
            long begin = System.currentTimeMillis();

            //termStatistics();
            topicStatistics(fastTopicStatistics);
            ontologyPopulationStatistics();
            taxonomyStatistics();
            nonTaxonomicStatistics();
            effortStatistics();
            overallRating();
            productivityStatistics();
            memoryStatistics();

            long end = System.currentTimeMillis();
            duration = end - begin;

            KecsUtils.saveStatus(this, settings.getOutputFolder());

            //after save to have the newest data
            chartData();

            //System.out.println("took " + duration + " ms");
            //System.out.println();
        }
    }
    
    //maybe this is just a side-effect, more important are concepts on files
    @Deprecated
    public void termStatistics() {
        //List<Assertion> assertions = assertionPool.getAssertions(null, KECS.containsDomainTerm, null, Phase.DomainTerminologyExtraction, null, null, null, 0);
        //System.out.println(stats(assertions));
    }
    
    public void topicStatistics(boolean fastTopicStatistics) {
        
        long termAssertionCount;
        long topicAssertionCount;
        
        if(fastTopicStatistics) {
            termAssertionCount = ((AssertionPoolSqlite) assertionPool).getCount(null, KECS.containsDomainTerm, null, 
                Phase.DomainTerminologyExtraction, null, null, Rating.Positive, 0);
            topicAssertionCount = ((AssertionPoolSqlite) assertionPool).getCount(null, FOAF.topic, null, 
                    Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
            
            
        } else {
            //takes some time on large datasets
            List<Assertion> termAssertions = assertionPool.getAssertions(null, KECS.containsDomainTerm, null, 
                Phase.DomainTerminologyExtraction, null, null, Rating.Positive, 0);
            List<Assertion> topicAssertions = assertionPool.getAssertions(null, FOAF.topic, null, 
                    Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
            
            //necessary to show next filename
            Map<Resource, Integer[]> file2counts = new HashMap<>();
            for(Assertion a : termAssertions) {
                Integer[] counts = file2counts.computeIfAbsent(a.getSubject(), s -> new Integer[]{0,0});
                counts[0]++;
            }
            for(Assertion a : topicAssertions) {
                Integer[] counts = file2counts.computeIfAbsent(a.getSubject(), s -> new Integer[]{0,0});
                counts[1]++;
            }

            conceptDiscoveryNextFileResource = null;
            int worstDiff = 0;
            for(Entry<Resource, Integer[]> entry : file2counts.entrySet()) {
                //if more terms then topics are attached
                //because more topics is fine
                if(entry.getValue()[0] > entry.getValue()[1]) {
                    int diff = entry.getValue()[0] - entry.getValue()[1];

                    if(diff > worstDiff) {
                        conceptDiscoveryNextFileResource = entry.getKey();
                        worstDiff = diff;
                    }
                }
            }
            
            termAssertionCount = termAssertions.size();
            topicAssertionCount = topicAssertions.size();
        }
        
        conceptDiscoveryNextFile = null;
        if(conceptDiscoveryNextFileResource != null) {
            int id = KecsUtils.getId(conceptDiscoveryNextFileResource.getURI());
            conceptDiscoveryNextFile = (FileInfo) fileInfoStorage.get(id);
        }
        
        positiveTerms = (int) termAssertionCount;
        positiveTopics = (int) topicAssertionCount;
        
        if(positiveTerms > 0) {
            conceptDiscoveryMetric = positiveTopics / (double) positiveTerms;
        }
        if(conceptDiscoveryMetric > 1) {
            conceptDiscoveryMetric = 1;
        }
    }
    
    public void ontologyPopulationStatistics() {
        
        //only from NI
        List<Assertion> typeAssertions = assertionPool.getAssertions(null, RDF.type, null, 
                Phase.OntologyPopulation, Intelligence.NI, null, Rating.Positive, 0);
        
        //remove classes and properties
        typeAssertions.removeIf(a -> {
            Resource type = a.getObject();
            return type.equals(RDFS.Class) || type.equals(RDF.Property);
        });
        
        Set<Resource> hasType = new HashSet<>();
        typeAssertions.forEach(a -> hasType.add(a.getSubject()));
        
        List<Assertion> conceptAssertions = assertionPool.getAssertions(null, RDF.type, ConceptDiscovery.DEFAULT_TYPE, 
                Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
        
        Set<Resource> isConcept = new HashSet<>();
        conceptAssertions.forEach(a -> isConcept.add(a.getSubject()));
        
        positiveTypes = SetUtility.intersection(hasType, isConcept).size();
        positiveConcepts = conceptAssertions.size();
        
        if(positiveConcepts > 0) {
            ontologyPopulationMetric = positiveTypes / (double) positiveConcepts;
        }
        if(ontologyPopulationMetric > 1) {
            ontologyPopulationMetric = 1;
        }
    }
    
    public void taxonomyStatistics() {
        DefaultUndirectedGraph<Resource, DefaultEdge> graph = graphManager.getTaxonomyGraph(assertionPool);
        
        ConnectivityInspector<Resource, DefaultEdge> connectivityInspector = new ConnectivityInspector<>(graph);
        
        List<Set<Resource>> connectedSets = connectivityInspector.connectedSets();
        Set<Resource> vertexSet = graph.vertexSet();
        
        taxComponents = connectedSets.size();
        taxVertices = vertexSet.size();
        
        //best is one connected Set, worst is vertexSet.size() connected sets
        double disconnection;
        if(taxVertices <= 1) {
            disconnection = 1.0;
        } else {
            disconnection = (taxComponents - 1) / (double) (taxVertices - 1);
        }
        taxConnectedness = 1.0 - disconnection;
        //System.out.println("taxConnectedness=" + taxConnectedness);
        
        taxConcepts = 0;
        taxBroader = 0;
        
        //all skos:Concepts are vertices
        List<Assertion> conceptAssertions = assertionPool.getAssertions(null, RDF.type, OntologyPopulation.CONCEPT_TYPE, Phase.OntologyPopulation, Intelligence.NI, null, Rating.Positive, 0);
        for(Assertion conceptAssertion : conceptAssertions) {
            
            //needs to be annotated on a file
            boolean isAnnotated = !assertionPool.getAssertions(null, FOAF.topic, conceptAssertion.getSubject(), 
                    Phase.ConceptDiscovery, null, null, Rating.Positive, 0).isEmpty();
         
            if(isAnnotated) {
                taxConcepts++;
                
                boolean hasBroader = !assertionPool.getAssertions(conceptAssertion.getSubject(), SKOS.broader, null, 
                    Phase.ConceptHierarchyDerivation, Intelligence.NI, null, Rating.Positive, 0).isEmpty();
                
                if(hasBroader) {
                    taxBroader++;
                }
            }
        }
        
        if(taxConcepts > 0) {
            taxGeneralization = taxBroader / (double) taxConcepts;
        }
        
        taxonomyMetric = (taxGeneralization + taxConnectedness) / 2.0;
    }
    
    //non-taxonomic
    public void nonTaxonomicStatistics() {
        
        DefaultUndirectedGraph<Resource, PropertyEdge> graph = graphManager.getNonTaxonomicGraph(assertionPool);
        
        ConnectivityInspector<Resource, PropertyEdge> connectivityInspector = new ConnectivityInspector<>(graph);
        
        
        
        List<Set<Resource>> connectedSets = connectivityInspector.connectedSets();
        Set<Resource> vertexSet = graph.vertexSet();
        
        nonTaxComponents = connectedSets.size();
        nonTaxVertices = vertexSet.size();
        
        //System.out.println("nonTaxComponents=" + nonTaxComponents);
        //System.out.println("nonTaxVertices=" + nonTaxVertices);
        
        //best is one connected Set, worst is vertexSet.size() connected sets
        double disconnection;
        if(vertexSet.size() <= 1) {
            disconnection = 1.0;
        } else {
            disconnection = (connectedSets.size() - 1) / (double) (vertexSet.size() - 1);
        }
        nonTaxConnectedness = 1.0 - disconnection;
        //System.out.println("nonTaxConnectedness=" + nonTaxConnectedness);
        
        nonTaxDiameter = 0;
        nonTaxCloseness = 0;
        
        List<Double> nonTaxDiameterList = new ArrayList<>();
        List<Double> nonTaxClosenessList = new ArrayList<>();
        for(Set<Resource> connectedSet : connectedSets) {
            
            AsSubgraph<Resource, PropertyEdge> subgraph = new AsSubgraph<>(graph, connectedSet);
            
            GraphMeasurer<Resource, PropertyEdge> graphMeasurer = new GraphMeasurer<>(subgraph);
        
            //longest shortest path
            double nonTaxDiameterLocal = graphMeasurer.getDiameter();
            //System.out.println("nonTaxDiameter=" + nonTaxDiameter);

            if(nonTaxDiameterLocal != 0 && nonTaxDiameterLocal != Double.POSITIVE_INFINITY) {
                //a string of nodes
                double worstDiameter = graph.vertexSet().size() - 1;
                double bestDiameter = 2.0; //maybe we use 2.0 to be realistic

                double nonTaxClosenessLocal = 1.0 - ((nonTaxDiameterLocal - bestDiameter) / (worstDiameter - bestDiameter));
                //System.out.println("nonTaxCloseness=" + nonTaxCloseness);
                
                if(!Double.isNaN(nonTaxClosenessLocal)) {
                    
                    if(nonTaxClosenessLocal > 1) {
                        nonTaxClosenessLocal = 1;
                    }
                    
                    nonTaxClosenessList.add(nonTaxClosenessLocal);
                }
            }

            if(nonTaxDiameterLocal == Double.POSITIVE_INFINITY) {
                nonTaxDiameterLocal = -1;
            }
            
            if(nonTaxDiameterLocal > 0) {
                nonTaxDiameterList.add(nonTaxDiameterLocal);
            }
        }
        
        OptionalDouble closenessOpt = nonTaxClosenessList.stream().mapToDouble(d -> d).average();
        OptionalDouble diameterOpt = nonTaxDiameterList.stream().mapToDouble(d -> d).average();
        
        if(closenessOpt.isPresent()) {
            nonTaxCloseness = closenessOpt.getAsDouble();
        }
        if(diameterOpt.isPresent()) {
            nonTaxDiameter = diameterOpt.getAsDouble();
        }
        
        nonTaxonomicMetric = (nonTaxConnectedness + nonTaxCloseness) / 2.0;
    }
    
    public void overallRating() {
        
        List<Double> values = new ArrayList<>();
        values.add(conceptDiscoveryMetric);
        values.add(ontologyPopulationMetric);
        values.add(taxonomyMetric);
        values.add(nonTaxonomicMetric);
        //values.add(taxGeneralization);
        //values.add(taxConnectedness);
        //values.add(nonTaxConnectedness);
        //values.add(nonTaxCloseness);
        
        overallRating = values.stream().mapToDouble(d -> d).average().getAsDouble();
    }
    
    public void effortStatistics() {
        effortClick = 0;
        effortKeydown = 0;
        effortInteractions = 0;
        
        File file = new File(settings.getOutputFolder(), "interactions.jsonl.gz");
        if(!file.exists())
            return;
        
        try { 
            BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file)), StandardCharsets.UTF_8));
            String line;
            while((line = br.readLine()) != null) {
                if(line.contains("\"type\":\"click\"")) {
                    effortClick++;
                } else if(line.contains("\"type\":\"keydown\"")) {
                    effortKeydown++;
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        
        effortInteractions = effortClick + effortKeydown;
    }
    
    public void productivityStatistics() {
        if(effortInteractions > 0) {
            productivity = overallRating / effortInteractions;
        }
    }
    
    public void memoryStatistics() {
        Runtime instance = Runtime.getRuntime();

        StringBuilder sb = new StringBuilder();

        long t = instance.totalMemory();
        long f = instance.freeMemory();
        long u = t - f;
        long m = instance.maxMemory();
        
        this.memoryTotal = t;
        this.memoryUsed = u;
        this.memoryMax = m;
    }
    
    public void chartData() {
        chartData = new JSONObject();
        JSONArray datasets = new JSONArray();
        chartData.put("datasets", datasets);
     
        File file = new File(settings.getOutputFolder(), "status.jsonl.gz");
        if(!file.exists())
            return;
        
        JSONObject dataset = new JSONObject();
        datasets.put(dataset);
        dataset.put("label", "Fortschritt"); //TODO german/english language
        dataset.put("borderColor", "#007bff");
        
        dataset.put("backgroundColor", "#007bff");
        //dataset.put("fill", "start");
        
        JSONArray data = new JSONArray();
        dataset.put("data", data);
        
        JSONArray labels = new JSONArray();
        chartData.put("labels", labels);
        
        double lastX = -1;
        try { 
            BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file)), StandardCharsets.UTF_8));
            String line;
            while((line = br.readLine()) != null) {
                JSONObject entry = new JSONObject(line);
                
                double x = entry.getDouble("effortInteractions");
                
                if(x > lastX) {
                    labels.put(x);
                    
                    data.put(entry.getDouble("overallRating") * 100); //to have percent
                    
                    lastX = x;
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private Map<String, Object> stats(List<Assertion> assertions) {
        Map<String, Object> m = new HashMap<>();
        
        m.put("total", (double) assertions.size());
        
        int niCount = 0;
        int aiCount = 0;
        
        int posCount = 0;
        int negCount = 0;
        int undCount = 0;
        
        MinAvgMaxSdDouble strLengthStat = new MinAvgMaxSdDouble();
        
        Set<Resource> distinctSubjects = new HashSet<>();
        Set<RDFNode> distinctObjects = new HashSet<>();
        
        for(Assertion assertion : assertions) {
            
            Intelligence intel = assertion.getIntelligence();
            switch(intel) {
                case AI: aiCount++; break;
                case NI: niCount++; break;
            }
            
            Rating rating = assertion.getRating();
            switch(rating) {
                case Positive: posCount++; break;
                case Negative: negCount++; break;
                case Undecided: undCount++; break;
            }
            
            if(assertion.getStatement().getObject().isLiteral()) {
                strLengthStat.add(assertion.getStatement().getString().length());
                
            }
            
            distinctSubjects.add(assertion.getSubject());
            distinctObjects.add(assertion.getStatement().getObject());
        }
        
        m.put("natural", niCount);
        m.put("artifical", aiCount);
        
        m.put("distinctSubjects", distinctSubjects.size());
        m.put("distinctObjects", distinctObjects.size());
        
        double totalCount = posCount + negCount + undCount;
        
        m.put("positive", posCount);
        m.put("positiveRate", posCount / totalCount);
        
        m.put("negative", negCount);
        m.put("negativeRate", negCount / totalCount);
        
        m.put("undecided", undCount);
        m.put("undecidedRate", undCount / totalCount);
        
        m.put("stringLength", strLengthStat.toMap());
        
        return m;
    }
    
    //JSON
    
    public void fillJSON(JSONObject result) {
        result.put("leafCount", getLeafCount());
        result.put("branchCount", getBranchCount());
        
        result.put("positiveConcepts", getPositiveConcepts());
        result.put("positiveTerms", getPositiveTerms());
        result.put("positiveTopics", getPositiveTopics());
        result.put("positiveTypes", getPositiveTypes());
        
        if(conceptDiscoveryNextFileResource != null) {
            JSONObject conceptDiscoveryNextFileObj = new JSONObject();
            conceptDiscoveryNextFileObj.put("uri", conceptDiscoveryNextFileResource.getURI());
            conceptDiscoveryNextFileObj.put("name", conceptDiscoveryNextFile.getName());
            conceptDiscoveryNextFileObj.put("isFile", !conceptDiscoveryNextFile.isDirectory());
            result.put("conceptDiscoveryNextFile", conceptDiscoveryNextFileObj);
        }
        
        result.put("ontologyPopulationMetric", getOntologyPopulationMetric());
        result.put("conceptDiscoveryMetric", getConceptDiscoveryMetric());
        result.put("taxonomyMetric", getTaxonomyMetric());
        result.put("nonTaxonomicMetric", getNonTaxonomicMetric());
        
        result.put("taxBroader", getTaxBroader());
        result.put("taxComponents", getTaxComponents());
        result.put("taxConcepts", getTaxConcepts());
        result.put("taxConnectedness", getTaxConnectedness());
        result.put("taxGeneralization", getTaxGeneralization());
        result.put("taxVertices", getTaxVertices());
        
        result.put("nonTaxComponents", getNonTaxComponents());
        result.put("nonTaxVertices", getNonTaxVertices());
        result.put("nonTaxConnectedness", getNonTaxConnectedness());
        result.put("nonTaxDiameter", getNonTaxDiameter());
        result.put("nonTaxCloseness", getNonTaxCloseness());
        
        result.put("effortClick", getEffortClick());
        result.put("effortKeydown", getEffortKeydown());
        result.put("effortInteractions", getEffortInteractions());
        
        result.put("memoryTotalBytes", getMemoryTotal());
        result.put("memoryUsedBytes", getMemoryUsed());
        result.put("memoryMaxBytes", getMemoryMax());
        result.put("memoryTotal", MemoryUtility.humanReadableByteCount(getMemoryTotal()));
        result.put("memoryUsed", MemoryUtility.humanReadableByteCount(getMemoryUsed()));
        result.put("memoryMax", MemoryUtility.humanReadableByteCount(getMemoryMax()));
        
        result.put("overallRating", getOverallRating());
        
        result.put("productivity", getProductivity());
        
        result.put("duration", getDuration());
        
        result.put("data", getChartData());
    }
    
    
    //get RDF
    
    //classes and properties
    public Model getTerminologyModel() {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefixes(PrefixMapping.Standard);
        model.setNsPrefix("skos", SKOS.uri);
        
        for(Assertion a : assertionPool.getAssertions(null, RDF.type, RDFS.Class, Phase.OntologyPopulation, null, null, Rating.Positive, 0)) {
            model.add(a.getStatement());
            for(Assertion b : assertionPool.getAssertions(a.getSubject(), null, null, null, null, null, Rating.Positive, 0)) {
                model.add(b.getStatement());
            }
        }
        
        for(Assertion a : assertionPool.getAssertions(null, RDF.type, RDF.Property, Phase.OntologyPopulation, null, null, Rating.Positive, 0)) {
            model.add(a.getStatement());
            for(Assertion b : assertionPool.getAssertions(a.getSubject(), null, null, null, null, null, Rating.Positive, 0)) {
                model.add(b.getStatement());
            }
        }
        
        return model;
    }
    
    //concepts, their types, taxonomy, non-taxonomic relations 
    public Model getAssertionModel() {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefixes(PrefixMapping.Standard);
        model.setNsPrefix("skos", SKOS.uri);
        
        //concepts
        for(Assertion a : assertionPool.getAssertions(null, RDF.type, ConceptDiscovery.DEFAULT_TYPE, Phase.ConceptDiscovery, null, null, Rating.Positive, 0)) {
            model.add(a.getStatement());
            //all about them
            for(Assertion b : assertionPool.getAssertions(a.getSubject(), null, null, null, null, null, Rating.Positive, 0)) {
                model.add(b.getStatement());
            }
        }
        
        for(Assertion a : assertionPool.getAssertions(null, null, null, Phase.ConceptHierarchyDerivation, null, null, Rating.Positive, 0)) {
            model.add(a.getStatement());
        }
        for(Assertion a : assertionPool.getAssertions(null, null, null, Phase.NonTaxonomicRelationLearning, null, null, Rating.Positive, 0)) {
            model.add(a.getStatement());
        }
        
        return model;
    }
    
    //file to concept mapping
    public Model getTopicModel() {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefixes(PrefixMapping.Standard);
        model.setNsPrefix("skos", SKOS.uri);
        model.setNsPrefix("dot", DOT.NS);
        model.setNsPrefix("foaf", FOAF.NS);
        model.setNsPrefix("nfo", NFO.NS);
        
        for(Assertion a : assertionPool.getAssertions(null, FOAF.topic, null, Phase.ConceptDiscovery, null, null, Rating.Positive, 0)) {
            model.add(a.getStatement());
            
            FileInfo fi = (FileInfo) fileInfoStorage.get(KecsUtils.getId(a.getSubject().getURI()));
            
            model.add(a.getSubject(), RDF.type, fi.isDirectory() ? NFO.Folder : NFO.LocalFileDataObject);
            model.add(a.getSubject(), SKOS.prefLabel, fi.getName());
            model.add(a.getSubject(), DOT.filePath, fi.getPath());
        }
        
        return model;
    }
    
    
    //all getter
    
    
    public long getBranchCount() {
        return branchCount;
    }

    public long getLeafCount() {
        return leafCount;
    }

    public int getPositiveTopics() {
        return positiveTopics;
    }

    public int getPositiveTerms() {
        return positiveTerms;
    }

    public Resource getConceptDiscoveryNextFileResource() {
        return conceptDiscoveryNextFileResource;
    }

    public FileInfo getConceptDiscoveryNextFile() {
        return conceptDiscoveryNextFile;
    }
    
    public double getOntologyPopulationMetric() {
        return ontologyPopulationMetric;
    }

    public int getPositiveTypes() {
        return positiveTypes;
    }

    public int getPositiveConcepts() {
        return positiveConcepts;
    }

    public int getNonTaxComponents() {
        return nonTaxComponents;
    }

    public int getNonTaxVertices() {
        return nonTaxVertices;
    }

    public double getNonTaxConnectedness() {
        return nonTaxConnectedness;
    }

    public double getNonTaxDiameter() {
        return nonTaxDiameter;
    }

    public double getNonTaxCloseness() {
        return nonTaxCloseness;
    }

    public double getNonTaxonomicMetric() {
        return nonTaxonomicMetric;
    }
    
    public double getConceptDiscoveryMetric() {
        return conceptDiscoveryMetric;
    }

    public int getTaxComponents() {
        return taxComponents;
    }

    public int getTaxVertices() {
        return taxVertices;
    }

    public int getTaxConcepts() {
        return taxConcepts;
    }

    public int getTaxBroader() {
        return taxBroader;
    }

    public double getTaxGeneralization() {
        return taxGeneralization;
    }

    public double getTaxConnectedness() {
        return taxConnectedness;
    }

    public double getTaxonomyMetric() {
        return taxonomyMetric;
    }
    
    public int getEffortClick() {
        return effortClick;
    }

    public int getEffortKeydown() {
        return effortKeydown;
    }

    public int getEffortInteractions() {
        return effortInteractions;
    }
    
    public double getOverallRating() {
        return overallRating;
    }

    public long getDuration() {
        return duration;
    }

    public JSONObject getChartData() {
        return chartData;
    }

    public double getProductivity() {
        return productivity;
    }

    public long getMemoryUsed() {
        return memoryUsed;
    }

    public long getMemoryTotal() {
        return memoryTotal;
    }

    public long getMemoryMax() {
        return memoryMax;
    }
    
}
