package de.dfki.sds.hephaistos.storage.excel;

import de.dfki.sds.hephaistos.storage.InternalStorageMetaData;
import de.dfki.sds.hephaistos.storage.RelationalStorage;
import de.dfki.sds.hephaistos.storage.StorageSummary;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Stores Excel sheets with their cells.
 * 
 */
public abstract class ExcelStorage extends RelationalStorage<ExcelSheet, StorageSummary> {

    public ExcelStorage(InternalStorageMetaData metaData) {
        super(metaData);
    }
    
    public abstract void addCellBulk(Collection<ExcelCell> items);
    
    public abstract void addTextStyleBulk(Collection<ExcelTextStyle> items);

    public abstract void removeCellBulk(Collection<ExcelCell> items);
    
    public abstract void removeTextStyleBulk(Collection<ExcelTextStyle> items);

    public abstract Iterable<ExcelCell> getCellListIter(ExcelSheet sheet);
    
    public abstract Iterable<ExcelTextStyle> getTextStyleListIter(ExcelCell cell);
    
    public abstract ExcelSheet getSheet(int id);
    
    public abstract ExcelCell getCell(int id);
    
    public List<ExcelCell> getCellList(ExcelSheet sheet){
        List<ExcelCell> result = new ArrayList<>();
        getCellListIter(sheet).forEach(result::add);
        return result;
    }
    
    public List<ExcelTextStyle> getTextStyleList(ExcelCell cell) {
        List<ExcelTextStyle> result = new ArrayList<>();
        getTextStyleListIter(cell).forEach(result::add);
        return result;
    }
    
    /**
     * 
     * @param sheet
     * @param greaterEqualRow
     * @param equalColumn
     * @return
     * @deprecated This should be implemented with SQL.
     */
    @Deprecated
    public List<ExcelCell> getColumn(ExcelSheet sheet, int greaterEqualRow, int equalColumn){
        List<ExcelCell> result = new ArrayList<>();
        getCellListIter(sheet).forEach((c) -> {
            if(c.getRow() >= greaterEqualRow && c.getColumn() == equalColumn) {
                result.add(c);
            }
        });
        return result;
    }
    
    public abstract List<ExcelCell> getRow(ExcelSheet sheet, int equalRow, int greaterEqualColumn); /* slow: {
        List<ExcelCell> result = new ArrayList<>();
        getCellListIter(sheet).forEach((c) -> {
            if(c.getRow() == equalRow && c.getColumn() >= greaterEqualColumn) {
                result.add(c);
            }
        });
        return result;
    }*/
    
    /**
     * 
     * @param sheet
     * @param equalRow
     * @param equalColumn
     * @return
     * @deprecated This should be implemented with SQL.
     */
    @Deprecated
    public ExcelCell getCell(ExcelSheet sheet, int equalRow, int equalColumn) {
        Iterator<ExcelCell> iter = getCellListIter(sheet).iterator();
        while(iter.hasNext()) {
            ExcelCell cell = iter.next();
            if(cell.getRow() == equalRow && cell.getColumn() == equalColumn) {
                return cell;
            }
        }
        return null;
    }
}
