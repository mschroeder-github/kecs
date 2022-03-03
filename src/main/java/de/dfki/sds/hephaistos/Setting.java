package de.dfki.sds.hephaistos;

import java.util.Arrays;

/**
 * A settings is part of a preference and has a name (key) and a value.
 * 
 */
public class Setting {
    
    //predefined settings names
    public static final String IS_AUXILIARY = "is auxiliary";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String GZIP = "gzip";
    public static final String CHARSET = "charset";
    public static final String SEPARATOR = "separator";
    
    private String name;
    private Object value;
    
    //if value is an array
    private Object[] array;
    private int choice;

    public Setting(String name) {
        this.name = name;
    }

    public Setting(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public Setting(String name, Object[] array, int choice) {
        this.name = name;
        this.value = array[choice];
        this.array = array;
        this.choice = choice;
    }

    public Setting(Setting setting) {
        this.name = setting.name;
        this.value = setting.value;
        this.array = setting.array;
        this.choice = setting.choice;
    }
    
    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    public int getChoice() {
        return choice;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void setChoice(int choice) {
        this.choice = choice;
    }

    /*package*/ void setArray(Object[] array) {
        this.array = array;
    }
    
    public Object[] getArray() {
        return array;
    }
    
    public boolean isArray() {
        return array != null;
    }

    @Override
    public String toString() {
        if(isArray()) {
            return "Setting{" + "name=" + name + ", value=" + value + ", array=" + Arrays.toString(array) + ", choice=" + choice + '}';
        }
        return "Setting{" + "name=" + name + ", value=" + value + '}';
    }
    
}
