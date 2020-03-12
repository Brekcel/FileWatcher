package com.senior_design.filewatcher;

import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Test");
        Arguments parsedArgs = new Arguments(args);
        try (PDFParser p = new PDFParser(new File(parsedArgs.getWatchPath()))) {
            p.splitPages();
//            String[] text = p.text();
//            for (int i = 0; i < text.length; i += 1) {
//                System.out.println("Page: " + i);
//                System.out.println(text[i]);
//            }
        }
//        FileWatcher fw = new FileWatcher(parsedArgs);
//        fw.run().join();
    }

}
