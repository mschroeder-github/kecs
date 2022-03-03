package de.dfki.sds.hephaistos.storage.file;

import de.dfki.sds.hephaistos.storage.BranchLeafStorageSqlite;
import de.dfki.sds.hephaistos.storage.BranchLeafStorageSummary;
import de.dfki.sds.hephaistos.storage.InternalStorageMetaData;
import de.dfki.sds.hephaistos.storage.TypedName;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;

/**
 * A file store based on {@link BranchLeafStorageSqlite}
 * 
 */
public class FileInfoStorage extends BranchLeafStorageSqlite<FolderInfo, FileInfo, BranchLeafStorageSummary> {

    private File cacheFolder;
    private File contentFolder;
    private boolean considerFileContent;
    
    public FileInfoStorage(InternalStorageMetaData metaData, Connection connection, File cacheFolder) {
        super(metaData, connection);
        this.cacheFolder = cacheFolder;
    }

    public boolean isConsiderFileContent() {
        return considerFileContent;
    }

    public void setConsiderFileContent(boolean considerFileContent) {
        this.considerFileContent = considerFileContent;
        if(considerFileContent) {
            cacheFolder.mkdirs();
            contentFolder = new File(cacheFolder, metaData.getId()); 
            contentFolder.mkdirs();
        } else {
            contentFolder = new File(cacheFolder, metaData.getId());
            FileUtils.deleteQuietly(contentFolder);
        }
    }

    private File getFile(FileInfo leaf) {
        return new File(contentFolder, "" + leaf.getId() + ".txt");
    }
    
    @Override
    protected void insertAdditionally(FileInfo leaf) {
        if(!considerFileContent)
            return;
        
        if(leaf.getContent() == null)
            return;
        
        File f = getFile(leaf);
        try {
            FileUtils.writeStringToFile(f, leaf.getContent(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    protected void retrieveAdditionally(FileInfo leaf) {
        if(!considerFileContent)
            return;
        
        File f = getFile(leaf);
        if(f.exists()) {
            try {
                leaf.setContent(FileUtils.readFileToString(f, StandardCharsets.UTF_8));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    protected void removeAdditionally(FileInfo leaf) {
        if(!considerFileContent)
            return;
        
        File f = getFile(leaf);
        f.delete();
    }
    
    @Override
    protected List<TypedName> getBranchSchema() {
        return getSchema();
    }

    @Override
    protected List<TypedName> getLeafSchema() {
        return getSchema();
    }

    private  List<TypedName> getSchema() {
        return Arrays.asList(
                new TypedName("name", String.class),
                new TypedName("path", String.class),
                new TypedName("meta", String.class),
                new TypedName("lastModifiedTime", Integer.class),
                new TypedName("lastAccessTime", Integer.class),
                new TypedName("creationTime", Integer.class),
                new TypedName("size", Integer.class),
                new TypedName("symbolicLink", Boolean.class)
        );
    }
    
    private Object[] getInsertParams(FileInfo file) {
        return new Object[] { 
            file.getName(), 
            file.getPath(), 
            file.getMeta(),
            file.getLastModifiedTimeMillis(),
            file.getLastAccessTimeMillis(), 
            file.getCreationTimeMillis(), 
            file.getSize(), 
            file.isSymbolicLink()
        };
    }
    
    @Override
    protected Object[] getBranchInsertParams(FolderInfo branch) {
        return getInsertParams(branch);
    }

    @Override
    protected Object[] getLeafInsertParams(FileInfo leaf) {
        return getInsertParams(leaf);
    }

    @Override
    protected Class<FolderInfo> getBranchClass() {
        return FolderInfo.class;
    }

    @Override
    protected Class<FileInfo> getLeafClass() {
        return FileInfo.class;
    }

    @Override
    protected FolderInfo getBranchFromRow(Row rs) {
        return getFolderFromRow(rs);
    }

    @Override
    protected FileInfo getLeafFromRow(Row rs) {
        return getFileFromRow(rs);
    }

    private FileInfo getFileFromRow(Row rs) {
        FileInfo fi = new FileInfo();
        fi.setName(rs.getString(1));
        fi.setPath(rs.getString(2));
        fi.setMeta(rs.getString(3));
        fi.setLastModifiedTime(FileTime.fromMillis(rs.getLong(4)));
        fi.setLastAccessTime(FileTime.fromMillis(rs.getLong(5)));
        fi.setCreationTime(FileTime.fromMillis(rs.getLong(6)));
        fi.setSize(rs.getLong(7));
        fi.setSymbolicLink(rs.getBoolean(8));
        return fi;
    }
    
    private FolderInfo getFolderFromRow(Row rs) {
        FolderInfo fi = new FolderInfo();
        fi.setName(rs.getString(1));
        fi.setPath(rs.getString(2));
        fi.setMeta(rs.getString(3));
        fi.setLastModifiedTime(FileTime.fromMillis(rs.getLong(4)));
        fi.setLastAccessTime(FileTime.fromMillis(rs.getLong(5)));
        fi.setCreationTime(FileTime.fromMillis(rs.getLong(6)));
        fi.setSize(rs.getLong(7));
        fi.setSymbolicLink(rs.getBoolean(8));
        return fi;
    }
    
    @Override
    protected MetaData getBranchMetaData(FolderInfo branch) {
        return new MetaData(branch.getId(), branch.getParent(), branch.getSort());//, branch.isDirectory() ? 0 : 1);
    }

    @Override
    protected MetaData getLeafMetaData(FileInfo leaf) {
        return new MetaData(leaf.getId(), leaf.getParent(), leaf.getSort());//, leaf.isDirectory() ? 0 : 1);
    }

    @Override
    protected void setBranchMetaData(FolderInfo branch, MetaData metaData) {
        branch.setId(metaData.getId());
        branch.setParent(metaData.getParent());
        branch.setSort(metaData.getSort());
        branch.setDirectory(true);
    }

    @Override
    protected void setLeafMetaData(FileInfo leaf, MetaData metaData) {
        leaf.setId(metaData.getId());
        leaf.setParent(metaData.getParent());
        leaf.setSort(metaData.getSort());
        leaf.setDirectory(false);
    }

    @Override
    public BranchLeafStorageSummary summary() {
        return new BranchLeafStorageSummary(this, metaData.getSummaryCache());
    }
    
}
