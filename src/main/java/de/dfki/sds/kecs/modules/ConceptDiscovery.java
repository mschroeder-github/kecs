package de.dfki.sds.kecs.modules;

import de.dfki.sds.hephaistos.storage.assertion.Assertion;
import de.dfki.sds.hephaistos.storage.assertion.AssertionPool;
import de.dfki.sds.hephaistos.storage.assertion.Concept;
import de.dfki.sds.hephaistos.storage.assertion.Intelligence;
import de.dfki.sds.hephaistos.storage.assertion.Phase;
import de.dfki.sds.hephaistos.storage.assertion.Rating;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import de.dfki.sds.kecs.KecsApp;
import de.dfki.sds.kecs.KecsSettings;
import de.dfki.sds.kecs.vocab.KECS;
import de.dfki.sds.mschroeder.commons.lang.SetUtility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;

/**
 *
 */
public class ConceptDiscovery extends Module {

    //we use NamedIndividual because this is the default type for all entities
    //but this lives in phase ConceptDiscovery and never in OntologyPopulation
    /**
     * owl:NamedIndividual
     */
    public static final Resource DEFAULT_TYPE = OWL2.NamedIndividual;
    
    private final Phase aiPhase = Phase.ConceptDiscovery;
    private final String aiName = "ConceptDiscovery";
    
    public ConceptDiscovery() {
        
    }

    @Override
    public void bootstrap(FileInfoStorage fileInfoStorage, AssertionPool pool, KecsSettings settings) {
        
    }

    @Override
    public void init(FileInfoStorage fileInfoStorage, AssertionPool pool, KecsSettings settings) {
        this.settings = settings;
    }
    
    @Override
    public void updateOnChanges(FileInfoStorage fileInfoStorage, AssertionPool pool, List<Assertion> changes) {

        timeStat("ConceptDiscovery", () -> {

            //OK: listens to (null, SKOS.prefLabel, null)
            timeStat("prefLabelEffectsTechLabel", () -> {
                prefLabelEffectsTechLabel(pool, changes);
            });

            //effect on topic =========================================
            
            //OK: listens to (null, KECS.containsDomainTerm, null), Rating.Negative
            timeStat("termNegativeTopicRelationNegative", () -> {
                termNegativeTopicRelationNegative(pool, changes);
            });

            //listens to (null, SKOS.hiddenLabel, null) of NI and adjusts (?, topic, ?)
            timeStat("hiddenLabelsEffectsTopicRelation", () -> {
                hiddenLabelsEffectsTopicRelation(pool, changes);
            });

            //OK: listens to (null, RDF.type, ConceptDiscovery.DEFAULT_TYPE)
            timeStat("conceptRatingEffectsVariousRelationsRating", () -> {
                conceptRatingEffectsVariousRelationsRating(pool, changes);
            });
            
            //effect on sameAs ===============================
            
            //listens to (null, prefLabel, null) from NI
            //listens to (null, techLabel, null) from NI
            //listens to (null, RDF.type, DEFAULT_TYPE) Positive
            timeStat("labelsLeadToSameAsSuggestions", () -> {
                labelsLeadToSameAsSuggestions(pool, changes);
            });
            
        });

        saveTimeStat();
    }

    private void termNegativeTopicRelationNegative(AssertionPool pool, List<Assertion> changes) {
        //terms are negatively rated (maybe merge or simple negative rate)
        //this is local so at least we can remove the foaf:topic relations to concepts
        for (Assertion assertion : AssertionPool.filter(changes, null, KECS.containsDomainTerm, null, Phase.DomainTerminologyExtraction, null, null, Rating.Negative, 0)) {
            Resource node = assertion.getStatement().getSubject();
            String changedDomainTerm = assertion.getStatement().getString();

            //for each topic
            for (Assertion topicAssertion : pool.getAssertions(node, FOAF.topic, null, aiPhase, null, null, Rating.Positive, 0)) {

                Assertion conceptAssertion = pool.getConceptAssertion(topicAssertion.getObject());

                Concept concept = pool.getConcept(conceptAssertion);

                for (Assertion techLabelAssertion : concept.getHiddenLabels()) {

                    if (techLabelAssertion.getRating() != Rating.Positive) {
                        continue;
                    }

                    String techLabel = techLabelAssertion.getStatement().getString();

                    if (changedDomainTerm.equals(techLabel)) {
                        pool.assertStatement(topicAssertion.getStatement(), aiPhase, Intelligence.AI, aiName, Rating.Negative, 1.0);
                    }
                }
            }
        }
    }

