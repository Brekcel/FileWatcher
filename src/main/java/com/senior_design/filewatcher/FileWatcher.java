package com.senior_design.filewatcher;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class FileWatcher {
    private WatchService watchService;
    private WatchKey watchKey;
    private Path watchPath;
    private Arguments args;
    private AtomicBoolean isRunning;

    public FileWatcher(Arguments args, AtomicBoolean isRunning) throws IOException {
        this.isRunning = isRunning;
        this.watchPath = Paths.get(args.getWatchPath());
        this.watchService = FileSystems.getDefault().newWatchService();
        this.watchKey = this.watchPath.register(this.watchService, ENTRY_CREATE);
        this.args = args;
    }

    public void run() {
        while (isRunning.get()) {
            for (WatchEvent<?> event : watchKey.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == OVERFLOW) {
                    continue;
                }

                Object context = event.context();
                if(!(context instanceof Path)) {
                    break;
                }
                Path fileName = (Path) event.context();
                // TMP Files
                if (fileName.toString().endsWith("~")) {
                    continue;
                }
                Path actualPath = Paths.get(this.watchPath.toString(), fileName.toString());
                try {
                    File f = actualPath.toFile();
                    try (PDFParser p = new PDFParser(args, f)) {
                        PDDocument[] docs = p.splitDoc();
                        for (PDDocument doc : docs) {
                            //TODO: Send them off to the solr bits
                            doc.close();
                        }
                    }
                    f.delete();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            watchKey.reset();
        }
    }
}
