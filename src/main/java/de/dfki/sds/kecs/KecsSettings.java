
package de.dfki.sds.kecs;

import java.io.File;
import java.util.Arrays;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class KecsSettings {
    
    //read settings and do

    //user & ontology info -------
    private File userCsvFile;
    private File ontologyFile;

    //where is data --------
    public enum Mode {
        //a) crawl filesystem and create result folder
        BootstrapFilesystem,
        
        //b) bootstrap existing ttl file and create result folder
        //BoostrapClassificationSchema,
        
        //c) bootstrap existing txt file (via linux find) and create result folder
        BootstrapFilesystemDump,
        
        //f) for another use case: transform table in tree
        BootstrapExcel,
        
        //d) load existing result folder (useful for remote access)
        Load,
        
        //e) Demo for testing
        Demo,
        
        //f) replay existing result folder
        //Replay
    }
    private Mode mode;
            
    //I/O parameters -------
    
    //for crawl/bootstrap (file or folder)
    private File input;
    
    //for all modes
    private File outputFolder;
    
    private String charset;
    private boolean filePathList;
    private String fileSeparator;
    
    private int limit;
    
    private boolean loadEmbedding;
    private boolean loadLanguageResource;
    
    //TODO for now deprecated since excelWhitelist is used
    private int excelCharacterSumThreshold;
    private int excelContentCountThreshold;
    
    private String excelWhitelist;
    
    private boolean loadDefaultOntology;
    
    private int nonTaxonomicTimeout;
    private int nonTaxonomicDepthThreshold;
    
    private Language language;
    
    //what to do with it ---------

    //0) stop
    //1) start server 
    private boolean runServer;

    //server port (useful for remote access)
    private int port;
    //Knowledge acquisition Huml
    private final static int DEFAULT_PORT = (int) 'K' * 100 + (int) 'H';
    
    private final static int DEFAULT_LIMIT = 100000;
    
    private final static String DEFAULT_CHARSET = "UTF-8";
    private final static String DEFAULT_SEPARATOR = "\\";
    private final static String DEFAULT_USERS = "users.csv";
    private final static String DEFAULT_ONTOLOGY = "ontology.json";
    
    private final static int DEFAULT_EXCEL_CHARACTER_SUM_THRESHOLD = 5000;
    private final static int DEFAULT_EXCEL_CONTENT_COUNT_THRESHOLD = 30;
    
    //(maybe open browser)
    private boolean openBrowser;
    
    //behavior settings ---------
    
    private final static int DEFAULT_SAVE_STATUS_THRESHOLD = 10;
    private int saveStatusThreshold;
    
    public KecsSettings() {
        
    }
    
    public static KecsSettings loadCommandline(String[] cmdargs) {
        
        KecsSettings settings = new KecsSettings();
        
        Options options = new Options();
        OptionGroup g = new OptionGroup();
        
        options.addOption("h", "help", false, "Prints this help message.");
        
        options.addOption("u",  "users", true, "A CSV file with user credentials. Default: " + DEFAULT_USERS);
        options.addOption("O",  "ontology", true, "A json file with ontology information. Default: " + DEFAULT_ONTOLOGY);
        
        options.addOption("m", "mode", true, "Selects the required mode which defines what this program should do. Possible values are: " + Arrays.deepToString(Mode.values()));
        
        options.addOption("i", "input", true, "An input file or folder, depending on the selected mode.");
        options.addOption("o", "output", true, "A required folder where the feedback progress is loaded and stored.");
        
        options.addOption("s", "server", false, "Runs a localhost server which provides the user interface.");
        options.addOption("p", "port", true, "Changes the server port. Default: " + DEFAULT_PORT);
        options.addOption("b", "browser", false, "Opens the webapp in a standard browser.");
        
        options.addOption("c", "charset", true, "Changes character encoding in " + Mode.BootstrapFilesystemDump + " mode. Default: " + DEFAULT_CHARSET);
        options.addOption("fpl", "file-path-list", false, "Changes reading behavior in " + Mode.BootstrapFilesystemDump + " mode.");
        options.addOption("fs", "file-separator", true, "Changes separation behavior in " + Mode.BootstrapFilesystemDump + " mode. Default: " + DEFAULT_SEPARATOR);
        options.addOption("l", "limit", true, "Changes file crawl limit in " + Mode.BootstrapFilesystem + " mode. Default: " + DEFAULT_LIMIT);
        options.addOption("ew", "excel-whitelist", true, "A whitelist to filter columns in " + Mode.BootstrapExcel + " mode.");
        
        options.addOption("E", "no-embedding", false, "Disables the loading of the embedding files.");
        options.addOption("G", "no-language-resource", false, "Disables the loading of the language resource files.");
        options.addOption("D", "no-default-ontology", false, "Disables the loading of the default ontology.");
        
        options.addOption("ntt", "non-taxonomic-timeout", true, "Sets timeout for init module NonTaxonomicRelationLearning.");
        options.addOption("ntdt", "non-taxonomic-depth-threshold", true, "Sets depth threshold for init module NonTaxonomicRelationLearning.");
        
        options.addOption("lang", "language", true, "Sets language (en or de). Default: en");
        
        //parse it
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        String cmdLineSyntax = "java -jar kecs.jar";
        String header = "";
        String footer = "\nVisit https://github.com/mschroeder-github/kecs for more information.\nVersion: " + KecsApp.VERSION + "\n";
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, cmdargs);
        } catch (ParseException ex) {
            System.err.println(ex.getMessage());
            formatter.printHelp(cmdLineSyntax, header, options, footer);
            System.exit(0);
        }
        
        //help
        if(cmd.hasOption("h")) {
            formatter.printHelp(cmdLineSyntax, header, options, footer);
            System.out.println();
            System.exit(0);
        }
        
        if(!cmd.hasOption("m")) {
            System.err.println("Mode required.");
            formatter.printHelp(cmdLineSyntax, header, options, footer);
            System.exit(0);
        }
        
        try {
            settings.mode = Mode.valueOf(cmd.getOptionValue("m"));
        } catch(IllegalArgumentException e) {
            System.err.println("Mode required.");
            formatter.printHelp(cmdLineSyntax, header, options, footer);
            System.exit(0);
        }
        
        settings.userCsvFile = new File(DEFAULT_USERS);
        if(cmd.hasOption("u")) {
            settings.userCsvFile = new File(cmd.getOptionValue("u"));
        }
        
        settings.ontologyFile = new File(DEFAULT_ONTOLOGY);
        if(cmd.hasOption("O")) {
            settings.ontologyFile = new File(cmd.getOptionValue("O"));
        }
        
        if(cmd.hasOption("i")) {
            settings.input = new File(cmd.getOptionValue("i"));
        }
        if(cmd.hasOption("o")) {
            settings.outputFolder = new File(cmd.getOptionValue("o"));
        }
        
        settings.charset = DEFAULT_CHARSET;
        if(cmd.hasOption("c")) {
            settings.charset = cmd.getOptionValue("c");
        }
        settings.fileSeparator = DEFAULT_SEPARATOR;
        if(cmd.hasOption("fs")) {
            settings.fileSeparator = cmd.getOptionValue("fs");
        }
        settings.filePathList = cmd.hasOption("fpl");
        
        settings.port = DEFAULT_PORT;
        if(cmd.hasOption("p")) {
            try {
                settings.port = Integer.parseInt(cmd.getOptionValue("p"));
            } catch(NumberFormatException e) {
                System.err.println("Port number could not be parsed.");
                formatter.printHelp(cmdLineSyntax, header, options, footer);
                System.exit(0);
            }
        }
        
        settings.limit = DEFAULT_LIMIT;
        if(cmd.hasOption("l")) {
            try {
                settings.limit = Integer.parseInt(cmd.getOptionValue("l"));
            } catch(NumberFormatException e) {
                System.err.println("Limit number could not be parsed.");
                formatter.printHelp(cmdLineSyntax, header, options, footer);
                System.exit(0);
            }
        }
        
        //settings.dontWriteData = cmd.hasOption("W");
        settings.runServer = cmd.hasOption("s");
        settings.openBrowser = cmd.hasOption("b");
        
        settings.loadEmbedding = !cmd.hasOption("E");
        settings.loadLanguageResource = !cmd.hasOption("G");
        settings.loadDefaultOntology = !cmd.hasOption("D");
        
        settings.saveStatusThreshold = DEFAULT_SAVE_STATUS_THRESHOLD;
        settings.excelCharacterSumThreshold = DEFAULT_EXCEL_CHARACTER_SUM_THRESHOLD;
        settings.excelContentCountThreshold = DEFAULT_EXCEL_CONTENT_COUNT_THRESHOLD;
        
        settings.nonTaxonomicTimeout = 10000;
        if(cmd.hasOption("ntt")) {
            try {
                settings.nonTaxonomicTimeout = Integer.parseInt(cmd.getOptionValue("ntt"));
            } catch(NumberFormatException e) {
                System.err.println("Non-taxonomic timeout could not be parsed.");
                formatter.printHelp(cmdLineSyntax, header, options, footer);
                System.exit(0);
            }
        }
        settings.nonTaxonomicDepthThreshold = 5;
        if(cmd.hasOption("ntdt")) {
            try {
                settings.nonTaxonomicDepthThreshold = Integer.parseInt(cmd.getOptionValue("ntdt"));
            } catch(NumberFormatException e) {
                System.err.println("Non-taxonomic depth threshold could not be parsed.");
                formatter.printHelp(cmdLineSyntax, header, options, footer);
                System.exit(0);
            }
        }
        
        if(cmd.hasOption("ew")) {
            settings.excelWhitelist = cmd.getOptionValue("ew");
        }
        
        settings.language = Language.semweb;
        if(cmd.hasOption("lang")) {
            try {
                settings.language = Language.valueOf(cmd.getOptionValue("lang"));
            } catch(Exception e) {
                //ignore
            }
        }
        
        //auto mode
        if(settings.mode == Mode.Demo) {
            settings.runServer = true;
            settings.outputFolder = new File("kecs-demo");
        }
        
        return settings;
    }

    public File getUserCsvFile() {
        return userCsvFile;
    }

    public Mode getMode() {
        return mode;
    }

    public File getInput() {
        return input;
    }

    public File getOutputFolder() {
        return outputFolder;
    }

    public String getCharset() {
        return charset;
    }

    public boolean isFilePathList() {
        return filePathList;
    }

    public String getFileSeparator() {
        return fileSeparator;
    }

    public boolean isRunServer() {
        return runServer;
    }

    public int getPort() {
        return port;
    }

    public boolean isOpenBrowser() {
        return openBrowser;
    }

    public int getLimit() {
        return limit;
    }

    public File getOntologyFile() {
        return ontologyFile;
    }
    
    public boolean isLoadEmbedding() {
        return loadEmbedding;
    }

    public boolean isLoadLanguageResource() {
        return loadLanguageResource;
    }

    public int getSaveStatusThreshold() {
        return saveStatusThreshold;
    }

    public int getExcelCharacterSumThreshold() {
        return excelCharacterSumThreshold;
    }

    public int getExcelContentCountThreshold() {
        return excelContentCountThreshold;
    }

    public String getExcelWhitelist() {
        return excelWhitelist;
    }

    public boolean isLoadDefaultOntology() {
        return loadDefaultOntology;
    }

    public int getNonTaxonomicTimeout() {
        return nonTaxonomicTimeout;
    }

    public int getNonTaxonomicDepthThreshold() {
        return nonTaxonomicDepthThreshold;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }
    
    public enum Language {
        semweb,
        en,
        de
    }
}