    private void conceptRatingEffectsVariousRelationsRating(AssertionPool pool, List<Assertion> changes) {
        double conf = 0.75;
        
        //a rating in concept (positive or negative), changes also a rating in foaf:topic relations
        for (Assertion assertion : AssertionPool.filter(changes, null, RDF.type, ConceptDiscovery.DEFAULT_TYPE, aiPhase, null, null, null, 0)) {

            for (Assertion topicAssertion : pool.getAssertions(null, FOAF.topic, assertion.getSubject(), aiPhase, null, null, null, 0)) {
                
                //change it if nesessary (this if avoids the notification recursion)
                if(topicAssertion.getRating() != assertion.getRating()) {
                    pool.assertStatement(topicAssertion.getStatement(), topicAssertion.getPhase(), 
                            topicAssertion.getIntelligence(), topicAssertion.getName(), assertion.getRating(), conf);
                }
            }
            
            for (Assertion sameAsAssertion : pool.getAssertions(null, OWL.sameAs, null, aiPhase, null, null, null, 0)) {
                
                //if sameAs is about the changed concept
                if((sameAsAssertion.getSubject().equals(assertion.getSubject()) || sameAsAssertion.getObject().equals(assertion.getSubject())) &&
                   sameAsAssertion.getRating() != assertion.getRating()) {
                    pool.assertStatement(sameAsAssertion.getStatement(), sameAsAssertion.getPhase(), 
                            sameAsAssertion.getIntelligence(), sameAsAssertion.getName(), assertion.getRating(), conf);
                }
            }
            
            for (Assertion broaderAssertion : pool.getAssertions(null, SKOS.broader, null, Phase.ConceptHierarchyDerivation, null, null, null, 0)) {
                
                //if broader is about the changed concept
                if((broaderAssertion.getSubject().equals(assertion.getSubject()) || broaderAssertion.getObject().equals(assertion.getSubject())) &&
                   broaderAssertion.getRating() != assertion.getRating()) {
                    pool.assertStatement(broaderAssertion.getStatement(), broaderAssertion.getPhase(), 
                            broaderAssertion.getIntelligence(), broaderAssertion.getName(), assertion.getRating(), conf);
                }
            }
            
            for (Assertion nonTaxAssertion : pool.getAssertions(null, null, null, Phase.NonTaxonomicRelationLearning, null, null, null, 0)) {
                
                //if broader is about the changed concept
                if((nonTaxAssertion.getSubject().equals(assertion.getSubject()) || nonTaxAssertion.getObject().equals(assertion.getSubject())) &&
                   nonTaxAssertion.getRating() != assertion.getRating()) {
                    pool.assertStatement(nonTaxAssertion.getStatement(), nonTaxAssertion.getPhase(), 
                            nonTaxAssertion.getIntelligence(), nonTaxAssertion.getName(), assertion.getRating(), conf);
                }
            }
        }
    }

    private void prefLabelEffectsTechLabel(AssertionPool pool, List<Assertion> changes) {
        //prefLabel is rated X: same techLabel is also rated X
        for (Assertion assertion : AssertionPool.filter(changes, null, SKOS.prefLabel, null, aiPhase, null, null, null, 0)) {

            boolean assertIt = true;

            String techLabel = assertion.getStatement().getString();

            //if there is one positive domain term that is exactly like the now negative techLabel, do not put it negative
            if (assertion.getRating() == Rating.Negative) {

                for (Assertion topicAssertion : pool.getAssertions(null, FOAF.topic, assertion.getSubject(), aiPhase, null, null, Rating.Positive, 0)) {
                    Resource node = topicAssertion.getSubject();
                    for (Assertion termAssertion : pool.getAssertions(node, KECS.containsDomainTerm, null, Phase.DomainTerminologyExtraction, null, null, Rating.Positive, 0)) {
                        String domainTerm = termAssertion.getStatement().getString();

                        if (techLabel.equals(domainTerm)) {
                            assertIt = false;
                            break;
                        }
                    }

                    if (!assertIt) {
                        break;
                    }
                }
            }

            if (assertIt) {
                pool.assertStatement(assertion.getSubject(), SKOS.hiddenLabel, assertion.getStatement().getObject(),
                        assertion.getPhase(), assertion.getIntelligence(), assertion.getName(), assertion.getRating(), assertion.getConfidence());
            }
        }
    }

