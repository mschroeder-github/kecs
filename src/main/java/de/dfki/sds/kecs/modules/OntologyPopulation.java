
package de.dfki.sds.kecs.modules;

import de.dfki.sds.hephaistos.storage.assertion.Assertion;
import de.dfki.sds.hephaistos.storage.assertion.AssertionPool;
import de.dfki.sds.hephaistos.storage.assertion.Intelligence;
import de.dfki.sds.hephaistos.storage.assertion.Phase;
import de.dfki.sds.hephaistos.storage.assertion.Rating;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import de.dfki.sds.kecs.KecsApp;
import de.dfki.sds.kecs.KecsSettings;
import de.dfki.sds.kecs.ml.PredictionManager;
import de.dfki.sds.kecs.util.ExceptionUtility;
import de.dfki.sds.kecs.util.Prediction;
import de.dfki.sds.stringanalyzer.string.StringEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import static java.util.stream.Collectors.toList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.json.JSONArray;
import org.json.JSONObject;
import weka.core.Attribute;

/**
 * Manual ontology definition with classes and properties but
 * also entity typing.
 */
public class OntologyPopulation extends Module {

    private final static Phase aiPhase = Phase.OntologyPopulation;
    private final static String aiName = "OntologyPopulation";
    
    /**
     * skos:Concept
     */
    public static final Resource CONCEPT_TYPE = SKOS.Concept;
    
    private PredictionManager predictionManager;
    
    public OntologyPopulation() {
        
    }
    
