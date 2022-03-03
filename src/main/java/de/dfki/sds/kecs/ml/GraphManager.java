package de.dfki.sds.kecs.ml;

import de.dfki.sds.hephaistos.storage.StorageItem;
import de.dfki.sds.hephaistos.storage.assertion.Assertion;
import de.dfki.sds.hephaistos.storage.assertion.AssertionPool;
import de.dfki.sds.hephaistos.storage.assertion.Intelligence;
import de.dfki.sds.hephaistos.storage.assertion.Phase;
import de.dfki.sds.hephaistos.storage.assertion.Rating;
import de.dfki.sds.hephaistos.storage.file.FileInfo;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import de.dfki.sds.hephaistos.storage.file.FolderInfo;
import de.dfki.sds.kecs.modules.ConceptDiscovery;
import de.dfki.sds.kecs.modules.OntologyPopulation;
import de.dfki.sds.kecs.util.KecsUtils;
import de.dfki.sds.kecs.util.Prediction;
import de.dfki.sds.kecs.util.PropertyEdge;
import de.dfki.sds.mschroeder.commons.lang.math.MinAvgMaxSdDouble;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.math3.ml.distance.*;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.LinkPredictionAlgorithm;
import org.jgrapht.alg.linkprediction.AdamicAdarIndexLinkPrediction;
import org.jgrapht.alg.linkprediction.CommonNeighborsLinkPrediction;
import org.jgrapht.alg.linkprediction.HubDepressedIndexLinkPrediction;
import org.jgrapht.alg.linkprediction.HubPromotedIndexLinkPrediction;
import org.jgrapht.alg.linkprediction.JaccardCoefficientLinkPrediction;
import org.jgrapht.alg.linkprediction.LeichtHolmeNewmanIndexLinkPrediction;
import org.jgrapht.alg.linkprediction.PreferentialAttachmentLinkPrediction;
import org.jgrapht.alg.linkprediction.ResourceAllocationIndexLinkPrediction;
import org.jgrapht.alg.linkprediction.SaltonIndexLinkPrediction;
import org.jgrapht.alg.linkprediction.SørensenIndexLinkPrediction;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.AttributeType;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.graphml.GraphMLExporter;
import org.jgrapht.traverse.BreadthFirstIterator;

/**
 *
 */
public class GraphManager {

    public static final Resource NEGATIVE_CLASS = ResourceFactory.createResource("urn:ml:negative");
    public static final Resource MISSING_CLASS = ResourceFactory.createResource("urn:ml:missing");

    private DefaultUndirectedGraph<FileNode, DefaultEdge> cachedFileNodeGraph;

    //this is the parent-child version
    public DefaultUndirectedGraph<FileNode, DefaultEdge> loadCachedFileNodeGraph(FileInfoStorage fileInfoStorage) {

        if (cachedFileNodeGraph != null) {
            return cachedFileNodeGraph;
        }

        long begin = System.currentTimeMillis();

        DefaultUndirectedGraph<FileNode, DefaultEdge> graph = new DefaultUndirectedGraph<>(DefaultEdge.class);

        FolderInfo root = fileInfoStorage.getRoot();

        List<StorageItem> tree = fileInfoStorage.getTree(root);

        Map<Integer, FileNode> id2filenode = new HashMap<>();
        for (StorageItem item : tree) {
            FileInfo fi = (FileInfo) item;

            FileNode node = new FileNode();
            node.setName(fi.getName());

            if (fi.getMeta() != null) {
                node.setFile(KecsUtils.getResource(fi));
            }

            graph.addVertex(node);

            id2filenode.put(fi.getId(), node);
        }

        for (StorageItem item : tree) {
            FileInfo fi = (FileInfo) item;

            if (fi.getParent() == root.getId()) {
                continue;
            }

            graph.addEdge(id2filenode.get(fi.getParent()), id2filenode.get(fi.getId()));
        }

        long end = System.currentTimeMillis();

        //graph 121747 vertices and 121746 edges took 1509 ms
        //System.out.println("cached FileNodeGraph " + graph.vertexSet().size() + " vertices and " + graph.edgeSet().size() + " edges took " + (end - begin) + " ms");

        cachedFileNodeGraph = graph;

        return graph;
    }