    private void hiddenLabelsEffectsTopicRelation(AssertionPool pool, List<Assertion> changes) {
        //hiddenLabels: a change in word cloud changes assumption to where it could be connected with foaf:topic
        //containsDomainTerm ?term <--> techLabel ?term (search for matches)
        
        //currently we allow that the domain term and the techLabel can match with ignore case
        
        //this takes some time when a new concept is created that has many hiddenLabels (e.g. 12)
        //by using Intelligence.NI we reduce the overhead that is created by AI when concept is created
        //but takes some time anyway
        
        boolean ignoreCase = false;
        
        for (Assertion assertion : AssertionPool.filter(changes, null, SKOS.hiddenLabel, null, aiPhase, Intelligence.NI, null, null, 0)) {

            Resource conceptWithTechLabel = assertion.getSubject();
            String techLabel = assertion.getStatement().getString();

            if (assertion.getRating() == Rating.Negative) {
                //if negative, is there still a connection to the nodes via domain terms?

                Assertion conceptAssertion = pool.getConceptAssertion(conceptWithTechLabel);
                Concept concept = pool.getConcept(conceptAssertion);

                //all techlabels from the concept
                Set<String> hiddenLabels = new HashSet<>();
                for (Assertion techLabelAssertion : concept.getHiddenLabels()) {
                    if (techLabelAssertion.getRating() == Rating.Positive) {
                        String str = techLabelAssertion.getStatement().getString();
                        hiddenLabels.add(ignoreCase ? str.toLowerCase() : str);
                    }
                }

                //for each annotated node
                for (Assertion topicAssertion : pool.getAssertions(null, FOAF.topic, conceptWithTechLabel, aiPhase, null, null, Rating.Positive, 0)) {

                    Resource node = topicAssertion.getSubject();

                    //if no tech label, there can not be a connection
                    if (hiddenLabels.isEmpty()) {
                        pool.assertStatement(node, FOAF.topic, conceptWithTechLabel, aiPhase, Intelligence.AI, aiName, Rating.Negative, 1.0);

                    } else {

                        //check if any term assertion still points to concept
                        Set<String> domainTerms = new HashSet<>();
                        for (Assertion termAssertion : pool.getAssertions(node, KECS.containsDomainTerm, null, Phase.DomainTerminologyExtraction, null, null, Rating.Positive, 0)) {
                            String str = termAssertion.getStatement().getString();
                            domainTerms.add(ignoreCase ? str.toLowerCase() : str);
                        }

                        //because both are to lower case this is also ignoreCase-ish
                        Set<String> intersection = SetUtility.intersection(hiddenLabels, domainTerms);

                        //if the intersection is empty there is no pointer anymore between them
                        if (intersection.isEmpty()) {
                            pool.assertStatement(node, FOAF.topic, conceptWithTechLabel, aiPhase, Intelligence.AI, aiName, Rating.Negative, 1.0);
                        }
                    }
                }

                //what if a domain term is mentioned but 
            } else if (assertion.getRating() == Rating.Positive) {
                //if positive, you maybe find this techLabel as a domain term in not yet connected notes 

                if(!ignoreCase) {
                    //here we can use getAssertions directly: performance increase in contrast to the code below (in else branch)
                    Literal techLblLit = KecsApp.creator.createLiteral(techLabel);
                    for (Assertion termAssertion : pool.getAssertions(null, KECS.containsDomainTerm, techLblLit, Phase.DomainTerminologyExtraction, null, null, Rating.Positive, 0)) {
                        pool.assertStatement(termAssertion.getSubject(), FOAF.topic, conceptWithTechLabel, aiPhase, Intelligence.AI, aiName, Rating.Positive, 1.0);
                    }
                    
                } else {
                    //we can not use the object place here because we want to check equals ignore case
                    for (Assertion termAssertion : pool.getAssertions(null, KECS.containsDomainTerm, null, Phase.DomainTerminologyExtraction, null, null, Rating.Positive, 0)) {

                        Resource node = termAssertion.getSubject();
                        String domainTerm = termAssertion.getStatement().getString();

                        boolean match = ignoreCase ? domainTerm.equalsIgnoreCase(techLabel) : domainTerm.equals(techLabel);

                        if (match) {
                            pool.assertStatement(node, FOAF.topic, conceptWithTechLabel, aiPhase, Intelligence.AI, aiName, Rating.Positive, 1.0);
                        }
                    }
                }
            }
        }
    }

