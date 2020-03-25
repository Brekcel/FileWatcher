package com.senior_design.filewatcher;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Test");
        Arguments parsedArgs = new Arguments(args);
        try (PDFParser p = new PDFParser(parsedArgs, new File("./watch/linkedin.big.pdf"))) {
            PDDocument[] docs = p.splitDoc();
            for (int i = 0; i < docs.length; i += 1) {
                docs[i].save("./saved/resume " + i + ".pdf");
            }
        }
//        FileWatcher fw = new FileWatcher(parsedArgs);
//        fw.run().join();
    }

}
