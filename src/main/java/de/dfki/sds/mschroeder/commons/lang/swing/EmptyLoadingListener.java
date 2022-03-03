
package de.dfki.sds.mschroeder.commons.lang.swing;

/**
 * 
 */
public class EmptyLoadingListener implements LoadingListener {

    @Override
    public void setMaximum(int max) {
    }

    @Override
    public void setCurrent(int current) {
    }

    @Override
    public boolean cancel() {
        return false;
    }

    @Override
    public void status(String status) {
    }

}
