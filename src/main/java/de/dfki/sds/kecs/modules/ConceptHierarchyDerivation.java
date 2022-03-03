
package de.dfki.sds.kecs.modules;

import de.dfki.sds.hephaistos.storage.assertion.Assertion;
import de.dfki.sds.hephaistos.storage.assertion.AssertionPool;
import de.dfki.sds.hephaistos.storage.assertion.Intelligence;
import de.dfki.sds.hephaistos.storage.assertion.Phase;
import de.dfki.sds.hephaistos.storage.assertion.Rating;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import de.dfki.sds.kecs.KecsApp;
import de.dfki.sds.kecs.KecsSettings;
import de.dfki.sds.stringanalyzer.helper.GermaNet;
import de.dfki.sds.stringanalyzer.helper.GermaNet.SynSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;

/**
 * 
 */
public class ConceptHierarchyDerivation extends Module {

    private final Phase aiPhase = Phase.ConceptHierarchyDerivation;
    private final String aiName = "ConceptHierarchyDerivation";
    
    //TODO magic number for deciding when a path is too long in germanet:
    //it would be too general, e.g. "Car" and "Person" are both "Entities" may not be a good suggestion
    private final double stepAvgThreshold = 3.0;
    
    @Override
    public void bootstrap(FileInfoStorage fileInfoStorage, AssertionPool pool, KecsSettings settings) {
        
    }

    @Override
    public void init(FileInfoStorage fileInfoStorage, AssertionPool pool, KecsSettings settings) {
        this.settings = settings;
    }
    
    @Override
    public void updateOnChanges(FileInfoStorage fileInfoStorage, AssertionPool pool, List<Assertion> changes) {
        timeStat("ConceptHierarchyDerivation", () -> {
        
            if(languageResourceIsLoaded()) {
                suggestBroaderWithLanguageResource(pool, changes);
            }
            
            //broader based rules
            List<Assertion> broaderChanges = AssertionPool.filter(changes, null, SKOS.broader, null, Phase.ConceptHierarchyDerivation, Intelligence.NI, null, null, 0);
            if(!broaderChanges.isEmpty()) {
                timeStat("broaderEffectsSubjectObject", () -> {
                    broaderEffectsSubjectObject(broaderChanges, pool);
                });
            }
            
        });
        
        saveTimeStat();
    }
    
    private void suggestBroaderWithLanguageResource(AssertionPool pool, List<Assertion> changes) {
        
        //do something when there is a new skos:Concept available
        boolean conceptOccurred = !AssertionPool.filter(changes, null, RDF.type, OntologyPopulation.CONCEPT_TYPE, Phase.OntologyPopulation, Intelligence.NI, null, Rating.Positive, 0).isEmpty();
        
        //also do something when a concept (TODO actually skos:Concept) is renamed
        boolean conceptRenamed = !AssertionPool.filter(changes, null, SKOS.prefLabel, null, Phase.ConceptDiscovery, Intelligence.NI, null, Rating.Positive, 0).isEmpty();
        
        if(conceptOccurred || conceptRenamed) {
            timeStat("suggestBroader", () -> {
                suggestBroaderWithLanguageResource(pool);
            });
        }
    }
    
