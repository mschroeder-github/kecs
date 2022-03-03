
package de.dfki.sds.kecs.modules;

import de.dfki.sds.hephaistos.storage.assertion.Assertion;
import de.dfki.sds.hephaistos.storage.assertion.AssertionPool;
import de.dfki.sds.hephaistos.storage.assertion.Intelligence;
import de.dfki.sds.hephaistos.storage.assertion.Phase;
import de.dfki.sds.hephaistos.storage.assertion.Rating;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import de.dfki.sds.kecs.KecsApp;
import de.dfki.sds.kecs.KecsSettings;
import de.dfki.sds.kecs.ml.Datasets;
import de.dfki.sds.kecs.ml.FileNode;
import de.dfki.sds.kecs.ml.GraphManager;
import de.dfki.sds.kecs.util.Prediction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.math3.ml.distance.DistanceMeasure;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.LinkPredictionAlgorithm;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 * 
 */
public class NonTaxonomicRelationLearning extends Module {

    private final Phase aiPhase = Phase.NonTaxonomicRelationLearning;
    private final String aiName = "NonTaxonomicRelationLearning";
    
    private GraphManager graphManager;
    private Graph<FileNode, DefaultEdge> fileNodeGraph;
    
    
    private DistanceMeasure distanceMeasure;
    
    public NonTaxonomicRelationLearning() {
        distanceMeasure = new EuclideanDistance();
    }
    
    @Override
    public void bootstrap(FileInfoStorage fileInfoStorage, AssertionPool pool, KecsSettings settings) {
        
    }

    @Override
    public void init(FileInfoStorage fileInfoStorage, AssertionPool pool, KecsSettings settings) {
        this.settings = settings;
        
        graphManager = new GraphManager();
        
        //because this will not change over time we can cache it
        if(settings.getMode() == KecsSettings.Mode.Load || settings.getMode() == KecsSettings.Mode.Demo) {
            
            //takes some time at the beginning
            //takes a long time in mirjam case
            fileNodeGraph = graphManager.loadCachedFileNodeGraphSiblings(fileInfoStorage, 
                    settings.getNonTaxonomicTimeout(), 
                    settings.getNonTaxonomicDepthThreshold()
            );
            System.out.println("\tfileNodeGraph: " + fileNodeGraph.vertexSet().size() + " nodes, " + fileNodeGraph.edgeSet().size() + " edges");

            nonTaxonomicLinkPrediction(pool);
            pool.commit();
        }
    }
    
    @Override
    public void updateOnChanges(FileInfoStorage fileInfoStorage, AssertionPool pool, List<Assertion> changes) {
        
        timeStat("NonTaxonomicRelationLearning", () -> {
        
            boolean humanNonTaxonomyFeedback = !AssertionPool.filter(changes, null, null, null, Phase.NonTaxonomicRelationLearning, Intelligence.NI, null, null, 0).isEmpty();

            /*
            boolean typeAsserted = false;
            for(Assertion typeAssertion : AssertionPool.filter(changes, null, RDF.type, null, Phase.OntologyPopulation, null, null, null, 0)) {
                if(!typeAssertion.getObject().equals(OntologyPopulation.CONCEPT_TYPE)) {
                    typeAsserted = true;
                    break;
                }
            }
            */
            boolean typeAsserted = !AssertionPool.filter(changes, null, RDF.type, null, Phase.OntologyPopulation, null, null, null, 0).isEmpty();

            if(humanNonTaxonomyFeedback || typeAsserted) {
                timeStat("nonTaxonomicLinkPrediction", () -> {
                    nonTaxonomicLinkPrediction(pool);
                });
            }
        });
        
        saveTimeStat();
    }
    
