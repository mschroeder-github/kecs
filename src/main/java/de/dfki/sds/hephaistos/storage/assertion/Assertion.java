
package de.dfki.sds.hephaistos.storage.assertion;

//import de.dfki.sds.kecs.util.JsonUtility;
import de.dfki.sds.hephaistos.storage.StorageItem;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A assertion for a statement that contains artifical and natural opinions.
 */
public class Assertion implements StorageItem {

    private Phase phase;
    private Statement statement;
    
    private Opinion artificalOpinion;
    private Opinion naturalOpinion;

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public Statement getStatement() {
        return statement;
    }

    public Resource getSubject() {
        return getStatement().getSubject();
    }
    
    public Resource getObject() {
        return getStatement().getResource();
    }
    
    public void setStatement(Statement statement) {
        this.statement = statement;
    }

    public Opinion getArtificalOpinion() {
        return artificalOpinion;
    }
    
    public boolean hasArtificalOpinion() {
        return artificalOpinion != null;
    }

    public void setArtificalOpinion(Opinion artificalOpinion) {
        this.artificalOpinion = artificalOpinion;
    }

    public Opinion getNaturalOpinion() {
        return naturalOpinion;
    }
    
    public boolean hasNaturalOpinion() {
        return naturalOpinion != null;
    }

    public void setNaturalOpinion(Opinion naturalOpinion) {
        this.naturalOpinion = naturalOpinion;
    }
    
    
    public Opinion getOpinion(Intelligence intel) {
        if(intel == Intelligence.AI)
            return artificalOpinion;
        if(intel == Intelligence.NI)
            return naturalOpinion;
        
        throw new RuntimeException("");
    }
    
    public void setOpinion(Opinion opinion) {
        if(opinion.getIntelligence() == Intelligence.AI)
            artificalOpinion = opinion;
        
        if(opinion.getIntelligence() == Intelligence.NI)
            naturalOpinion = opinion;
    }
    
    //returns true if information is new
    public boolean setOpinion(Intelligence intel, String name, LocalDateTime when, Rating rating, double confidence) {
        
        Opinion op = getOpinion(intel);
        
        if(op == null) {
            Opinion newOp = new Opinion(intel, name, when, rating, confidence);
            setOpinion(newOp);
            
            return true;
            
        } else {
            
            if(!op.getName().equals(name)) {
                throw new RuntimeException(statement + " has already opinion " + op + " that differ in name");
            }
            
            //one can change the rating because the agent knows better now
            /*
            if(!op.getRating().equals(rating)) {
                throw new RuntimeException(statement + " has already opinion " + op + " that differ in rating");
            }
            */
            boolean newInformation = op.getRating() != rating;
            
            op.setRating(rating);
            op.setConfidence(confidence);
            op.setWhen(when);
            
            return newInformation;
        }
    }
    
    public Opinion getPrimaryOpinion() {
        if(hasNaturalOpinion()) {
            return naturalOpinion;
        }
        if(hasArtificalOpinion()) {
            return artificalOpinion;
        }
        throw new RuntimeException("both opinions are missing");
    }
    
    public Intelligence getIntelligence() {
        return getPrimaryOpinion().getIntelligence();
    }

    public String getName() {
        return getPrimaryOpinion().getName();
    }

    public LocalDateTime getWhen() {
        return getPrimaryOpinion().getWhen();
    }

    public Rating getRating() {
        return getPrimaryOpinion().getRating();
    }

    public double getConfidence() {
        return getPrimaryOpinion().getConfidence();
    }

    public JSONObject getStatementJson() {
        return Assertion.toJsonStatement(statement);
    }
    
    public void setStatementJson(JSONObject stmt) {
        this.statement = Assertion.parseStatement(stmt);
    }
    
    @Override
    public String toString() {
        return "Assertion{" + "phase=" + phase + ", statement=" + statement + ", artificalOpinion=" + artificalOpinion + ", naturalOpinion=" + naturalOpinion + '}';
    }
    
    public static JSONObject toJson(List<Assertion> assertions) {
        JSONObject asObj = new JSONObject();
        
        Map<Rating, List<Assertion>> rating2assertions = AssertionPool.ratingMap(assertions);
        for(Rating rating : rating2assertions.keySet()) {
            asObj.put(rating.toString(), toJsonArrayOrderByRating(rating2assertions.get(rating)));
        }
        
        return asObj;
    }
    
    public static JSONArray toJsonArrayOrderByRating(List<Assertion> assertions) {
        return toJsonArrayOrderByRating(assertions, null);
    }
    
    public static JSONArray toJsonArrayOrderByRating(List<Assertion> assertions, BiConsumer<Assertion, JSONObject> extra) {
        JSONArray array = toJsonArray(assertions, extra);
        
        List<JSONObject> l = getList(array, JSONObject.class);
        l.sort((a,b) -> {
            return a.getEnum(Rating.class, "rating").compareTo(b.getEnum(Rating.class, "rating"));
        });
        
        return array;
    }
    
    public static JSONArray toJsonArrayOrderByIntel(List<Assertion> assertions) {
        return toJsonArrayOrderByIntel(assertions, null);
    }
    
    public static JSONArray toJsonArrayOrderByIntel(List<Assertion> assertions, BiConsumer<Assertion, JSONObject> extra) {
        JSONArray array = toJsonArray(assertions, extra);
        
        List<JSONObject> l = getList(array, JSONObject.class);
        l.sort((a,b) -> {
            //NI first
            return b.getEnum(Intelligence.class, "intelligence").compareTo(a.getEnum(Intelligence.class, "intelligence"));
        });
        
        return array;
    }
    
