package de.dfki.sds.hephaistos.storage.excel;

import de.dfki.sds.hephaistos.storage.StorageItem;

/**
 * A workbook is a collection of Excel Cells and has a name.
 * 
 */
public class ExcelSheet implements StorageItem {
    
    private String deepLink;
    
    private int id;
    
    private String name;
    
    private int width;
    private int height;
    
    private String filename;
    private String filepath;

    public ExcelSheet() {
    }

    public ExcelSheet(int id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 83 * hash + this.id;
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
        final ExcelSheet other = (ExcelSheet) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }
    
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFilePath() {
        return filepath;
    }

    public void setFilePath(String filepath) {
        this.filepath = filepath;
    }
    
}
