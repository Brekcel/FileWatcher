package com.senior_design.filewatcher;

import lombok.Data;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

@Data
public class Arguments {

    private String watchPath;
    private String watermark;
    private String solrUrl;

    public Arguments(String[] args) throws ArgumentParserException {
        ArgumentParser ap = ArgumentParsers.newFor("FileWatcher").build().defaultHelp(true).description("PDF FileWatching program");

        ap.addArgument("-p", "--path")
                .help("The path to the directory to watch for PDF's to be added to")
                .required(true)
                .setDefault("./watch");

        ap.addArgument("-w", "--watermark")
                .help("The path to the LinkedIn Watermark to search for to split pdfs")
                .required(true)
                .setDefault("./linkedin.watermark.png");

        ap.addArgument("-s", "--solr")
                .help("The url of the Solr server")
                .required(true);

        Namespace ns = ap.parseArgs(args);

        watchPath = ns.getString("path");
        watermark = ns.getString("watermark");
        solrUrl = ns.getString("solr");
    }

}
