package de.dfki.sds.hephaistos.storage.excel;

import de.dfki.sds.hephaistos.DataStoreDescription;
import de.dfki.sds.hephaistos.Preference;
import de.dfki.sds.hephaistos.storage.DataIO;
import de.dfki.sds.hephaistos.storage.DataModel;
import de.dfki.sds.hephaistos.storage.InternalStorage;
import de.dfki.sds.hephaistos.storage.StorageManager;
import de.dfki.sds.mschroeder.commons.lang.MemoryUtility;
import de.dfki.sds.mschroeder.commons.lang.swing.LoadingListener;
import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.TableModel;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FontUnderline;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 *
 * 
 */
public class ExcelStorageIO extends DataIO<ExcelStorage, TableModel> {

    //if it returns 8x5 it means there are 8 columns and 5 rows filled
    private Dimension getMaxima(Sheet sheet) {

        int w = 0;
        int h = 0;

        int minRow = sheet.getFirstRowNum();
        int maxRow = sheet.getLastRowNum() + 1;
        //row number = 0-based index, that is why + 1

        h = Math.max(h, maxRow);

        for (int k = minRow; k < maxRow; k++) {
            Row row = sheet.getRow(k);
            if (row == null) {
                continue;
            }

            int maxCol = row.getLastCellNum();

            w = Math.max(w, maxCol);
        }

        return new Dimension(w, h);
    }

    private Color toAwtColor(XSSFColor color) {
        if (color == null || color.getARGB() == null) {
            return null;
        }

        return new Color(
                (int) (color.getARGB()[1] & 0xFF), //R
                (int) (color.getARGB()[2] & 0xFF), //G
                (int) (color.getARGB()[3] & 0xFF), //B

                (int) (color.getARGB()[0] & 0xFF) //A
        );
    }

    @Override
    public InternalStorage createInternalStorage(StorageManager storageManager) {
        return null;
    }

