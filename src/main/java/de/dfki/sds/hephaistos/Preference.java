package de.dfki.sds.hephaistos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * A preference is a list of settings (key value pairs).
 * 
 */
public class Preference extends ArrayList<Setting> {

    public Preference() {
    }

    public Preference(Collection<? extends Setting> c) {
        super(c);
    }
 
    public Preference(Setting... settings) {
        super(Arrays.asList(settings));
    }
    
    public Preference(Preference pref) {
        for(Setting setting : pref) {
            add(new Setting(setting));
        }
    }
    
    public void applyValuesFrom(Preference pref) {
        for(Setting other : pref) {
            Setting my = get(other.getName());
            if(my != null) {
                my.setValue(other.getValue());
                my.setArray(other.getArray());
                my.setChoice(other.getChoice());
            }
        }
    }
    
    public boolean has(String name) {
        return get(name) != null;
    }
    
    public Setting get(String name) {
        for(Setting setting : this) {
            if(setting.getName().equals(name)) {
                return setting;
            }
        }
        return null;
    }
    
    public boolean getValueAsBoolean(String name) {
        return getValueAsBoolean(name, false);
    }
    
    public boolean getValueAsBoolean(String name, boolean def) {
        if(!has(name))
            return def;
        
        return (boolean) get(name).getValue();
    }
    
    public String getValueAsString(String name) {
        return getValueAsString(name, null);
    }
    
    public String getValueAsString(String name, String def) {
        if(!has(name))
            return def;
        
        return (String) get(name).getValue();
    }
    
    public int getValueAsInt(String name) {
        return getValueAsInt(name, 0);
    }
    
    public int getValueAsInt(String name, int def) {
        if(!has(name))
            return def;
        
        Object value = get(name).getValue();
        if(value instanceof Double) {
            return (int) Math.round((double)value);
        }
        return (int) get(name).getValue();
    }
    
    public Preference setValue(String name, int value) {
        if(!has(name)) {
            add(new Setting(name, (int) value));
            return this;
        }
        get(name).setValue((int) value);
        return this;
    }
    
    public Preference setValue(String name, String value) {
        if(!has(name)) {
            add(new Setting(name, value));
            return this;
        }
        get(name).setValue(value);
        return this;
    }
    
    public Preference setValue(String name, boolean value) {
        if(!has(name)) {
            add(new Setting(name, value));
            return this;
        }
        get(name).setValue(value);
        return this;
    }
}
