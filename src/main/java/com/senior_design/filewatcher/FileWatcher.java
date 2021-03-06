package com.senior_design.filewatcher;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class FileWatcher {

    private FileWatcher() {
    }

    public static void run() throws IOException, InterruptedException {
        Path watchPath = Paths.get(Arguments.the().getWatchPath());
        Consumer<String> makeIfNotExists = path -> {
            File f = new File(path);
            if (!f.exists()) {
                f.mkdirs();
            } else {
                File[] files = f.listFiles();
                if (files == null) {
                    return;
                }
                for (File innerFile : files) {
                    innerFile.delete();
                }
            }
        };
        makeIfNotExists.accept(Arguments.the().getWatchPath());
        makeIfNotExists.accept(Arguments.the().getMoveToPath());
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            watchPath.register(watchService, ENTRY_CREATE);
            while (true) {
                String normalizedWatchPath = Arguments.the().getWatchPath().replace("/", File.separator).replace("\\", File.separator);
                String normalizedMoveToPAth = Arguments.the().getMoveToPath().replace("/", File.separator).replace("\\", File.separator);
                WatchKey watchKey = watchService.take();
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
                    String fileString = fileName.toString();
                    // TMP Files
                    if (!fileString.endsWith(".pdf")) {
                        continue;
                    }
                    Path actualPath = Paths.get(watchPath.toString(), fileString);
                    try {
                        File f = actualPath.toFile();
                        if (!f.exists()) {
                            continue;
                        }
                        try (PDFSplitter splitter = new PDFSplitter(f)) {
                            PDDocument[] docs = splitter.splitDoc();
                            PDFParser parser = new PDFParser(docs);
                            parser.run();
                        }
                        String path = f.getPath();
                        String newPath = path.replace(normalizedWatchPath, normalizedMoveToPAth);
                        File newFile = new File(newPath);
                        if (newFile.exists()) {
                            newFile.delete();
                        }
                        f.renameTo(newFile);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (!watchKey.reset()) {
                    break;
                }
            }
        }
    }
}
