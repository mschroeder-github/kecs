package de.dfki.sds.hephaistos.storage;

/**
 * Just a class holding a name and a type (class).
 * 
 */
public class TypedName {
    
    private String name;
    private Class type;

    public TypedName(String name, Class type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Class getType() {
        return type;
    }

    public void setType(Class type) {
        this.type = type;
    }

    public TypedName withPrefix(String prefix) {
        return new TypedName(prefix + name, type);
    }
    
    @Override
    public String toString() {
        return "TypedName{" + "name=" + name + ", type=" + type + '}';
    }
    
}
