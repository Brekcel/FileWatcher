package com.senior_design.filewatcher;

import java.io.IOException;
import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.*;

public class FileWatcher {
    private WatchService watchService;
    private WatchKey watchKey;
    private Path watchPath;

    public FileWatcher(Arguments args) throws IOException {
        this.watchPath = Paths.get(args.getWatchPath());
        this.watchService = FileSystems.getDefault().newWatchService();
        this.watchKey = Paths.get(args.getWatchPath()).register(this.watchService, ENTRY_CREATE, ENTRY_MODIFY);
    }

    Thread run() {
        Thread t = new Thread(() -> {
            System.out.println("Running from other thread");
            while (true) {
                WatchKey key;
                try {
                    key = this.watchService.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) {
                        continue;
                    }
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path fileName = ev.context();
                    // TMP Files
                    if (fileName.toString().endsWith("~")) {
                        continue;
                    }
                    Path actualPath = Paths.get(this.watchPath.toString(), fileName.toString());
                    try {
                        System.out.println("Read file:" + Files.readString(actualPath));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                key.reset();
            }
        });
        t.start();
        return t;
    }
}
