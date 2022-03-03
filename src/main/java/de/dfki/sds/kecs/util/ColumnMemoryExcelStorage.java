
package de.dfki.sds.kecs.util;

import de.dfki.sds.hephaistos.storage.InternalStorageMetaData;
import de.dfki.sds.hephaistos.storage.StorageSummary;
import de.dfki.sds.hephaistos.storage.excel.ExcelCell;
import de.dfki.sds.hephaistos.storage.excel.ExcelSheet;
import de.dfki.sds.hephaistos.storage.excel.ExcelStorage;
import de.dfki.sds.hephaistos.storage.excel.ExcelTextStyle;
import de.dfki.sds.hephaistos.storage.file.FileInfo;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import de.dfki.sds.hephaistos.storage.file.FolderInfo;
import de.dfki.sds.kecs.KecsSettings;
import de.dfki.sds.mschroeder.commons.lang.data.Pair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

/**
 * 
 */
public class ColumnMemoryExcelStorage extends ExcelStorage {

    private Map<Integer, ExcelTable> sheetId2table;
    private Map<Pair<Integer>, ExcelColumn> sheetCol2Column;
    private int tblIndex;
    
    public ColumnMemoryExcelStorage(InternalStorageMetaData metaData) {
        super(metaData);
        sheetId2table = new HashMap<>();
        sheetCol2Column = new HashMap<>();
    }

    @Override
    public void addCellBulk(Collection<ExcelCell> items) {
        for(ExcelCell cell : items) {
            ExcelTable table = getTable(cell.getSheetId());
            ExcelColumn col = getColumn(cell.getSheetId(), cell.getColumn(), table);
            col.setIndex(cell.getColumn());
            col.getDataCells().add(cell);
        }
    }
    
    private ExcelTable getTable(int sheetId) {
        return sheetId2table.computeIfAbsent(sheetId, id -> new ExcelTable().setIndex(tblIndex++));
    }
    
    private ExcelColumn getColumn(int sheetId, int column, ExcelTable table) {
        return sheetCol2Column.computeIfAbsent(new Pair<>(sheetId, column), id -> {
            ExcelColumn c = new ExcelColumn();
            c.setTable(table);
            table.getColumns().add(c);
            return c;
        });
    }
    
    @Override
    public void addBulk(Collection<ExcelSheet> items) {
        for(ExcelSheet sheet : items) {
            ExcelTable tbl = getTable(sheet.getId());
            tbl.setSheetName(sheet.getName());
        }
    }
    
    public List<ExcelTable> getTables() {
        return new ArrayList<>(sheetId2table.values());
    }
    
    public List<ExcelColumn> getColumns() {
        return new ArrayList<>(sheetCol2Column.values());
    }

