package com.senior_design.filewatcher;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static void main(String[] args) throws Exception {
        AtomicBoolean isRunning = new AtomicBoolean(true);
        FileWatcher fw = new FileWatcher(isRunning);
        Thread t = new Thread(fw::run);
        t.start();
        try(Scanner s = new Scanner(System.in)) {
            while (isRunning.get()) {
                System.out.println("To stop enter q: ");
                System.out.flush();
                String in = s.nextLine();
                if(in.equalsIgnoreCase("q")) {
                    isRunning.set(false);
                }
            }
        }
        t.join();
    }
}