    private void suggestBroaderWithLanguageResource(AssertionPool pool) {
        boolean print = false;
        
        Map<Resource, String> cpt2prefLbl = pool.getConceptPrefLabelMap(ConceptDiscovery.DEFAULT_TYPE, null);
        
        //all of the skos:Concept
        List<Assertion> skosConceptAssertions = pool.getAssertions(null, RDF.type, OntologyPopulation.CONCEPT_TYPE, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
        
        
        //build a small tree of all the skos:concept nodes with their germanet parents
        Node root = new Node("ROOT");
        Map<String, Node> id2node = new HashMap<>();
        for(Assertion skosConceptAssertion : skosConceptAssertions) {
            
            Resource leftConcept = skosConceptAssertion.getSubject();
            
            //we only check those which are grounded in file system, i.e. there is a positive foaf:topic relation
            List<Assertion> topicRelations = pool.getAssertions(null, FOAF.topic, leftConcept, Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
            if(topicRelations.isEmpty()) {
                continue;
            }
            
            String prefLabel = cpt2prefLbl.get(leftConcept);
            List<SynSet> synsets = germaNet.lookup(prefLabel);
            
            if(!synsets.isEmpty()) {
                
                for(SynSet start : synsets) {
                    
                    List<SynSet> parentPath = start.getParentPathDisambig((int) (stepAvgThreshold + 1));
                    parentPath.add(start);
                    
                    int depth = 0;
                    
                    Node cur = root;
                    for(SynSet synset : parentPath) {
                        cur = cur.getOrCreate(synset, depth);
                        
                        depth++;
                        
                        id2node.put(cur.id, cur);
                    }
                    
                    int step = 0;
                    while(cur != root) {
                        
                        if(step > 0) {
                            cur.steps.add(new SynSetWithStep(start, step, leftConcept));
                        }
                        
                        step++;
                        
                        cur = cur.parent;
                    }
                    
                    if(print) {
                        System.out.println("[SynSet] " +  prefLabel + ": " + parentPath);
                    }
                }
            }
        }
        
        List<Node> nodes = new ArrayList<>(id2node.values());
        
        //remove uninteresting ones
        nodes.removeIf(n -> n.steps.size() <= 1);
        
        //sum of steps made
        for(Node node : nodes) {
            node.stepAvg = node.steps.stream().mapToInt(s -> s.step).average().getAsDouble();
        }
        
        //remove if stepAvg is over threshold
        nodes.removeIf(n -> n.stepAvg > stepAvgThreshold);
        
        //sort them to prefer the shortest pathes first
        nodes.sort((a,b) -> {
            return Double.compare(a.stepAvg, b.stepAvg);
        });
        
        if(print) {
            for(Node node : nodes) {
                System.out.println(node);
            }
        }
        
        //could be the case that we create it already in this run
        Set<Resource> createdInThisSession = new HashSet<>();
        
        //already suggested concepts in broader relation do not suggest them again
        Set<Resource> usedInBroader = new HashSet<>();
        
        //check for every constellation
        for(Node node : nodes) {
            
            //does the broad topic exist?
            List<Assertion> broadTopicAssertions = pool.getAssertions(
                    node.concept, RDF.type, OntologyPopulation.CONCEPT_TYPE, 
                    Phase.OntologyPopulation, null, null, null, 0
            );
            
            //if there is no such statement we have to create it
            //could be the case that we create it already in this run
            boolean createBroaderTopic = broadTopicAssertions.isEmpty() && !createdInThisSession.contains(node.concept);
            
            List<SynSetWithStep> suggested = new ArrayList<>();
            
            int positiveCount = 0;
            
            //does the broader relations exist
            for(SynSetWithStep entry : node.steps) {
                
                //if we already proposed it in this session
                if(usedInBroader.contains(entry.concept)) {
                    //it was first used somewhere else
                    continue;
                }
                
                List<Assertion> assertions = pool.getAssertions(
                        entry.concept, SKOS.broader, node.concept,
                        Phase.ConceptHierarchyDerivation, null, null, null, 0
                );
                
                //if already stated, do not state again
                if(!assertions.isEmpty()) {
                    //it was already proposed
                    
                    Assertion assertion = assertions.get(0);
                    
                    //it is still proposed by AI and was not touched by user
                    //also if NI set it positive we should not propose it again
                    if(//assertion.getIntelligence() == Intelligence.AI &&
                       assertion.getRating() == Rating.Positive) {
                        
                        positiveCount++;
                        
                        //so it is marked as used and is not suggested for another broader relation
                        usedInBroader.add(entry.concept);
                    }
                    
                    continue;
                }
                
                //only suggest it when there is not a statement in the pool
                suggested.add(entry);

                // else {
                    //should be only one then
                    //Assertion broaderAssertion = assertions.get(0);
                    
                    //maybe don't do anything if there is already the statement regardless of the rating
                //}
            }
            
            //if you do not have to suggest something; just continue
            if(suggested.isEmpty() || suggested.size() + positiveCount <= 1) {
                continue;
            }
            
            if(createBroaderTopic) {
                //the concept is undecided because we do not know if the broader relation is correct
                pool.assertStatement(node.concept, RDF.type, ConceptDiscovery.DEFAULT_TYPE,      Phase.ConceptDiscovery, Intelligence.AI, aiName, Rating.Undecided, 1.0);

                //we already know that it has to be a concept (topic)
                pool.assertStatement(node.concept, RDF.type, OntologyPopulation.CONCEPT_TYPE,    Phase.OntologyPopulation, Intelligence.AI, aiName, Rating.Positive, 1.0);

                List<GermaNet.LexUnit> parentLexUnits = node.synset.getLexUnits();
                for(int luIndex = 0; luIndex < parentLexUnits.size(); luIndex++) {
                    GermaNet.LexUnit lexUnit = parentLexUnits.get(luIndex);

                    //only first one will be prefLabel
                    if(luIndex == 0) {
                        pool.assertStatement(node.concept, SKOS.prefLabel, lexUnit.getOrthForm(),  Phase.ConceptDiscovery, Intelligence.AI, aiName, Rating.Positive, 1.0);
                    }

                    //all lex units are then hidden labels
                    pool.assertStatement(node.concept, SKOS.hiddenLabel, lexUnit.getOrthForm(),      Phase.ConceptDiscovery, Intelligence.AI, aiName, Rating.Positive, 1.0);
                }

                //keep track of what was created
                createdInThisSession.add(node.concept);
            }
            
            for(SynSetWithStep entry : suggested) {
                double conf = 1.0 - ((entry.step - 1.0) / (stepAvgThreshold * 2));
                
                pool.assertStatement(
                        entry.concept, SKOS.broader, node.concept, 
                        aiPhase, Intelligence.AI, aiName, Rating.Positive, conf
                );
                
                usedInBroader.add(entry.concept);
            }
        }
    }
    
    private void broaderEffectsSubjectObject(List<Assertion> broaderChanges, AssertionPool pool) {
    
        for(Assertion broaderAssertion : broaderChanges) {
            if(broaderAssertion.getRating() != Rating.Positive) {
                continue;
            }
            
            for(Resource res : Arrays.asList(broaderAssertion.getSubject(), broaderAssertion.getObject())) {
                
                //it is a concept
                pool.assertStatement(res, RDF.type, ConceptDiscovery.DEFAULT_TYPE, 
                        Phase.ConceptDiscovery, broaderAssertion.getIntelligence(), broaderAssertion.getName(), Rating.Positive, 1.0);

                //it is a skos:Concept
                pool.assertStatement(res, RDF.type, OntologyPopulation.CONCEPT_TYPE, 
                        Phase.OntologyPopulation, broaderAssertion.getIntelligence(), broaderAssertion.getName(), Rating.Positive, 1.0);

            }
            
            //maybe we should support polyhierarchies so this is comment out:
            //we will not reject anything
            
            /*
            //1. A -> C (existing)
            //2. A -> B (new = broaderAssertion)
            List<Assertion> existingAssertions = pool.getAssertions(broaderAssertion.getSubject(), SKOS.broader, null, aiPhase, null, null, Rating.Positive, 0);
            if(existingAssertions.size() > 1) {
                //remove the already added one (case 2.)
                existingAssertions.removeIf(a -> a.getStatement().equals(broaderAssertion.getStatement()));
                
                //3. A -> B -> C   but could also be A -> C -> B
                //that is why we remove it for now
                for(Assertion existing : existingAssertions) {
                    pool.assertStatement(existing.getStatement(), Phase.ConceptHierarchyDerivation, 
                            broaderAssertion.getIntelligence(), broaderAssertion.getName(), Rating.Negative, 1.0);
                }
            }
            */
        }
        
    }
    
    
    private class Node {
        
        String id;
        Map<String, Node> childMap = new HashMap<>();
        SynSet synset;
        Node parent;
        int depth;
        
        int stepSum;
        double stepAvg;

        Set<SynSetWithStep> steps = new HashSet<>();
        
        Resource concept;

        public Node() {
        }

        public Node(String id) {
            this.id = id;
        }

        public Node(SynSet synset) {
            this.synset = synset;
            this.id = synset.getId();
            this.concept = KecsApp.creator.createResource("urn:concept:" + synset.getId());
        }
        
        public Node getOrCreate(SynSet synset, int depth) {
            if(childMap.containsKey(synset.getId())) {
                return childMap.get(synset.getId());
            } else {
                Node n = new Node(synset);
                n.depth = depth;
                this.childMap.put(n.id, n);
                n.parent = this;
                return n;
            }
        }

        @Override
        public String toString() {
            return "depth=" + depth + ", stepAvg=" + stepAvg + " " + concept + " " + (synset != null ? synset.getLexSimple() : "") + " => " + steps.toString(); //synset != null ? synset.toString() : id;
        }

        public List<Node> getChildren() {
            return new ArrayList<>(childMap.values());
        }

        public String toStringTree() {
        StringBuilder sb = new StringBuilder();
        toStringTree("", true, sb);
        return sb.toString();
    }

        private void toStringTree(String prefix, boolean isTail, StringBuilder sb) {
            List<Node> children = getChildren();

            sb.append(prefix).append(isTail ? "└── " : "├── ").append(toString()).append("\n");
            for (int i = 0; i < children.size() - 1; i++) {
                children.get(i).toStringTree(prefix + (isTail ? "    " : "│   "), false, sb);
            }
            if (children.size() > 0) {
                children.get(children.size() - 1)
                        .toStringTree(prefix + (isTail ? "    " : "│   "), true, sb);
            }
        }

    }
    
    private class SynSetWithStep {
        
        private SynSet synset;
        private int step;
        private Resource concept;

        public SynSetWithStep(SynSet synset, int step, Resource concept) {
            this.synset = synset;
            this.step = step;
            this.concept = concept;
        }

        @Override
        public String toString() {
            return "(" + step + ":" + synset.getLexSimple() + " " + concept + ")";
        }
        
        @Override
        public int hashCode() {
            int hash = 7;
            hash = 41 * hash + Objects.hashCode(this.synset.getId());
            hash = 41 * hash + this.step;
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
            final SynSetWithStep other = (SynSetWithStep) obj;
            if (this.step != other.step) {
                return false;
            }
            if (!Objects.equals(this.synset.getId(), other.synset.getId())) {
                return false;
            }
            return true;
        }
        
    }
    
}
