package de.dfki.sds.hephaistos.storage.excel;

import de.dfki.sds.hephaistos.storage.StorageItem;
import java.awt.Color;

/**
 *
 * 
 */
public class ExcelTextStyle implements StorageItem {
    
    private int id;
    private int cellId;
    
    private String text;
    private int begin;
    private int end;
    
    private boolean fontBold;
    private boolean fontItalic;
    private boolean fontStrikeout;
    private boolean fontUnderline;
    private int fontSize;
    private String fontName;
    private Color fontColor;

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 29 * hash + this.id;
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
        final ExcelTextStyle other = (ExcelTextStyle) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }

    public int getBegin() {
        return begin;
    }

    public void setBegin(int begin) {
        this.begin = begin;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }
    
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCellId() {
        return cellId;
    }

    public void setCellId(int cellId) {
        this.cellId = cellId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isFontBold() {
        return fontBold;
    }

    public void setFontBold(boolean fontBold) {
        this.fontBold = fontBold;
    }

    public boolean isFontItalic() {
        return fontItalic;
    }

    public void setFontItalic(boolean fontItalic) {
        this.fontItalic = fontItalic;
    }

    public boolean isFontStrikeout() {
        return fontStrikeout;
    }

    public void setFontStrikeout(boolean fontStrikeout) {
        this.fontStrikeout = fontStrikeout;
    }

    public boolean isFontUnderline() {
        return fontUnderline;
    }

    public void setFontUnderline(boolean fontUnderline) {
        this.fontUnderline = fontUnderline;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public String getFontName() {
        return fontName;
    }

    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    public Color getFontColor() {
        return fontColor;
    }

    public void setFontColor(Color fontColor) {
        this.fontColor = fontColor;
    }
    
    @Override
    public String toString() {
        return "ExcelTextStyle{" + "id=" + id + ", cellId=" + cellId + ", text=" + text + ", fontBold=" + fontBold + ", fontItalic=" + fontItalic + ", fontStrikeout=" + fontStrikeout + ", fontUnderline=" + fontUnderline + ", fontSize=" + fontSize + ", fontName=" + fontName + ", fontColor=" + fontColor + '}';
    }
    
}
