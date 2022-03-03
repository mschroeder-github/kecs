
package de.dfki.sds.kecs.util;

import de.dfki.sds.hephaistos.storage.excel.ExcelCell;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.rdf.model.Resource;
import org.apache.poi.ss.util.CellAddress;

/**
 * 
 */
public class ExcelColumn {
    
    private int index;
    
    private List<ExcelCell> headerCells;
    
    private List<ExcelCell> dataCells;
    
    private List<Resource> predicateObjectMaps;
    
    private ExcelTable table;
    
    private int maxColumn;
    private int maxRow;
    
    private String provenance;

    public ExcelColumn() {
        headerCells = new ArrayList<>();
        dataCells = new ArrayList<>();
        predicateObjectMaps = new ArrayList<>();
    }

    public ExcelColumn(int index) {
        this();
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
    
    public String getLetter() {
       CellAddress addr = new CellAddress(0, getIndex());
       String columnLetter = addr.formatAsString().replaceAll("\\d+", "");
       return columnLetter;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public List<ExcelCell> getHeaderCells() {
        return headerCells;
    }
    
    public String getHeaderCellJoinedValues() {
        StringBuilder sb = new StringBuilder();
        for(ExcelCell cell : headerCells) {
            sb.append(cell.getValue()).append("\n");
        }
        return sb.toString().trim();
    }

    public void setHeaderCells(List<ExcelCell> headerCells) {
        this.headerCells = headerCells;
    }

    public List<ExcelCell> getDataCells() {
        return dataCells;
    }
    
    public List<ExcelCell> getAllCells() {
        List<ExcelCell> allCells = new ArrayList<>();
        allCells.addAll(getHeaderCells());
        allCells.addAll(getDataCells());
        return allCells;
    } 

    public void setDataCells(List<ExcelCell> dataCells) {
        this.dataCells = dataCells;
    }

    public List<Resource> getPredicateObjectMaps() {
        return predicateObjectMaps;
    }

    public ExcelTable getTable() {
        return table;
    }

    public ExcelColumn setTable(ExcelTable table) {
        this.table = table;
        return this;
    }

    //to have the max values for calculating features
    
    public int getMaxColumn() {
        return maxColumn;
    }

    public void setMaxColumn(int maxColumn) {
        this.maxColumn = maxColumn;
    }

    public int getMaxRow() {
        return maxRow;
    }

    public void setMaxRow(int maxRow) {
        this.maxRow = maxRow;
    }

    public String getProvenance() {
        return provenance;
    }

    public void setProvenance(String provenance) {
        this.provenance = provenance;
    }
    
}
