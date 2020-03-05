package com.senior_design.filewatcher;

import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class Main {
    public static void main(String[] args) throws ArgumentParserException {
        Arguments parsedArgs = new Arguments(args);
        System.out.println(parsedArgs);
    }

}