    //this version also adds the siblings of a file node
    //so connected nodes are: one parent, siblings, children
    public DefaultUndirectedGraph<FileNode, DefaultEdge> loadCachedFileNodeGraphSiblings(FileInfoStorage fileInfoStorage, int nonTaxonomicTimeout, int nonTaxonomicDepthThreshold) {

        if (cachedFileNodeGraph != null) {
            return cachedFileNodeGraph;
        }

        //long begin = System.currentTimeMillis();

        DefaultUndirectedGraph<FileNode, DefaultEdge> graph = new DefaultUndirectedGraph<>(DefaultEdge.class);
        
        FolderInfo root = fileInfoStorage.getRoot();

        List<StorageItem> tree = fileInfoStorage.getTree(root);
        
        //because fileInfoStorage.get*Children takes too long
        Map<Integer, List<Integer>> fi2children = new HashMap<>();
        for(StorageItem item : tree) {
            FileInfo fi = (FileInfo) item;
            fi2children.computeIfAbsent(fi.getParent(), f -> new ArrayList<>()).add(fi.getId());
        }

        Map<Integer, FileNode> id2filenode = new HashMap<>();
        for (StorageItem item : tree) {
            FileInfo fi = (FileInfo) item;

            FileNode node = new FileNode();
            node.setName(fi.getName());

            if (fi.getMeta() != null) {
                node.setFile(KecsUtils.getResource(fi));
            }

            graph.addVertex(node);

            id2filenode.put(fi.getId(), node);
        }

        /*
        MinAvgMaxSdDouble depthStat = new MinAvgMaxSdDouble();
        for (StorageItem item : tree) {
            FileInfo fi = (FileInfo) item;
            depthStat.add(getDepth(fi));
        }
        */
        
        //System.out.println(depthStat);
        //System.out.println(depthStat.getHistogram());
        
        long begin = System.currentTimeMillis();
        boolean inTimeout = false;
        MinAvgMaxSdDouble skippedStat = new MinAvgMaxSdDouble();
        
        //addEdge takes time
        
        for (StorageItem item : tree) {
            FileInfo fi = (FileInfo) item;
            
            if (fi.getParent() == root.getId()) {
                continue;
            }
            
            //optimize
            if(nonTaxonomicTimeout > 0) {
                long now = System.currentTimeMillis();
                if((now - begin) > nonTaxonomicTimeout) {
                    inTimeout = true;
                    int d = getDepth(fi);
                    if(d > nonTaxonomicDepthThreshold) {
                        skippedStat.add(d);
                        continue;
                    }
                }
            }
            
            FileNode current = id2filenode.get(fi.getId());

            graph.addEdge(id2filenode.get(fi.getParent()), current);
            
            List<Integer> children = fi2children.get(fi.getParent());
            if(children != null) {
                for(Integer childId : children) {
                    FileNode siblingNode = id2filenode.get(childId);
                
                    if(current != siblingNode) {
                        graph.addEdge(current, siblingNode);
                    }
                }
            }
        }

        //long end = System.currentTimeMillis();

        //graph 121747 vertices and 121746 edges took 1509 ms
        //System.out.println("cached FileNodeGraph (sibling version) " + graph.vertexSet().size() + " vertices and " + graph.edgeSet().size() + " edges took " + (end - begin) + " ms");

        long end = System.currentTimeMillis();
        
        System.out.println("took: " + (end - begin) + " ms, timeout: "+ nonTaxonomicTimeout +" ms, depth threshold: "+ nonTaxonomicDepthThreshold +", in timeout: " + inTimeout + ", skipped: " + skippedStat);
        if(skippedStat.getCount() > 0) {
            System.out.println(skippedStat.getHistogram());
        }
        
        cachedFileNodeGraph = graph;

        return graph;
    }
    
    private int getDepth(FileInfo fi) {
        int d1 = fi.getPath().split("\\\\").length;
        int d2 = fi.getPath().split("/").length;
        return Math.max(d1, d2);
    }
    
    public DefaultUndirectedGraph<FileNode, DefaultEdge> getCachedFileNodeGraph() {
        return cachedFileNodeGraph;
    }

