package de.dfki.sds.stringanalyzer;

import de.dfki.sds.stringanalyzer.string.StringEntity;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The abstract class for all components in string analyzer.
 * 
 */
public abstract class StringAnalyzerComponent {
    
    
    public abstract void add(StringEntity entity);
    
    /**
     * Overwrite to allow configuration from json.
     * @param config 
     */
    public void configure(JSONObject config) throws JSONException {
        
    }
    
    /**
     * Overwrite to report the analysis result.
     * May use JSONObject config to set e.g. verbosity.
     * @param report
     * @return 
     * @throws org.json.JSONException 
     */
    protected void reportJson(JSONObject report) throws JSONException {
        
    }
    
    
    //REPORTING (always in json so that a GUI can process it)
    
    public JSONObject reportJson() {
        JSONObject jo = new JSONObject();
        reportJson(jo);
        return jo;
    }
    
    public String reportJsonString() {
        return reportJson().toString(2);
    }
    
    public void reportJsonPrint() {
        System.out.println(reportJson().toString(2));
    }
    
    @Override
    public String toString() {
        return reportJsonString();
    }

    
    
    
}
