package de.dfki.sds.hephaistos;

import de.dfki.sds.hephaistos.storage.DataIO;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Describes a data source to import or export it.
 * You have to define where it is located, which parts should be addressed and
 * some preference how to import or export the store.
 * 
 */
public class DataStoreDescription {
    
    //to refer to it
    private String id;
    
    //the code that import, exports and previews (singleton)
    private DataIO dataIO;
    
    //user given name
    private String name;
    
    //user given notes
    private String notes;
    
    //e.g. path or url
    private List<String> locators;
    
    //e.g. drive, subgraph
    private List<String> parts;
    
    //how to import or export it
    private Preference preference;

    public DataStoreDescription() {
        this.id = "desc:" + UUID.randomUUID().toString();
        this.locators = new ArrayList<>();
        this.parts = new ArrayList<>();
        this.preference = new Preference();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    public List<String> getLocators() {
        return locators;
    }

    public List<String> getParts() {
        return parts;
    }

    public Preference getPreference() {
        return preference;
    }

    public DataIO getDataIO() {
        return dataIO;
    }

    public void setDataIO(DataIO dataIO) {
        this.dataIO = dataIO;
    }

    public void setPreference(Preference preference) {
        this.preference = preference;
    }
    
    public String getId() {
        return id;
    }
    
    @Override
    public String toString() {
        return "DataStoreDescription{" + "id=" + id + ", dataIO=" + dataIO + ", name=" + name + ", locators=" + locators + ", parts=" + parts + ", preference=" + preference + '}';
    }
    
}