    public void updateCachedFileNodeGraph(List<Assertion> assertions) {

        if (cachedFileNodeGraph == null) {
            return;
        }

        //faster access
        Map<Resource, Set<Resource>> file2topics = new HashMap<>();
        for (Assertion assertion : assertions) {
            file2topics
                    .computeIfAbsent(assertion.getSubject(), f -> new HashSet<>())
                    .add(assertion.getObject());
        }

        //just update every node with topics in the file node graph 
        for (FileNode fn : cachedFileNodeGraph.vertexSet()) {
            fn.getTopics().clear();

            Set<Resource> topics = file2topics.get(fn.getFile());
            if (topics != null) {
                fn.getTopics().addAll(topics);
            }
        }
    }

    //takes long in mirjam case
    public DefaultUndirectedWeightedGraph<Resource, DefaultWeightedEdge> getTopicGraph(Graph<FileNode, DefaultEdge> fileNodeGraph, int maxDepth) {

        long begin = System.currentTimeMillis();

        DefaultUndirectedWeightedGraph<Resource, DefaultWeightedEdge> topicGraph = new DefaultUndirectedWeightedGraph<>(DefaultWeightedEdge.class);

        for (FileNode vertex : fileNodeGraph.vertexSet()) {

            if (vertex.getTopics().isEmpty()) {
                continue;
            }

            List<Resource> leftList = new ArrayList<>(vertex.getTopics());

            BreadthFirstIterator<FileNode, DefaultEdge> bfs = new BreadthFirstIterator<>(fileNodeGraph, vertex);

            while (bfs.hasNext()) {

                //next can take time
                FileNode f = bfs.next();

                int depth = bfs.getDepth(f);
                if (depth > maxDepth) {
                    break;
                }

                //high weight if distance low
                //gephi does not like zero weights, that is why: 1.0
                double weight = 1.0 + maxDepth - depth;

                List<Resource> rightList = new ArrayList<>(f.getTopics());

                for (int i = 0; i < leftList.size(); i++) {
                    for (int j = 0; j < rightList.size(); j++) {

                        Resource left = leftList.get(i);
                        Resource right = rightList.get(j);
                        
                        //no self-ref
                        if(left.equals(right)) {
                            continue;
                        }
                        
                        //System.out.println(left + " -> " + right);

                        topicGraph.addVertex(left);
                        topicGraph.addVertex(right);

                        DefaultWeightedEdge edge = topicGraph.getEdge(left, right);
                        if (edge != null) {
                            //if edge exists check if the distance is smaller
                            double edgeWeight = topicGraph.getEdgeWeight(edge);

                            //if higher weight (= distance low), update it
                            if (weight > edgeWeight) {
                                topicGraph.setEdgeWeight(edge, weight);
                            }

                        } else {
                            DefaultWeightedEdge newEdge = topicGraph.addEdge(left, right);
                            topicGraph.setEdgeWeight(newEdge, weight);
                        }

                    }
                }
            }
        }
        long end = System.currentTimeMillis();

        //System.out.println("TopicGraph maxDepth=" + maxDepth + ", " + topicGraph.vertexSet().size() + " vertices and " + topicGraph.edgeSet().size() + " edges took " + (end - begin) + " ms");

        return topicGraph;
    }