    private void nonTaxonomicLinkPrediction(AssertionPool pool) {
        //System.out.println();
        //System.out.println();
        
        
        //topic relations are used to update the file nodes with topics 
        long begin = System.currentTimeMillis();
        List<Assertion> topicAssertions = pool.getAssertions(
                null, FOAF.topic, null, Phase.ConceptDiscovery, 
                null, null, Rating.Positive, 0
        );
        long end = System.currentTimeMillis();
        //System.out.println("\t" + topicAssertions.size() + " topic assertions: " + (end - begin) + " ms");
        
        
        //maybe do not remove concepts because some relations use skos:Concepts
        boolean removeConcepts = false;
        //remove the topics which are skos:concept
        if(removeConcepts) {
            topicAssertions.removeIf(a -> {

                List<Assertion> typeAssertions = 
                pool.getAssertions(
                    a.getObject(), RDF.type, null, Phase.OntologyPopulation, 
                    null, null, Rating.Positive, 0
                );

                if(typeAssertions.isEmpty())
                    return true;

                //remove if it is a skos:concept
                return typeAssertions.get(0).getObject().equals(OntologyPopulation.CONCEPT_TYPE);
            });
        }
        
        //no topics so nothing to predict
        if(topicAssertions.isEmpty())
            return;
        
        //all NI made relations are used for training
        List<Assertion> assertions = pool.getAssertions(null, null, null, Phase.NonTaxonomicRelationLearning, Intelligence.NI, null, null, 0);
        
        //no assertion so nothing to learn
        if(assertions.isEmpty())
            return;
        
        //System.out.println("\t" + assertions.size() + " non taxonomic relation assertions");
        
        //update filenode graph with topics (could be changed)
        graphManager.updateCachedFileNodeGraph(topicAssertions);
        
        //System.out.println("\tgraphManager.updated");
        
        //less depth means it is more precise but recall is lower: e.g. markus is never connected to SensAI for depth=2
        //if more is directly connected (maxDepth >= 3) then more is predicted that is actually "far away"
        //so keep the topic graph more locally connected
        //TODO magic number, how to build the topic graph
        //depth = 0, current node (topics directly attached on file)
        //depth = 1, parent, siblings, children (adjacent topics of adjacent files)
        //int maxDepth = 1;
        //V2 uses maxDepth 1 (so no BFS)
        DefaultUndirectedWeightedGraph<Resource, DefaultWeightedEdge> topicGraph = graphManager.getTopicGraphV2(fileNodeGraph);
        
        //export
        //graphManager.exportTopicGraph(topicGraph, pool, new File("/home/otaku/tmp/topic.graphml"));
        
        if(topicGraph.vertexSet().isEmpty())
            return;
        
        //System.out.println("\ttopicGraph: " + topicGraph.vertexSet().size() + " nodes, " + topicGraph.edgeSet().size() + " edges");
        
        //TODO which features are the best for our task?
        List<LinkPredictionAlgorithm<Resource, DefaultWeightedEdge>> algos = GraphManager.allLinkPredictionAlgorithms(topicGraph);
        algos.remove(6); //remove PreferentialAttachmentLinkPrediction because it is maybe inappropriate here
        
        //this will create all potentional ones based the type
        //all non-taxonomic relations are used for testing
        List<Statement> toTest = getPotentialRelations(pool);
        //System.out.println("all potential relations = " + toTest.size());
        
        if(toTest.isEmpty())
            return;
        
        //System.out.println("\ttoTest: " + toTest.size() + " statements");
        
        //based on topic graph we calculate link prediction features for
        //a) the asserted ones
        //b) the to be tested ones
        Datasets datasets = graphManager.linkPrediction(topicGraph, algos, assertions, toTest);
        
        //TODO magic number, when is euclidian distance threshold ok?
        //was 1.5 but on mschroeder dataset it causes many false positive
        double distanceThreshold = 1.0;
        List<Prediction> predictions = graphManager.distanceBasedPrediction(datasets, distanceMeasure, distanceThreshold, pool);
        
        //System.out.println("\tprediction: " + predictions.size() + " predictions");
        
        assertPredictions(predictions, pool, distanceThreshold);
    }
    
