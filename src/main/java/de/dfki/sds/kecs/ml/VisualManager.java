
package de.dfki.sds.kecs.ml;

import de.dfki.sds.hephaistos.storage.assertion.Assertion;
import de.dfki.sds.hephaistos.storage.assertion.AssertionPool;
import de.dfki.sds.hephaistos.storage.assertion.Phase;
import de.dfki.sds.hephaistos.storage.assertion.Rating;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import de.dfki.sds.kecs.KecsSettings;
import de.dfki.sds.kecs.modules.ConceptDiscovery;
import de.dfki.sds.kecs.util.KecsUtils;
import de.dfki.sds.kecs.util.PropertyEdge;
import de.dfki.sds.kecs.util.TypeWithIntel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Displays data in HTML.
 */
public class VisualManager {

    private FileInfoStorage fileInfoStorage;
    private AssertionPool assertionPool;
    private KecsSettings settings;
    
    private GraphManager graphManager;
    
    public VisualManager(FileInfoStorage fileInfoStorage, AssertionPool assertionPool, KecsSettings settings) {
        this.fileInfoStorage = fileInfoStorage;
        this.assertionPool = assertionPool;
        this.settings = settings;
        
        this.graphManager = new GraphManager();
    }
    
    public String getNonTaxGraph() {
        
        DefaultUndirectedGraph<Resource, PropertyEdge> graph = graphManager.getNonTaxonomicGraph(assertionPool);
        
        Map<Resource, String> cpt2prefLabel = assertionPool.getConceptPrefLabelMap(ConceptDiscovery.DEFAULT_TYPE, null);
        Map<Resource, String> type2prefLabel = assertionPool.getTypePrefLabelMap();
        Map<Resource, String> prop2prefLabel = assertionPool.getPropertyPrefLabelMap();
        
        JSONArray nodes = new JSONArray();
        for(Resource vertex : graph.vertexSet()) {
            JSONObject node = new JSONObject();
            
            node.put("id", vertex.getURI());
            
            List<Assertion> types = assertionPool.getAssertions(vertex, RDF.type, null, Phase.OntologyPopulation, null, null, Rating.Positive, 0);
            TypeWithIntel typeWithIntel = KecsUtils.getType(types);
            node.put("group", typeWithIntel.getType().getURI());
            
            String label = "#" + cpt2prefLabel.get(vertex);
            
            String typePrefLabel = type2prefLabel.getOrDefault(typeWithIntel.getType(), "");
            label += "\n(" + typePrefLabel + ")";
            
            node.put("label", label);
            
            nodes.put(node);
        }
        
        JSONArray edges = new JSONArray();
        for(PropertyEdge edge : graph.edgeSet()) {
            
            JSONObject edgeObj = new JSONObject();
            
            Resource from = graph.getEdgeSource(edge);
            Resource to = graph.getEdgeTarget(edge);
            
            edgeObj.put("from", from.toString());
            edgeObj.put("to", to.toString());
            
            edgeObj.put("label", prop2prefLabel.getOrDefault(edge.getProperty(), ""));
            
            JSONObject arrows = new JSONObject();
            edgeObj.put("arrows", arrows);
            
            JSONObject arrowsTo = new JSONObject();
            arrows.put("to", arrowsTo);
            
            arrowsTo.put("enabled", true);
            arrowsTo.put("type", "arrow");
            
            edges.put(edgeObj);
        }
        
        JSONObject options = new JSONObject();
        
        return getNetworkPage("Beziehungsgraph", nodes, edges, options);
    }
    
    public String getTaxonomy() {
        DefaultUndirectedGraph<Resource, DefaultEdge> graph = graphManager.getTaxonomyGraph(assertionPool);
        
        Map<Resource, String> cpt2prefLabel = assertionPool.getConceptPrefLabelMap(ConceptDiscovery.DEFAULT_TYPE, null);
        
        JSONArray nodes = new JSONArray();
        for(Resource vertex : graph.vertexSet()) {
            JSONObject node = new JSONObject();
            
            node.put("id", vertex.getURI());
            
            String label = "#" + cpt2prefLabel.get(vertex);
            
            node.put("label", label);
            
            nodes.put(node);
        }
        
        JSONArray edges = new JSONArray();
        for(DefaultEdge edge : graph.edgeSet()) {
            
            JSONObject edgeObj = new JSONObject();
            
            Resource from = graph.getEdgeSource(edge);
            Resource to = graph.getEdgeTarget(edge);
            
            //because of layout we turn it around
            edgeObj.put("from", to.toString());
            edgeObj.put("to", from.toString());
            
            edges.put(edgeObj);
        }
        
        JSONObject options = new JSONObject();
        
        JSONObject layout = new JSONObject();
        JSONObject hierarchical = new JSONObject();
        hierarchical.put("direction", "UD");
        hierarchical.put("sortMethod", "directed");
        layout.put("hierarchical", hierarchical);
        options.put("layout", layout);
        
        return getNetworkPage("Taxonomie", nodes, edges, options);
    }
    
    public String getNetworkPage(String title, JSONArray nodes, JSONArray edges, JSONObject options) {
        String tmpl = getTemplate("network.html");
        tmpl = tmpl.replace("${title}", title);
        tmpl = tmpl.replace("${nodes}", nodes.toString(2));
        tmpl = tmpl.replace("${edges}", edges.toString(2));
        tmpl = tmpl.replace("${options}", options.toString(2));
        return tmpl;
    }
    
    private String getTemplate(String name) {
        try {
            return IOUtils.toString(VisualManager.class.getResourceAsStream("/de/dfki/sds/kecs/tmpl/" + name), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
}
