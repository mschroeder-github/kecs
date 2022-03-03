package de.dfki.sds.hephaistos.storage.file;

import de.dfki.sds.hephaistos.storage.StorageItem;
import java.nio.file.attribute.FileTime;

/**
 *
 * 
 */
public class FileInfo implements StorageItem {
    
    private String deepLink;
    
    private int id;
    private int parent;
    private int sort;
    private int type;
    
    private String name;
    private String path;
    private String meta;
    
    private FileTime lastModifiedTime; 	
    private FileTime lastAccessTime;
    private FileTime creationTime;
    
    private long size;
    
    private boolean symbolicLink;
    
    //for leaf = file  the file's content (maybe got with tika)
    private String content;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getParent() {
        return parent;
    }

    public void setParent(int parent) {
        this.parent = parent;
    }

    public int getSort() {
        return sort;
    }

    public void setSort(int sort) {
        this.sort = sort;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
    
    public FileTime getLastModifiedTime() {
        return lastModifiedTime;
    }
    
    public long getLastModifiedTimeMillis() {
        return lastModifiedTime == null ? 0L : lastModifiedTime.toMillis();
    }

    public void setLastModifiedTime(FileTime lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }

    public FileTime getLastAccessTime() {
        return lastAccessTime;
    }

    public long getLastAccessTimeMillis() {
        return lastAccessTime == null ? 0L : lastAccessTime.toMillis();
    }
    
    public void setLastAccessTime(FileTime lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public FileTime getCreationTime() {
        return creationTime;
    }

    public long getCreationTimeMillis() {
        return creationTime == null ? 0L : creationTime.toMillis();
    }
    
    public void setCreationTime(FileTime creationTime) {
        this.creationTime = creationTime;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public boolean isDirectory() {
        return type == 0;
    }

    public void setDirectory(boolean directory) {
        this.type = directory ? 0 : 1;
    }

    public boolean isSymbolicLink() {
        return symbolicLink;
    }

    public void setSymbolicLink(boolean symbolicLink) {
        this.symbolicLink = symbolicLink;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
    }
    
    public boolean hasContent() {
        return content != null;
    }
    
    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + this.id;
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
        final FileInfo other = (FileInfo) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }


    public void setDeepLink(String deepLink) {
        this.deepLink = deepLink;
    }
    
    
    
}