    private void assertPredictions(List<Prediction> predictions, AssertionPool pool, double distanceThreshold) {
     
        //reset
        for(Assertion assertion : pool.getAssertions(null, null, null, aiPhase, Intelligence.AI, aiName, Rating.Positive, 0)) {
            pool.assertStatement(assertion.getStatement(), aiPhase, Intelligence.AI, aiName, Rating.Negative, 1.0);
        }

        //overwrite new predictions
        for (Prediction pred : predictions) {

            Statement stmt = pred.getStatement();

            Rating rating;
            if (stmt.getPredicate().equals(GraphManager.NEGATIVE_CLASS)) {
                rating = Rating.Negative;
            } else {
                rating = Rating.Positive;
            }

            List<Assertion> stmtAssertions = pool.getAssertions(
                    stmt.getSubject(), stmt.getPredicate(), stmt.getObject(),
                    aiPhase, null, null, null, 0);

            //maximal distance is 0% and minimal (= 0) is 100%
            double confidence = 1.0 - (pred.getDistance() / distanceThreshold);
            
            //System.out.println("\t" + stmt + ", dist: " + pred.getDistance() + " / " + distanceThreshold + ", conf: " + confidence);
            
            //if it does not exist, suggest it
            //if suggested by AI, so we wait for human, but maybe needs to be updated
            if (stmtAssertions.isEmpty() || stmtAssertions.get(0).getIntelligence() == Intelligence.AI) {
                pool.assertStatement(stmt, aiPhase, Intelligence.AI, aiName, rating, confidence);
            }
        }
    }

    private List<Statement> getPotentialRelations(AssertionPool pool) {
        List<Statement> result = new ArrayList<>();
        
        //Map<Resource, String> toPrefLabelMap = pool.getConceptPrefLabelMap(ConceptDiscovery.DEFAULT_TYPE);
        //Map<Resource, String> propertyPrefLblMap = pool.getPropertyPrefLabelMap();
        
        Map<Resource, Resource> domainMap = new HashMap<>();
        for(Assertion domainAssertion : pool.getAssertions(null, RDFS.domain, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0)) {
            domainMap.put(domainAssertion.getSubject(), domainAssertion.getObject());
        }
        Map<Resource, Resource> rangeMap = new HashMap<>();
        for(Assertion rangeAssertion : pool.getAssertions(null, RDFS.range, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0)) {
            rangeMap.put(rangeAssertion.getSubject(), rangeAssertion.getObject());
        }
        
        //here we use NI because we have to make sure what type it really has
        Set<Resource> allInsts = new HashSet<>();
        Map<Resource, Set<Resource>> type2insts = new HashMap<>();
        for(Assertion typeAssertion : pool.getAssertions(null, RDF.type, null, Phase.OntologyPopulation, Intelligence.NI, null, Rating.Positive, 0)) {
            
            Resource clazz = typeAssertion.getObject();
            if(clazz.equals(RDF.Property) || clazz.equals(RDFS.Class)) {
                continue;
            }
            
            type2insts.computeIfAbsent(clazz, r -> new HashSet<>()).add(typeAssertion.getSubject());
            allInsts.add(typeAssertion.getSubject());
        }
        
        for(Assertion propertyAssertion : pool.getAssertions(null, RDF.type, RDF.Property, Phase.OntologyPopulation, null, null, Rating.Positive, 0)) {
            
            Resource property = propertyAssertion.getSubject();
            
            Resource domain = domainMap.get(property);
            Resource range = rangeMap.get(property);
            
            Set<Resource> domainInsts;
            if(domain == null) {
                domainInsts = allInsts;
            } else {
                domainInsts = type2insts.get(domain);
            }
            
            Set<Resource> rangeInsts;
            if(range == null) {
                rangeInsts = allInsts;
            } else {
                rangeInsts = type2insts.get(range);
            }
            
            if(domainInsts == null || rangeInsts == null)
                continue;
            
            Property predicate = KecsApp.creator.createProperty(property.getURI());
            
            for(Resource subject : domainInsts) {
                for(Resource object : rangeInsts) {
                    
                    Statement stmt = KecsApp.creator.createStatement(subject, predicate, object);
                    
                    result.add(stmt);
                    
                    //System.out.println("[getPotentialRelations] " + Arrays.asList(toPrefLabelMap.get(subject), propertyPrefLblMap.get(predicate), toPrefLabelMap.get(object)));
                }
            }
        }
        
        return result;
    }
    
}