    //to also support the excel use case we have
    public static void convertToTree(ColumnMemoryExcelStorage excelStorage, FileInfoStorage fileInfoStorage, KecsSettings settings) {
        
        //tree structure:
        //sheet
        // * column
        //   * distinct values (maybe skip dates booleans)
        
        List<FileInfo> bulk = new ArrayList<>();
        
        FolderInfo excelFolder = new FolderInfo();
        excelFolder.setId(2);
        excelFolder.setDirectory(true);
        excelFolder.setParent(1);
        excelFolder.setName("");
        excelFolder.setPath("/Excel");
        
        JSONObject meta = new JSONObject();
        meta.put("uri", "urn:file:2");
        meta.put("basename", excelFolder.getName());
        excelFolder.setMeta(meta.toString());
        
        bulk.add(excelFolder);
        
        int id = 3;
        
        List<ExcelColumn> skippedColumnsContent = new ArrayList<>();
        
        String whiteList = settings.getExcelWhitelist();
        Map<String, List<String>> sheet2letterWhitelist = null;
        if(whiteList != null && !whiteList.isEmpty()) {
            sheet2letterWhitelist = new HashMap<>();
            
            for(String segment : whiteList.split("\\;")) {
                if(segment.trim().isEmpty())
                    continue;
                
                String[] split = segment.split("\\:");
                String sheetName = split[0];
                String[] letters = split[1].split("\\,");
                
                sheet2letterWhitelist.put(sheetName, Arrays.asList(letters));
            }
        }
        
        
        
        for(ExcelTable table : excelStorage.getTables()) {
            
            FolderInfo tableFolder = new FolderInfo();
            tableFolder.setId(id);
            tableFolder.setDirectory(true);
            tableFolder.setParent(2);
            tableFolder.setPath(excelFolder.getPath() + "/" + table.getSheetName());
            tableFolder.setName(table.getSheetName());
            tableFolder.setSort(table.getIndex());
            
            meta = new JSONObject();
            meta.put("uri", "urn:file:" + id);
            meta.put("basename", table.getSheetName());
            tableFolder.setMeta(meta.toString());
            
            id++;
            
            bulk.add(tableFolder);
            
            for(ExcelColumn column : table.getColumns()) {
                
                List<ExcelCell> dataCells = column.getDataCells();
                
                if(dataCells.isEmpty())
                    continue;
                
                //head is always first cell
                ExcelCell head = dataCells.remove(0);
                
                if(head.getValueString() == null) {
                    continue;
                }
                
                FolderInfo columnFolder = new FolderInfo();
                columnFolder.setId(id);
                columnFolder.setDirectory(true);
                columnFolder.setParent(tableFolder.getId());
                columnFolder.setSort(column.getIndex());
                
                String colName = head.getValueString().replace("\n", " ").replace("\t", " ");
                
                columnFolder.setPath(tableFolder.getPath() + "/" + colName);
                columnFolder.setName(colName);

                meta = new JSONObject();
                meta.put("uri", "urn:file:" + id);
                meta.put("basename", colName);
                columnFolder.setMeta(meta.toString());

                id++;

                bulk.add(columnFolder);
                
                //eliminate duplicates
                Set<String> contents = new HashSet<>();
                
                for(ExcelCell cell : dataCells) {
                    //maybe ignore all numeric and boolean based ones
                    if(cell.getValueString() == null) {
                        continue;
                    }
                    
                    String content = cell.getValueString().replace("\n", " ").replace("\t", " ").trim();
                    
                    if(content.isEmpty()) {
                        continue;
                    }
                    
                    contents.add(content);
                }

                //avoid adding a lot of text (like titles)
                int charSum = 0;
                for(String content : contents) {
                    charSum += content.length();
                }
                
                //System.out.println(column.getLetter() + ": " + tableFolder.getPath() + "/" + colName + ", charSum=" + charSum + ", contentCount=" + contents.size());
                
                if(sheet2letterWhitelist != null) {
                    List<String> letters = sheet2letterWhitelist.get(table.getSheetName());
                    if(letters != null) {
                        //not in whitelist, so skip
                        if(!letters.contains(column.getLetter())) {
                            System.out.println(table.getSheetName() + ":" + column.getLetter() + " skipped due to whitelist");
                            skippedColumnsContent.add(column);
                            continue;
                        }
                    }
                }
                
                if(!contents.isEmpty()) { //&&
                   //charSum         <= settings.getExcelCharacterSumThreshold() && 
                   //contents.size() <= settings.getExcelContentCountThreshold()) {
                    
                    List<String> contentList = new ArrayList<>(contents);
                    contentList.sort((a,b) -> a.compareToIgnoreCase(b));
                    
                    int sortIndex = 0;
                    for(String content : contentList) {
                        FileInfo cellFile = new FileInfo();
                        cellFile.setId(id);
                        cellFile.setDirectory(false);
                        cellFile.setParent(columnFolder.getId());
                        cellFile.setSort(sortIndex++);

                        cellFile.setPath(tableFolder.getPath() + "/" + content);
                        cellFile.setName(content);

                        meta = new JSONObject();
                        meta.put("uri", "urn:file:" + id);
                        meta.put("basename", content);
                        cellFile.setMeta(meta.toString());

                        id++;

                        bulk.add(cellFile); 
                    }
                } else {
                    skippedColumnsContent.add(column);
                }
            }
        }
        
        fileInfoStorage.insertBulk(bulk);
        
        System.out.println(skippedColumnsContent.size() + " columns skipped");
    }
    
    //==================================================================
    
    @Override
    public void addTextStyleBulk(Collection<ExcelTextStyle> items) {
        //System.out.println("textStyles: " + items.size());
    }
    
    @Override
    public void removeCellBulk(Collection<ExcelCell> items) {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public void removeTextStyleBulk(Collection<ExcelTextStyle> items) {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public Iterable<ExcelCell> getCellListIter(ExcelSheet sheet) {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public Iterable<ExcelTextStyle> getTextStyleListIter(ExcelCell cell) {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public ExcelSheet getSheet(int id) {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public ExcelCell getCell(int id) {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public List<ExcelCell> getRow(ExcelSheet sheet, int equalRow, int greaterEqualColumn) {
        throw new RuntimeException("not implemented yet");
    }

    

    @Override
    public void removeBulk(Collection<ExcelSheet> items) {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public Iterable<ExcelSheet> getListIter() {
        throw new RuntimeException("not implemented yet");
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
        throw new RuntimeException("not implemented yet");
    }

}