    public static JSONArray toJsonArrayOrderByRatingIntelConfidence(List<Assertion> assertions) {
        return toJsonArrayOrderByRatingIntelConfidence(assertions, null);
    }
    
    public static void orderByRatingIntelConfidence(List<Assertion> assertions) {
        assertions.sort((a,b) -> {
            
            int cmpRating = a.getRating().compareTo(b.getRating());
            
            if(cmpRating == 0) {
            
                int cmpIntel = a.getIntelligence().compareTo(b.getIntelligence());

                if(cmpIntel == 0) {
                    return Double.compare(b.getConfidence(), a.getConfidence());
                }
            
                return cmpIntel;
            }
            
            return cmpRating;
        });
    }
    
    public static JSONArray toJsonArrayOrderByRatingIntelConfidence(List<Assertion> assertions, BiConsumer<Assertion, JSONObject> extra) {
        JSONArray array = toJsonArray(assertions, extra);
        
        List<JSONObject> l = getList(array, JSONObject.class);
        l.sort((a,b) -> {
            
            int cmpRating = a.getEnum(Rating.class, "rating").compareTo(b.getEnum(Rating.class, "rating"));
            
            if(cmpRating == 0) {
            
                int cmpIntel = a.getEnum(Intelligence.class, "intelligence").compareTo(b.getEnum(Intelligence.class, "intelligence"));

                if(cmpIntel == 0) {
                    return Double.compare(b.getDouble("confidence"), a.getDouble("confidence"));
                }
            
                return cmpIntel;
            }
            
            return cmpRating;
        });
        
        return array;
    }
    
    public static JSONArray toJsonArrayOrderByConfidence(List<Assertion> assertions) {
        return toJsonArrayOrderByConfidence(assertions, null);
    }
    
    public static JSONArray toJsonArrayOrderByConfidence(List<Assertion> assertions, BiConsumer<Assertion, JSONObject> extra) {
        JSONArray array = toJsonArray(assertions, extra);
        
        List<JSONObject> l = getList(array, JSONObject.class);
        l.sort((a,b) -> {
            return Double.compare(b.getDouble("confidence"), a.getDouble("confidence"));
        });
        
        return array;
    }
    
    public static JSONArray toJsonArray(List<Assertion> assertions) {
        return toJsonArray(assertions, null);
    }
    
    public static JSONArray toJsonArray(List<Assertion> assertions, BiConsumer<Assertion, JSONObject> extra) {
        JSONArray array = new JSONArray();
        for(Assertion assertion : assertions) {
            JSONObject json = toJson(assertion);
            if(extra != null) {
                extra.accept(assertion, json);
            }
            array.put(json);
        }
        return array;
    }
    
    public static JSONObject toJson(Assertion assertion) {
        JSONObject obj = new JSONObject();
            
        obj.put("statement", assertion.getStatementJson());
        obj.put("phase", assertion.getPhase());

        obj.put("intelligence", assertion.getIntelligence());
        obj.put("name", assertion.getName());
        obj.put("rating", assertion.getRating());
        obj.put("confidence", assertion.getConfidence());
        
        if(assertion.getStatement().getObject().isLiteral()) {
            obj.put("fixedValue", assertion.getStatement().getString());
        } else if(assertion.getStatement().getObject().isURIResource()) {
            obj.put("fixedValue", assertion.getStatement().getResource().getURI());
        }

        obj.put("when", assertion.getWhen());
        
        return obj;
    }
    
    public static Assertion fromJson(JSONObject json) {
        Assertion assertion = new Assertion();
        
        assertion.setPhase(json.getEnum(Phase.class, "phase"));
        assertion.setStatementJson(json.getJSONObject("statement"));
        
        Opinion opinion = new Opinion();
        
        if(json.has("confidence")) {
            opinion.setConfidence(json.getDouble("confidence"));
        }
        if(json.has("name")) {
            opinion.setName(json.getString("name"));
        }
        if(json.has("intelligence")) {
            opinion.setIntelligence(json.getEnum(Intelligence.class, "intelligence"));
        }
        if(json.has("intelligence")) {
            opinion.setRating(json.getEnum(Rating.class, "rating"));
        }
        
        opinion.setWhen(LocalDateTime.now());
        
        assertion.setOpinion(opinion);
        
        return assertion;
    }
    
    
    private static final Model exchange = ModelFactory.createDefaultModel();

    public static Statement parseStatement(JSONObject statementJson) {
        synchronized (exchange) {
            Statement stmt = exchange.read(new StringReader(statementJson.toString()), null, "JSON-LD").listStatements().next();
            exchange.removeAll();
            return stmt;
        }
    }

    public static JSONObject toJsonStatement(Statement stmt) {
        synchronized (exchange) {
            exchange.add(stmt);
            StringWriter sw = new StringWriter();
            exchange.write(sw, "JSON-LD");
            exchange.removeAll();
            return new JSONObject(sw.toString());
        }
    }
    
    private static <T> List<T> getList(JSONArray array, Class<T> type) {
        try {
            Field list = array.getClass().getDeclaredField("myArrayList");
            list.setAccessible(true);
            List<T> l = (List<T>) list.get(array);
            list.setAccessible(false);
            return l;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}
