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

public class PDFParser {

    static class SolrData implements AutoCloseable {
        PDDocument doc;
        Scanner scan;
        String text;
        CurState curState;

        boolean parsed = false;

        String name;
        String location;
        String current_position;
        String summary;
        String[] experience;
        String[] education;
        String references;
        String notes;

        enum CurState {
            Name,
            Summary,
            Experience,
            Education,
            References,
        }

        boolean maybeTransition(String line) {
            switch (line.toLowerCase()) {
                case "summary": {
                    this.curState = CurState.Summary;
                    return true;
                }
                case "experience": {
                    this.curState = CurState.Experience;
                    return true;
                }
                case "education": {
                    this.curState = CurState.Education;
                    return true;
                }
                case "references": {
                    this.curState = CurState.References;
                    return true;
                }
                default: {
                    return false;
                }
            }
        }

        static class BadPDFException extends Exception {
        }

        static boolean stringEmpty(String s) {
            return s == null || s.isEmpty() || s.trim().isEmpty();
        }

        public SolrData(PDDocument doc) throws IOException {
            this.doc = doc;
            PDFTextStripper textStripper = new PDFTextStripper();

            textStripper.setStartPage(0);
            textStripper.setEndPage(doc.getNumberOfPages());

            //removes unknown characters
            String wordsInHandler = textStripper.getText(doc).replaceAll("[^\\x00-\\x7F]", " ");
            this.text = wordsInHandler;
            this.scan = new Scanner(wordsInHandler);
        }

        public void parse() throws BadPDFException {
            if (parsed) {
                return;
            }
            this.parsed = true;
            //Gets the person's name, location, and title
            findPerson();
            //Checks if there are reference
            String reference = "";
            if (this.text.contains("people have recommended")) {
                reference = this.text.substring(this.text.indexOf("people have recommended"));
                reference = reference.substring(reference.indexOf("\""));
            }

            //Gets the person's summary
            if (curState == CurState.Summary && this.text.contains("Summary")) {
                findSummary();

                summary = summary.replaceAll("(\r\n|\n)", " ");
            }

            //Gets the person's Experience
            if (curState == CurState.Experience && this.text.contains("Experience")) {
                findExperience();
            }
            //Gets the person's Education
            if (curState == CurState.Education && this.text.contains("Education")) {
                findEducation();
            }

            if (curState == CurState.References && reference.contains("Profile Notes and Activity")) {
                this.references = reference.substring(0, reference.lastIndexOf("Profile Notes and Activity")).replaceAll("\n", " ");
                reference = reference.substring(reference.indexOf(")") + 1);
                reference = reference.replaceAll("(\r\n|\n)", " ");
                this.notes = reference;
            } else {
                this.references = reference;
            }
        }

        public SolrInputDocument toSolrDoc() throws BadPDFException {
            this.parse();
            SolrInputDocument doc = new SolrInputDocument();

            doc.addField("name", this.name);

            doc.addField("location", this.location);

            doc.addField("current_position", this.current_position);

            if (this.summary != null)
                doc.addField("summary", this.summary);

            if (this.experience != null)
                doc.addField("experience", this.experience);

            if (this.education != null)
                doc.addField("education", this.education);

            if (this.references != null)
                doc.addField("references", this.references);

            if (this.notes != null)
                doc.addField("notes", this.notes);

            return doc;
        }

        public void findPerson() throws BadPDFException {
            //Finds the person's name, location, and current position
            String firstLine = scan.nextLine();
            if (stringEmpty(firstLine) || maybeTransition(firstLine)) {
                throw new BadPDFException();
            }
            this.name = firstLine;

            String secondLine = scan.nextLine();
            if (stringEmpty(secondLine) || maybeTransition(secondLine)) {
                throw new BadPDFException();
            }
            this.location = secondLine;

            StringBuilder thirdLine = new StringBuilder();
            while (scan.hasNextLine()) {
                String nextLine = scan.nextLine();
                if (stringEmpty(nextLine) || maybeTransition(nextLine)) {
                    break;
                } else
                    thirdLine.append(nextLine).append(" ");
            }
            String thirdLineOutput = thirdLine.toString();
            if (stringEmpty(thirdLineOutput)) {
                throw new BadPDFException();
            }
            this.current_position = thirdLineOutput;
            this.curState = CurState.Summary;
        }

        public void findSummary() {
            //Finds the person's summary
            StringBuilder output = new StringBuilder();
            while (scan.hasNextLine()) {
                String nextLine = scan.nextLine();
                if (maybeTransition(nextLine) && curState != CurState.Summary) {
                    break;
                }
                output.append(nextLine).append(" ");
            }
            this.summary = output.toString();
        }

        public void findExperience() {
            //Finds the person's experience
            ArrayList<String> output = new ArrayList<String>();
            StringBuilder paragraph = new StringBuilder();
            while (scan.hasNextLine()) {
                String nextLine = scan.nextLine();
                if (nextLine.isEmpty()) {
                    if (paragraph.length() != 0) {
                        output.add(paragraph.toString().replaceAll("\n", " "));
                        paragraph = new StringBuilder();
                    }
                } else {
                    if (maybeTransition(nextLine)) {
                        break;
                    }
                    paragraph.append(nextLine).append(" ");
                }
            }


            String[] arr = new String[0];
            this.experience = output.toArray(arr);
        }

        public void findEducation() {
            //Finds the person's education
            ArrayList<String> output = new ArrayList<String>();
            while (scan.hasNextLine()) {
                String nextLine = scan.nextLine();
                if (!nextLine.isEmpty()) {
                    if (maybeTransition(nextLine)) {
                        break;
                    }
                    output.add(nextLine);
                }
            }
            String[] arr = new String[0];
            this.education = output.toArray(arr);
        }


        @Override
        public void close() throws Exception {
            if (this.doc != null) {
                this.doc.close();
            }
            if (this.scan != null) {
                this.scan.close();
            }
        }

    }

    static SolrClient SOLR_CLIENT;
    final static Object CLIENT_MUTEX = new Object();

    private final PDDocument[] docs;

    public PDFParser(PDDocument[] docs) {
        synchronized (CLIENT_MUTEX) {
            if (SOLR_CLIENT == null) {
                SOLR_CLIENT = new HttpSolrClient.Builder(Arguments.the().getSolrUrl()).build();
            }
        }
        this.docs = docs;
    }

    public void run() {
        List<SolrInputDocument> docs = Arrays.stream(this.docs).parallel().map(pdfDoc -> {
            try (SolrData data = new SolrData(pdfDoc)) {
                return data.toSolrDoc();
            } catch (Exception e) {
                if (!(e instanceof SolrData.BadPDFException)) {
                    e.printStackTrace();
                }
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
        if (docs.size() <= 0) {
            System.out.println("No good resumes from that batch");
            return;
        }
        try {
            System.out.println("Adding " + docs.size() + " resumes to Solr");
            SOLR_CLIENT.add(docs);
            SOLR_CLIENT.commit();
        } catch (SolrServerException | IOException e) {
            e.printStackTrace();
        }
    }


}
