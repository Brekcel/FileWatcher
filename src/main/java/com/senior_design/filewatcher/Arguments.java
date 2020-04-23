package com.senior_design.filewatcher;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

@Data
public class Arguments {
    private static final Object[] MUTEX = new Object[0];
    private static Arguments args;

    @SerializedName("WatchPath")
    private String watchPath;

    @SerializedName("WatermarkPath")
    private String watermark;

    @SerializedName("SolrURL")
    private String solrUrl;

    @SerializedName("MoveToPath")
    private String moveToPath;

    public static Arguments the() {
        if (args != null) {
            return args;
        }
        synchronized (MUTEX) {
            Gson gs = new Gson();
            try {
                try (BufferedReader br = new BufferedReader(new FileReader(new File("./config.json")))) {
                    Arguments.args = gs.fromJson(br, Arguments.class);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        return the();
    }

}
