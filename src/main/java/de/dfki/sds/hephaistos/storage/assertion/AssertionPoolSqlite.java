
package de.dfki.sds.hephaistos.storage.assertion;

import de.dfki.sds.hephaistos.storage.InternalStorageMetaData;
import de.dfki.sds.hephaistos.storage.SQLiteUtility;
import de.dfki.sds.hephaistos.storage.StorageSummary;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.impl.LiteralImpl;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.sparql.util.NodeFactoryExtra;
import org.json.JSONObject;

/**
 * 
 */
public class AssertionPoolSqlite extends AssertionPool {

    private Connection connection;
    private String tablename;
    
    private static final String QUERY_PATH = "/de/dfki/sds/hephaistos/storage/assertion/";
    
    private String upsertQuery;
    
    //private PreparedStatement preparedStatement;
    
    private PreparedStatement[] preparedStatements;
    
    private File folder;
    
    public AssertionPoolSqlite(InternalStorageMetaData metaData, Connection connection, File folder) {
        super(metaData, folder);
        this.connection = connection;
        this.folder = folder;
        this.tablename = metaData.getId();
        
        //creaSQLiteUtility.execute(connection, getQuery(QUERY_PATH + "CreateTableAssertion.sql"));te tables
        SQLiteUtility.execute(connection, getQuery(QUERY_PATH + "CreateTableAssertion.sql"));
        SQLiteUtility.execute(connection, getQuery(QUERY_PATH + "CreateIndexSP.sql"));
        SQLiteUtility.execute(connection, getQuery(QUERY_PATH + "CreateIndexPO.sql"));
        SQLiteUtility.execute(connection, getQuery(QUERY_PATH + "CreateIndexP.sql"));
        
        upsertQuery = getQuery(QUERY_PATH + "UpsertAssertion.sql");
        
        //because takes some time we do it here once
        SQLiteUtility.run(connection, c -> {
            c.setAutoCommit(false);
        });
        
        //for AI and NI
        preparedStatements = new PreparedStatement[2];
    }
    
