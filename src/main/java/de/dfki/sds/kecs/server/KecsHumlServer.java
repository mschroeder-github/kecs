package de.dfki.sds.kecs.server;

import com.google.gson.GsonBuilder;
import com.r6lab.sparkjava.jwt.TokenService;
import com.r6lab.sparkjava.jwt.controller.AuthController;
import com.r6lab.sparkjava.jwt.user.UserPrincipal;
import com.r6lab.sparkjava.jwt.user.UserService;
import de.dfki.sds.hephaistos.storage.StorageItem;
import de.dfki.sds.hephaistos.storage.assertion.Assertion;
import de.dfki.sds.hephaistos.storage.assertion.AssertionPool;
import de.dfki.sds.hephaistos.storage.assertion.Intelligence;
import de.dfki.sds.hephaistos.storage.assertion.Phase;
import de.dfki.sds.hephaistos.storage.assertion.Rating;
import de.dfki.sds.hephaistos.storage.file.FileInfo;
import de.dfki.sds.hephaistos.storage.file.FolderInfo;
import de.dfki.sds.kecs.KecsApp;
import de.dfki.sds.kecs.KecsManager;
import de.dfki.sds.kecs.KecsSettings.Language;
import de.dfki.sds.kecs.ml.StatusManager;
import de.dfki.sds.kecs.ml.VisualManager;
import de.dfki.sds.kecs.modules.ConceptDiscovery;
import de.dfki.sds.kecs.modules.DomainTerminologyExtraction;
import de.dfki.sds.kecs.modules.OntologyPopulation;
import de.dfki.sds.kecs.util.ExceptionUtility;
import de.dfki.sds.kecs.util.FileInfoSearchResult;
import de.dfki.sds.kecs.util.JsonUtility;
import de.dfki.sds.kecs.util.KecsUtils;
import de.dfki.sds.kecs.util.TypeWithIntel;
import de.dfki.sds.kecs.vocab.KECS;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 *
 */
public class KecsHumlServer {
    
    private static final String ROOT_PATH = "/de/dfki/sds/kecs";
    
    private static final String SECRET_JWT = "kO9nqmbYcMFzB0gUWofW";
    private TokenService tokenService;
    private AuthController authController;
    private UserService userService;
    
    private int port;
    
    private KecsManager manager;
    private File resultFolder;
    
    private final int defaultLimit = 10;
    private final int defaultSearchConceptLimit = 50;
    
    //to save the status every x event entries
    private int eventCounter = -1;
    
    private File userCsvFile;
    
    private Language language;

    public KecsHumlServer(int port, File userCsvFile, KecsManager manager, File resultFolder, Language language) {
        this.port = port;
        this.userCsvFile = userCsvFile;
        this.manager = manager;
        this.resultFolder = resultFolder;
        this.language = language;
    }
    
    public void start() {
        Spark.port(port);

        Spark.exception(Exception.class, (exception, request, response) -> {
            ExceptionUtility.save(exception);
            exception.printStackTrace();
            response.body(exception.getMessage());
        });

        Spark.staticFiles.location(ROOT_PATH + "/web");

        Spark.before((req, res) -> {
            String path = req.pathInfo();
            if (!path.equals("/") && path.endsWith("/")) {
                res.redirect(path.substring(0, path.length() - 1));
            }
        });

        Spark.get("/", this::getRoot);
        Spark.get("/app", this::getApp);
        
        //endpoints for app
        Spark.post("/getChildren", this::getChildren);
        Spark.post("/browseChild", this::browseChild);
        Spark.post("/getTypes", this::getTypes);
        Spark.post("/getProperties", this::getProperties);
        Spark.post("/getPositiveTypes", this::getPositiveTypes);
        Spark.post("/getPositiveProperties", this::getPositiveProperties);
        Spark.post("/getSuggestions", this::getSuggestions);
        Spark.post("/getStatus", this::getStatus);
        
        Spark.post("/addType", this::addType);
        Spark.post("/addProperty", this::addProperty);
        Spark.post("/sendAssertion", this::sendAssertion);
        
        Spark.post("/assertType", this::assertType);
        Spark.post("/assertTypes", this::assertTypes);
        
        Spark.post("/assertMerge", this::assertMerge);
        Spark.post("/assertMerges", this::assertMerges);
        
        Spark.post("/assertNegativeTermsRecursively", this::assertNegativeTermsRecursively);
        Spark.post("/assertNewConcept", this::assertNewConcept);
        
        Spark.post("/sendEvent", this::sendEvent);
        
        Spark.post("/searchConcepts", this::searchConcepts);
        Spark.post("/browseConcept", this::browseConcept);
        
        Spark.post("/explorerSearch", this::explorerSearch);
        Spark.post("/explorerSearchCreate", this::explorerSearchCreate);
        
        Spark.get("/download/:name", this::download);
        Spark.get("/visual/:name", this::visual);
        
        initJsonWebToken();
        
        Spark.awaitInitialization();
        
        System.out.println("server running at localhost:" + port);
    }
    
    private void initJsonWebToken() {
        tokenService = new TokenService(SECRET_JWT);
        userService = new UserService(userCsvFile);
        authController = new AuthController(new GsonBuilder().create(), userService, tokenService, resultFolder);
        authController.init();
    }
    
    private Object getRoot(Request req, Response resp) throws IOException {
        saveRequest(req);
        
        String html = IOUtils.toString(KecsHumlServer.class.getResourceAsStream(ROOT_PATH + "/web/root_"+ language.name() +".html"), StandardCharsets.UTF_8);
        
        boolean unauthorized = req.queryMap().hasKey("unauthorized");
        html = html.replace("${extraMessage}", unauthorized ? "<div class=\"alert alert-warning\" role=\"alert\">Sie müssen sich zuerst anmelden.</div>" : "");
        
        resp.type("text/html");
        return html;
    }
    
    private Object getApp(Request req, Response resp) throws IOException {
        saveRequest(req);
        
        String html = IOUtils.toString(KecsHumlServer.class.getResourceAsStream(ROOT_PATH + "/web/app.html"), StandardCharsets.UTF_8);
        
        html = html.replace("${locale}", language.name());
        
        resp.type("text/html");
        return html;
    }
    