    private void labelsLeadToSameAsSuggestions(AssertionPool pool, List<Assertion> changes) {
        
        //TODO magic numbers
        int levenshteinThreshold = 2;
        int levenshteinLengthThreshold = 5;
        
        //only do something if prefLabel or techLabel changed by human or concept is created
        List<Assertion> triggerAssertions = new ArrayList<>();
        triggerAssertions.addAll(AssertionPool.filter(changes, null, SKOS.prefLabel, null, aiPhase, Intelligence.NI, null, null, 0));
        triggerAssertions.addAll(AssertionPool.filter(changes, null, SKOS.hiddenLabel, null, aiPhase, Intelligence.NI, null, null, 0));
        triggerAssertions.addAll(AssertionPool.filter(changes, null, RDF.type, DEFAULT_TYPE, aiPhase, null, null, Rating.Positive, 0));
        if(triggerAssertions.isEmpty()) {
            return;
        }
        
        //to skip the ones that are marked negative
        Set<String> alreadyNegative = new HashSet<>();
        pool.getAssertions(null, OWL.sameAs, null, aiPhase, null, null, Rating.Negative, 0).forEach(a -> {
            alreadyNegative.add(a.getSubject().getURI() + a.getObject().getURI());
        });
        
        //all the concepts that need to be checked if they are sameAs to existing concepts
        Set<Resource> concepts = new HashSet<>();
        triggerAssertions.forEach(a -> concepts.add(a.getSubject()));
        
        Map<Resource, String> prefLabelMap = pool.getConceptPrefLabelMap(DEFAULT_TYPE, Rating.Positive);
        //remove them to avoid self check
        Map<Resource, String> ownPrefLabelMap = new HashMap<>();
        concepts.forEach(cpt -> { 
            //this way only positive rated concepts are used
            if(prefLabelMap.containsKey(cpt)) {
                ownPrefLabelMap.put(cpt, prefLabelMap.get(cpt));
                prefLabelMap.remove(cpt); 
            }
        });
        
        for(Resource src : concepts) {
            
            String prefLblSrc = ownPrefLabelMap.get(src);
            
            Set<String> srcHiddenLabels = new HashSet<>();
            pool.getAssertions(src, SKOS.hiddenLabel, null, aiPhase, null, null, Rating.Positive, 0).forEach(a -> {
                srcHiddenLabels.add(a.getStatement().getString());
            });
            
            for(Entry<Resource, String> entry : prefLabelMap.entrySet()) {
                Resource dst = entry.getKey();
                
                //skip the ones that are marked negative
                if(alreadyNegative.contains(src.getURI() + dst.getURI())) {
                    continue;
                }
                
                String prefLblDst = prefLabelMap.get(dst);
                
                if(prefLblSrc == null || prefLblDst == null) {
                    continue;
                }
                
                //System.out.println(prefLblSrc + " check " + prefLblDst);
                
                double conf = 0;
                boolean match = false;
                Resource left = src;
                Resource right = dst;
                
                //check starts & ends with & equals
                if(prefLblSrc.startsWith(prefLblDst) || prefLblSrc.endsWith(prefLblDst) ||
                   prefLblDst.startsWith(prefLblSrc) || prefLblDst.endsWith(prefLblSrc) ||
                   prefLblDst.equals(prefLblSrc)) {
                    
                    //longest pref label is preferred
                    
                    if(prefLblSrc.length() > prefLblDst.length()) {
                        left = src;
                        right = dst;
                        
                        conf = prefLblDst.length() / (double) prefLblSrc.length();
                        
                    } else {
                        left = dst;
                        right = src;
                        
                        conf = prefLblSrc.length() / (double) prefLblDst.length();
                    }
                    
                    match = true;
                }
                
                //check distance of pref label
                if(!match && 
                   prefLblSrc.length() >= levenshteinLengthThreshold && 
                   prefLblDst.length() >= levenshteinLengthThreshold) {
                    int dist = StringUtils.getLevenshteinDistance(prefLblSrc, prefLblDst, levenshteinThreshold);
                    if(dist >= 0) {
                        conf = (levenshteinThreshold - dist) / (double) levenshteinThreshold;
                        conf = 0.66 + (0.33 * conf);
                        match = true;
                    }
                }
                
                //check pref label tokens fully overlap: 
                //"Peter Parker" vs "Parker Peter"
                //"ABC-123" vs "123-ABC"
                if(!match) {
                    String regex = "[ \\-_]+";
                    Set<String> srcSet = new HashSet<>(Arrays.asList(prefLblSrc.split(regex)));
                    Set<String> dstSet = new HashSet<>(Arrays.asList(prefLblDst.split(regex)));
                    
                    match = srcSet.equals(dstSet);
                    if(match) {
                        conf = 1.0;
                    }
                }
                
                //check hidden labels overlap
                if(!match) {
                    Set<String> dstHiddenLabels = new HashSet<>();
                    pool.getAssertions(dst, SKOS.hiddenLabel, null, aiPhase, null, null, Rating.Positive, 0).forEach(a -> {
                        dstHiddenLabels.add(a.getStatement().getString());
                    });
                    
                    Set<String> intersection = SetUtility.intersection(srcHiddenLabels, dstHiddenLabels);
                    
                    match = !intersection.isEmpty();
                    if(match) {
                        //jaccard
                        conf = intersection.size() / (double) SetUtility.union(srcHiddenLabels, dstHiddenLabels).size();
                    }
                }
                
                if(match) {
                    //we will merge into left, so right will be removed
                    pool.assertStatement(left, OWL.sameAs, right, Phase.ConceptDiscovery, Intelligence.AI, aiName, Rating.Positive, conf);
                }
            }
        }
        
        
        
        
        
        /*
        //sameAs: similar word clouds lead to sameAs assumption with Undecided by AI
        Map<Resource, Set<String>> resource2words = new HashMap<>();
        for (Assertion assertion : pool.getAssertions(null, SKOS.hiddenLabel, null, aiPhase, null, null, Rating.Positive, 0)) {
            Set<String> wordSet = resource2words.computeIfAbsent(assertion.getSubject(), sub -> new HashSet<>());
            String techLabel = assertion.getStatement().getString();
            for (String token : techLabel.split("[ _\\-]+")) {
                wordSet.add(token.toLowerCase());
            }
        }
        List<Resource> l = new ArrayList<>(resource2words.keySet());

        //only the resources that are positive
        l.removeIf(r -> pool.getAssertions(null, RDF.type, ConceptDiscovery.DEFAULT_TYPE, aiPhase, null, null, Rating.Positive, 0).isEmpty());

        for (int i = 0; i < l.size(); i++) {
            for (int j = (i + 1); j < l.size(); j++) {
                Resource r1 = l.get(i);
                Resource r2 = l.get(j);

                Set<String> r1Set = resource2words.get(r1);
                Set<String> r2Set = resource2words.get(r2);

                double jaccard = SetUtility.intersection(r1Set, r2Set).size() / (double) SetUtility.union(r1Set, r2Set).size();

                if (jaccard > 0) {
                    //System.out.println(r1Set + " " + r2Set + ": " + jaccard);

                    //TODO maybe direction can be predicted
                    pool.assertStatement(r1, OWL.sameAs, r2, Phase.ConceptDiscovery, Intelligence.AI, aiName, Rating.Undecided, jaccard);
                }
            }
        }
        */
        //only one direction
        //for (Assertion assertion : pool.getAssertions(null, OWL.sameAs, null, phase, null, null, Rating.Positive, 0)) {
        //    pool.assertStatement(assertion.getObject(), OWL.sameAs, assertion.getSubject(), Phase.ConceptDiscovery, Intelligence.AI, name, Rating.Negative, 1.0);
        //}
    }

    public static String getIndividualName(KecsSettings settings) {
        if(settings.getLanguage() == KecsSettings.Language.de) {
            return "(Schlagwort)";
        } else if(settings.getLanguage() == KecsSettings.Language.semweb) {
            return "owl:NamedIndividual";
        }
        return "(Tag)";
    }
    
}
