package com.senior_design.filewatcher;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static void main(String[] args) throws Exception {
        FileWatcher fw = new FileWatcher();
        fw.run();
    }
}
