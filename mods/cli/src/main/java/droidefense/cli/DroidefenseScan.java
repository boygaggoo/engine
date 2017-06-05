package droidefense.cli;

import droidefense.analysis.base.AbstractAndroidAnalysis;
import droidefense.analysis.base.AnalysisFactory;
import droidefense.exception.ConfigFileNotFoundException;
import droidefense.exception.InvalidScanParametersException;
import droidefense.exception.UnknownAnalyzerException;
import droidefense.sdk.helpers.APKUnpacker;
import droidefense.sdk.helpers.DroidDefenseParams;
import droidefense.sdk.log4j.Log;
import droidefense.sdk.log4j.LoggerType;
import droidefense.sdk.model.base.DroidefenseProject;
import droidefense.sdk.model.io.LocalApkFile;
import droidefense.util.DroidefenseIntel;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;

import java.io.File;

public class DroidefenseScan {

    private static boolean init;
    private DroidefenseOptions options;

    public static void main(String[] args) throws InvalidScanParametersException {
        new DroidefenseScan(args);
    }

    public DroidefenseScan(String[] args) throws InvalidScanParametersException {
        options = new DroidefenseOptions();
        options.showAsciiBanner();
        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);
            executeUserCmd(cmd);
        } catch (ParseException e) {
            showParsingError(e);
        }
    }

    private void executeUserCmd(CommandLine cmd) {
        if (cmd.hasOption("-help")) {
            options.showHelp();
        }
        else if (cmd.hasOption("-version")) {
            options.showVersion();
        }
        else{
            executeCustom(cmd);
        }
    }

    private void executeCustom(CommandLine cmd) {
        //get user selected unpacker. default apktool
        APKUnpacker unpacker = APKUnpacker.ZIP;
        if(cmd.hasOption("unpacker")){
            String unpackerStr = cmd.getOptionValue("unpacker");
            if (unpackerStr != null) {
                if (unpackerStr.equalsIgnoreCase(APKUnpacker.APKTOOL.name())) {
                    unpacker = APKUnpacker.APKTOOL;
                } else if (unpackerStr.equalsIgnoreCase(APKUnpacker.ZIP.name())) {
                    unpacker = APKUnpacker.ZIP;
                }
            }
        }

        DroidefenseProject project = new DroidefenseProject();

        if(cmd.hasOption("format")){
            project.setSettingsReportType(cmd.getOptionValue("format"));
        }
        project.setSettingAutoOpen(cmd.hasOption("show"));
        Log.beVerbose(cmd.hasOption("verbose"));

        //read user selected .apk
        if (cmd.hasOption("input")) {
            //initialize environment first
            init = loadVariables();
            if (!init) {
                Log.write(LoggerType.FATAL, "Droidefense initialization error");
                return;
            }

            boolean profilingEnabled = cmd.hasOption("profile");
            File inputFile = new File(cmd.getOptionValue("input"));
            //profiler wait time | start
            if (profilingEnabled) {
                profilingAlert("activate");
            }
            initScan(project, inputFile, unpacker);
            //profiler wait time | stop
            if (profilingEnabled) {
                profilingAlert("deactivate");
            }
        }
    }

    private void showParsingError(ParseException e) {
        Log.write(LoggerType.ERROR, e.getLocalizedMessage()+"\n");
        options.showHelp();
    }

    private static boolean loadVariables() {
        //init data structs
        try {
            DroidDefenseParams.init();
            Log.write(LoggerType.TRACE, "Loading Droidefense data structs...");
            //create singleton instance of AtomIntelligence
            DroidefenseIntel.getInstance();
            Log.write(LoggerType.TRACE, "Data loaded!!");
        } catch (ConfigFileNotFoundException e) {
            Log.write(LoggerType.FATAL, e.getLocalizedMessage());
            return false;
        }
        return true;
    }

    private void profilingAlert(String status) {
        System.out.println("Profiling mode enabled. Waiting user to " + status + " profler. Press enter key when ready.");
        System.out.println("Press enter key to continue...");
        options.readKeyBoard();
    }

    public void stop(DroidefenseProject project) {
        //save report .json to file
        Log.write(LoggerType.TRACE, "Saving report file...");
        project.finish();
        Log.write(LoggerType.TRACE, "Droidefense scan finished");
    }

    private void initScan(DroidefenseProject project, File f, APKUnpacker unpacker) {

        if(f.exists() && f.canRead()) {
            Log.write(LoggerType.TRACE, "Building project");

            //set sample
            LocalApkFile sample = new LocalApkFile(f, project, unpacker);
            project.setSample(sample);

            Log.write(LoggerType.TRACE, "Running Droidefense [ANALYSIS]");

            Log.write(LoggerType.TRACE, "Project ID:\t" + project.getProjectId());

            AbstractAndroidAnalysis analyzer;
            try {
                analyzer = AnalysisFactory.getAnalyzer(AnalysisFactory.GENERAL);
                //Start analysis
                project.analyze(analyzer);
            } catch (UnknownAnalyzerException e) {
                Log.write(LoggerType.FATAL, e.getLocalizedMessage());
                Log.write(LoggerType.ERROR, "Analyzer not found" + e.getLocalizedMessage());
            }

            //stop scan
            this.stop(project);
        }
        else{
            Log.write(LoggerType.FATAL, "Could not read selected file");
            Log.write(LoggerType.FATAL, "Source file was: "+f.getAbsolutePath());
        }
    }
}