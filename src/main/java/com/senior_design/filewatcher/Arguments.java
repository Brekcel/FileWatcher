package com.senior_design.filewatcher;

import lombok.Data;
import lombok.Getter;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

@Data
public class Arguments {

    private String watchPath;

    public Arguments(String[] args) throws ArgumentParserException {
        ArgumentParser ap = ArgumentParsers.newFor("FileWatcher").build().defaultHelp(true).description("PDF FileWatching program");
        ap.addArgument("-p", "--path").help("The path to the directory to watch for PDF's to be added to").required(true).setDefault("./watch");
        Namespace ns = ap.parseArgs(args);
        watchPath = ns.getString("path");
    }

}
