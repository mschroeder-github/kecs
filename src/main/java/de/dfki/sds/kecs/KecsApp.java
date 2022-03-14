
package de.dfki.sds.kecs;

import de.dfki.sds.hephaistos.storage.assertion.Phase;
import de.dfki.sds.kecs.modules.ConceptDiscovery;
import de.dfki.sds.kecs.modules.ConceptHierarchyDerivation;
import de.dfki.sds.kecs.modules.DomainTerminologyExtraction;
import de.dfki.sds.kecs.modules.NonTaxonomicRelationLearning;
import de.dfki.sds.kecs.modules.OntologyPopulation;
import de.dfki.sds.kecs.server.KecsHumlServer;
import de.dfki.sds.kecs.util.ExceptionUtility;
import de.dfki.sds.mschroeder.commons.lang.MemoryUtility;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 * 
 */
public class KecsApp {
    
    public static final String VERSION = "2022-03-14";
    
    public static Model creator = ModelFactory.createDefaultModel();
    
    private KecsSettings settings;
    
    public KecsApp(String[] args) {
        settings = KecsSettings.loadCommandline(args);
    }
    
    public void run() {
        
        KecsManager manager = new KecsManager(settings);
        
        manager.setModule(Phase.DomainTerminologyExtraction, new DomainTerminologyExtraction());
        manager.setModule(Phase.ConceptDiscovery, new ConceptDiscovery());
        manager.setModule(Phase.OntologyPopulation, new OntologyPopulation());
        manager.setModule(Phase.ConceptHierarchyDerivation, new ConceptHierarchyDerivation());
        manager.setModule(Phase.NonTaxonomicRelationLearning, new NonTaxonomicRelationLearning());
        
        if(settings.isRunServer()) {
            if(settings.isLoadEmbedding()) {
                manager.loadEmbedding();
            }
            if(settings.isLoadLanguageResource()) {
                manager.loadLanguageResource();
            }
        }
        
        switch(settings.getMode()) {
            case BootstrapFilesystem: 
                manager.bootstrapFromFilesystem(
                        settings.getInput(), 
                        settings.getOutputFolder(),
                        settings.getLimit()
                );
                break;
                
            case BootstrapFilesystemDump: 
                manager.bootstrapFromFilesystemDump(
                        settings.getInput(), 
                        settings.getOutputFolder(),
                        settings.getCharset(),
                        settings.isFilePathList(),
                        settings.getFileSeparator()
                );
                break;
                
            case BootstrapExcel: 
                manager.bootstrapFromExcel(
                        settings.getInput(), 
                        settings.getOutputFolder()
                );
                break;
        
            case Load:
                manager.loadAssertionPool(settings.getOutputFolder());
                break;
            
            case Demo:
                manager.loadDemo(settings.getOutputFolder());
                break;
               
            //case Replay:
            //    manager.replay(settings.getOutputFolder());
            //    break;
        }
        
        if(settings.isRunServer()) {
            KecsHumlServer server = new KecsHumlServer(settings.getPort(), settings.getUserCsvFile(), manager, settings.getOutputFolder(), settings.getLanguage());
            server.start();
            
            if(settings.isOpenBrowser()) {
                try {
                    Desktop.getDesktop().browse(new URI("http://localhost:" + settings.getPort()));
                } catch (URISyntaxException | IOException ex) {
                    ExceptionUtility.save(ex);
                    throw new RuntimeException(ex);
                }
            }
            
        } else {
            manager.closeStorage();
        }
        
        System.out.println(MemoryUtility.memoryStatistics());
    }
    
    public static void main(String[] args) {
        KecsApp app = new KecsApp(args);
        app.run();
    }
    
}
