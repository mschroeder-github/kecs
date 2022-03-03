package de.dfki.sds.hephaistos.storage.assertion;

import de.dfki.sds.hephaistos.storage.InternalStorage;
import de.dfki.sds.hephaistos.storage.InternalStorageMetaData;
import de.dfki.sds.hephaistos.storage.StorageSummary;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;

/**
 *
 */
public abstract class AssertionPool extends InternalStorage<Assertion, StorageSummary, Object> {

    private static final Model creator = ModelFactory.createDefaultModel();

    private List<AssertionListener> listeners;
    protected List<Assertion> notificationBuffer;
    private final int notifyRecursionThreshold = 100;
    
    protected List<Assertion> commitBuffer = new ArrayList<>();
    protected boolean bulkMode;
    
    private boolean print = false;
    private boolean logging = true;
    
    private File folder;
    private Counter counter;

    public AssertionPool(InternalStorageMetaData metaData, File folder) {
        super(metaData);
        this.folder = folder;
        this.counter = new Counter(new File(folder, "counters"));
        listeners = new ArrayList<>();
        notificationBuffer = new ArrayList<>();
    }

    public void addListener(AssertionListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(AssertionListener listener) {
        listeners.remove(listener);
    }

    public void clearListeners() {
        listeners.clear();
    }

    public void notifyListenersRecursively(FileInfoStorage fileInfoStorage) {
        notifyListenersRecursively(fileInfoStorage, 0);
    }

    private void notifyListenersRecursively(FileInfoStorage fileInfoStorage, int depth) {
        if (depth >= notifyRecursionThreshold) {
            //System.err.println("WARNING: notifyListenersRecursively stopped because recursion is too deep");
            clearNotificationBuffer();
            throw new StackOverflowError("ERROR: notifyListenersRecursively stopped because recursion is too deep");
        }

        if (notificationBuffer.isEmpty()) {
            return;
        }

        if(print) {
            System.out.println("========================");
            System.out.println("notifyListenersRecursively depth: " + depth);
            System.out.println("========================");
        }
        
        List<Assertion> copy = new ArrayList<>(notificationBuffer);
        clearNotificationBuffer();
        for (AssertionListener al : listeners) {
            al.updateOnChanges(fileInfoStorage, this, copy);
        }
        commit();
        if (!notificationBuffer.isEmpty()) {
            notifyListenersRecursively(fileInfoStorage, depth + 1);
        }
    }

    public void clearNotificationBuffer() {
        notificationBuffer.clear();
    }

    //assert
    //here time can be set
    protected abstract void assertStatement(Statement stmt, Phase phase, Intelligence intel, String name, Rating rating, double confidence, LocalDateTime when);

    public void assertStatement(Statement stmt, Phase phase, Intelligence intel, String name, Rating rating, double confidence) {
        assertStatement(stmt, phase, intel, name, rating, confidence, LocalDateTime.now());
    }

    public void assertAssertion(Assertion assertion) {
        assertStatement(assertion.getStatement(), assertion.getPhase(), assertion.getIntelligence(), assertion.getName(), assertion.getRating(), assertion.getConfidence());
    }

    public void assertAssertionTimePreserving(Assertion assertion) {
        assertStatement(assertion.getStatement(), assertion.getPhase(), assertion.getIntelligence(), assertion.getName(), assertion.getRating(), assertion.getConfidence(), assertion.getWhen());
    }

    public void assertStatement(Resource subject, Property predicate, RDFNode object, Phase phase, Intelligence intel, String name, Rating rating, double confidence) {
        assertStatement(creator.createStatement(subject, predicate, object), phase, intel, name, rating, confidence);
    }

    public void assertStatement(Resource subject, Property predicate, String plainLiteral, Phase phase, Intelligence intel, String name, Rating rating, double confidence) {
        assertStatement(creator.createStatement(subject, predicate, creator.createLiteral(plainLiteral)), phase, intel, name, rating, confidence);
    }

    public void assertLiteralStatement(String subjectUri, String predicateUri, String plainLiteral, Phase phase, Intelligence intel, String name, Rating rating, double confidence) {
        assertStatement(creator.createStatement(creator.createResource(subjectUri), creator.getProperty(predicateUri), creator.createLiteral(plainLiteral)), phase, intel, name, rating, confidence);
    }

    public void assertResourceStatement(String subjectUri, String predicateUri, String objectUri, Phase phase, Intelligence intel, String name, Rating rating, double confidence) {
        assertStatement(creator.createStatement(creator.createResource(subjectUri), creator.getProperty(predicateUri), creator.createResource(objectUri)), phase, intel, name, rating, confidence);
    }
    
    public abstract void removeAllAbout(Resource resource);

    public Resource createConcept() {
        return creator.createResource("urn:concept:" + counter.getIncreased("concept"));
    }
    
    public Resource createType() {
        return creator.createResource("urn:type:" + counter.getIncreased("type"));
    }
    
    public Resource createProperty() {
        return creator.createResource("urn:property:" + counter.getIncreased("property"));
    }

    //query
    public abstract List<Assertion> getAssertions(Resource subject, Property predicate, RDFNode object, Phase phase, Intelligence intel, String name, Rating rating, double confidenceThreshold);

    public abstract List<Assertion> getAssertionsIn(Property predicate, List<RDFNode> object, Phase phase);

    //deprecated: used when owl:sameAs was used
    @Deprecated
    public List<Assertion> getAssertionsViaSubjects(List<Assertion> inputAssertions, Function<Assertion, Resource> toSubject, Property predicate, RDFNode object, Phase phase, Intelligence intel, String name, Rating rating, double confidenceThreshold) {
        List<Assertion> l = new ArrayList<>();
        for (Assertion inputAs : inputAssertions) {
            l.addAll(getAssertions(toSubject.apply(inputAs), predicate, object, phase, intel, name, rating, confidenceThreshold));
        }
        return l;
    }

    //deprecated: used when owl:sameAs was used
    @Deprecated
    public List<Assertion> getAssertionsViaObjects(List<Assertion> inputAssertions, Function<Assertion, RDFNode> toObject, Resource Subject, Property predicate, Phase phase, Intelligence intel, String name, Rating rating, double confidenceThreshold) {
        List<Assertion> l = new ArrayList<>();
        for (Assertion inputAs : inputAssertions) {
            l.addAll(getAssertions(Subject, predicate, toObject.apply(inputAs), phase, intel, name, rating, confidenceThreshold));
        }
        return l;
    }

    public List<Concept> getConcepts() {
        return getConcepts(false);
    }

    public List<Concept> getConcepts(boolean alsoNegative) {
        List<Concept> concepts = new ArrayList<>();

        Rating ratingFilter = alsoNegative ? null : Rating.Positive;
        List<Assertion> conceptAssertions = getAssertions(null, RDF.type, OWL2.NamedIndividual, Phase.ConceptDiscovery, null, null, ratingFilter, 0);

        for (Assertion conceptAssertion : conceptAssertions) {

            //apply owl:sameAs check: 
            //List<Assertion> incomingSameAs = getAssertions(null, OWL.sameAs, conceptAssertion.getSubject(), Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
            //boolean isMerged = !incomingSameAs.isEmpty();

            //only the ones that have no incoming sameAs are displayed
            //thus, the ones that are merged are only shown if alsoMerged is true
            //if (isMerged && !alsoMerged) {
            //    continue;
            //}

            concepts.add(getConcept(conceptAssertion));
        }

        return concepts;
    }

    public Assertion getConceptAssertion(Resource concept) {
        List<Assertion> conceptAssertions = getAssertions(concept, RDF.type, OWL2.NamedIndividual, Phase.ConceptDiscovery, null, null, null, 0);
        if (!conceptAssertions.isEmpty()) {
            return conceptAssertions.get(0);
        }
        return null;
    }

    public Concept getConcept(Assertion conceptAssertion) {
        //List<Assertion> incomingSameAs = getAssertions(null, OWL.sameAs, conceptAssertion.getSubject(), Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
        //boolean isMerged = !incomingSameAs.isEmpty();

        //for each concept collect all concepts connected transitive with sameAs and use their assertions (regardless of rating)
        //show the sameAs assertions also in the GUI
        //List<Assertion> mergedConcepts = getConceptAssertionsViaTransitiveSameAs(conceptAssertion);

        List<Assertion> prefLabels = getAssertions(conceptAssertion.getSubject(), SKOS.prefLabel, null, Phase.ConceptDiscovery, null, null, null, 0);
        
        List<Assertion> hiddenLabels = getAssertions(conceptAssertion.getSubject(), SKOS.hiddenLabel, null, Phase.ConceptDiscovery, null, null, null, 0);
        hiddenLabels.sort((a, b) -> a.getStatement().getString().compareToIgnoreCase(b.getStatement().getString()));

        List<Assertion> types = getAssertions(conceptAssertion.getSubject(), RDF.type, null, Phase.ConceptDiscovery, null, null, null, 0);

        List<Assertion> isTopicOfs = getAssertions(conceptAssertion.getSubject(), null, FOAF.topic, Phase.ConceptDiscovery, null, null, null, 0);

        //List<Assertion> outgoingSameAs = getAssertions(conceptAssertion.getSubject(), OWL.sameAs, null, Phase.ConceptDiscovery, null, null, null, 0);

        Concept concept = new Concept();
        concept.setPrefLabel(getPrefLabelString(prefLabels));
        concept.setURI(conceptAssertion.getSubject().getURI());
        concept.setConceptAssertion(conceptAssertion);
        //concept.setIncomingSameAs(incomingSameAs);
        //concept.setOutgoingSameAs(outgoingSameAs);
        concept.setPrefLabels(prefLabels);
        concept.setHiddenLabels(hiddenLabels);
        concept.setTypes(types);
        concept.setIsTopicOfs(isTopicOfs);
        //concept.setMergedConcepts(mergedConcepts);
        //concept.setMerged(isMerged);

        return concept;
    }

    public static String getPrefLabelString(List<Assertion> prefLabels) {
        //first positive prefLabel
        //TODO maybe use highest confidence later
        String prefLabel = "";
        for(Assertion prefLabelAssertion : prefLabels) {
            if(prefLabelAssertion.getRating() == Rating.Positive) {
                prefLabel = prefLabelAssertion.getStatement().getString();
                break;
            }
        }
        return prefLabel;
    }
    
    public Map<Resource, String> getTypePrefLabelMap() {
        Map<Resource, String> m = new HashMap<>();
        
        List<Assertion> typeAssertions = getAssertions(null, RDF.type, RDFS.Class, Phase.OntologyPopulation, null, null, null, 0);
        for(Assertion typeAssertion : typeAssertions) {
            
            List<Assertion> prefLabels = getAssertions(typeAssertion.getSubject(), SKOS.prefLabel, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
            if(prefLabels.isEmpty()) {
                continue;
            }
            
            m.put(typeAssertion.getSubject(), prefLabels.get(0).getStatement().getString());
        }
        
        return m;
    }
    
    public Map<Resource, String> getPropertyPrefLabelMap() {
        Map<Resource, String> m = new HashMap<>();
        
        List<Assertion> propertyAssertions = getAssertions(null, RDF.type, RDF.Property, Phase.OntologyPopulation, null, null, null, 0);
        for(Assertion propertyAssertion : propertyAssertions) {
            
            List<Assertion> prefLabels = getAssertions(propertyAssertion.getSubject(), SKOS.prefLabel, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
            if(prefLabels.isEmpty()) {
                continue;
            }
            
            m.put(propertyAssertion.getSubject(), prefLabels.get(0).getStatement().getString());
        }
        
        return m;
    }
    
    public Map<Resource, String> getConceptPrefLabelMap(Resource defaultType, Rating rating) {
        Map<Resource, String> m = new HashMap<>();
        
        List<Assertion> conceptAssertions = getAssertions(null, RDF.type, defaultType, Phase.ConceptDiscovery, null, null, rating, 0);
        for(Assertion conceptAssertion : conceptAssertions) {
            
            List<Assertion> prefLabels = getAssertions(conceptAssertion.getSubject(), SKOS.prefLabel, null, Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
            if(prefLabels.isEmpty()) {
                continue;
            }
            
            m.put(conceptAssertion.getSubject(), prefLabels.get(0).getStatement().getString());
        }
        
        return m;
    }
    
    public Map<String, List<Assertion>> getLabelConceptMap(Resource defaultType, boolean lowerCase) {
        Map<String, List<Assertion>> m = new HashMap<>();
        
        List<Assertion> conceptAssertions = getAssertions(null, RDF.type, defaultType, Phase.ConceptDiscovery, null, null, null, 0);
        for(Assertion conceptAssertion : conceptAssertions) {
            
            List<Assertion> prefLabels = getAssertions(conceptAssertion.getSubject(), SKOS.prefLabel, null, Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
            List<Assertion> hiddenLabels = getAssertions(conceptAssertion.getSubject(), SKOS.hiddenLabel, null, Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
            
            List<Assertion> assertions = new ArrayList<>();
            assertions.add(prefLabels.get(0));
            assertions.addAll(hiddenLabels);
            
            for(Assertion assertion : assertions) {
                
                String lbl = assertion.getStatement().getString();
                if(lowerCase) {
                    lbl = lbl.toLowerCase();
                }

                List<Assertion> list = m.computeIfAbsent(lbl, l -> new ArrayList<>());

                //do not add duplicates
                boolean exists = list.stream().anyMatch(a -> a.getStatement().equals(conceptAssertion.getStatement()));
                if(!exists) {
                    list.add(conceptAssertion);
                }
            }
        }
        
        return m;
    }
    
    public static List<Assertion> filter(Collection<Assertion> assertions, Resource subject, Property predicate, RDFNode object, Phase phase, Intelligence intel, String name, Rating rating, double confidenceThreshold) {
        List<Assertion> result = new ArrayList<>();
        for (Assertion assertion : assertions) {

            if (subject != null && !assertion.getStatement().getSubject().equals(subject)) {
                continue;
            }
            if (predicate != null && !assertion.getStatement().getPredicate().equals(predicate)) {
                continue;
            }
            if (object != null && !assertion.getStatement().getObject().equals(object)) {
                continue;
            }

            if (phase != null && assertion.getPhase() != phase) {
                continue;
            }
            if (intel != null && assertion.getIntelligence() != intel) {
                continue;
            }
            if (name != null && !assertion.getName().equals(name)) {
                continue;
            }
            if (rating != null && assertion.getRating() != rating) {
                continue;
            }
            if (confidenceThreshold > 0 && assertion.getConfidence() >= confidenceThreshold) {
                continue;
            }
            result.add(assertion);
        }
        return result;
    }

    public static Map<Rating, List<Assertion>> ratingMap(Collection<Assertion> assertions) {
        Map<Rating, List<Assertion>> rating2assertions = new HashMap<>();
        for (Assertion assertion : assertions) {
            List<Assertion> l = rating2assertions.computeIfAbsent(assertion.getRating(), r -> new ArrayList<>());
            l.add(assertion);
        }
        return rating2assertions;
    }

    @Deprecated
    public List<Assertion> getConceptAssertionsViaTransitiveSameAs(Assertion startConceptAssertion) {
        List<Assertion> assertions = new ArrayList<>();

        Stack<Assertion> stack = new Stack<>();
        stack.add(startConceptAssertion);
        Set<Resource> visitedConcepts = new HashSet<>();

        while (!stack.isEmpty()) {
            Assertion assertion = stack.pop();

            if (visitedConcepts.contains(assertion.getSubject())) {
                continue;
            }

            visitedConcepts.add(assertion.getSubject());
            assertions.add(assertion);

            //only positive sameAs relations
            List<Assertion> outgoingSameAsList = getAssertions(assertion.getSubject(), OWL.sameAs, null, Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
            for (Assertion outgoingSameAs : outgoingSameAsList) {

                Resource target = outgoingSameAs.getStatement().getResource();

                if (visitedConcepts.contains(target)) {
                    continue;
                }

                //only positive concepts
                List<Assertion> conceptAssertions = getAssertions(target, RDF.type, OWL2.NamedIndividual, Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
                if (!conceptAssertions.isEmpty()) {
                    stack.push(conceptAssertions.get(0));
                }
            }
        }

        return assertions;
    }

    public int getCommitBufferSize() {
        return commitBuffer.size();
    }

    public boolean isBulkMode() {
        return bulkMode;
    }

    public void setBulkMode(boolean bulkMode) {
        this.bulkMode = bulkMode;
    }

    public boolean isLogging() {
        return logging;
    }

    public void setLogging(boolean logging) {
        this.logging = logging;
    }
    
    public abstract void commit();
    
    public abstract void rollback();

    public Counter getCounter() {
        return counter;
    }
    
}
