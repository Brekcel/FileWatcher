package com.senior_design.filewatcher;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Test");
        Arguments parsedArgs = new Arguments(args);
        FileWatcher fw = new FileWatcher(parsedArgs);
        fw.run().join();
    }

}
