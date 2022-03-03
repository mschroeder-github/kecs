
package de.dfki.sds.kecs.util;

import de.dfki.sds.hephaistos.storage.file.FileInfo;
import java.util.Set;

/**
 * 
 */
public class FileInfoSearchResult {
    
    private FileInfo fileInfo;
    private Set<String> terms;
    
    private String left;
    private String middle;
    private String right;
    
    private boolean regex;
    private Set<String> variations;
    private Set<String> found;

    public FileInfo getFileInfo() {
        return fileInfo;
    }

    public void setFileInfo(FileInfo fileInfo) {
        this.fileInfo = fileInfo;
    }

    public Set<String> getTerms() {
        return terms;
    }

    public void setTerms(Set<String> terms) {
        this.terms = terms;
    }

    public String getLeft() {
        return left;
    }

    public void setLeft(String left) {
        this.left = left;
    }

    public String getMiddle() {
        return middle;
    }

    public void setMiddle(String middle) {
        this.middle = middle;
    }

    public String getRight() {
        return right;
    }

    public void setRight(String right) {
        this.right = right;
    }

    public boolean isRegex() {
        return regex;
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }

    public Set<String> getVariations() {
        return variations;
    }

    public void setVariations(Set<String> variations) {
        this.variations = variations;
    }

    public Set<String> getFound() {
        return found;
    }

    public void setFound(Set<String> found) {
        this.found = found;
    }
    
    @Override
    public String toString() {
        return "FileInfoSearchResult{" + "fileInfo=" + fileInfo + ", left=" + left + ", middle=" + middle + ", right=" + right + '}';
    }
    
}