    //maxDepth is always 1
    public DefaultUndirectedWeightedGraph<Resource, DefaultWeightedEdge> getTopicGraphV2(Graph<FileNode, DefaultEdge> fileNodeGraph) {

        long begin = System.currentTimeMillis();

        DefaultUndirectedWeightedGraph<Resource, DefaultWeightedEdge> topicGraph = new DefaultUndirectedWeightedGraph<>(DefaultWeightedEdge.class);

        for (FileNode vertex : fileNodeGraph.vertexSet()) {

            if (vertex.getTopics().isEmpty()) {
                continue;
            }

            List<Resource> leftList = new ArrayList<>(vertex.getTopics());

            List<FileNode> adjacent = new ArrayList<>();
            adjacent.add(vertex);
            
            for(DefaultEdge edge : fileNodeGraph.edgesOf(vertex)) {
                
                FileNode src = fileNodeGraph.getEdgeSource(edge);
                if(!src.equals(vertex)) {
                    adjacent.add(src);
                } else {
                    adjacent.add(fileNodeGraph.getEdgeTarget(edge));
                }
            }
            
            for(FileNode f : adjacent) {
                
                double depth = f.equals(vertex) ? 0 : 1;
                double weight = 2.0 - depth;
                
                List<Resource> rightList = new ArrayList<>(f.getTopics());

                for (int i = 0; i < leftList.size(); i++) {
                    for (int j = 0; j < rightList.size(); j++) {

                        Resource left = leftList.get(i);
                        Resource right = rightList.get(j);
                        
                        //no self-ref
                        if(left.equals(right)) {
                            continue;
                        }
                        
                        //System.out.println(left + " -> " + right);

                        topicGraph.addVertex(left);
                        topicGraph.addVertex(right);

                        DefaultWeightedEdge edge = topicGraph.getEdge(left, right);
                        if (edge != null) {
                            //if edge exists check if the distance is smaller
                            double edgeWeight = topicGraph.getEdgeWeight(edge);

                            //if higher weight (= distance low), update it
                            if (weight > edgeWeight) {
                                topicGraph.setEdgeWeight(edge, weight);
                            }

                        } else {
                            DefaultWeightedEdge newEdge = topicGraph.addEdge(left, right);
                            topicGraph.setEdgeWeight(newEdge, weight);
                        }

                    }
                }
            }
        }
        long end = System.currentTimeMillis();

        //System.out.println("\tTopicGraphV2 " + topicGraph.vertexSet().size() + " vertices and " + topicGraph.edgeSet().size() + " edges took " + (end - begin) + " ms");

        return topicGraph;
    }
    
