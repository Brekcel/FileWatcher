package com.senior_design.filewatcher;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class FileWatcher {
    private WatchService watchService;
    private final WatchKey watchKey;
    private final Path watchPath;

    public FileWatcher() throws IOException {
        this.watchPath = Paths.get(Arguments.the().getWatchPath());
        this.watchService = FileSystems.getDefault().newWatchService();
        this.watchKey = this.watchPath.register(this.watchService, ENTRY_CREATE);
    }

    public void run() {
        while (true) {
            for (WatchEvent<?> event : watchKey.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == OVERFLOW) {
                    continue;
                }

                Object context = event.context();
                if (!(context instanceof Path)) {
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
                    try (PDFSplitter splitter = new PDFSplitter(f)) {
                        PDDocument[] docs = splitter.splitDoc();
                        PDFParser parser = new PDFParser(docs);
                        parser.run();
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
