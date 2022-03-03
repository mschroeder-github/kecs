package de.dfki.sds.kecs;

import de.dfki.sds.hephaistos.DataStoreDescription;
import de.dfki.sds.hephaistos.Setting;
import de.dfki.sds.hephaistos.storage.InternalStorageMetaData;
import de.dfki.sds.hephaistos.storage.StorageManager;
import de.dfki.sds.hephaistos.storage.assertion.AssertionPool;
import de.dfki.sds.hephaistos.storage.assertion.Phase;
import de.dfki.sds.hephaistos.storage.excel.ExcelStorageIO;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorageIO;
import de.dfki.sds.kecs.KecsSettings.Language;
import de.dfki.sds.kecs.ml.DefaultItemEmbedding;
import de.dfki.sds.kecs.ml.StatusManager;
import de.dfki.sds.kecs.ml.VisualManager;
import de.dfki.sds.kecs.modules.DomainTerminologyExtraction;
import de.dfki.sds.kecs.modules.Module;
import de.dfki.sds.kecs.server.KecsHumlServer;
import de.dfki.sds.kecs.util.ColumnMemoryExcelStorage;
import de.dfki.sds.kecs.util.ExceptionUtility;
import de.dfki.sds.mschroeder.commons.lang.swing.EmptyLoadingListener;
import de.dfki.sds.stringanalyzer.helper.GermaNet;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * Manages data and modules. Used by {@link KecsHumlServer}.
 */
public class KecsManager {

    private Map<Phase, Module> phase2module;

    private StorageManager storageManager;
    private FileInfoStorage fileInfoStorage;
    private AssertionPool assertionPool;
    
    private StatusManager statusManager;
    private VisualManager visualManager;
    
    private KecsSettings settings;

    public KecsManager(KecsSettings settings) {
        this.settings = settings;
        phase2module = new HashMap<>();
    }
    
    public void loadEmbedding() {
        long begin = System.currentTimeMillis();
        DefaultItemEmbedding embedding = new DefaultItemEmbedding(
                "/de/dfki/sds/kecs/auxiliary/country_state_city_month_years_names_stopword_germanet_dict_lemma.csv.gz",
                false);
        long end = System.currentTimeMillis();
        System.out.println("DefaultItemEmbedding init took " + (end - begin) + " ms");
        
        phase2module.values().forEach(m -> m.setEmbedding(embedding));
    }
    