    @Override
    public void exporting(ExcelStorage from, DataStoreDescription to, LoadingListener listener) {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public void importing(DataStoreDescription from, ExcelStorage to, LoadingListener listener) throws IOException {

        FileInputStream fis;
        Workbook workbook;
        
        //avoid zip bomb detection
        ZipSecureFile.setMinInflateRatio(0.0);

        //TODO import parameters?
        final int CELL_BUFFER_SIZE = 10000;
        final int TEXT_STYLE_BUFFER_SIZE = 10000;
        final boolean ignoreBlank = true;

        int sheetId = 1;
        int cellId = 1;
        int textStyleId = 1;

        List<ExcelSheet> sheetBuffer = new ArrayList<>();
        List<ExcelCell> cellBuffer = new ArrayList<>();
        List<ExcelTextStyle> textStyleBuffer = new ArrayList<>();

        for (String locator : from.getLocators()) {

            File locatorFile = new File(locator);
            String fileSize = MemoryUtility.humanReadableByteCount(locatorFile.length());

            listener.status("Load workbook " + locatorFile.getName() + " with " + fileSize);

            fis = new FileInputStream(locator);
            if (locator.endsWith("xls")) {
                try {
                    workbook = new HSSFWorkbook(fis);
                } catch (OfficeXmlFileException e) {
                    //fallback
                    fis = new FileInputStream(locator);
                    workbook = new XSSFWorkbook(fis);
                }
            } else {
                //xlsx
                workbook = new XSSFWorkbook(fis);
            }

            if (listener.cancel()) {
                break;
            }

            System.gc();

            //sheets
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {

                if (listener.cancel()) {
                    break;
                }

                if(from.getParts() != null && !from.getParts().isEmpty()) {
                    if(!from.getParts().contains(workbook.getSheetAt(i).getSheetName())) {
                        listener.status("Skip Sheet " + workbook.getSheetAt(i).getSheetName());
                        continue;
                    }
                }
                
                ExcelSheet excelSheet = new ExcelSheet();
                sheetBuffer.add(excelSheet);
                excelSheet.setId(sheetId++);
                excelSheet.setFilename(locatorFile.getName());
                excelSheet.setFilePath(locatorFile.getPath());

                Sheet sheet = workbook.getSheetAt(i);
                Dimension maxima = getMaxima(sheet);
                int numberOfCells = maxima.width * maxima.height;

                excelSheet.setName(sheet.getSheetName());
                excelSheet.setWidth(maxima.width);
                excelSheet.setHeight(maxima.height);

                int runningCellNumber = 1;
                listener.status("Load Sheet " + sheet.getSheetName() + " with approx. " + numberOfCells + " cells");
                listener.setMaximum(numberOfCells);
                listener.setCurrent(runningCellNumber++);

                int minRow = sheet.getFirstRowNum();
                int maxRow = sheet.getLastRowNum();

                for (int k = minRow; k <= maxRow; k++) {
                    Row row = sheet.getRow(k);
                    if (row == null) {
                        continue;
                    }

                    if (listener.cancel()) {
                        break;
                    }

                    int minCol = row.getFirstCellNum();
                    int maxCol = row.getLastCellNum();
                    
                    if(minCol < 0) {
                        minCol = 0;
                    }

                    for (int j = minCol; j <= maxCol; j++) {
                        Cell cell = row.getCell(j);
                        if (cell == null) {
                            continue;
                        }

                        if (listener.cancel()) {
                            break;
                        }

                        if (ignoreBlank && cell.getCellType() == CellType.BLANK) {
                            continue;
                        }

                        ExcelCell excelCell = new ExcelCell();
                        cellBuffer.add(excelCell);
                        excelCell.setId(cellId++);
                        excelCell.setSheetId(excelSheet.getId());
                        
                        CellStyle cst = cell.getCellStyle();
                        if (cst instanceof XSSFCellStyle) {
                            XSSFCellStyle cs = (XSSFCellStyle) cell.getCellStyle();
                            XSSFFont font = cs.getFont();

                            excelCell.setFontBold(font.getBold());
                            excelCell.setFontItalic(font.getItalic());
                            excelCell.setFontStrikeout(font.getStrikeout());
                            excelCell.setFontUnderline(font.getUnderline() != FontUnderline.NONE.getByteValue());
                            excelCell.setFontName(font.getFontName());
                            excelCell.setFontColor(toAwtColor(font.getXSSFColor()));
                            excelCell.setFontSize(font.getFontHeightInPoints());

                            excelCell.setForegroundColor(toAwtColor(cs.getFillForegroundColorColor()));
                            excelCell.setBackgroundColor(toAwtColor(cs.getFillBackgroundColorColor()));
                            excelCell.setRotation(cs.getRotation());
                            excelCell.setHorizontalAlignment(cs.getAlignmentEnum().toString().toLowerCase());
                            excelCell.setVerticalAlignment(cs.getVerticalAlignmentEnum().toString().toLowerCase());
                            excelCell.setBorderTop(cs.getBorderTopEnum().toString().toLowerCase());
                            excelCell.setBorderLeft(cs.getBorderLeftEnum().toString().toLowerCase());
                            excelCell.setBorderRight(cs.getBorderRightEnum().toString().toLowerCase());
                            excelCell.setBorderBottom(cs.getBorderBottomEnum().toString().toLowerCase());
                            
                            excelCell.setDataFormat(cs.getDataFormatString());
                        } else if(cst instanceof HSSFCellStyle) {
                            HSSFCellStyle cs = (HSSFCellStyle) cell.getCellStyle();
                            
                            HSSFFont font = cs.getFont(workbook);

                            excelCell.setFontBold(font.getBold());
                            excelCell.setFontItalic(font.getItalic());
                            excelCell.setFontStrikeout(font.getStrikeout());
                            excelCell.setFontUnderline(font.getUnderline() != FontUnderline.NONE.getByteValue());
                            excelCell.setFontName(font.getFontName());
                            //excelCell.setFontColor(toAwtColor(font.getXSSFColor())); //TODO need toAwtColor method
                            excelCell.setFontSize(font.getFontHeightInPoints());

                            //excelCell.setForegroundColor(toAwtColor(cs.getFillForegroundColorColor()));
                            //excelCell.setBackgroundColor(toAwtColor(cs.getFillBackgroundColorColor()));
                            excelCell.setRotation(cs.getRotation());
                            excelCell.setHorizontalAlignment(cs.getAlignmentEnum().toString().toLowerCase());
                            excelCell.setVerticalAlignment(cs.getVerticalAlignmentEnum().toString().toLowerCase());
                            excelCell.setBorderTop(cs.getBorderTopEnum().toString().toLowerCase());
                            excelCell.setBorderLeft(cs.getBorderLeftEnum().toString().toLowerCase());
                            excelCell.setBorderRight(cs.getBorderRightEnum().toString().toLowerCase());
                            excelCell.setBorderBottom(cs.getBorderBottomEnum().toString().toLowerCase());
                            
                            excelCell.setDataFormat(cs.getDataFormatString());
                        }
                        
                        CellType formularCellType = null;
                        excelCell.setCellType(cell.getCellTypeEnum().toString().toLowerCase());
                        switch (cell.getCellTypeEnum()) {
                            case NUMERIC:
                                excelCell.setValueNumeric(cell.getNumericCellValue());
                                break;
                            case BOOLEAN:
                                excelCell.setValueBoolean(cell.getBooleanCellValue());
                                break;
                            case STRING:
                                String plainText = cell.getStringCellValue();
                                excelCell.setValueString(plainText);

                                StringBuilder richTextBuilder = new StringBuilder();

                                //rich text
                                RichTextString richTextString = cell.getRichStringCellValue();
                                if (richTextString instanceof XSSFRichTextString) {
                                    XSSFRichTextString rts = (XSSFRichTextString) richTextString;

                                    if (rts.hasFormatting()) {

                                        //numFormattingRuns is how often the <main:r> tag is in rts.getCTRst().xmlText()
                                        //String xmlText = rts.getCTRst().xmlText();
                                        for (int r = 0; r < rts.numFormattingRuns(); r++) {

                                            ExcelTextStyle textStyle = new ExcelTextStyle();

                                            int begin = rts.getIndexOfFormattingRun(r);
                                            int length = rts.getLengthOfFormattingRun(r);
                                            String subtext = plainText.substring(begin, begin + length);

                                            textStyle.setId(textStyleId++);
                                            textStyle.setCellId(excelCell.getId());

                                            textStyle.setBegin(begin);
                                            textStyle.setEnd(begin + length);
                                            textStyle.setText(subtext);

                                            XSSFFont font = rts.getFontOfFormattingRun(r);
                                            
                                            //if font is null use cell's font
                                            if(font == null && cst instanceof XSSFCellStyle) {
                                                XSSFCellStyle cs = (XSSFCellStyle) cell.getCellStyle();
                                                XSSFFont cellFont = cs.getFont();
                                                font = cellFont;
                                            }
                                            
                                            if (font != null) {
                                                /*
                                                <font face='' size='' color=''></font>
                                                <br>
                                                <i></i>
                                                <u></u>
                                                <b></b>
                                                <strike></strike>
                                                */

                                                textStyle.setFontBold(font.getBold());
                                                if (font.getBold()) {
                                                    richTextBuilder.append("<b>");
                                                }

                                                textStyle.setFontItalic(font.getItalic());
                                                if (font.getItalic()) {
                                                    richTextBuilder.append("<i>");
                                                }

                                                textStyle.setFontStrikeout(font.getStrikeout());
                                                if (font.getStrikeout()) {
                                                    richTextBuilder.append("<strike>");
                                                }

                                                boolean underline = font.getUnderline() != FontUnderline.NONE.getByteValue();
                                                textStyle.setFontUnderline(underline);
                                                if (underline) {
                                                    richTextBuilder.append("<u>");
                                                }

                                                textStyle.setFontName(font.getFontName());
                                                textStyle.setFontSize(font.getFontHeightInPoints());
                                                textStyle.setFontColor(toAwtColor(font.getXSSFColor()));

                                                richTextBuilder.append("<font ");
                                                if (font.getFontName() != null) {
                                                    richTextBuilder.append("face='" + font.getFontName() + "' ");
                                                }
                                                if (textStyle.getFontColor() != null) {
                                                    String hex = String.format("#%02x%02x%02x",
                                                            textStyle.getFontColor().getRed(),
                                                            textStyle.getFontColor().getGreen(),
                                                            textStyle.getFontColor().getBlue()
                                                    );
                                                    richTextBuilder.append("color='" + hex + "' ");
                                                }
                                                
                                                //size is not correct when shown in JTable
                                                //richTextBuilder.append("size='" + font.getFontHeightInPoints() + "' ");
                                                richTextBuilder.append(">");

                                                richTextBuilder.append(subtext.replace("\n", "<br>"));

                                                richTextBuilder.append("</font>");

                                                if (underline) {
                                                    richTextBuilder.append("</u>");
                                                }
                                                if (font.getStrikeout()) {
                                                    richTextBuilder.append("</strike>");
                                                }
                                                if (font.getItalic()) {
                                                    richTextBuilder.append("</i>");
                                                }
                                                if (font.getBold()) {
                                                    richTextBuilder.append("</b>");
                                                }

                                            } else {
                                                richTextBuilder.append(subtext.replace("\n", "<br>"));
                                            }

                                            textStyleBuffer.add(textStyle);
                                        }
                                    }
                                }

                                //set always if string value is available
                                excelCell.setValueRichText(richTextBuilder.toString());

                                //richTextString.numFormattingRuns() how many changes are made
                                //richTextString.toString is unformatted
                                break;
                            case FORMULA:
                                try {
                                excelCell.setValueFormular(cell.getCellFormula());
                            } catch (Exception e) {
                                //ignore
                                //excelCell.setValueFormular("formula error");
                            }
                            formularCellType = cell.getCachedFormulaResultTypeEnum();
                            break;
                            case ERROR:
                                excelCell.setValueError(cell.getErrorCellValue());
                                break;
                        }

                        excelCell.setRow(cell.getAddress().getRow());
                        excelCell.setColumn(cell.getAddress().getColumn());
                        excelCell.setAddress(cell.getAddress().toString());
                        

                        if (formularCellType != null) {
                            excelCell.setCellTypeFormular(formularCellType.toString().toLowerCase());

                            switch (formularCellType) {
                                case NUMERIC:
                                    excelCell.setValueNumeric(cell.getNumericCellValue());
                                    break;
                                case BOOLEAN:
                                    excelCell.setValueBoolean(cell.getBooleanCellValue());
                                    break;
                                case STRING:
                                    excelCell.setValueString(cell.getStringCellValue());
                                    break;
                            }
                        }

                        listener.setCurrent(runningCellNumber++);

                        //flush buffer
                        if (cellBuffer.size() >= CELL_BUFFER_SIZE) {
                            to.addCellBulk(cellBuffer);
                            cellBuffer.clear();
                        }

                        if (textStyleBuffer.size() >= TEXT_STYLE_BUFFER_SIZE) {
                            to.addTextStyleBulk(textStyleBuffer);
                            textStyleBuffer.clear();
                        }

                    }//for each col
                }//for each row

                //the rest
                to.addCellBulk(cellBuffer);
                cellBuffer.clear();

                to.addTextStyleBulk(textStyleBuffer);
                textStyleBuffer.clear();

            }//for each sheet

            workbook.close();
            System.gc();

        }//for each locator

        to.addBulk(sheetBuffer);
        sheetBuffer.clear();

    }

    @Override
    public TableModel preview(DataStoreDescription from) {
        return null;
    }

    @Override
    public Preference getPreference() {
        return new Preference();
    }

    @Override
    public String getName() {
        return "Excel";
    }

    @Override
    public DataModel getDataModel() {
        return DataModel.Relational;
    }

}
