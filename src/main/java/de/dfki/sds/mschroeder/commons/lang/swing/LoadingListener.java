package de.dfki.sds.mschroeder.commons.lang.swing;

/**
 * Listens while importing, exporting or execution.
 * 
 */
public interface LoadingListener {
    
    /**
     * Sets the maximum steps to do.
     * @param max 
     */
    public void setMaximum(int max);
    
    /**
     * Sets the current step that is processed.
     * @param current 
     */
    public void setCurrent(int current);
    
    /**
     * Return true to cancel the importing, exporting or execution.
     * @return 
     */
    public boolean cancel();
    
    /**
     * Sets the status message.
     * @param status 
     */
    public void status(String status);
}
