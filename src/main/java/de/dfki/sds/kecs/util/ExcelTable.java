package de.dfki.sds.kecs.util;

import de.dfki.sds.hephaistos.storage.excel.ExcelCell;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 *
 */
public class ExcelTable {


    private transient ExcelCell[][] data;
    //same information as data matrix, but separates cells better
    private transient List<ExcelColumn> columns;
    
    //the final sheet name assigned by workbook creator
    private transient String sheetName;
    
    //a string that explains mode locale randomSeed sheet etc = generation prov.
    //not transient, so it will be stored
    private String generationProvenance;
    
    private transient int index;
    
    private static final Model creator = ModelFactory.createDefaultModel();

    public ExcelTable() {
        columns = new ArrayList<>();
    }

    public ExcelCell[][] getData() {
        return data;
    }

    public void setData(ExcelCell[][] data) {
        this.data = data;
    }

    public List<ExcelColumn> getColumns() {
        return columns;
    }
    
     public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }
    
    public String toCSV() throws IOException {
        StringWriter sw = new StringWriter();
        CSVPrinter p = CSVFormat.DEFAULT.print(sw);

        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {

                ExcelCell cell = data[i][j];

                if (cell == null) {
                    p.print("");
                } else {

                    if (cell.getCellType() == null) {
                        throw new RuntimeException("type is null in (" + j + "," + i + ")");
                    }

                    switch (cell.getCellType()) {
                        case "string":
                            p.print(cell.getValueString());
                            break;
                        case "numeric":
                            p.print(cell.getValueNumeric());
                            break;
                        case "boolean":
                            p.print(cell.getValueBoolean());
                            break;
                        default:
                            throw new RuntimeException(cell.getCellType() + " type not implemented");
                    }
                }
            }

            p.println();
        }

        return sw.toString();
    }


    @Override
    public String toString() {
        return "ExcelCell{" + "(" + data[0].length + ", " + data.length + ")}";
    }

    public int getIndex() {
        return index;
    }

    public ExcelTable setIndex(int index) {
        this.index = index;
        return this;
    }
    
}