    public DefaultUndirectedGraph<Resource, PropertyEdge> getNonTaxonomicGraph(AssertionPool pool) {

        DefaultUndirectedGraph<Resource, PropertyEdge> graph = new DefaultUndirectedGraph<>(PropertyEdge.class);
        
        //TODO yeah, here we use the "Jahr" label to get the time related type to filter it
        Map<Resource, String> type2prefLbl = pool.getTypePrefLabelMap();
        Optional<Resource> yearTypeOpt = type2prefLbl.entrySet().stream().filter(e -> e.getValue().equals("Jahr")).map(e -> e.getKey()).findFirst();
        
        List<Assertion> concepts = pool.getAssertions(null, RDF.type, ConceptDiscovery.DEFAULT_TYPE, Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
        for(Assertion conceptAssertion : concepts) {
            //needs a type from ontology population phase
            List<Assertion> typeAssertions = pool.getAssertions(conceptAssertion.getSubject(), RDF.type, null, Phase.OntologyPopulation, Intelligence.NI, null, Rating.Positive, 0);
            
            if(!typeAssertions.isEmpty()) {
                
                Resource type = typeAssertions.get(0).getObject();
                
                //here are also skos:Concepts
                //but we do not want it for the non-taxonamic check
                if(!type.equals(OntologyPopulation.CONCEPT_TYPE)) {
                    
                    //here are also concepts representing years
                    //but we do not want to have time related concepts in the calculation
                    if(!yearTypeOpt.isPresent() || !type.equals(yearTypeOpt.get())) {
                        graph.addVertex(conceptAssertion.getSubject());
                    } 
                }
            }
        }
        
        //only from NI
        List<Assertion> relations = pool.getAssertions(null, null, null, Phase.NonTaxonomicRelationLearning, Intelligence.NI, null, Rating.Positive, 0);
        for(Assertion relationAssertion : relations) {
            
            if(graph.containsVertex(relationAssertion.getSubject()) && graph.containsVertex(relationAssertion.getObject())) {
                PropertyEdge propertyEdge = new PropertyEdge(relationAssertion.getStatement().getPredicate());
                graph.addEdge(relationAssertion.getSubject(), relationAssertion.getObject(), propertyEdge);
            }
        }
        
        return graph;
    }
    
    public DefaultUndirectedGraph<Resource, DefaultEdge> getTaxonomyGraph(AssertionPool pool) {

        DefaultUndirectedGraph<Resource, DefaultEdge> graph = new DefaultUndirectedGraph<>(DefaultEdge.class);
        
        //all skos:Concepts are vertices
        List<Assertion> concepts = pool.getAssertions(null, RDF.type, OntologyPopulation.CONCEPT_TYPE, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
        for(Assertion conceptAssertion : concepts) {
            
            boolean exists = !pool.getAssertions(conceptAssertion.getSubject(), RDF.type, ConceptDiscovery.DEFAULT_TYPE, Phase.ConceptDiscovery, 
                    null, null, Rating.Positive, 0).isEmpty();
            
            if(exists) {
                graph.addVertex(conceptAssertion.getSubject());
            }
        }
        
        //only from NI
        List<Assertion> relations = pool.getAssertions(null, SKOS.broader, null, Phase.ConceptHierarchyDerivation, Intelligence.NI, null, Rating.Positive, 0);
        for(Assertion relationAssertion : relations) {
            
            if(graph.containsVertex(relationAssertion.getSubject()) && graph.containsVertex(relationAssertion.getObject())) {
                graph.addEdge(relationAssertion.getSubject(), relationAssertion.getObject());
            }
        }
        
        return graph;
    }
    
    public Datasets linkPrediction(
            DefaultUndirectedWeightedGraph<Resource, DefaultWeightedEdge> topicGraph,
            List<LinkPredictionAlgorithm<Resource, DefaultWeightedEdge>> algorithms,
            List<Assertion> assertions,
            List<Statement> toTest) {

        Datasets datasets = new Datasets();

        //we store what assertions are from human so that it will not be in testset
        Map<Statement, Assertion> stmtAssertionMap = new HashMap<>();
        for (Assertion assertion : assertions) {
            stmtAssertionMap.put(
                    assertion.getStatement(),
                    assertion
            );
        }

        int numPos = 0;
        int numNeg = 0;
        int numUnd = 0;

        Set<String> classes = new HashSet<>();

        //train set consists of the given assertions
        for (Assertion assertion : assertions) {

            Object[] record = new Object[algorithms.size() + 1];

            int index = 0;

            //features
            for (LinkPredictionAlgorithm<Resource, DefaultWeightedEdge> algo : algorithms) {

                double score = 0;
                try {
                    score = algo.predict(assertion.getSubject(), assertion.getObject());
                } catch (Exception e) {
                    //AdamicAdarIndexLinkPrediction
                    //LinkPredictionIndexNotWellDefinedException: 
                    //Vertex has less than 2 degree

                    //ignore: score is then 0 (no signal)
                }

                record[index] = score;
                index++;
            }
            
            //a record where every score is 0 has no signal
            //do not add to dataset
            if(isZero(record)) {
                continue;
            }

            switch (assertion.getRating()) {
                case Positive:
                    record[record.length - 1] = assertion.getStatement().getPredicate().getURI();
                    numPos++;
                    break;
                case Negative:
                    record[record.length - 1] = NEGATIVE_CLASS.getURI();
                    numNeg++;
                    break;
                case Undecided:
                    record[record.length - 1] = NEGATIVE_CLASS.getURI();
                    numUnd++;
                    break;
            }

            classes.add((String) record[record.length - 1]);

            //to know what the resources are for a given record java object
            datasets.getRecord2stmt().put(record, assertion.getStatement());
            
            datasets.getTrainSet().add(record);
        }

        //test set consists of all non-asserted edges
        //all edges we have (some are positive, some are negative)
        //for (DefaultWeightedEdge edge : topicGraph.edgeSet()) {
        //Resource src = topicGraph.getEdgeSource(edge);
        //Resource trg = topicGraph.getEdgeTarget(edge);
        for (Statement testStmt : toTest) {
            Resource src = testStmt.getSubject();
            Resource trg = testStmt.getResource();

            //Set<Resource> unorderedPair = new HashSet<>(Arrays.asList(src, trg));

            //already in train set (so NI said something to it)
            if (stmtAssertionMap.containsKey(testStmt)) {
                continue;
            }

            Object[] record = new Object[algorithms.size() + 1];

            //features
            int index = 0;
            for (LinkPredictionAlgorithm<Resource, DefaultWeightedEdge> algo : algorithms) {

                double score = 0;
                try {
                    score = algo.predict(src, trg);
                } catch (Exception e) {
                    //AdamicAdarIndexLinkPrediction
                    //LinkPredictionIndexNotWellDefinedException: 
                    //Vertex has less than 2 degree

                    //ignore: score is then 0 (no signal)
                }

                record[index] = score;
                index++;
            }

            //a record where every score is 0 has no signal
            //do not add to dataset
            if(isZero(record)) {
                continue;
            }
            
            //to know what the resources are for a given record java object
            datasets.getRecord2stmt().put(record, testStmt);

            //missing because it is test set
            record[record.length - 1] = MISSING_CLASS.getURI();

            classes.add((String) record[record.length - 1]);

            datasets.getTestSet().add(record);
        }

        //System.out.println(numAssertions + " cases / " + topicGraph.edgeSet().size() + " all edges");
        //System.out.println(assertions.size() + " non-taxonomic assertions");
        //System.out.println(numPos + " positive");
        //System.out.println(numNeg + " negative");
        //System.out.println(numUnd + " undecided");

        //System.out.println(datasets.getTrainSet().size() + " trainset size");
        //System.out.println(datasets.getTestSet().size() + " testset size vs " + toTest.size() + " all potential");
        
        List<String> classLabels = new ArrayList<>(classes);
        classLabels.sort((a, b) -> a.compareTo(b));
        datasets.setClassLabels(classLabels);

        return datasets;
    }
    
    public List<Prediction> distanceBasedPrediction(Datasets datasets, DistanceMeasure distMeasure, double distanceThreshold, AssertionPool pool) {
        
        List<Prediction> predictions = new ArrayList<>();
        
        /*
        List<DistanceMeasure> measures = new ArrayList<>();
        measures.add(new CanberraDistance());
        measures.add(new ChebyshevDistance());
        measures.add(new EarthMoversDistance());
        measures.add(new EuclideanDistance());
        measures.add(new ManhattanDistance());
        */
        
        Map<Statement, Double> stmt2dist = new HashMap<>();
        
        for(Object[] trainRecord : datasets.getTrainSet()) {
            
            //we do not use negative examples here
            String clazz = (String) trainRecord[trainRecord.length - 1];
            if(clazz.equals(NEGATIVE_CLASS.getURI())) {
                continue;
            }
            
            Statement trainStmt = datasets.getRecord2stmt().get(trainRecord);
            double[] trainVector =  Datasets.toDoubleArray(trainRecord);
            
            //System.out.println("[train] " + toString(trainStmt, toPrefLabelMap, propertyPrefLblMap) + " " + Arrays.toString(trainRecord));
            //System.out.println(Arrays.toString(trainRecord));
            //System.out.println(Arrays.toString(trainVector));
            
            for(Object[] testRecord : datasets.getTestSet()) {
                
                Statement testStmt = datasets.getRecord2stmt().get(testRecord);
                
                //System.out.println("\t[test] " + toString(testStmt, toPrefLabelMap, propertyPrefLblMap) + " " + Arrays.toString(testRecord));
                
                //predicate must match
                if(!trainStmt.getPredicate().equals(testStmt.getPredicate())) {
                    continue;
                }
                
                double[] testVector = Datasets.toDoubleArray(testRecord);
                
                //Preferential Attachment seems to help if nodes do not share any neighbors
                double dist = distMeasure.compute(trainVector, testVector);
                
                //get the shortest dist of one statement
                Double existingDist = stmt2dist.get(testStmt);
                if(existingDist != null) {
                    if(dist < existingDist) {
                        stmt2dist.put(testStmt, dist);
                    }
                } else {
                    stmt2dist.put(testStmt, dist);
                }
            }
        }
        
        //System.out.println(stmt2dist.size() + " predictions");
        
        //double maximumDistance = stmt2dist.values().stream().mapToDouble(d -> d).max().getAsDouble();
        
        for(Entry<Statement, Double> e : stmt2dist.entrySet()) {
            Prediction prediction = new Prediction();
            prediction.setStatement(e.getKey());
            prediction.setClassLabel(e.getKey().getPredicate().getURI());
            prediction.setDistance(e.getValue());

            predictions.add(prediction);
        }
        
        predictions.sort((a,b) -> Double.compare(a.getDistance(), b.getDistance()));
        
        //print
        //Map<Resource, String> toPrefLabelMap = pool.getConceptPrefLabelMap(ConceptDiscovery.DEFAULT_TYPE, null);
        //Map<Resource, String> propertyPrefLblMap = pool.getPropertyPrefLabelMap();
        //predictions.forEach(p -> System.out.println(toString(p.getStatement(), toPrefLabelMap, propertyPrefLblMap) + " " + p.getDistance() + " / " + distanceThreshold));
        
        predictions.removeIf(p -> p.getDistance() > distanceThreshold);
        
        //System.out.println(predictions.size() + " predictions after distanceThreshold=" + distanceThreshold);
        
        return predictions;
    }
    
    private boolean isZero(Object[] record) {
        //a test vector that has too much zero values is not appropriate to use in prediction
        for(int i = 0; i < record.length; i++) {
            if(record[i] != null && record[i] instanceof Double && (double) record[i] != 0) {
                return false;
            }
        }
        return true;
    }
    
    private String toString(Statement stmt, Map<Resource, String> toPrefLabelMap, Map<Resource, String> propertyPrefLblMap) {
        return Arrays.asList(
                toPrefLabelMap.get(stmt.getSubject()), 
                propertyPrefLblMap.get(stmt.getPredicate()), 
                toPrefLabelMap.get(stmt.getResource())
        ).toString();
    }
    
    public void exportTopicGraph(DefaultUndirectedWeightedGraph<Resource, DefaultWeightedEdge> topicGraph, AssertionPool pool, File graphmlFile) {
        GraphMLExporter<Resource, DefaultWeightedEdge> exporter = new GraphMLExporter();
        //exporter.setExportVertexLabels(true);
        exporter.registerAttribute("uri", GraphMLExporter.AttributeCategory.NODE, AttributeType.STRING);
        exporter.registerAttribute("label", GraphMLExporter.AttributeCategory.NODE, AttributeType.STRING);

        exporter.registerAttribute("distance", GraphMLExporter.AttributeCategory.EDGE, AttributeType.DOUBLE);

        //already exports weight
        exporter.setExportEdgeWeights(true);

        exporter.setVertexAttributeProvider(res -> {
            Map<String, Attribute> m = new HashMap<>();

            m.put("uri", new DefaultAttribute(res, AttributeType.STRING));

            String prefLabel = AssertionPool.getPrefLabelString(pool.getAssertions(res, SKOS.prefLabel, null, Phase.ConceptDiscovery, null, null, Rating.Positive, 0));
            if (prefLabel.isEmpty()) {
                prefLabel = res.getLocalName();
            }

            m.put("label", new DefaultAttribute(prefLabel, AttributeType.STRING));

            return m;
        });

        //exporter.setEdgeAttributeProvider(edge -> {
        //    Map<String, Attribute> m = new HashMap<>();
        //m.put("distance", new DefaultAttribute(topicGraph.getEdgeWeight(edge), AttributeType.DOUBLE));
        //m.put("weight", new DefaultAttribute(4 - topicGraph.getEdgeWeight(edge), AttributeType.DOUBLE));
        //    return m;
        //});
        //System.out.println("export...");
        exporter.exportGraph(topicGraph, graphmlFile);
        //System.out.println("done");
    }

    public static List<LinkPredictionAlgorithm<Resource, DefaultWeightedEdge>> allLinkPredictionAlgorithms(DefaultUndirectedWeightedGraph<Resource, DefaultWeightedEdge> topicGraph) {
        List<LinkPredictionAlgorithm<Resource, DefaultWeightedEdge>> algorithms = new ArrayList<>();
        algorithms.add(new AdamicAdarIndexLinkPrediction<>(topicGraph));
        algorithms.add(new CommonNeighborsLinkPrediction<>(topicGraph));
        algorithms.add(new HubDepressedIndexLinkPrediction<>(topicGraph));
        algorithms.add(new HubPromotedIndexLinkPrediction<>(topicGraph));
        algorithms.add(new JaccardCoefficientLinkPrediction<>(topicGraph));
        algorithms.add(new LeichtHolmeNewmanIndexLinkPrediction<>(topicGraph));
        algorithms.add(new PreferentialAttachmentLinkPrediction<>(topicGraph));
        algorithms.add(new ResourceAllocationIndexLinkPrediction<>(topicGraph));
        algorithms.add(new SaltonIndexLinkPrediction<>(topicGraph));
        algorithms.add(new SørensenIndexLinkPrediction<>(topicGraph));
        return algorithms;
    }

}