    public void loadLanguageResource() {
        long begin = System.currentTimeMillis();
        //we reuse the germanet class also for wordnet
        GermaNet germaNet;
        try {
            String filename;
            if(settings.getLanguage() == Language.de) {
                filename = "germanet.json.gz";
            } else {
                filename = "wordnet.json.gz";
            }
            germaNet = new GermaNet(new GZIPInputStream(KecsManager.class.getResourceAsStream("/de/dfki/sds/kecs/auxiliary/" + filename)));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        long end = System.currentTimeMillis();
        
        phase2module.values().forEach(m -> m.setGermaNet(germaNet));
        System.out.println("Language Resource for "+ settings.getLanguage() +" init took " + (end - begin) + " ms " + germaNet.getStatusLine());
    }
    
    public void bootstrapFromFilesystemDump(File filesystemDumpFile, File assertionPoolFolder, String charset, boolean filePathList, String fileSeparator) {
        if (assertionPoolFolder.exists()) {
            throw new RuntimeException("There is already a folder at " + assertionPoolFolder + ". Use load or replay method");
        }

        openStorage(assertionPoolFolder);

        printLoading("Load dump into file info storage", () -> {
            FileInfoStorageIO io = new FileInfoStorageIO();

            DataStoreDescription dsd = new DataStoreDescription();
            dsd.getLocators().add(filesystemDumpFile.getAbsolutePath());
            
            dsd.getPreference().setValue(Setting.CHARSET, charset);
            dsd.getPreference().setValue("file path list", filePathList);
            dsd.getPreference().setValue(Setting.SEPARATOR, fileSeparator);
            dsd.getPreference().setValue(Setting.GZIP, filesystemDumpFile.getName().endsWith("gz"));
            
            try {
                io.importing(dsd, fileInfoStorage, new EmptyLoadingListener());
            } catch (IOException ex) {
                ExceptionUtility.save(ex);
                throw new RuntimeException(ex);
            }
        });

        modulesBootstrap();
    }

    //first crawl filesystem to get classification schema
    public void bootstrapFromFilesystem(File rootFolder, File assertionPoolFolder, int limit) {
        if (assertionPoolFolder.exists()) {
            throw new RuntimeException("There is already a folder at " + assertionPoolFolder + ". Use load or replay method");
        }
        if(rootFolder.isFile()) {
            throw new RuntimeException("The input file has to be a folder");
        }
        
        openStorage(assertionPoolFolder);

        printLoading("Load native filesystem into file info storage", () -> {
            FileInfoStorageIO io = new FileInfoStorageIO();

            DataStoreDescription dsd = new DataStoreDescription();
            dsd.getLocators().add(rootFolder.getAbsolutePath());
            
            dsd.getPreference().setValue("max limit", limit);
            
            try {
                io.importing(dsd, fileInfoStorage, new EmptyLoadingListener());
            } catch (IOException ex) {
                ExceptionUtility.save(ex);
                throw new RuntimeException(ex);
            }
        });

        modulesBootstrap();
    }

    public void bootstrapFromExcel(File excelFile, File assertionPoolFolder) {
        if (assertionPoolFolder.exists()) {
            throw new RuntimeException("There is already a folder at " + assertionPoolFolder + ". Use load or replay method");
        }
        
        openStorage(assertionPoolFolder);
                
        printLoading("Load excel into file info storage", () -> {
            //load spreadsheet
            ExcelStorageIO io = new ExcelStorageIO();
            DataStoreDescription dsd = new DataStoreDescription();
            dsd.getLocators().add(excelFile.getAbsolutePath());

            ColumnMemoryExcelStorage excelStorage = new ColumnMemoryExcelStorage(new InternalStorageMetaData("id", "ColumnMemoryExcelStorage"));
            try {
                io.importing(dsd, excelStorage, new EmptyLoadingListener());
            } catch (IOException ex) {
                ExceptionUtility.save(ex);
                throw new RuntimeException(ex);
            }
            
            //will commit in fileInfoStorage, like io.importing
            ColumnMemoryExcelStorage.convertToTree(excelStorage, fileInfoStorage, settings);
        });

        modulesBootstrap();
    }
    
    private void openStorage(File assertionPoolFolder) {
        //Counter.getSingleton().reset("concept");
        //Counter.getSingleton().reset("type");
        
        printLoading("Open storage", () -> {
            storageManager = new StorageManager(assertionPoolFolder);
            storageManager.open();

            fileInfoStorage = storageManager.getFileInfoStorage("classificationSchema");
            assertionPool = storageManager.getAssertionPool("Assertion");
            
            assertionPool.getCounter().reset("concept");
            assertionPool.getCounter().reset("type");
        });
    }
    
    private void modulesBootstrap() {
        //bootstrap already calls commit
        //we go in bulk mode so notification is disabled
        assertionPool.setBulkMode(true);
        for (Module module : getModules()) {
            printLoading("Init and bootstrap module " + module.getClass().getSimpleName(), () -> {
                module.init(fileInfoStorage, assertionPool, settings);
                module.bootstrap(fileInfoStorage, assertionPool, settings);
            });
        }
        assertionPool.setBulkMode(false);

        for (Module module : getModules()) {
            assertionPool.addListener(module);
        }
        
        printLoading("Init status manager", () -> {
            statusManager = new StatusManager(fileInfoStorage, assertionPool, settings);
        });
        printLoading("Init visual manager", () -> {
            visualManager = new VisualManager(fileInfoStorage, assertionPool, settings);
        });
    }
    
    public void closeStorage() {
        printLoading("Close storage", () -> {
            storageManager.close();
        });
    }
    
    //assume there is already human feedback
    //do AI but then replay human actions (time preserving)
    /*
    public void replay(File assertionPoolFolder) {
        if (!AssertionPool.loadable(assertionPoolFolder, assertionPoolType)) {
            throw new RuntimeException("There is no folder at " + assertionPoolFolder + " with an assertion pool. Use boostrap method");
        }

        //AssertionPool loadedPool = AssertionPool.load(assertionPoolFolder);
        //List<Assertion> humanAssertions = loadedPool.getAssertions(null, null, null, null, Intelligence.NI, null, null, 0);
        //deletes counter in /results
        Counter.getSingleton().reset("concept");

        //do it new
        try {
            //do it new
            assertionPool = assertionPoolType.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }

        printLoading("Open annotation pool", () -> {
            assertionPool.open(assertionPoolFolder);
        });

        printLoading("Load classification schema", () -> {
            assertionPool.loadClassificationSchemaFromFolder(assertionPoolFolder);
        });

        for (Module module : getModules()) {
            module.bootstrap(assertionPool);
        }

        assertionPool.clearNotificationBuffer();

        for (Module module : getModules()) {
            assertionPool.addListener(module);
        }

        //sort by time
        //humanAssertions.sort((a,b) -> a.getWhen().compareTo(b.getWhen()));
        //history is already sorted by time
        List<JSONObject> entries = AssertionPool.loadHistoryEntries(assertionPoolFolder);

        for (JSONObject entry : entries) {

            JSONArray array = entry.getJSONArray("list");
            for (int i = 0; i < array.length(); i++) {
                Assertion assertion = Assertion.fromJson(array.getJSONObject(i));
                assertionPool.assertAssertionTimePreserving(assertion);
            }

            assertionPool.notifyListenersRecursively();
        }

        assertionPool.commit(assertionPoolFolder);
    }
    */

    //folder is loaded and it is continued
    public void loadAssertionPool(File assertionPoolFolder) {
        printLoading("Open storage", () -> {
            storageManager = new StorageManager(assertionPoolFolder);
            storageManager.open();

            fileInfoStorage = storageManager.getFileInfoStorage("classificationSchema");
            assertionPool = storageManager.getAssertionPool("Assertion");
        });

        //should be empty anyway
        assertionPool.clearNotificationBuffer();

        for (Module module : getModules()) {
            printLoading("Init module " + module.getClass().getSimpleName(), () -> {
                module.init(fileInfoStorage, assertionPool, settings);
            });
            assertionPool.addListener(module);
        }
        
        printLoading("Init status manager", () -> {
            statusManager = new StatusManager(fileInfoStorage, assertionPool, settings);
        });
        printLoading("Init visual manager", () -> {
            visualManager = new VisualManager(fileInfoStorage, assertionPool, settings);
        });
    }

    public void loadDemo(File assertionPoolFolder) {
        
        //remove folder assertionPoolFolder
        FileUtils.deleteQuietly(assertionPoolFolder);
        
        //load demo.txt from resources into assertionPoolFolder
        byte[] data;
        try {
            data = IOUtils.toByteArray(KecsManager.class.getResourceAsStream("/de/dfki/sds/kecs/auxiliary/demo_"+ settings.getLanguage().name() +".txt"));
            
            File dumpFile = new File("kecs-demo.txt");
            FileUtils.writeByteArrayToFile(dumpFile, data);
            
            bootstrapFromFilesystemDump(dumpFile, assertionPoolFolder, "UTF-8", false, "/");
            
            FileUtils.deleteQuietly(dumpFile);
            
            closeStorage();
            
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        
        loadAssertionPool(assertionPoolFolder);
    }
    
    public List<Module> getModules() {
        List<Module> modules = new ArrayList<>();
        for (Phase phase : Phase.values()) {
            Module module = phase2module.get(phase);
            if (module != null) {
                modules.add(module);
            }
        }
        return modules;
    }

    public void setModule(Phase phase, Module module) {
        phase2module.put(phase, module);
    }

    public AssertionPool getAssertionPool() {
        return assertionPool;
    }

    private void printLoading(String message, Runnable run) {
        System.out.println(message);
        long begin = System.currentTimeMillis();
        run.run();
        long end = System.currentTimeMillis();
        System.out.println("took " + (end - begin) + " ms");
    }

    public FileInfoStorage getFileInfoStorage() {
        return fileInfoStorage;
    }

    public StatusManager getStatusManager() {
        return statusManager;
    }

    public VisualManager getVisualManager() {
        return visualManager;
    }
    
    public KecsSettings getSettings() {
        return settings;
    }
    
    public DomainTerminologyExtraction getDomainTerminologyExtraction() {
        return (DomainTerminologyExtraction) phase2module.get(Phase.DomainTerminologyExtraction);
    }
    
}