    @Override
    public void clear() {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public void remove() {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public StorageSummary summary() {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public long size() {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public void close() {
        //ignore
    }
    
    @Override
    public List<Assertion> getAssertions(Resource subject, Property predicate, RDFNode object, Phase phase, Intelligence intel, String name, Rating rating, double confidenceThreshold) {
        //SELECT
        
        StringBuilder querySB = new StringBuilder();
        querySB.append("SELECT * FROM \"Assertion\"\n");
        querySB.append("WHERE\n");
        
        StringJoiner whereJoiner = new StringJoiner(" AND ");
        if(subject != null) {
            whereJoiner.add("s = ?");
        }
        if(predicate != null) {
            whereJoiner.add("p = ?");
        }
        if(object != null) {
            whereJoiner.add("o = ?");
        }
        if(phase != null) {
            whereJoiner.add("phase = ?");
        }
        
        
        //ni_intelligence IS NOT NULL AND ...
        //OR
        //ni_intelligence IS NULL AND ai_intelligence IS NOT NULL AND ...
        
        boolean opinionPartNeed = intel != null || name != null || rating != null || confidenceThreshold > 0;
        
        if(opinionPartNeed) {
            Map<String, StringJoiner> m = new LinkedHashMap<>();
            m.put("ni_", new StringJoiner(" AND "));
            m.put("ai_", new StringJoiner(" AND "));

            List<String> prefixes = new ArrayList<>(Arrays.asList("ni_", "ai_"));
            if(intel != null) {
                if(intel == Intelligence.AI) {
                    prefixes.remove("ni_");
                    m.remove("ni_");
                } else if(intel == Intelligence.NI) {
                    prefixes.remove("ai_");
                    m.remove("ai_");
                }
            }
            
            StringJoiner orJoiner = new StringJoiner(" OR ", "(", ")");
            
            for(Entry<String, StringJoiner> entry : m.entrySet()) {

                String prefix = entry.getKey();
                StringJoiner joiner = entry.getValue();

                if(prefix.equals("ni_")) {
                    joiner.add("ni_intelligence IS NOT NULL");
                } else if(prefix.equals("ai_")) {
                    joiner.add("ni_intelligence IS NULL AND ai_intelligence IS NOT NULL");
                }

                if(name != null) {
                    joiner.add(prefix + "name = ?");
                }
                if(rating != null) {
                    joiner.add(prefix + "rating = ?");
                }
                if(confidenceThreshold > 0) {
                    joiner.add(prefix + "confidence >= ?");
                }

                orJoiner.add(entry.getValue().toString());
            }
            whereJoiner.add(orJoiner.toString());
        }
        
        querySB.append(whereJoiner.toString());
        
        //System.out.println(querySB);
        
        return SQLiteUtility.supply(connection, c -> {
        
            List<Assertion> assertions = new ArrayList<>();
            
            //System.out.println("------------");
            //System.out.println(querySB.toString());
            //System.out.println("------------");
            
            PreparedStatement pstmt = c.prepareStatement(querySB.toString());
        
            int paramIndex = 1;
            
            if(subject != null) {
                pstmt.setString(paramIndex++, subject.getURI());
            }
            if(predicate != null) {
                pstmt.setString(paramIndex++, predicate.getURI());
            }
            if(object != null) {
                pstmt.setString(paramIndex++, toString(object));
            }
            if(phase != null) {
                pstmt.setString(paramIndex++, phase.toString());
            }
            
            //if intel == null we have to fill both, so '2'
            for(int i = 0; i < (intel == null ? 2 : 1); i++) {
                if(name != null) {
                    pstmt.setString(paramIndex++, name);
                }
                if(rating != null) {
                    pstmt.setString(paramIndex++, rating.toString());
                }
                if(confidenceThreshold > 0) {
                    pstmt.setDouble(paramIndex++, confidenceThreshold);
                }
            }
            
            
            //long begin = System.currentTimeMillis();
            java.sql.ResultSet rs = pstmt.executeQuery();
            
            List<String> prefixes = new ArrayList<>(Arrays.asList("ni_", "ai_"));
            
            while(rs.next()) {
                Assertion assertion = new Assertion();
                Statement stmt = toStatement(new String[] { rs.getString("s"), rs.getString("p"), rs.getString("o") });
                assertion.setStatement(stmt);
                
                assertion.setPhase(Phase.valueOf(rs.getString("phase")));
                
                for(String prefix : prefixes) {
                    if(rs.getString(prefix + "intelligence") != null) {
                        Opinion opinion = new Opinion();
                        
                        opinion.setIntelligence(prefix.equals("ni_") ? Intelligence.NI : Intelligence.AI);
                        opinion.setName(rs.getString(prefix + "name"));
                        opinion.setRating(Rating.valueOf(rs.getString(prefix + "rating")));
                        opinion.setConfidence(rs.getDouble(prefix + "confidence"));
                        opinion.setWhen(LocalDateTime.ofEpochSecond(rs.getLong(prefix + "when"), 0, ZoneOffset.ofHours(0)));
                        
                        assertion.setOpinion(opinion);
                    }
                }
                
                assertions.add(assertion);
            }
            //long end = System.currentTimeMillis();
            
            rs.close();
            pstmt.close();
            
            /*
            System.out.println("getAssertions(" + 
                    Arrays.asList(subject, predicate, object, phase, intel, name, rating, confidenceThreshold) + ") took " + 
                    (end - begin) + " ms with " + assertions.size() + " entries"
            );
            */
            
            return assertions;
        });
    }
    
    //copy of getAssertions (2021-11-16) but with count modification
    public long getCount(Resource subject, Property predicate, RDFNode object, Phase phase, Intelligence intel, String name, Rating rating, double confidenceThreshold) {
        //SELECT
        
        StringBuilder querySB = new StringBuilder();
        querySB.append("SELECT COUNT(*) FROM \"Assertion\"\n");
        querySB.append("WHERE\n");
        
        StringJoiner whereJoiner = new StringJoiner(" AND ");
        if(subject != null) {
            whereJoiner.add("s = ?");
        }
        if(predicate != null) {
            whereJoiner.add("p = ?");
        }
        if(object != null) {
            whereJoiner.add("o = ?");
        }
        if(phase != null) {
            whereJoiner.add("phase = ?");
        }
        
        
        //ni_intelligence IS NOT NULL AND ...
        //OR
        //ni_intelligence IS NULL AND ai_intelligence IS NOT NULL AND ...
        
        boolean opinionPartNeed = intel != null || name != null || rating != null || confidenceThreshold > 0;
        
        if(opinionPartNeed) {
            Map<String, StringJoiner> m = new LinkedHashMap<>();
            m.put("ni_", new StringJoiner(" AND "));
            m.put("ai_", new StringJoiner(" AND "));

            List<String> prefixes = new ArrayList<>(Arrays.asList("ni_", "ai_"));
            if(intel != null) {
                if(intel == Intelligence.AI) {
                    prefixes.remove("ni_");
                    m.remove("ni_");
                } else if(intel == Intelligence.NI) {
                    prefixes.remove("ai_");
                    m.remove("ai_");
                }
            }
            
            StringJoiner orJoiner = new StringJoiner(" OR ", "(", ")");
            
            for(Entry<String, StringJoiner> entry : m.entrySet()) {

                String prefix = entry.getKey();
                StringJoiner joiner = entry.getValue();

                if(prefix.equals("ni_")) {
                    joiner.add("ni_intelligence IS NOT NULL");
                } else if(prefix.equals("ai_")) {
                    joiner.add("ni_intelligence IS NULL AND ai_intelligence IS NOT NULL");
                }

                if(name != null) {
                    joiner.add(prefix + "name = ?");
                }
                if(rating != null) {
                    joiner.add(prefix + "rating = ?");
                }
                if(confidenceThreshold > 0) {
                    joiner.add(prefix + "confidence >= ?");
                }

                orJoiner.add(entry.getValue().toString());
            }
            whereJoiner.add(orJoiner.toString());
        }
        
        querySB.append(whereJoiner.toString());
        
        //System.out.println(querySB);
        
        return SQLiteUtility.supply(connection, c -> {
        
            //System.out.println("------------");
            //System.out.println(querySB.toString());
            //System.out.println("------------");
            
            PreparedStatement pstmt = c.prepareStatement(querySB.toString());
        
            int paramIndex = 1;
            
            if(subject != null) {
                pstmt.setString(paramIndex++, subject.getURI());
            }
            if(predicate != null) {
                pstmt.setString(paramIndex++, predicate.getURI());
            }
            if(object != null) {
                pstmt.setString(paramIndex++, toString(object));
            }
            if(phase != null) {
                pstmt.setString(paramIndex++, phase.toString());
            }
            
            //if intel == null we have to fill both, so '2'
            for(int i = 0; i < (intel == null ? 2 : 1); i++) {
                if(name != null) {
                    pstmt.setString(paramIndex++, name);
                }
                if(rating != null) {
                    pstmt.setString(paramIndex++, rating.toString());
                }
                if(confidenceThreshold > 0) {
                    pstmt.setDouble(paramIndex++, confidenceThreshold);
                }
            }
            
            
            //long begin = System.currentTimeMillis();
            java.sql.ResultSet rs = pstmt.executeQuery();
            
            //List<String> prefixes = new ArrayList<>(Arrays.asList("ni_", "ai_"));
            
            long count = -1;
            if(rs.next()) {
                //count value
                count = rs.getLong(1);
            }
            
            //long end = System.currentTimeMillis();
            
            rs.close();
            pstmt.close();
            
            /*
            System.out.println("getAssertions(" + 
                    Arrays.asList(subject, predicate, object, phase, intel, name, rating, confidenceThreshold) + ") took " + 
                    (end - begin) + " ms with " + assertions.size() + " entries"
            );
            */
            
            return count;
        });
    }
    
    
    @Override
    public List<Assertion> getAssertionsIn(Property predicate, List<RDFNode> objects, Phase phase) {
        //SELECT
        
        StringBuilder querySB = new StringBuilder();
        querySB.append("SELECT * FROM \"Assertion\"\n");
        querySB.append("WHERE\n");
        
        StringJoiner whereJoiner = new StringJoiner(" AND ");
        if(predicate != null) {
            whereJoiner.add("p = ?");
        }
        if(phase != null) {
            whereJoiner.add("phase = ?");
        }
        StringJoiner inJoiner = new StringJoiner(",", "(", ")");
        for(RDFNode node : objects) {
            inJoiner.add("?");
        }
        whereJoiner.add("o IN " + inJoiner.toString());
        
        querySB.append(whereJoiner.toString());
        
        //System.out.println(querySB);
        
        return SQLiteUtility.supply(connection, c -> {
        
            List<Assertion> assertions = new ArrayList<>();
            
            //System.out.println("------------");
            //System.out.println(querySB.toString());
            //System.out.println("------------");
            
            PreparedStatement pstmt = c.prepareStatement(querySB.toString());
        
            int paramIndex = 1;
            
            if(predicate != null) {
                pstmt.setString(paramIndex++, predicate.getURI());
            }
            if(phase != null) {
                pstmt.setString(paramIndex++, phase.toString());
            }
            for(RDFNode object : objects) {
                pstmt.setString(paramIndex++, toString(object));
            }
            
            //long begin = System.currentTimeMillis();
            java.sql.ResultSet rs = pstmt.executeQuery();
            
            List<String> prefixes = new ArrayList<>(Arrays.asList("ni_", "ai_"));
            
            while(rs.next()) {
                Assertion assertion = new Assertion();
                Statement stmt = toStatement(new String[] { rs.getString("s"), rs.getString("p"), rs.getString("o") });
                assertion.setStatement(stmt);
                
                assertion.setPhase(Phase.valueOf(rs.getString("phase")));
                
                for(String prefix : prefixes) {
                    if(rs.getString(prefix + "intelligence") != null) {
                        Opinion opinion = new Opinion();
                        
                        opinion.setIntelligence(prefix.equals("ni_") ? Intelligence.NI : Intelligence.AI);
                        opinion.setName(rs.getString(prefix + "name"));
                        opinion.setRating(Rating.valueOf(rs.getString(prefix + "rating")));
                        opinion.setConfidence(rs.getDouble(prefix + "confidence"));
                        opinion.setWhen(LocalDateTime.ofEpochSecond(rs.getLong(prefix + "when"), 0, ZoneOffset.ofHours(0)));
                        
                        assertion.setOpinion(opinion);
                    }
                }
                
                assertions.add(assertion);
            }
            //long end = System.currentTimeMillis();
            
            rs.close();
            pstmt.close();
            
            /*
            System.out.println("getAssertions(" + 
                    Arrays.asList(subject, predicate, object, phase, intel, name, rating, confidenceThreshold) + ") took " + 
                    (end - begin) + " ms with " + assertions.size() + " entries"
            );
            */
            
            return assertions;
        });
    }
    
    
    @Override
    protected void assertStatement(Statement stmt, Phase phase, Intelligence intel, String name, Rating rating, double confidence, LocalDateTime when) {
        
        if(stmt == null || phase == null || intel == null || name == null || rating == null ) {
            throw new RuntimeException("assertStatement null detected: " + Arrays.asList(stmt, phase, intel, name, rating));
        }
        
        //prepareStatement takes some time
        
        //in bulk mode do not notify
        boolean notify = false;
        if(!bulkMode) {
            //check if it already exists
            List<Assertion> exists = getAssertions(stmt.getSubject(), stmt.getPredicate(), stmt.getObject(), phase, intel, name, rating, confidence);
            //only if not exists do notify as new assertion 
            notify = exists.isEmpty();
        }
        //nofiy if the statement is new
        if(notify) {
            //create an object for it to put it in the assertion buffer, on commit it is in notification buffer
            Assertion assertion = new Assertion();
            assertion.setStatement(stmt);
            assertion.setPhase(phase);
            assertion.setOpinion(intel, name, when, rating, confidence);

            //System.out.println("[ASSERT] " + assertion);
            if(isLogging()) {
                saveAssertion(assertion, folder);
            }
            
            commitBuffer.add(assertion);
        }
        
        //UPSERT
        //if not notify still do upsert to update 'when' time 
        
        String query = upsertQuery.replace("${prefix}", intel == Intelligence.AI ? "ai_" : "ni_");
        
        //create one for batch
        if(preparedStatements[intel.ordinal()] == null) {
            preparedStatements[intel.ordinal()] = SQLiteUtility.supply(connection, c -> {
                return c.prepareStatement(query);
            });
        }
        
        /*
        INSERT INTO "Assertion" VALUES (
            1 ?, -- "s" TEXT,//1
            2 ?, -- "p" TEXT,//2
            3 ?, -- "o" TEXT,
            4 ?, -- "phase" TEXT,

            5 ?, -- "ai_intelligence" TEXT,
            6 ?, -- "ai_name" TEXT,
            7 ?, -- "ai_rating" TEXT,
            8 ?, -- "ai_confidence" REAL,
            9 ?, -- "ai_when" INTEGER,

            10 ?, -- "ni_intelligence" TEXT,
            11 ?, -- "ni_name" TEXT,
            12 ?, -- "ni_rating" TEXT,
            13 ?, -- "ni_confidence" REAL,
            14 ?  -- "ni_when" INTEGER,
        )
        ON CONFLICT(s, p, o) DO UPDATE SET
            ${prefix}intelligence = ?, 15
            ${prefix}name = ?, 16
            ${prefix}rating = ?, 17
            ${prefix}confidence = ?, 18
            ${prefix}when = ? ; 19
        */
        
        PreparedStatement preparedStatement = preparedStatements[intel.ordinal()];
        
        //reuse prepared statement
        SQLiteUtility.run(connection, c -> {
            //connection.setAutoCommit(false);
            
            setParametersNull(preparedStatement);
            
            //statement and phase
            String[] array = toStringArray(stmt);
            preparedStatement.setString(1, array[0]);
            preparedStatement.setString(2, array[1]);
            preparedStatement.setString(3, array[2]);
            preparedStatement.setString(4, phase.toString());
            
            //insert into opinion
            int paramIndex = intel == Intelligence.AI ? 5 : 10;
            preparedStatement.setString(paramIndex + 0, intel.toString());
            preparedStatement.setString(paramIndex + 1, name);
            preparedStatement.setString(paramIndex + 2, rating.toString());
            preparedStatement.setDouble(paramIndex + 3, confidence);
            preparedStatement.setLong  (paramIndex + 4, when.toEpochSecond(ZoneOffset.ofHours(0)));
            
            //ON CONFLICT(s, p, o) DO UPDATE SET
            preparedStatement.setString(15, intel.toString());
            preparedStatement.setString(16, name);
            preparedStatement.setString(17, rating.toString());
            preparedStatement.setDouble(18, confidence);
            preparedStatement.setLong  (19, when.toEpochSecond(ZoneOffset.ofHours(0)));
            
            //only add to batch
            //execute and commit comes in the save() method
            preparedStatement.addBatch();
            
            //connection.setAutoCommit(true);
        });
    }
    
    //use this when you want to merge something
    @Override
    public void removeAllAbout(Resource resource) {
        String query = getQuery(QUERY_PATH + "DeleteAssertion.sql");
        
        SQLiteUtility.run(connection, c -> {
            
            PreparedStatement pstmt = c.prepareStatement(query);
            pstmt.setString(1, resource.getURI());
            pstmt.setString(2, "<" + resource.getURI() + ">");
            
            pstmt.executeUpdate();
            pstmt.close();
            
            c.commit();
        });
    }
    
    private void setParametersNull(PreparedStatement ps) throws SQLException {
        for (int i = 0; i < ps.getParameterMetaData().getParameterCount(); i++) {
            if (ps.getParameterMetaData().isNullable(i + 1) == ParameterMetaData.parameterNullable) {
                ps.setNull(i + 1, Types.OTHER);
            }
        }
    }

    @Override
    public void commit() {
        
        if(preparedStatements[Intelligence.AI.ordinal()] != null ||
           preparedStatements[Intelligence.NI.ordinal()] != null) {
            SQLiteUtility.run(connection, c -> {
                //c.setAutoCommit(false);
                for(Intelligence intel : Intelligence.values()) {
                    if(preparedStatements[intel.ordinal()] != null) {
                        preparedStatements[intel.ordinal()].executeBatch();
                    }
                }
                
                c.commit();
                
                for(Intelligence intel : Intelligence.values()) {
                    if(preparedStatements[intel.ordinal()] != null) {
                        preparedStatements[intel.ordinal()].close();
                        preparedStatements[intel.ordinal()] = null;
                    }
                }
                
                //c.setAutoCommit(true);
            });
            
            notificationBuffer.addAll(commitBuffer);
            commitBuffer.clear();
        }
    }
    
    @Override
    public void rollback() {
        if(preparedStatements[Intelligence.AI.ordinal()] != null ||
           preparedStatements[Intelligence.NI.ordinal()] != null) {
            SQLiteUtility.run(connection, c -> {
                for(Intelligence intel : Intelligence.values()) {
                    if(preparedStatements[intel.ordinal()] != null) {
                        preparedStatements[intel.ordinal()].close();
                        preparedStatements[intel.ordinal()] = null;
                    }
                }
                c.rollback();
            });
            commitBuffer.clear();
        }
    }

    private String getQuery(String path) {
        try {
            String query = IOUtils.toString(getClass().getResourceAsStream(path), StandardCharsets.UTF_8);
            query = query.replace("${tablename}", tablename);
            return query;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private File getSqliteFile(File folder) {
        return new File(folder, "data.sqlite");
    }
    
    private static String[] toStringArray(Statement stmt) {
        return new String[] {
            stmt.getSubject().getURI(),
            stmt.getPredicate().getURI(),
            toString(stmt.getObject())
        };
    }
    
    private static String toString(RDFNode rdfNode) {
        boolean r = rdfNode.isResource();
        return (r?"<":"") + rdfNode.asNode().toString(true) + (r?">":"");
    }
    
    private static Statement toStatement(String[] array) {
        Node obj = NodeFactoryExtra.parseNode(array[2]);

        Statement stmt = ResourceFactory.createStatement(
                ResourceFactory.createResource(array[0]), 
                ResourceFactory.createProperty(array[1]), 
                toRDFNode(obj)
        );

        return stmt;
    }
    
    private static RDFNode toRDFNode(Node node) {
        if(node.isLiteral()) {
            return new LiteralImpl(node, null);
        }
        return new ResourceImpl(node, null);
    }
    
    private static final Object syncPoint = new Object();
    
    private void saveAssertion(Assertion assertion, File folder) {
        JSONObject entry = Assertion.toJson(assertion);
        entry.put("saveTime", LocalDateTime.now());
        
        String name = this.metaData.getId() + "-log.jsonl.gz";
        
        synchronized(syncPoint) {
            File file = new File(folder, name);
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
            try {
                OutputStream os = new GZIPOutputStream(new FileOutputStream(file, true));
                String line = entry.toString() + "\n";
                os.write(line.getBytes(StandardCharsets.UTF_8));
                os.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
    
}
