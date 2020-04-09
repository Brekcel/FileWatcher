package com.senior_design.filewatcher;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class PDFParser implements AutoCloseable {

    static class SolrData {
        String name;
        String location;
        String current_position;
        String summary;
        String[] experience;
        String[] education;
        String references;
        String notes;
    }

    static SolrClient SOLR_CLIENT;
    final static Object CLIENT_MUTEX = new Object();

    private PDDocument[] docs;

    public PDFParser(Arguments args, PDDocument[] docs) {
        synchronized (CLIENT_MUTEX) {
            if (SOLR_CLIENT == null) {
                SOLR_CLIENT = new HttpSolrClient.Builder(args.getSolrUrl()).build();
            }
        }
        this.docs = docs;
    }

    public void run() {
        List<SolrInputDocument> docs = Arrays.stream(this.docs).parallel().map(pdfDoc -> {
            try {
                SolrInputDocument doc = new SolrInputDocument();
                PDFTextStripper textStripper = new PDFTextStripper();

                textStripper.setStartPage(0);
                textStripper.setEndPage(pdfDoc.getNumberOfPages());
                SolrData data = fetchData(textStripper.getText(pdfDoc));

                if (data == null) {
                    return null;
                }
                doc.addField("name", data.name);

                doc.addField("location", data.location);

                doc.addField("current_position", data.current_position);
                if (data.summary != null)
                    doc.addField("summary", data.summary);
                if (data.experience != null)
                    doc.addField("experience", data.experience);
                if (data.education != null)
                    doc.addField("education", data.education);
                if (data.references != null)
                    doc.addField("references", data.references);
                if (data.notes != null)
                    doc.addField("notes", data.notes);
                return doc;

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());

        try {
            SOLR_CLIENT.add(docs);
            SOLR_CLIENT.commit();
        } catch (SolrServerException | IOException e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<String> findPerson(String content) {
        //Finds the person's name, location, and current position
        ArrayList<String> output = new ArrayList<String>();

        Scanner scan = new Scanner(content);
        scan.nextLine();

        String firstLine = scan.nextLine();
        if (firstLine.compareTo("") == 0) {
            scan.close();
            return output;
        }
        output.add(firstLine);

        String secondLine = scan.nextLine();
        if (secondLine.compareTo("") == 0) {
            scan.close();
            return output;
        }
        output.add(secondLine);

        String thirdLine = "";
        while (scan.hasNextLine()) {
            String nextLine = scan.nextLine();
            if (nextLine.compareTo("") == 0) {
                break;
            } else
                thirdLine += nextLine + " ";
        }
        if (thirdLine.compareTo("") != 0)
            output.add(thirdLine);


        scan.close();

        return output;
    }

    public String findSummary(String content) {
        //Finds the person's summary
        String output = "";
        Scanner scan = new Scanner(content);
        boolean found = false; //This checks the scanner is at the summary area
        while (scan.hasNextLine()) {
            String nextLine = scan.nextLine();
            if (found && nextLine.compareTo("") != 0) {
                if (nextLine.compareTo("Experience") == 0 ||
                        nextLine.compareTo("Education") == 0)
                    break;
                output += nextLine + " ";
            } else if (nextLine.compareTo("Summary") != 0)
                continue;
            else
                found = true;

        }
        scan.close();


        return output;

    }

    public ArrayList<String> findExperience(String content) {
        //Finds the person's experience
        ArrayList<String> output = new ArrayList<String>();
        Scanner scan = new Scanner(content);
        boolean found = false; //This checks the scanner is at the Experience area
        String paragraph = "";
        while (scan.hasNextLine()) {
            String nextLine = scan.nextLine();
            if (found) {
                if (nextLine.compareTo("") == 0) {
                    if (paragraph.compareTo("") != 0) {
                        paragraph = paragraph.replaceAll("\n", " ");
                        output.add(paragraph);
                        paragraph = "";
                    }
                    continue;
                } else {
                    if (nextLine.compareTo("Summary") == 0 ||
                            nextLine.compareTo("Education") == 0)
                        break;
                    paragraph += nextLine + " ";
                }
            } else if (nextLine.compareTo("Experience") != 0)
                continue;
            else
                found = true;

        }
        scan.close();


        return output;
    }

    public ArrayList<String> findEducation(String content) {
        //Finds the person's education
        ArrayList<String> output = new ArrayList<String>();
        Scanner scan = new Scanner(content);
        boolean found = false; //This checks the scanner is at the Education area
        while (scan.hasNextLine()) {
            String nextLine = scan.nextLine();
            if (found && nextLine.compareTo("") != 0) {
                if (nextLine.compareTo("Experience") == 0 ||
                        nextLine.compareTo("Summary") == 0)
                    break;
                output.add(nextLine);
            } else if (nextLine.compareTo("Education") != 0)
                continue;
            else
                found = true;

        }
        scan.close();


        return output;
    }

    public SolrData fetchData(String wordsInHandler) {
        //Converts the contents of the pdf into a json object
        SolrData data = new SolrData();

        //Gets the person's name, location, and title
        ArrayList<String> contents;
        contents = findPerson(wordsInHandler);

        wordsInHandler = wordsInHandler.replaceAll("[^\\x00-\\x7F]", " ");//removes unknown characters

        if (contents.size() == 3) {
            data.name = contents.get(0);
            data.location = contents.get(1);
            data.current_position = contents.get(2);
        } else
            return null;

        //Checks if there are reference
        String reference = "";
        if (wordsInHandler.contains("people have recommended")) {
            reference = wordsInHandler.substring(wordsInHandler.indexOf("people have recommended"));
            reference = reference.substring(reference.indexOf("\""));
            wordsInHandler = wordsInHandler.substring(0, wordsInHandler.lastIndexOf(contents.get(0)));
        } else {
            wordsInHandler = wordsInHandler.substring(0, wordsInHandler.lastIndexOf(contents.get(0)));
        }

        //Gets the person's summary
        if (wordsInHandler.contains("Summary")) {
            String summary = findSummary(wordsInHandler);
            summary = summary.replaceAll("\n", " ");
            data.summary = summary;
        }
        //Gets the person's Experience
        if (wordsInHandler.contains("Experience")) {
            contents = findExperience(wordsInHandler);
            String[] arr = new String[0];
            data.experience = contents.toArray(arr);
        }
        //Gets the person's Education
        if (wordsInHandler.contains("Education")) {
            contents = findEducation(wordsInHandler);
            String[] arr = new String[0];
            data.education = contents.toArray(arr);
        }

        if (reference.contains("Profile Notes and Activity")) {
            data.references = reference.substring(0, reference.lastIndexOf("Profile Notes and Activity")).replaceAll("\n", " ");
            reference = reference.substring(reference.indexOf(")") + 1);
            reference = reference.replaceAll("\n", " ");
            data.notes = reference;
        } else
            data.references = reference;


        return data;
    }

    @Override
    public void close() throws Exception {
        for (PDDocument doc : this.docs) {
            doc.close();
        }
    }

}
