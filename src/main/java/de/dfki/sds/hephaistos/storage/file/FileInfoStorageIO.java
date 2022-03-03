package de.dfki.sds.hephaistos.storage.file;

import de.dfki.sds.hephaistos.DataStoreDescription;
import de.dfki.sds.hephaistos.Preference;
import de.dfki.sds.hephaistos.Setting;
import de.dfki.sds.hephaistos.storage.DataIO;
import de.dfki.sds.hephaistos.storage.DataModel;
import de.dfki.sds.hephaistos.storage.InternalStorage;
import de.dfki.sds.hephaistos.storage.StorageManager;
import de.dfki.sds.mschroeder.commons.lang.FileUtility;
import de.dfki.sds.mschroeder.commons.lang.RegexUtility;
import de.dfki.sds.mschroeder.commons.lang.swing.LoadingListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.StringJoiner;
import java.util.zip.GZIPInputStream;
import javax.swing.tree.TreeModel;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONObject;

/**
 *
 * 
 */
public class FileInfoStorageIO extends DataIO<FileInfoStorage, TreeModel> {

    @Override
    public InternalStorage createInternalStorage(StorageManager storageManager) {
        return storageManager.getFileInfoStorage();
    }

    @Override
    public void exporting(FileInfoStorage from, DataStoreDescription to, LoadingListener listener) {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public void importing(DataStoreDescription from, FileInfoStorage to, LoadingListener listener) throws IOException {
        int fileInfoId = 2;

        for (String locator : from.getLocators()) {

            File locatedFile = new File(locator);

            if (locatedFile.isFile()) {
                boolean filePathList = from.getPreference().getValueAsBoolean("file path list", false);
                if(filePathList) {
                    importingFromFilePathList(locatedFile, from, to, listener);
                } else {
                    importingFromLinuxFind(locatedFile, from, to, listener);
                }
            } else if (locatedFile.isDirectory()) {
                fileInfoId = importingFromLocalFilesystem(locatedFile, from, to, listener, fileInfoId);
            }
        }
    }

    //the first version we had
    private void importingFromFilePathList(File file, DataStoreDescription desc, FileInfoStorage to, LoadingListener listener) throws IOException {
        String charsetName = desc.getPreference().getValueAsString(Setting.CHARSET, "UTF-8");
        String separator = desc.getPreference().getValueAsString(Setting.SEPARATOR, "/");
        
        InputStream is = new FileInputStream(file);
        if (desc.getPreference().getValueAsBoolean(Setting.GZIP)) {
            is = new GZIPInputStream(is);
        }
        InputStreamReader isr = new InputStreamReader(is, charsetName);
        BufferedReader br = new BufferedReader(isr);
        
        int id = 2;
        Set<FileInfo> bulk = new HashSet<>();
        
        int lineCount = FileUtility.countLines(file);
        listener.setMaximum(lineCount);
        listener.setCurrent(1);

        int lineIndex = 1;
        
        class Node {
            String name;
            Map<String, Node> name2child = new HashMap<>();
            boolean isFile;
            Node parent;
            FileInfo fileInfo;
            String uri;
            String line;
        }
        
        Node root = new Node();
        root.name = "ROOT";
        
        String line;
        while ((line = br.readLine()) != null) {
            listener.setCurrent(lineIndex++);
            
            if(listener.cancel()) {
                break;
            }
            
            List<String> segments = new ArrayList<>(Arrays.asList(line.split(RegexUtility.quote(separator))));
            Node cur = root;
            for(int i = 0; i < segments.size(); i++) {
                String seg = segments.get(i);
                
                if(cur.name2child.containsKey(seg)) {
                    cur = cur.name2child.get(seg);
                    
                } else {
                    
                    Node n = new Node();
                    n.name = seg;
                    n.isFile = i == segments.size() - 1;
                    cur.name2child.put(seg, n);
                    n.parent = cur;
                    
                    StringJoiner sj = new StringJoiner(separator);
                    segments.subList(0, i + 1).forEach(str -> sj.add(str));
                    n.line = sj.toString(); //FileUtility.file(segments).toURI().toString();
                    
                    cur = n;
                }
            }
        }
        
        if(listener.cancel()) {
            return;
        }
        
        Queue<Node> queue = new LinkedList<>();
        queue.add(root);
        
        while(!queue.isEmpty()) {
            
            if(listener.cancel()) {
                break;
            }
            
            Node cur = queue.poll();
            
            List<Node> childNodes = new ArrayList<>(cur.name2child.values());
            
            //already sort correct
            childNodes.sort((a, b) -> {
                int cmp = Boolean.compare(b.isFile, a.isFile);
                if(cmp == 0) {
                    return a.name.compareToIgnoreCase(b.name);
                }
                return cmp;
            });
            
            int sortIndex = 0;
            for(Node childNode : childNodes) {

                FileInfo fi;
                if (!childNode.isFile) {
                    fi = new FolderInfo();
                } else {
                    fi = new FileInfo();
                }
                fi.setId(id);
                fi.setDirectory(!childNode.isFile);
                fi.setPath(childNode.line);
                fi.setName(childNode.name);
                fi.setSort(sortIndex++);
                
                //basename stored in meta
                String basename = childNode.name;
                if(childNode.isFile) {
                    basename = FilenameUtils.getBaseName(childNode.name);
                }
                
                JSONObject meta = new JSONObject();
                meta.put("uri", "urn:file:" + id);
                meta.put("basename", basename);
                fi.setMeta(meta.toString());
                
                childNode.fileInfo = fi;
                
                if(childNode.parent != root) {
                    fi.setParent(childNode.parent.fileInfo.getId());
                } else {
                    //root has always ID 1
                    fi.setParent(1);
                }
                
                id++;
                bulk.add(fi);
                
                queue.add(childNode);
            }
        }
        
        to.insertBulk(bulk);
    }
    
    //linux
    //find "$(pwd -P)" -type f
    //find "$(pwd -P)"
    //find -printf "%y %p\n"
    //find "$(pwd -P)" -printf "%y %p\n"
    //find "$(pwd -P)" -printf "%y %p\n" | gzip > out.txt.gz
    //has to be sorted output (parents come before their children)
    private void importingFromLinuxFind(File file, DataStoreDescription desc, FileInfoStorage to, LoadingListener listener) throws IOException {
        
        int maxDepth = desc.getPreference().getValueAsInt("max depth", -1);
        int maxLimit = desc.getPreference().getValueAsInt("max limit", -1);
        boolean allowHidden = desc.getPreference().getValueAsBoolean("hidden", false);
        boolean tika = desc.getPreference().getValueAsBoolean("tika", false);
        String charsetName = desc.getPreference().getValueAsString(Setting.CHARSET, "UTF-8");
        String separator = desc.getPreference().getValueAsString(Setting.SEPARATOR, "/");
        
        InputStream is = new FileInputStream(file);
        if (desc.getPreference().getValueAsBoolean(Setting.GZIP)) {
            is = new GZIPInputStream(is);
        }
        InputStreamReader isr = new InputStreamReader(is, charsetName);
        BufferedReader br = new BufferedReader(isr);

        Map<String, FileInfo> id2f = new HashMap<>();

        Map<FileInfo, Integer> file2depth = new HashMap<>();

        FileInfo root = null;

        List<String> errorLines = new ArrayList<>();
        List<String> missingParent = new ArrayList<>();

        int id = 2;
        Set<FileInfo> bulk = new HashSet<>();

        int lineCount = FileUtility.countLines(file);
        listener.setMaximum(lineCount);
        listener.setCurrent(1);

        int lineIndex = 1;

        String line;
        while ((line = br.readLine()) != null) {

            listener.setCurrent(lineIndex++);

            String type;
            String path;
            
            if(line.contains("\t")) {
                //the windows version, e.g. DIR 	C:\Bla\Blub	Blub	X:\Bla
                String[] split = line.split("\\t");
                type = split[0].trim();
                path = split[1];
                
            } else {
                //linux version: d or f and then path
                
                //System.out.println(line);
                type = line.substring(0, 1);
                path = line.substring(2, line.length());
            }
            
            int lastSep = path.lastIndexOf(separator);

            if (lastSep == -1) {
                errorLines.add(line);
                continue;
            }

            String parentPath = path.substring(0, lastSep);
            String childName = path.substring(lastSep + 1, path.length());

            FileInfo parent = id2f.get(parentPath);
            FileInfo child;
            if (type.equals("d") || type.equals("DIR")) {
                child = new FolderInfo();
            } else {
                child = new FileInfo();
            }
            child.setId(id);
            child.setDirectory(type.equals("d") || type.equals("DIR"));
            child.setPath(path);
            child.setName(childName);
            
            String basename = childName;
            if(!child.isDirectory()) {
                basename = FilenameUtils.getBaseName(childName);
            }
            
            JSONObject meta = new JSONObject();
            meta.put("uri", "urn:file:" + id);
            meta.put("basename", basename);
            child.setMeta(meta.toString());

            id++;

            bulk.add(child);

            
            
            if (parent != null) {
                child.setParent(parent.getId());
                //to.insertAsLastChild(child, parent);
            }

            int parentDepth = 0;
            if (parent != null) {
                parentDepth = file2depth.get(parent); //int) (long) parent.getTreeLevel();
            } else if (root != null) {
                //this is called when maxDepth is used
                //no parent is created so there are many missing ones
                missingParent.add(line);
                continue;
            }

            //first one will be the root
            if (root == null) {
                root = child;
                file2depth.put(root, 1);
                //root.setTreeLevel(1L);

                root.setParent(1);

                //special case
                //to.insertAsLastChild(root, to.getRoot());
            }

            int childDepth = parentDepth + 1;
            file2depth.put(child, childDepth); //child.setTreeLevel((long) childDepth);
            if (maxDepth > -1 && childDepth > maxDepth) {
                continue;
            }

            //if(parent != null) {
            //    parent.addChild(child);
            //}
            //child.setJsonMetadata(JSON.Object().addObject(FILE, file -> file.add("depth", childDepth).add("type", type.equals("f") ? "file" : "dir")).asJSONObject());
            id2f.put(path, child);
            
            if(maxLimit > -1 && bulk.size() >= maxLimit) {
                break;
            }
        }

        to.insertBulk(bulk);
    }

    private int importingFromLocalFilesystem(File root, DataStoreDescription desc, FileInfoStorage to, LoadingListener listener, int startId) {

        int maxDepth = desc.getPreference().getValueAsInt("max depth", -1);
        int maxLimit = desc.getPreference().getValueAsInt("max limit", -1);
        boolean allowHidden = desc.getPreference().getValueAsBoolean("hidden", false);
        boolean tika = desc.getPreference().getValueAsBoolean("tika", false);
        FileFilter filefilter = null;

        Queue<FileUtility.FileDepth> q = new LinkedList<>();
        q.add(new FileUtility.FileDepth(root, null, 0));

        Map<File, FileInfo> file2fi = new HashMap<>();

        int fileInfoId = startId;

        int currentProgress = 0;
        int maxProgress = 0;

        while (!q.isEmpty()) {

            if (listener.cancel()) {
                break;
            }

            listener.setMaximum(maxProgress);
            listener.setCurrent(currentProgress);

            FileUtility.FileDepth fd = q.poll();

            FileInfo fiCur = fromFile(fd.file, fileInfoId++, tika, root);
            file2fi.put(fd.file, fiCur);

            if (fd.parent != null) {

                //the first file has parent main root with id=1
                if (fiCur.getId() == startId) {
                    fiCur.setParent(1);
                } else {
                    fiCur.setParent(file2fi.get(fd.parent).getId());
                }
            } else {
                fiCur.setParent(1);
            }

            file2fi.put(fd.file, fiCur);

            if (maxDepth != -1 && fd.depth >= maxDepth) {
                break;
            }

            if (maxLimit != -1 && file2fi.size() >= maxLimit) {
                break;
            }

            //children
            File[] children;
            if (filefilter != null) {
                children = fd.file.listFiles(filefilter);
            } else {
                children = fd.file.listFiles();
            }

            //process child and recursive depth+1
            if (children != null) {
                maxProgress += children.length;

                List<File> childList = new ArrayList<>(Arrays.asList(children));
                childList.sort((a,b) -> a.getName().compareToIgnoreCase(b.getName()));
                
                for (File child : childList) {

                    if (!allowHidden && (child.isHidden() || child.getName().startsWith("."))) {
                        continue;
                    }

                    q.add(new FileUtility.FileDepth(child, fd.file, fd.depth + 1));
                }
            }

            currentProgress++;
        }

        to.insertBulk(file2fi.values());

        return fileInfoId;
    }

    private FileInfo fromFile(File file, int id, boolean tika, File root) {
        FileInfo fi;
        if (file.isDirectory()) {
            fi = new FolderInfo();
        } else {
            fi = new FileInfo();
        }
        fi.setId(id);
        fi.setName(file.getName());
        fi.setDirectory(file.isDirectory());
        fi.setPath(file.getAbsolutePath().replace(root.getAbsolutePath(), ""));
        fi.setSize(file.length());
        //TODO file times
        
        String basename = file.getName();
        if(!fi.isDirectory()) {
            basename = FilenameUtils.getBaseName(file.getName());
        }

        JSONObject meta = new JSONObject();
        meta.put("uri", "urn:file:" + id);
        meta.put("basename", file == root ? "" : basename);//do this to avoid putting terms on root folder
        fi.setMeta(meta.toString());
        
        //tika
        if(tika) {
            //dependency is big in size
            //fi.setContent(parseToPlainText(file));
        }
        
        return fi;
    }
    
    //tika stuff
    
    /*
    public String parseToPlainText(File file) {
        if(file.isDirectory())
            return null;
        
        BodyContentHandler handler = new BodyContentHandler();

        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        try (InputStream stream = new FileInputStream(file)) {
            parser.parse(stream, handler, metadata);
            return handler.toString();
        } catch (Exception ex) {
            //ignore
        }
        
        return null;
    }
    */

    @Override
    public TreeModel preview(DataStoreDescription from) {
        return null;
    }

    @Override
    public Preference getPreference() {
        return new Preference(
                new Setting(Setting.GZIP, false),
                new Setting(Setting.CHARSET, "UTF-8"),
                new Setting("max depth", -1),
                new Setting("max limit", -1),
                new Setting("hidden", false),
                new Setting("tika", false)
        );
    }

    @Override
    public String getName() {
        return "Hierarchical File System";
    }

    @Override
    public DataModel getDataModel() {
        return DataModel.Hierarchical;
    }

}