    private JSONObject getChildren(String parentUri) {
        JSONObject result = new JSONObject();
        JSONArray children = new JSONArray();
        result.put("children", children);
        
        FolderInfo folderInfo;
        if(parentUri == null) {
            folderInfo = manager.getFileInfoStorage().getRoot();
        } else {
            int id = 2;
            if(!parentUri.isEmpty()) {
                id = KecsUtils.getId(parentUri);
            }
            
            //e.g. urn:file:3
            folderInfo = (FolderInfo) manager.getFileInfoStorage().get(id);
        }
        
        Optional<FolderInfo> parentOfParent = manager.getFileInfoStorage().getParentOf(folderInfo);
        
        List<FileInfo> childrenList = new ArrayList<>();
        childrenList.addAll(manager.getFileInfoStorage().getBranchChildren(folderInfo));
        childrenList.addAll(manager.getFileInfoStorage().getLeafChildren(folderInfo));
        
        JSONObject parentObj = toJson(folderInfo);
        result.put("parent", parentObj);
        
        for(FileInfo fn : childrenList) {
            
            JSONObject obj = toJson(fn);
            
            Resource resource = ResourceFactory.createResource(obj.getString("uri"));
            
            //terms
            List<Assertion> domainTermAssertions = manager.getAssertionPool().getAssertions(resource, KECS.containsDomainTerm, null, Phase.DomainTerminologyExtraction, null, null, null, 0);
            //sort by occurance
            domainTermAssertions.sort((a,b) -> {
                int iA = fn.getName().indexOf(a.getStatement().getString());
                int iB = fn.getName().indexOf(b.getStatement().getString());
                return Integer.compare(iA, iB);
            });
            //code to sort it
            Map<String, Integer> domainTerm2index = new HashMap<>();
            for(int i = 0; i < domainTermAssertions.size(); i++) {
                domainTerm2index.put(domainTermAssertions.get(i).getStatement().getString(), i);
            }
            JSONObject terms = Assertion.toJson(domainTermAssertions);
            //add a special merge object to have a button in between
            if(terms.has(Rating.Positive.toString())) {
                JSONArray array = terms.getJSONArray(Rating.Positive.toString());
                
                List<JSONObject> l = JsonUtility.getList(array, JSONObject.class);
                
                for(int i = array.length() - 1; i >= 1; i--) {
                    JSONObject mergeObject = new JSONObject();
                    mergeObject.put("merge", true);
                    l.add(i, mergeObject);
                }
            }
            obj.put("terms", terms);
            
            //concepts
            List<Assertion> topicAssertions = manager.getAssertionPool().getAssertions(resource, FOAF.topic, null, Phase.ConceptDiscovery, null, null, null, 0);
            //code to sort it
            Map<Resource, Integer> topic2index = new HashMap<>();
            for(Assertion topicAssertion : topicAssertions) {
                Resource topic = topicAssertion.getObject();
                for(Assertion hiddenLbl : manager.getAssertionPool().getAssertions(topic, SKOS.hiddenLabel, null, Phase.ConceptDiscovery, null, null, null, 0)) {
                    Integer index = domainTerm2index.get(hiddenLbl.getStatement().getString());
                    if(index != null) {
                        topic2index.put(topic, index);
                        break;
                    }
                }
            }
            topicAssertions.sort((a,b) -> {
                Resource topicA = a.getObject();
                Resource topicB = b.getObject();
                Integer indexA = topic2index.get(topicA);
                Integer indexB = topic2index.get(topicB);
                if(indexA == null || indexB == null)
                    return 0;
                
                return Integer.compare(indexA, indexB);
            });
            JSONObject topics = Assertion.toJson(topicAssertions);
            for(String rating : topics.keySet()) {
                JSONArray ratingArray = topics.getJSONArray(rating);
                for(int i = 0; i < ratingArray.length(); i++) {
                    JSONObject entry = ratingArray.getJSONObject(i);
                    
                    Resource target = ResourceFactory.createResource(entry.getJSONObject("statement").getString("topic"));
                    
                    List<Assertion> targetPrefLabels = manager.getAssertionPool().getAssertions(target, SKOS.prefLabel, null, Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
                    String targetPrefLabel = AssertionPool.getPrefLabelString(targetPrefLabels);
                    entry.put("targetPrefLabel", targetPrefLabel);
                }
            }
            obj.put("concepts", topics);
            
            children.put(obj);
        }
        
        //to jump up
        if(parentOfParent.isPresent()) {
            result.put("parentOfParent", new JSONObject(parentOfParent.get().getMeta()).getString("uri"));
        }
        
        return result;
    }
    
    private Object getChildren(Request req, Response resp) {
        //saveRequest(req);

        JSONObject json = new JSONObject(req.body());
        //use empty string for root browsing
        String parentUri = json.getString("parent");
        //Resource parent = (parentUri == null || parentUri.isEmpty()) ? null : ResourceFactory.createResource(parentUri);
        
        JSONObject result = getChildren(parentUri);
        
        resp.type("application/json");
        return result.toString(2);
    }
    
    private Object browseChild(Request req, Response resp) {
        saveRequest(req);

        JSONObject json = new JSONObject(req.body());
        String childUri = json.getString("child");
        
        JSONObject result = browseChild(childUri);
        
        if(result != null) {
            resp.type("application/json");
        }
        
        return result.toString(2);
    }
    
    private JSONObject browseChild(String childUri) {
        int childId = KecsUtils.getId(childUri);
        
        FileInfo child = (FileInfo) manager.getFileInfoStorage().get(childId);
        Optional<FolderInfo> parentOpt = manager.getFileInfoStorage().getParentOf(child);
        
        if(parentOpt.isPresent()) {
            String parentUri = new JSONObject(parentOpt.get().getMeta()).getString("uri");
            JSONObject result = getChildren(parentUri);
            
            return result;
        }
        
        return null;
    }
    
    private Object searchConcepts(Request req, Response resp) {
        saveRequest(req);
        
        JSONObject reqJson = new JSONObject(req.body());
        
        String conceptName = reqJson.optString("conceptName").toLowerCase();
        boolean hasConceptName = conceptName != null && !conceptName.trim().isEmpty();
        
        String conceptType = reqJson.getString("conceptType");
        boolean hasConceptType = conceptType != null && !conceptType.trim().isEmpty();
        Resource conceptTypeResource = hasConceptType ? ResourceFactory.createResource(conceptType) : null;
        
        boolean alsoNegative = reqJson.getBoolean("alsoNegative");
        //boolean alsoMerged = reqJson.getBoolean("alsoMerged");
        
        int offset = reqJson.optInt("offset", 0);
        
        JSONObject result = new JSONObject();
        
        Rating ratingFilter = alsoNegative ? null : Rating.Positive;
        
        JSONArray conceptResult = new JSONArray();
        result.put("concepts", conceptResult);
        
        List<Assertion> conceptAssertions = manager.getAssertionPool().getAssertions(null, RDF.type, ConceptDiscovery.DEFAULT_TYPE, Phase.ConceptDiscovery, null, null, ratingFilter, 0);
        result.put("total", conceptAssertions.size());
        
        Map<Resource, String> typeMap = null;
        if(!conceptAssertions.isEmpty()) {
            typeMap = manager.getAssertionPool().getTypePrefLabelMap();
        }
        
        //no filter means we do not have to search for something
        if(!hasConceptName && !hasConceptType) {
            
            conceptAssertions.sort((a,b) -> b.getWhen().compareTo(a.getWhen()));
            List<Assertion> sublist = calculateList(offset, defaultSearchConceptLimit, conceptAssertions, result);
            
            for(Assertion conceptAssertion : sublist) {
                
                //List<Assertion> mergedConcepts = manager.getAssertionPool().getConceptAssertionsViaTransitiveSameAs(conceptAssertion);
                
                List<Assertion> prefLabels = manager.getAssertionPool().getAssertions(conceptAssertion.getSubject(), SKOS.prefLabel, null, Phase.ConceptDiscovery, null, null, null, 0);
                List<Assertion> types      = manager.getAssertionPool().getAssertions(conceptAssertion.getSubject(), RDF.type,       null, Phase.OntologyPopulation, null, null, null, 0);
            
                TypeWithIntel typeWithIntel = KecsUtils.getType(types);
                
                JSONObject resultEntry = new JSONObject();
                
                conceptToJson(AssertionPool.getPrefLabelString(prefLabels), conceptAssertion, typeWithIntel, typeMap, resultEntry);
                
                conceptResult.put(resultEntry);
            }
            
            result.put("paginationMode", true);
            
            resp.type("application/json");
            return result.toString(2);
        }
        
        //when type is set we visualize:
        //to know which has broader relation, or
        Set<Resource> hasBroader = new HashSet<>();
        //to know which has non-taxonomic relation
        Set<Resource> hasNonTaxRel = new HashSet<>();
        if(hasConceptType) {
            
            //from the drag&drop dropdown menu
            String predicateUri = reqJson.optString("predicate", "");
            
            if(conceptTypeResource.equals(OntologyPopulation.CONCEPT_TYPE)) {
                //taxonomy case
                for(Assertion broaderAssertion : manager.getAssertionPool().getAssertions(null, SKOS.broader, null, 
                        Phase.ConceptHierarchyDerivation, null, null, Rating.Positive, 0)) {

                    hasBroader.add(broaderAssertion.getSubject());
                }
                
            } else if(!predicateUri.isEmpty()) {
                //non-taxonomic case
                
                Property predicate = KecsApp.creator.createProperty(predicateUri);
                
                for(Assertion nonTaxAssertion : manager.getAssertionPool().getAssertions(null, predicate, null, 
                        Phase.NonTaxonomicRelationLearning, Intelligence.NI, null, Rating.Positive, 0)) {

                    hasNonTaxRel.add(nonTaxAssertion.getSubject());
                }
            }
        }
        
        for(Assertion conceptAssertion : conceptAssertions) {
            
            //apply owl:sameAs check: 
            //List<Assertion> incomingSameAs = manager.getAssertionPool().getAssertions(null, OWL.sameAs, conceptAssertion.getSubject(), Phase.ConceptDiscovery, null, null, Rating.Positive, 0);
            //boolean isMerged = !incomingSameAs.isEmpty();
            
            //only the ones that have no incoming sameAs are displayed
            //thus, the ones that are merged are only shown if alsoMerged is true
            //if(isMerged && !alsoMerged) {
            //    continue;
            //}
            
            //for each concept collect all concepts connected transitive with sameAs and use their assertions (regardless of rating)
            //show the sameAs assertions also in the GUI
            //List<Assertion> mergedConcepts = manager.getAssertionPool().getConceptAssertionsViaTransitiveSameAs(conceptAssertion);
            
            List<Assertion> prefLabels = manager.getAssertionPool().getAssertions(conceptAssertion.getSubject(), SKOS.prefLabel, null, Phase.ConceptDiscovery, null, null, null, 0);
            
            List<Assertion> hiddenLabels = manager.getAssertionPool().getAssertions(conceptAssertion.getSubject(), SKOS.hiddenLabel, null, Phase.ConceptDiscovery, null, null, null, 0);
            
            //because we are in OntologyPopulation phase these are real types (no NamedIndividual type here)
            List<Assertion> types      = manager.getAssertionPool().getAssertions(conceptAssertion.getSubject(), RDF.type,       null, Phase.OntologyPopulation, null, null, null, 0);
            
            JSONObject resultEntry = new JSONObject();
            
            TypeWithIntel typeWithIntel = KecsUtils.getType(types);
            
            String prefLabel = AssertionPool.getPrefLabelString(prefLabels);
            
            //all labels
            Set<String> allLabels = new HashSet<>();
            for(Assertion prefLabelAssertion : prefLabels) {
                if(alsoNegative || prefLabelAssertion.getRating() == Rating.Positive) {
                    allLabels.add(prefLabelAssertion.getStatement().getString());
                }
            }
            for(Assertion techLabelAssertion : hiddenLabels) {
                if(alsoNegative || techLabelAssertion.getRating() == Rating.Positive) {
                    allLabels.add(techLabelAssertion.getStatement().getString());
                }
            }
            
            //label match
            boolean labelMatch = false;
            if(hasConceptName) {
                boolean oneMatch = false;
                for (String lbl : allLabels) {
                    int i = lbl.toLowerCase().indexOf(conceptName);
                    if (i != -1) {
                        oneMatch = true;
                        
                        resultEntry.put("left", lbl.substring(0, i));
                        resultEntry.put("middle", lbl.substring(i, i + conceptName.length()));
                        resultEntry.put("right", lbl.substring(i + conceptName.length(), lbl.length()));
                        resultEntry.put("matchesPrefLbl", lbl.equals(prefLabel));

                        break;
                    }
                }
                if(oneMatch) {
                    labelMatch = true;
                }
            }
            
            //type match
            boolean typeMatch = false;
            if(typeWithIntel != null && conceptTypeResource != null) {
                typeMatch = typeWithIntel.getType().equals(conceptTypeResource);
            }
            
            //add concept
            boolean bothAreGiven = hasConceptName && hasConceptType;
            boolean oneIsGiven = (hasConceptName || hasConceptType) && !bothAreGiven;
            if((oneIsGiven && (labelMatch || typeMatch))   ||  (bothAreGiven && labelMatch && typeMatch) ) {
                
                conceptToJson(prefLabel, conceptAssertion, typeWithIntel, typeMap, resultEntry);
                
                //visualize extra marker to see what is connected
                if(hasConceptType) {
                    //taxonomy and non-taxonomic case
                    if(hasBroader.contains(conceptAssertion.getSubject())) {
                        resultEntry.put("showTree", true);
                    } else if(hasNonTaxRel.contains(conceptAssertion.getSubject())) {
                        resultEntry.put("showArrow", true);
                    } else if(typeWithIntel.getType().equals(OntologyPopulation.CONCEPT_TYPE)) {
                        
                        //performance problems?
                        boolean isNotAnnotated = manager.getAssertionPool().getAssertions(null, FOAF.topic, conceptAssertion.getSubject(), 
                            Phase.ConceptDiscovery, null, null, Rating.Positive, 0).isEmpty();
                        
                        resultEntry.put("showLeaf", isNotAnnotated);
                    }
                }
                
                conceptResult.put(resultEntry);
            }
        }
        
        List<JSONObject> list = JsonUtility.getList(conceptResult, JSONObject.class);
        list.sort((a,b) -> {
            Object whenA = a.getJSONObject("assertion").get("when");
            Object whenB = b.getJSONObject("assertion").get("when");
            
            LocalDateTime ldtA = (LocalDateTime) whenA;
            LocalDateTime ldtB = (LocalDateTime) whenB;
            
            return ldtB.compareTo(ldtA);
        });
        
        //System.out.println(result.toString(2));
        
        resp.type("application/json");
        return result.toString(2);
    }
    
    //needs just { uri: <uri> }
    private Object browseConcept(Request req, Response resp) {
        saveRequest(req);
        
        JSONObject reqJson = new JSONObject(req.body());
        
        String uri = reqJson.getString("uri");
        Resource conceptResource = uri != null && !uri.isEmpty() ? ResourceFactory.createResource(uri) : null;
        
        int offsetP = reqJson.optInt("offsetP", 0);
        int limitP = reqJson.optInt("limitP", defaultLimit);
        
        int offsetN = reqJson.optInt("offsetN", 0);
        int limitN = reqJson.optInt("limitN", defaultLimit);
        
        if(conceptResource == null)
            return null;
        
        JSONObject conceptObj = browseConcept(conceptResource, offsetP, limitP, offsetN, limitN);
        if(conceptObj == null)
            return null;
        
        resp.type("application/json");
        return conceptObj.toString(2);
    }
    
    private JSONObject browseConcept(Resource conceptResource, int offsetP, int limitP, int offsetN, int limitN) {
        List<Assertion> concepts = manager.getAssertionPool().getAssertions(conceptResource, RDF.type, ConceptDiscovery.DEFAULT_TYPE, Phase.ConceptDiscovery, null, null, null, 0);
        
        if(concepts.isEmpty())
            return null;
        
        Assertion conceptAssertion = concepts.get(0);
        
        //for each concept collect all concepts connected transitive with sameAs and use their assertions (regardless of rating)
        //show the sameAs assertions also in the GUI
        //List<Assertion> mergedConcepts = manager.getAssertionPool().getConceptAssertionsViaTransitiveSameAs(conceptAssertion);
        
        JSONObject conceptObj = new JSONObject();
            
        conceptObj.put("userEntryAltLabel", "");
        conceptObj.put("uri", conceptAssertion.getStatement().getSubject().getURI());

        conceptObj.put("assertion", Assertion.toJson(conceptAssertion));

        //because we are in phase OntologyPopulation this will not return rdf:type owl:NamedIndividual
        List<Assertion> types = manager.getAssertionPool().getAssertions(conceptAssertion.getSubject(), RDF.type, null, Phase.OntologyPopulation, null, null, null, 0);
        Assertion.orderByRatingIntelConfidence(types);
        conceptObj.put("types", Assertion.toJsonArray(types));
        
        //assume type assertion is the  'RDF.type, ConceptDiscovery.DEFAULT_TYPE' assertion, but maybe you find a better one
        TypeWithIntel typeWithIntel = KecsUtils.getType(types);
        conceptObj.put("type", typeWithIntel.getType().getURI());
        conceptObj.put("typeIntelligence", typeWithIntel.getIntel().name());
        
        //prefLabels
        List<Assertion> prefLabels = manager.getAssertionPool().getAssertions(conceptAssertion.getSubject(), SKOS.prefLabel, null, Phase.ConceptDiscovery, null, null, null, 0);
        conceptObj.put("prefLabels", Assertion.toJsonArrayOrderByRating(prefLabels));
        conceptObj.put("prefLabel", AssertionPool.getPrefLabelString(prefLabels));

        //hiddenLabels
        List<Assertion> hiddenLabels = manager.getAssertionPool().getAssertions(conceptAssertion.getSubject(), SKOS.hiddenLabel, null, Phase.ConceptDiscovery, null, null, null, 0);
        hiddenLabels.sort((a,b) -> a.getStatement().getString().compareToIgnoreCase(b.getStatement().getString()));
        conceptObj.put("hiddenLabels", Assertion.toJsonArrayOrderByRating(hiddenLabels));
            
        //isTopicOfs ===============================================================
        
        //all
        List<Assertion> isTopicOfs = manager.getAssertionPool().getAssertions(null, FOAF.topic, conceptAssertion.getSubject(), Phase.ConceptDiscovery, null, null, null, 0);
        
        //positive vs non-positive
        List<Assertion> isTopicOfsPositive = new ArrayList<>();
        List<Assertion> isTopicOfsNegative = new ArrayList<>();
        isTopicOfs.forEach(a -> { if(a.getRating() == Rating.Positive) isTopicOfsPositive.add(a); else isTopicOfsNegative.add(a); } );
        
        
        JSONObject topicPaginationPositive = new JSONObject();
        conceptObj.put("isTopicOfsPositive", topicPaginationPositive);
        List<Assertion> isTopicOfsPositiveSublist = calculateList(offsetP, limitP, isTopicOfsPositive, topicPaginationPositive);
        
        JSONObject topicPaginationNegative = new JSONObject();
        conceptObj.put("isTopicOfsNegative", topicPaginationNegative);
        List<Assertion> isTopicOfsNegativeSublist = calculateList(offsetN, limitN, isTopicOfsNegative, topicPaginationNegative);
        
        JSONArray isTopicOfsPositiveArray = Assertion.toJsonArrayOrderByIntel(isTopicOfsPositiveSublist);
        JSONArray isTopicOfsNegativeArray = Assertion.toJsonArrayOrderByRating(isTopicOfsNegativeSublist);
        
        List<JSONArray> listOfArrays = Arrays.asList(isTopicOfsPositiveArray, isTopicOfsNegativeArray);
        
        //long positiveCount = isTopicOfs.stream().filter(a -> a.getRating() == Rating.Positive).count();
        topicPaginationPositive.put("list", isTopicOfsPositiveArray);
        topicPaginationNegative.put("list", isTopicOfsNegativeArray);
        
        //add more info for isTopicOfs
        for(JSONArray isTopicOfsArray : listOfArrays) {
            for(int i = 0; i < isTopicOfsArray.length(); i++) {
                JSONObject isTopicOf = isTopicOfsArray.getJSONObject(i);

                //uri is e.g. urn:file:123
                int id = KecsUtils.getId(isTopicOf.getJSONObject("statement").getString("@id"));
                
                FileInfo fi = (FileInfo) manager.getFileInfoStorage().get(id);
                Optional<FolderInfo> parentOpt = manager.getFileInfoStorage().getParentOf(fi);
                
                JSONObject fileObj = toJson(fi);
                isTopicOf.put("child", fileObj);
                
                if(parentOpt.isPresent()) {
                    JSONObject parentObj = toJson(parentOpt.get());
                    isTopicOf.put("parent", parentObj);
                }
            }
        }
        
        return conceptObj;
    }
    
    private Object explorerSearch(Request req, Response resp) {
        saveRequest(req);
        
        JSONObject reqJson = new JSONObject(req.body());
        String search = reqJson.getString("search");
        boolean regex = reqJson.getBoolean("regex");
        
        //TODO later
        //boolean onlyFolder = reqJson.getBoolean("onlyFolder");
        //String folder = reqJson.getString("folder");
        Resource folderResource = null; //later: onlyFolder ? KecsApp.creator.createResource(folder) : null;
        
        DomainTerminologyExtraction dte = manager.getDomainTerminologyExtraction();
        
        List<FileInfoSearchResult> searchResult = dte.search(search, regex, folderResource);
        
        //searchResult.forEach(r -> System.out.println(r));
        
        JSONObject result = new JSONObject();
        
        JSONArray matches = new JSONArray();
        result.put("matches", matches);

        //TODO magic number: limit of search result
        int limit = 500;
        result.put("limit", limit);
        result.put("total", searchResult.size());
        if(searchResult.size() > limit) {
            searchResult = searchResult.subList(0, limit);
        }
        
        result.put("regex", regex);
        
        for(FileInfoSearchResult sr : searchResult) {
            
            JSONObject srObj = new JSONObject();
            srObj.put("prefLabel", sr.getFileInfo().getName());
            srObj.put("isFile", !sr.getFileInfo().isDirectory());
            srObj.put("left", sr.getLeft());
            srObj.put("middle", sr.getMiddle());
            srObj.put("right", sr.getRight());
            srObj.put("uri", KecsUtils.getURI(sr.getFileInfo()));
            srObj.put("selected", true);
         
            matches.put(srObj);
        }
        
        resp.type("application/json");
        return result.toString(2);
    }
    
    private Object getTypes(Request req, Response resp) {
        saveRequest(req);
        
        JSONObject result = new JSONObject();
        
        JSONArray typeArray = new JSONArray();
        result.put("types", typeArray);
        
        List<Assertion> typeAssertions = manager.getAssertionPool().getAssertions(null, RDF.type, RDFS.Class, Phase.OntologyPopulation, null, null, null, 0);
        
        typeAssertions.sort((a,b) -> {
            int cmp = a.getRating().compareTo(b.getRating());
            
            if(cmp == 0) {
                List<Assertion> prefLabelsA = manager.getAssertionPool().getAssertions(a.getSubject(), SKOS.prefLabel, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
                List<Assertion> prefLabelsB = manager.getAssertionPool().getAssertions(b.getSubject(), SKOS.prefLabel, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
            
                if(prefLabelsA.isEmpty() || prefLabelsB.isEmpty()) {
                    return 0;
                }
                
                return prefLabelsA.get(0).getStatement().getString().compareTo(prefLabelsB.get(0).getStatement().getString());
            }
            
            return cmp;
        });
        
        for(Assertion typeAssertion : typeAssertions) {
            
            //do not show user to edit because this is fix
            if(typeAssertion.getSubject().equals(ConceptDiscovery.DEFAULT_TYPE) ||
               typeAssertion.getSubject().equals(OntologyPopulation.CONCEPT_TYPE)) {
                continue;
            }
            
            List<Assertion> prefLabels = manager.getAssertionPool().getAssertions(typeAssertion.getSubject(), SKOS.prefLabel, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
            if(prefLabels.isEmpty()) {
                continue;
            }
            
            JSONObject typeObj = new JSONObject();
            typeObj.put("assertion", Assertion.toJson(typeAssertion));
            typeObj.put("prefLabelAssertion", Assertion.toJson(prefLabels.get(0)));
         
            typeArray.put(typeObj);
        }
        
        //System.out.println(result.toString(2));
        
        resp.type("application/json");
        return result.toString(2);
    }
    
    private Object getProperties(Request req, Response resp) {
        saveRequest(req);
        
        JSONObject result = new JSONObject();
        
        JSONArray propArray = new JSONArray();
        result.put("properties", propArray);
        
        List<Assertion> propAssertions = manager.getAssertionPool().getAssertions(null, RDF.type, RDF.Property, Phase.OntologyPopulation, null, null, null, 0);
        
        propAssertions.sort((a,b) -> {
            int cmp = a.getRating().compareTo(b.getRating());
            
            if(cmp == 0) {
                List<Assertion> prefLabelsA = manager.getAssertionPool().getAssertions(a.getSubject(), SKOS.prefLabel, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
                List<Assertion> prefLabelsB = manager.getAssertionPool().getAssertions(b.getSubject(), SKOS.prefLabel, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
            
                if(prefLabelsA.isEmpty() || prefLabelsB.isEmpty()) {
                    return 0;
                }
                
                return prefLabelsA.get(0).getStatement().getString().compareTo(prefLabelsB.get(0).getStatement().getString());
            }
            
            return cmp;
        });
        
        for(Assertion propAssertion : propAssertions) {
            
            List<Assertion> prefLabels = manager.getAssertionPool().getAssertions(propAssertion.getSubject(), SKOS.prefLabel, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
            List<Assertion> domains = manager.getAssertionPool().getAssertions(propAssertion.getSubject(), RDFS.domain, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
            List<Assertion> ranges = manager.getAssertionPool().getAssertions(propAssertion.getSubject(), RDFS.range, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
            
            JSONObject typeObj = new JSONObject();
            typeObj.put("assertion", Assertion.toJson(propAssertion));
            typeObj.put("prefLabelAssertion", Assertion.toJson(prefLabels.get(0)));
            
            if(!domains.isEmpty()) {
                typeObj.put("domainAssertion", Assertion.toJson(domains.get(0)));
            }
            if(!ranges.isEmpty()) {
                typeObj.put("rangeAssertion", Assertion.toJson(ranges.get(0)));
            }
         
            propArray.put(typeObj);
        }
        
        //System.out.println(result.toString(2));
        
        resp.type("application/json");
        return result.toString(2);
    }
    
    public Object getPositiveTypes(Request req, Response resp) {
        saveRequest(req);
        
        JSONObject result = new JSONObject();
        
        JSONArray typeArray = new JSONArray();
        result.put("types", typeArray);
        
        List<Assertion> typeAssertions = manager.getAssertionPool().getAssertions(null, RDF.type, RDFS.Class, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
        for(Assertion typeAssertion : typeAssertions) {
            
            List<Assertion> prefLabels = manager.getAssertionPool().getAssertions(typeAssertion.getSubject(), SKOS.prefLabel, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
            if(prefLabels.isEmpty()) {
                continue;
            }
            
            JSONObject typeObj = new JSONObject();
            typeObj.put("uri", typeAssertion.getSubject().getURI());
            typeObj.put("prefLabel", prefLabels.get(0).getStatement().getString());
         
            typeArray.put(typeObj);
        }
        
        List<JSONObject> l = JsonUtility.getList(typeArray, JSONObject.class);
        l.sort((a,b) -> {
            return a.getString("prefLabel").compareToIgnoreCase(b.getString("prefLabel"));
        });
        
        resp.type("application/json");
        return result.toString(2);
    }
    
    public Object getPositiveProperties(Request req, Response resp) {
        saveRequest(req);
        
        JSONObject result = new JSONObject();
        
        JSONArray propArray = new JSONArray();
        result.put("properties", propArray);
        
        List<Assertion> propAssertions = manager.getAssertionPool().getAssertions(null, RDF.type, RDF.Property, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
        for(Assertion propAssertion : propAssertions) {
            
            List<Assertion> prefLabels = manager.getAssertionPool().getAssertions(propAssertion.getSubject(), SKOS.prefLabel, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
            if(prefLabels.isEmpty()) {
                continue;
            }
            
            JSONObject typeObj = new JSONObject();
            typeObj.put("uri", propAssertion.getSubject().getURI());
            typeObj.put("prefLabel", prefLabels.get(0).getStatement().getString());
         
            propArray.put(typeObj);
        }
        
        List<JSONObject> l = JsonUtility.getList(propArray, JSONObject.class);
        l.sort((a,b) -> {
            return a.getString("prefLabel").compareToIgnoreCase(b.getString("prefLabel"));
        });
        
        resp.type("application/json");
        return result.toString(2);
    }
    
    public Object getSuggestions(Request req, Response resp) {
        saveRequest(req);
        
        JSONObject reqJson = new JSONObject(req.body());
        String typeArg = reqJson.getString("type");
        Map<String, Phase> type2phase = new HashMap<>();
        type2phase.put("merge", Phase.ConceptDiscovery);
        type2phase.put("typing", Phase.OntologyPopulation);
        type2phase.put("taxonomy", Phase.ConceptHierarchyDerivation);
        type2phase.put("relations", Phase.NonTaxonomicRelationLearning);
        
        int offsetP = reqJson.optInt("offsetP", 0);
        int offsetN = reqJson.optInt("offsetN", 0);
        
        Phase phase = type2phase.get(typeArg);
        if(phase == null) {
            return null;
        }
        
        //maybe takes some time? 
        Map<Resource, String> prefLabelMap = new HashMap<>();
        prefLabelMap.putAll(manager.getAssertionPool().getTypePrefLabelMap());
        prefLabelMap.putAll(manager.getAssertionPool().getPropertyPrefLabelMap());
        prefLabelMap.putAll(manager.getAssertionPool().getConceptPrefLabelMap(ConceptDiscovery.DEFAULT_TYPE, null));
        
        JSONObject result = new JSONObject();
        
        AssertionPool pool = manager.getAssertionPool();
        
        Property prop = null;
        switch(phase) {
            case ConceptDiscovery: prop = OWL.sameAs; break;
            case OntologyPopulation: prop = RDF.type; break;
            case ConceptHierarchyDerivation: prop = SKOS.broader; break;
            case NonTaxonomicRelationLearning: prop = null; break;
        }
        
        List<Assertion> suggested = pool.getAssertions(null, prop, null, phase, Intelligence.AI, null, null, 0);
        List<Assertion> accepted = pool.getAssertions(null, prop, null, phase, Intelligence.NI, null, Rating.Positive, 0);
        List<Assertion> declined = pool.getAssertions(null, prop, null, phase, Intelligence.NI, null, Rating.Negative, 0);
        
        //do not suggest negative ones
        suggested.removeIf(sug -> sug.getRating() == Rating.Negative);
        
        //remove unwanted assertions in OntologyPopulation phase
        if(phase == Phase.OntologyPopulation) {
            for(List<Assertion> list : Arrays.asList(suggested, accepted, declined)) {
                list.removeIf(sug -> 
                    (sug.getObject().equals(RDFS.Class) ||
                     sug.getObject().equals(RDF.Property))
                );
            }
            
            //remove the type suggestion if the concept is not positive yet 
            suggested.removeIf(sug -> {
                return pool.getAssertions(sug.getSubject(), RDF.type, ConceptDiscovery.DEFAULT_TYPE, 
                        Phase.ConceptDiscovery, null, null, Rating.Positive, 0).isEmpty();
            });
        }
        
        BiConsumer<Assertion, JSONObject> extra = (assertion, json) -> {
            JSONObject prefLabels = new JSONObject();
            prefLabels.put("subject", prefLabelMap.get(assertion.getSubject()));
            prefLabels.put("predicate", prefLabelMap.get(assertion.getStatement().getPredicate()));
            prefLabels.put("object", prefLabelMap.get(assertion.getObject()));
            json.put("prefLabels", prefLabels);
            
            JSONObject uris = new JSONObject();
            uris.put("subject", assertion.getSubject().getURI());
            uris.put("predicate", assertion.getStatement().getPredicate().getURI());
            uris.put("object", assertion.getObject().getURI());
            json.put("uris", uris);
        };
        
        JSONObject acceptedPagination = new JSONObject();
        result.put("acceptedPagination", acceptedPagination);
        accepted.sort((a,b) -> b.getWhen().compareTo(a.getWhen()));
        List<Assertion> acceptedSublist = calculateList(offsetP, 5, accepted, acceptedPagination);
        result.put("accepted", Assertion.toJsonArray(acceptedSublist, extra));
        
        JSONObject declinedPagination = new JSONObject();
        result.put("declinedPagination", declinedPagination);
        declined.sort((a,b) -> b.getWhen().compareTo(a.getWhen()));
        List<Assertion> declinedSublist = calculateList(offsetN, 5, declined, declinedPagination);
        result.put("declined", Assertion.toJsonArray(declinedSublist, extra));
        
        
        result.put("suggested", Assertion.toJsonArrayOrderByConfidence(suggested, extra));
        
        resp.type("application/json");
        return result.toString(2);
    }
    
    public Object getStatus(Request req, Response resp) {
        saveRequest(req);
        
        UserPrincipal userP = authController.getUserPrincipal(req);
        
        JSONObject result = new JSONObject();
        result.put("username", userP.getUserName());
        
        StatusManager status = manager.getStatusManager();
        
        status.calculateAll(true);
        
        status.fillJSON(result);
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        result.put("time", formatter.format(LocalDateTime.now()));
        
        resp.type("application/json");
        return result.toString(2);
    }
    
    public Object download(Request req, Response resp) {
        
        String name = req.params("name");
        
        if(name.endsWith("ttl")) {
         
            StatusManager status = manager.getStatusManager();
            
            Model model; 
            switch(name) {
                case "terminology.ttl": 
                    model = status.getTerminologyModel();
                    break;
                
                case "assertions.ttl": 
                    model = status.getAssertionModel();
                    break;
                    
                case "topic-statements.ttl": 
                    model = status.getTopicModel();
                    break;
                    
                default:
                    model = ModelFactory.createDefaultModel();
                    break;
            }
            
            StringWriter sw = new StringWriter();
            model.write(sw, "TTL");
            
            resp.type("text/turtle; charset=utf-8");
            return sw.toString();
        }
        
        return null;
    }
    
    public Object visual(Request req, Response resp) {
        String name = req.params("name");
        
        VisualManager visualManager = manager.getVisualManager();
        
        String content;
        switch(name) {
            case "graph": 
                content = visualManager.getNonTaxGraph();
                break;
                
            case "taxonomy":
                content = visualManager.getTaxonomy();
                break;

            default:
                content = ""; 
                break;
        }
        
        resp.type("text/html; charset=utf-8");
        return content;
    }
    
    //assert -------------
    
    private Object addType(Request req, Response resp) {
        saveRequest(req);
        
        UserPrincipal userP = authController.getUserPrincipal(req);
        
        JSONObject reqJson = new JSONObject(req.body());
        
        String typeName = reqJson.getString("typeName").trim();
        
        Set<String> labelSet = new HashSet<>(manager.getAssertionPool().getTypePrefLabelMap().values());
        if(labelSet.contains(typeName)) {
            return getTypes(req, resp);
        }
        
        Resource type = manager.getAssertionPool().createType();
        
        List<Assertion> assertions = new ArrayList<>();
        
        Assertion typeAssertion = new Assertion();
        typeAssertion.setStatement(KecsApp.creator.createStatement(type, RDF.type, RDFS.Class));
        typeAssertion.setPhase(Phase.OntologyPopulation);
        typeAssertion.setOpinion(Intelligence.NI, userP.getUserName(), LocalDateTime.now(), Rating.Positive, 1.0);
        assertions.add(typeAssertion);
        
        Assertion prefLabelAssertion = new Assertion();
        prefLabelAssertion.setStatement(KecsApp.creator.createStatement(type, SKOS.prefLabel, typeName));
        prefLabelAssertion.setPhase(Phase.OntologyPopulation);
        prefLabelAssertion.setOpinion(Intelligence.NI, userP.getUserName(), LocalDateTime.now(), Rating.Positive, 1.0);
        assertions.add(prefLabelAssertion);
        
        sendAssertion(assertions, userP.getUserName());
        
        return getTypes(req, resp);
    }
    
    private Object addProperty(Request req, Response resp) {
        saveRequest(req);
        
        UserPrincipal userP = authController.getUserPrincipal(req);
        
        JSONObject reqJson = new JSONObject(req.body());
        
        String propName = reqJson.getString("propertyName").trim();
        
        Set<String> labelSet = new HashSet<>(manager.getAssertionPool().getPropertyPrefLabelMap().values());
        if(labelSet.contains(propName)) {
            return getTypes(req, resp);
        }
        
        Resource prop = manager.getAssertionPool().createProperty();
        
        List<Assertion> assertions = new ArrayList<>();
        
        Assertion typeAssertion = new Assertion();
        typeAssertion.setStatement(KecsApp.creator.createStatement(prop, RDF.type, RDF.Property));
        typeAssertion.setPhase(Phase.OntologyPopulation);
        typeAssertion.setOpinion(Intelligence.NI, userP.getUserName(), LocalDateTime.now(), Rating.Positive, 1.0);
        assertions.add(typeAssertion);
        
        Assertion prefLabelAssertion = new Assertion();
        prefLabelAssertion.setStatement(KecsApp.creator.createStatement(prop, SKOS.prefLabel, propName));
        prefLabelAssertion.setPhase(Phase.OntologyPopulation);
        prefLabelAssertion.setOpinion(Intelligence.NI, userP.getUserName(), LocalDateTime.now(), Rating.Positive, 1.0);
        assertions.add(prefLabelAssertion);
        
        Assertion domainAssertion = new Assertion();
        domainAssertion.setStatement(KecsApp.creator.createStatement(prop, RDFS.domain, ConceptDiscovery.DEFAULT_TYPE));
        domainAssertion.setPhase(Phase.OntologyPopulation);
        domainAssertion.setOpinion(Intelligence.NI, userP.getUserName(), LocalDateTime.now(), Rating.Positive, 1.0);
        assertions.add(domainAssertion);
        
        Assertion rangeAssertion = new Assertion();
        rangeAssertion.setStatement(KecsApp.creator.createStatement(prop, RDFS.range, ConceptDiscovery.DEFAULT_TYPE));
        rangeAssertion.setPhase(Phase.OntologyPopulation);
        rangeAssertion.setOpinion(Intelligence.NI, userP.getUserName(), LocalDateTime.now(), Rating.Positive, 1.0);
        assertions.add(rangeAssertion);
        
        sendAssertion(assertions, userP.getUserName());
        
        return getProperties(req, resp);
    }
    
    //special method for type handling
    private Object assertType(Request req, Response resp) {
        saveRequest(req);
        
        UserPrincipal userP = authController.getUserPrincipal(req);
        
        JSONObject reqJson = new JSONObject(req.body());
        
        String conceptUri = reqJson.getString("conceptUri");
        String typeUri = reqJson.getString("typeUri");
        String ratingArg = reqJson.optString("rating");
        Rating rating = Rating.Positive;
        if(!ratingArg.isEmpty()) {
            rating = Rating.valueOf(ratingArg);
        }
        
        List<Assertion> assertions = new ArrayList<>();
        
        Resource concept = KecsApp.creator.createResource(conceptUri);
        Resource type = KecsApp.creator.createResource(typeUri);
        
        addTypeAssertion(concept, type, rating, userP.getUserName(), assertions, manager.getAssertionPool());
        
        sendAssertion(assertions, userP.getUserName());
        
        resp.status(200);
        return "";
    }
    
    //special method for merge
    //we will merge into left, so right will be removed
    private Object assertMerge(Request req, Response resp) {
        saveRequest(req);
        
        UserPrincipal userP = authController.getUserPrincipal(req);
        
        JSONObject reqJson = new JSONObject(req.body());
        
        String conceptUriLeft = reqJson.getString("conceptUriLeft");
        String conceptUriRight = reqJson.getString("conceptUriRight");
        String ratingArg = reqJson.optString("rating");
        Rating rating = Rating.Positive;
        if(!ratingArg.isEmpty()) {
            rating = Rating.valueOf(ratingArg);
        }
        
        Resource left = KecsApp.creator.createResource(conceptUriLeft);
        Resource right = KecsApp.creator.createResource(conceptUriRight);
        
        List<Assertion> assertions = new ArrayList<>();
        
        addMergeAssertion(left, right, rating, userP.getUserName(), assertions);
        
        //all substituted assertions are send
        sendAssertion(assertions, userP.getUserName());
        
        resp.status(200);
        return "";
    }
    
    private Object assertNegativeTermsRecursively(Request req, Response resp) {
        saveRequest(req);
        
        UserPrincipal userP = authController.getUserPrincipal(req);
        
        JSONObject reqJson = new JSONObject(req.body());
        
        String fileUri = reqJson.getString("fileUri");
        int id = KecsUtils.getId(fileUri);
        
        FileInfo parentFile = (FileInfo) manager.getFileInfoStorage().get(id);
        
        List<StorageItem> list;
        if(parentFile.isDirectory()) {
            FolderInfo parent = (FolderInfo) parentFile;
            list = manager.getFileInfoStorage().getTree(parent);
        } else {
            list = new ArrayList<>();
            list.add(parentFile);
        }
        
        for(StorageItem item : list) {
            FileInfo fi = (FileInfo) item;
            
            Resource fileResource = KecsApp.creator.createResource(new JSONObject(fi.getMeta()).getString("uri"));
            
            List<Assertion> posAssertions = manager.getAssertionPool().getAssertions(fileResource, KECS.containsDomainTerm, null, 
                    Phase.DomainTerminologyExtraction, null, null, Rating.Positive, 0);
            
            //turn positive into negative
            for(Assertion assertion : posAssertions) {
                manager.getAssertionPool().assertStatement(
                        assertion.getStatement(), Phase.DomainTerminologyExtraction, 
                        Intelligence.NI, userP.getUserName(), Rating.Negative, 1);
            }
        }
        
        //save
        manager.getAssertionPool().commit();
        
        JSONObject result;
        //if(parentFile.isDirectory()) {
        //    result = getChildren(fileUri);
        //} else {
            result = browseChild(fileUri);
        //}
        
        resp.type("application/json");
        return result.toString(2);
    }
    
    private Object assertNewConcept(Request req, Response resp) {
        saveRequest(req);
        
        UserPrincipal userP = authController.getUserPrincipal(req);
        String username = userP.getUserName();
        
        JSONObject reqJson = new JSONObject(req.body());
        
        String prefLabel = reqJson.getString("prefLabel");
        String typeUri = reqJson.getString("type");
        
        Resource type = KecsApp.creator.createResource(typeUri);
        
        AssertionPool pool = manager.getAssertionPool();
        
        Resource newConcept = pool.createConcept();
        
        pool.assertStatement(newConcept, RDF.type, ConceptDiscovery.DEFAULT_TYPE, Phase.ConceptDiscovery, Intelligence.NI, username, Rating.Positive, 1.0);
        pool.assertStatement(newConcept, SKOS.prefLabel, prefLabel, Phase.ConceptDiscovery, Intelligence.NI, username, Rating.Positive, 1.0);
        pool.assertStatement(newConcept, SKOS.hiddenLabel, prefLabel, Phase.ConceptDiscovery, Intelligence.NI, username, Rating.Positive, 1.0);
        pool.assertStatement(newConcept, RDF.type, type, Phase.OntologyPopulation, Intelligence.NI, username, Rating.Positive, 1.0);
        pool.commit();
        
        pool.notifyListenersRecursively(manager.getFileInfoStorage());
        
        JSONObject conceptObj = browseConcept(newConcept, 0, defaultLimit, 0, defaultLimit);
        
        resp.type("application/json");
        return conceptObj.toString(2);
    }
    
    //single methods
    
    public static void addTypeAssertion(Resource concept, Resource type, Rating rating, String username, List<Assertion> assertions, AssertionPool pool) {
        Statement positiveStmt = KecsApp.creator.createStatement(concept, RDF.type, type);
        
        if(rating == Rating.Positive) {
            //all positive ones are negative (here is not the NamedIndividual because it is in phase ConceptDiscovery)
            for(Assertion typeAssertion : pool.getAssertions(concept, RDF.type, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0)) {
                
                //do not make it negative if you make it directly positive
                if(positiveStmt.equals(typeAssertion.getStatement())) {
                    continue;
                }
                
                Assertion changeAssertion = new Assertion();
                changeAssertion.setStatement(typeAssertion.getStatement());
                changeAssertion.setPhase(Phase.OntologyPopulation);
                changeAssertion.setOpinion(Intelligence.NI, username, LocalDateTime.now(), Rating.Negative, 1.0);
                assertions.add(changeAssertion);
            }
        }
        
        //assert one that is the new type
        //never assert named individual because this is part of ConceptDiscovery
        if(!type.equals(ConceptDiscovery.DEFAULT_TYPE)) {
            Assertion changeAssertion = new Assertion();
            changeAssertion.setStatement(positiveStmt);
            changeAssertion.setPhase(Phase.OntologyPopulation);
            changeAssertion.setOpinion(Intelligence.NI, username, LocalDateTime.now(), rating, 1.0);
            assertions.add(changeAssertion);
        }
    }
    
    private void addMergeAssertion(Resource left, Resource right, Rating rating, String username, List<Assertion> assertions) {
        if(rating == Rating.Positive) {
            AssertionPool pool = manager.getAssertionPool();

            List<Assertion> outgoing = pool.getAssertions(right, null, null, null, null, null, null, 0);
            List<Assertion> incoming = pool.getAssertions(null, null, right, null, null, null, null, 0);

            for(Assertion out : outgoing) {

                Statement stmt = out.getStatement();
                
                //do not copy type and prefLabel assertions
                if(stmt.getPredicate().equals(SKOS.prefLabel) ||
                   stmt.getPredicate().equals(RDF.type)) {
                    continue;
                }
                
                //avoid after substitution to point to itself
                if(out.getStatement().getPredicate().equals(OWL.sameAs)) {
                    if(left.equals(stmt.getObject())) {
                        continue;
                    }
                }

                
                Statement subst = KecsApp.creator.createStatement(left, stmt.getPredicate(), stmt.getObject());
                out.setStatement(subst);
                
                assertions.add(out);
            }

            for(Assertion in : incoming) {
                //the original left sameAs right is not copied
                if(in.getStatement().getPredicate().equals(OWL.sameAs)) {
                    continue;
                }
                
                Statement stmt = in.getStatement();
                Statement subst = KecsApp.creator.createStatement(stmt.getSubject(), stmt.getPredicate(), left);
                in.setStatement(subst);
                
                assertions.add(in);
            }

            //assertions.forEach(a -> System.out.println(a));
            
            //all statements about right will be removed (this commits)
            pool.removeAllAbout(right);

        } else if(rating == Rating.Negative) {
            
            //both directions then
            List<Statement> stmts = new ArrayList<>();
            stmts.add(KecsApp.creator.createStatement(left, OWL.sameAs, right));
            stmts.add(KecsApp.creator.createStatement(right, OWL.sameAs, left));
            
            for(Statement negStatement : stmts) {
                Assertion changeAssertion = new Assertion();
                changeAssertion.setStatement(negStatement);
                changeAssertion.setPhase(Phase.ConceptDiscovery);
                changeAssertion.setOpinion(Intelligence.NI, username, LocalDateTime.now(), rating, 1.0);
                assertions.add(changeAssertion);
            }
            
        }
    }
    
    //bulk methods
    
    private Object explorerSearchCreate(Request req, Response resp) {
        saveRequest(req);
        
        UserPrincipal userP = authController.getUserPrincipal(req);
        
        JSONObject result = new JSONObject(req.body());
        
        String typeUri = result.getString("typeUri");
        Resource type = typeUri.isEmpty() ? null : KecsApp.creator.createResource(typeUri);
        
        DomainTerminologyExtraction dte = manager.getDomainTerminologyExtraction();
        
        List<Object[]> fileTermList = new ArrayList<>();
        JSONArray matches = result.getJSONArray("matches");
        for(int i = 0; i < matches.length(); i++) {
            
            JSONObject match = matches.getJSONObject(i);
            
            if(!match.getBoolean("selected")) {
                continue;
            }
            
            fileTermList.add(new Object[]{ KecsApp.creator.createResource(match.getString("uri")), match.getString("middle") });
        }
        
        dte.createFromSearch(fileTermList, type, manager.getAssertionPool(), userP.getUserName(), result.getBoolean("regex"));
        
        resp.type("application/json");
        return "{}";
    }
    
    private Object assertTypes(Request req, Response resp) {
        saveRequest(req);
        
        UserPrincipal userP = authController.getUserPrincipal(req);
        
        JSONObject reqJson = new JSONObject(req.body());
        
        JSONArray suggested = reqJson.getJSONArray("suggested");
        Rating rating = reqJson.getEnum(Rating.class, "rating");
        
        Set<Resource> visited = new HashSet<>();
        
        List<Assertion> assertions = new ArrayList<>();
        for(int i = 0; i < suggested.length(); i++) {
            JSONObject entry = suggested.getJSONObject(i);
            
            Resource concept = KecsApp.creator.createResource(entry.getJSONObject("uris").getString("subject"));
            Resource type = KecsApp.creator.createResource(entry.getJSONObject("uris").getString("object"));
            
            //ensure that only the type wins that comes first which has the highest confidence by default
            //this means we skip if we already visited the concept (if rating is positive)
            //TODO maybe sort suggested again by confidence to be sure
            if(rating == Rating.Positive && visited.contains(concept)) {
                continue;
            }
            visited.add(concept);
            
            addTypeAssertion(concept, type, rating, userP.getUserName(), assertions, manager.getAssertionPool());
        }
        
        //all are send
        sendAssertion(assertions, userP.getUserName());
        
        resp.status(200);
        return "";
    }
    
    private Object assertMerges(Request req, Response resp) {
        saveRequest(req);
        
        UserPrincipal userP = authController.getUserPrincipal(req);
        
        JSONObject reqJson = new JSONObject(req.body());
        
        JSONArray suggested = reqJson.getJSONArray("suggested");
        Rating rating = reqJson.getEnum(Rating.class, "rating");
        
        Set<Resource> visited = new HashSet<>();
        
        List<Assertion> assertions = new ArrayList<>();
        for(int i = 0; i < suggested.length(); i++) {
            JSONObject entry = suggested.getJSONObject(i);
            
            Resource left = KecsApp.creator.createResource(entry.getJSONObject("uris").getString("subject"));
            Resource right = KecsApp.creator.createResource(entry.getJSONObject("uris").getString("object"));
            
            //if right already visited it is already merged with some other concept and
            //that is why it is already removed, so we skip it better
            if(rating == Rating.Positive && visited.contains(right)) {
                continue;
            }
            visited.add(right);
            
            addMergeAssertion(left, right, rating, userP.getUserName(), assertions);
        }
        
        //all are send
        sendAssertion(assertions, userP.getUserName());
        
        resp.status(200);
        return "";
    }
    
    private Object sendAssertion(Request req, Response resp) {
        saveRequest(req);
        
        UserPrincipal userP = authController.getUserPrincipal(req);
        //TODO use username from JWT
        
        boolean isArray = req.body().trim().startsWith("[");
        boolean isObject = req.body().trim().startsWith("{");
        
        //System.out.println();
        //System.out.println("Body: " + req.body());
        
        List<Assertion> assertions = new ArrayList<>();
        if(isObject) {
            JSONObject json = new JSONObject(req.body());
            assertions.add(Assertion.fromJson(json));
        } else if(isArray) {
            JSONArray array = new JSONArray(req.body());
            for(int i = 0; i < array.length(); i++) {
                assertions.add(Assertion.fromJson(array.getJSONObject(i)));
            }
        }
        
        sendAssertion(assertions, userP.getUserName());
        
        resp.status(200);
        return "";
    }
    
    private void sendAssertion(List<Assertion> assertions, String username) {
        synchronized(manager) {
            
            long begin = System.currentTimeMillis();
            
            //update username to the user who is logged in
            assertions.forEach(a -> {
                if(a.hasNaturalOpinion())
                    a.getNaturalOpinion().setName(username);
            });
            
            JSONObject historyEntry = new JSONObject();
            historyEntry.put("when", LocalDateTime.now().toString());
            historyEntry.put("list", Assertion.toJsonArrayOrderByRating(assertions));

            //in history every send assertion call is stored
            KecsUtils.saveHistoryEntry(historyEntry, resultFolder);

            //System.out.println(historyEntry.toString(2));
            //assertions.forEach(as -> System.out.println(as));
        
            for(Assertion assertion : assertions) {
                manager.getAssertionPool().assertAssertion(assertion);
            }
            
            manager.getAssertionPool().commit();
            
            manager.getAssertionPool().notifyListenersRecursively(manager.getFileInfoStorage());
            
            long end = System.currentTimeMillis();
        }
    }
    
    private Object sendEvent(Request req, Response resp) {
        UserPrincipal userP = authController.getUserPrincipal(req);
        
        //do not track special readonly user
        if(userP.getUserName().equals("readonly"))
            return "";
        
        saveRequest(req);
        
        eventCounter++;
        
        if(eventCounter == 0 || eventCounter >= manager.getSettings().getSaveStatusThreshold()) {
            StatusManager status = manager.getStatusManager();
            
            //calculates but also saves it
            //takes time when filesystem is large
            //we do it in background with a thread so that user does not have to wait
            Thread thread = new Thread(() -> {
                //avoid blocking db for too long
                status.calculateAll(true);
            });
            thread.start();
            
            //reset counter
            eventCounter = 0;
        }
        
        return "";
    }
    
    //helper --------------------
    
    protected <T> List<T> calculateList(Integer offsetParam, Integer limitParam, List<T> resources, JSONObject result) {
        int total = resources.size();
        int offset = offsetParam == null ? 0 : offsetParam;
        if(offset < 0) {
            offset = 0;
        }
        if(offset >= total) {
            offset = 0;
        }
        
        int limit = limitParam == null ? defaultLimit : limitParam;
        int pages = limit == 0 ? 0 : ((int) (total / (float) limit) + ((total % limit) == 0 ? 0 : 1));
        int page = (limit == 0 ? 0 : offset / limit) + 1;
        
        List<T> sublist = resources.subList(offset, Math.min(offset + limit, resources.size()));
        int shown = sublist.size();
        
        //resource and page states
        result.put("total", total);
        result.put("shown", shown);
        result.put("offset", offset);
        result.put("limit", limit);
        result.put("page", page);
        
        //String limitStr = limit != defaultLimit ? ("&limit=" + limit) : "";
        
        //Integer limitValue = limit != defaultLimit ? limit : null;
        
        int rightRestPages = 5 - Math.min(5, pages - page);
        //int leftRestPages  = 5 - Math.min(5, page  -    1);
        
        JSONArray pageArray = new JSONArray();
        int startPage = Math.max(1, page - 5 - rightRestPages);
        //int endPage = Math.min(pages, page + 5);
        for(int i = startPage; i <=  Math.min(pages, startPage+10); i++) {
            JSONObject obj = new JSONObject();
            
            obj.put("active", i == page);
            obj.put("number", i);
            obj.put("offset", (i-1) * limit);
            //obj.put("path", req.uri() + "?offset=" + (limit*(i-1)) + limitStr);
            //obj.put("path", createQueryParameterUri(req, "offset", (limit*(i-1)), "limit", limitValue));
            
            pageArray.put(obj);
        }
        result.put("pages", pageArray);
        
        //page links
        /*
        //result.put("first", req.uri());
        if(offset + limit < total) {
            //result.put("next", createQueryParameterUri(req, "offset", (offset + limit), "limit", limitValue));
        }
        if(offset > 0 && offset - limit >= 0) {
            //result.put("prev", createQueryParameterUri(req, "offset", (offset - limit), "limit", limitValue));
        }
        if(total > limit) {
            //result.put("last", createQueryParameterUri(req, "offset", (total - limit), "limit", limitValue));
        }
        */
        
        return sublist;
    }
    
    private JSONObject toJson(FileInfo fi) {
        if(fi == null)
            return null;
        
        JSONObject meta = new JSONObject(fi.getMeta());
        
        JSONObject obj = new JSONObject();
        obj.put("uri", meta.getString("uri"));
        obj.put("prefLabel", fi.getName());
        obj.put("isFile", !fi.isDirectory());
        obj.put("path", fi.getPath());
        return obj;
    }
    
    private void conceptToJson(String prefLabel, Assertion conceptAssertion, TypeWithIntel typeWithIntel, Map<Resource, String> typeMap, JSONObject resultEntry) {
        resultEntry.put("prefLabel", prefLabel);
        resultEntry.put("uri", conceptAssertion.getSubject());
        resultEntry.put("assertion", Assertion.toJson(conceptAssertion));

        if(typeWithIntel != null) {
            JSONObject typeObj = new JSONObject();
            typeObj.put("prefLabel", typeMap.get(typeWithIntel.getType()));
            typeObj.put("uri", typeWithIntel.getType().getURI());
            typeObj.put("intelligence", typeWithIntel.getIntel().name());

            resultEntry.put("type", typeObj);
        }
    }
    
    private void saveRequest(Request req) {
        
        JSONObject interaction = new JSONObject();
        interaction.put("when", LocalDateTime.now().toString());
        interaction.put("uri", req.uri());
        
        try {
            UserPrincipal userP = authController.getUserPrincipal(req);
            interaction.put("username", userP.getUserName());
        } catch(Exception e) {
            //ignore
        }
        
        try {
            JSONObject body = new JSONObject(req.body());
            interaction.put("body", body);
        } catch(Exception e1) {
            try {
                JSONArray array = new JSONArray(req.body());
                JSONObject body = new JSONObject();
                body.put("array", array);
                interaction.put("body", body);
            } catch(Exception e2) {
                //ignore
            } 
        } 
        
        //System.out.println(interaction.toString(2));
        
        KecsUtils.saveInteraction(interaction, resultFolder);
    }
    
}