    private void initPredictionManager() {
        if(!embeddingIsLoaded())
            return;
        
        predictionManager = new PredictionManager();
        predictionManager.randomForest();
        //predictionManager.knn(1);
        //predictionManager.svm();
        
        predictionManager.setPrintClassifier(false);
        predictionManager.setPrintEvaluation(false);
        
        predictionManager.setSchemaDefinition(attrs -> {
            embedding.getFeatures().forEach(f -> attrs.add(new Attribute(f)));
        });
        predictionManager.setFeatureDefinition(ctx -> {
            Resource cpt = ctx.getAssertion().getSubject();
            
            List<Assertion> prefLabels = ctx.getAssertionPool().getAssertions(cpt, SKOS.prefLabel, null, Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
            
            String prefLabel = prefLabels.get(0).getStatement().getString();
            
            StringEntity se = new StringEntity(cpt.getURI(), prefLabel);
            embedding.add(se);
            
            double[] vector = embedding.getVector(se);
            for(int i = 0; i < vector.length; i++) {
                ctx.getInstance().setValue(i, vector[i]);
            }
        });
        predictionManager.setClassProvider(assertion -> assertion.getObject().getURI());
        
        predictionManager.setTrainSetProvider(ctx -> {
            //all human given positive type assertions
            List<Assertion> typeAssertions = ctx.getAssertionPool().getAssertions(null, RDF.type, null, Phase.OntologyPopulation, Intelligence.NI, null, Rating.Positive, 0);

            //ignore all classes and properties in the OntologyPopulation phase
            typeAssertions.removeIf(a -> a.getObject().equals(RDFS.Class) ||
                                         a.getObject().equals(RDF.Property) //||
                                         //a.getObject().equals(CONCEPT_TYPE)
            );
            
            ctx.getAssertionSet().addAll(typeAssertions);
        });
        
        predictionManager.setTestSetProvider(ctx -> {
            List<Assertion> conceptAssertions = ctx.getAssertionPool().getAssertions(null, RDF.type, ConceptDiscovery.DEFAULT_TYPE, Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
        
            conceptAssertions.removeIf(cptAs -> {
                List<Assertion> typeAssertions = ctx.getAssertionPool().getAssertions(cptAs.getSubject(), RDF.type, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
                return typeAssertions.stream().anyMatch(a -> a.getIntelligence() == Intelligence.NI);
            });
            
            ctx.getAssertionSet().addAll(conceptAssertions);
        });
        
        predictionManager.setPredictionsConsumer(ctx -> {
            
            String aiPredName = aiName + "Prediction";
            
            //reset all current ones
            List<Assertion> assertions = ctx.getAssertionPool().getAssertions(null, null, null, aiPhase, Intelligence.AI, aiPredName, Rating.Positive, 0);
            for(Assertion assertion : assertions) {
                ctx.getAssertionPool().assertStatement(assertion.getStatement(),
                        aiPhase, Intelligence.AI, aiPredName, Rating.Negative, 1.0
                );
            }
            //save reset
            //ctx.getAssertionPool().commit();
            
            Set<Resource> visited = new HashSet<>();
            
            //ctx.getPredictions().forEach(p -> System.out.println(p));
            
            //prediction to assertion
            for(Prediction prediction : ctx.getPredictions()) {
                
                Resource cpt = prediction.getAssertion().getSubject();
                Resource type = KecsApp.creator.createResource(prediction.getClassLabel());
                
                //already negative feedback?
                List<Assertion> niFeedback = ctx.getAssertionPool().getAssertions(cpt, RDF.type, type, aiPhase, Intelligence.NI, null, Rating.Negative, 0);
                
                //only one prediction per resource
                //also do not predict it again when NI already said its negative
                if(visited.contains(cpt) || !niFeedback.isEmpty()) {
                    //System.out.println(visited.contains(cpt) ? (cpt + " visited") : cpt + " niFeedback not empty");
                    continue;
                }
                
                ctx.getAssertionPool().assertStatement(cpt, RDF.type, type,
                        aiPhase, Intelligence.AI, aiPredName, Rating.Positive, prediction.getConfidence()
                );
                
                visited.add(cpt);
            }
        });
    }
    
    @Override
    public void init(FileInfoStorage fileInfoStorage, AssertionPool pool, KecsSettings settings) {
        this.settings = settings;
        initPredictionManager();
    }
    
    @Override
    public void bootstrap(FileInfoStorage fileInfoStorage, AssertionPool pool, KecsSettings settings) {
        
        //always create these
        
        //a default type for the special "Concept" (aka pimo:thing#Topic)
        pool.assertStatement(CONCEPT_TYPE, RDF.type, RDFS.Class,              aiPhase, Intelligence.AI, aiName, Rating.Positive, 1.0);
        pool.assertStatement(CONCEPT_TYPE, SKOS.prefLabel, getTopicName(settings), aiPhase, Intelligence.AI, aiName, Rating.Positive, 1.0);
        
        //a default type for domain and range if it is unspecified
        pool.assertStatement(ConceptDiscovery.DEFAULT_TYPE, RDF.type, RDFS.Class,                               aiPhase, Intelligence.AI, aiName, Rating.Positive, 1.0);
        pool.assertStatement(ConceptDiscovery.DEFAULT_TYPE, SKOS.prefLabel, ConceptDiscovery.getIndividualName(settings), aiPhase, Intelligence.AI, aiName, Rating.Positive, 1.0);
        
        //ontology
        if(settings.getOntologyFile().exists()) {
            JSONObject ontology;
            try {
                ontology = new JSONObject(FileUtils.readFileToString(settings.getOntologyFile(), StandardCharsets.UTF_8));
            } catch (IOException ex) {
                ExceptionUtility.save(ex);
                throw new RuntimeException(ex);
            }

            loadOntology(ontology, pool, settings);
            
        } else if(settings.isLoadDefaultOntology()) {
            //if no ontology.json found in current directory
            //we load a default one from resources
            
            JSONObject ontology;
            try {
                ontology = new JSONObject(IOUtils.toString(OntologyPopulation.class.getResourceAsStream("/de/dfki/sds/kecs/auxiliary/ontology_"+ settings.getLanguage().name() +".json"), StandardCharsets.UTF_8));
            } catch (IOException ex) {
                ExceptionUtility.save(ex);
                throw new RuntimeException(ex);
            }

            loadOntology(ontology, pool, settings);
        }
        
        //always commit
        pool.commit();
    }
    
    public static void loadOntology(JSONObject ontology, AssertionPool pool, KecsSettings settings) {
        JSONArray classes = ontology.getJSONArray("classes");
        
        List<String> typeNames = classes.toList().stream().map(o -> (String)o).collect(toList());
        typeNames.sort((a,b) -> a.compareTo(b));
        
        Map<String, Resource> typeName2Resource = new HashMap<>();
        typeName2Resource.put(getTopicName(settings), CONCEPT_TYPE);
        typeName2Resource.put(ConceptDiscovery.getIndividualName(settings), ConceptDiscovery.DEFAULT_TYPE);
        
        Map<Resource, String> typePrefLabelMap = pool.getTypePrefLabelMap();
        for(Entry<Resource, String> entry : typePrefLabelMap.entrySet()) {
            typeName2Resource.put(entry.getValue(), entry.getKey());
        }
        
        Set<String> typeNameSet = new HashSet<>(typePrefLabelMap.values());
        Set<String> propertyNameSet = new HashSet<>(pool.getPropertyPrefLabelMap().values());
        
        for(String typeName : typeNames) {
            
            if(typeNameSet.contains(typeName)) {
                continue;
            }
            
            Resource type = pool.createType();
            
            pool.assertStatement(type, RDF.type, RDFS.Class, aiPhase, Intelligence.AI, aiName, Rating.Positive, 1.0);
            pool.assertStatement(type, SKOS.prefLabel, typeName, aiPhase, Intelligence.AI, aiName, Rating.Positive, 1.0);
            
            typeName2Resource.put(typeName, type);
            
            typeNameSet.add(typeName);
        }
        
        JSONArray properties = ontology.getJSONArray("properties");
        for(int i = 0; i < properties.length(); i++) {
            
            JSONArray entry = properties.getJSONArray(i);
            
            if(propertyNameSet.contains(entry.getString(1))) {
                continue;
            }
            
            addProperty(
                    entry.getString(0), entry.getString(1), entry.getString(2), 
                    typeName2Resource, pool
            );
            
            propertyNameSet.add(entry.getString(1));
        }
    }
    
    private static String getTopicName(KecsSettings settings) {
        if(settings.getLanguage() == KecsSettings.Language.de) {
            return "Thema";
        } else if(settings.getLanguage() == KecsSettings.Language.semweb) {
            return "skos:Concept";
        }
        return "Topic";
    }
    
    @Override
    public void updateOnChanges(FileInfoStorage fileInfoStorage, AssertionPool pool, List<Assertion> changes) {
    
        timeStat("OntologyPopulation", () -> {
        
            if(embeddingIsLoaded()) {
                suggestType(fileInfoStorage, pool, changes);
            }
        
        });
        
        saveTimeStat();
    }
    
    private void suggestType(FileInfoStorage fileInfoStorage, AssertionPool pool, List<Assertion> changes) {

        //if new concept is discovered or type is asserted
        //(null, RDF.type, null) has to be NI to avoid recursion loop
        boolean changed = !AssertionPool.filter(changes, null, RDF.type, ConceptDiscovery.DEFAULT_TYPE, Phase.ConceptDiscovery, null, null, null, 0).isEmpty() ||
                          !AssertionPool.filter(changes, null, RDF.type, null,                          Phase.OntologyPopulation, Intelligence.NI, null, null, 0).isEmpty()
                          || !AssertionPool.filter(changes, null, SKOS.prefLabel, null,                  Phase.ConceptDiscovery, null, null, null, 0).isEmpty();
        
        if(changed) {
            timeStat("suggestType.predictionManager.trainAndPredict", () -> {
                predictionManager.trainAndPredict(fileInfoStorage, pool);
            });
        }
    }

    private static void addProperty(String domainTypeName, String propertyName, String rangeTypeName, Map<String, Resource> typeName2Resource, AssertionPool pool) {
        if(!typeName2Resource.containsKey(domainTypeName) || !typeName2Resource.containsKey(rangeTypeName)) {
            return;
        }
        
        Resource prop = pool.createProperty();
            
        pool.assertStatement(prop, RDF.type, RDF.Property,                             aiPhase, Intelligence.AI, aiName, Rating.Positive, 1.0);
        pool.assertStatement(prop, SKOS.prefLabel, propertyName,                       aiPhase, Intelligence.AI, aiName, Rating.Positive, 1.0);
        pool.assertStatement(prop, RDFS.domain, typeName2Resource.get(domainTypeName), aiPhase, Intelligence.AI, aiName, Rating.Positive, 1.0);
        pool.assertStatement(prop, RDFS.range, typeName2Resource.get(rangeTypeName),   aiPhase, Intelligence.AI, aiName, Rating.Positive, 1.0);
    }

}
