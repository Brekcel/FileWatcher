package com.senior_design.filewatcher;

import nu.pattern.OpenCV;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PDFParser implements AutoCloseable {
    private class EndPagePair {
        int lastPageOfResume;
        int lastPageOfRecommends;

        EndPagePair(int endPage) {
            this.lastPageOfResume = endPage;
            this.lastPageOfRecommends = endPage;
        }
    }

    private static final Object MUTEX = new Object();
    private static Mat linkedInTemplate;

    PDDocument doc;
    PDFRenderer pdr;

    public PDFParser(Arguments args, File file) throws IOException {
        doc = PDDocument.load(file);
        pdr = new PDFRenderer(doc);
        synchronized (MUTEX) {
            if (linkedInTemplate == null) {
                OpenCV.loadLocally();
                linkedInTemplate = Imgcodecs.imread(args.getWatermark(), Imgcodecs.IMREAD_GRAYSCALE);
            }
        }
    }

    public String[] text() throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        String[] pages = new String[doc.getNumberOfPages()];
        for (int page = 0; page < pages.length; page += 1) {
            stripper.setStartPage(page + 1);
            stripper.setEndPage(page + 1);
            pages[page] = stripper.getText(doc);
        }
        return pages;
    }

    File indexToFile(int i) {
        return new File("./tmp/" + i + ".png");
    }

    public PDDocument[] splitDoc() throws IOException {
        IntStream.range(0, doc.getNumberOfPages()).forEach(i -> {
            BufferedImage bi = null;
            try {
                bi = pdr.renderImage(i);
                File tmpFile = indexToFile(i);
                ImageIO.write(bi, "png", tmpFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        ArrayList<EndPagePair> splitPages = IntStream.range(0, doc.getNumberOfPages())
                .parallel()
                .filter(i -> {
                    File tmpFile = indexToFile(i);
                    Mat page = Imgcodecs.imread(tmpFile.getPath(), Imgcodecs.IMREAD_GRAYSCALE);

                    int result_cols = page.cols() - linkedInTemplate.cols() + 1;
                    int result_rows = page.rows() - linkedInTemplate.rows() + 1;

                    Mat result = new Mat(result_rows, result_cols, CvType.CV_8UC1);
                    Imgproc.matchTemplate(page, linkedInTemplate, result, Imgproc.TM_CCOEFF);
                    Core.normalize(result, result, 0, 1, Core.NORM_MINMAX, -1, new Mat());

                    Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
                    Point matchLoc = mmr.maxLoc;

                    return (int) matchLoc.x == 36;
                }).map(i -> i + 1).sorted().mapToObj(endPage -> {
                    EndPagePair pair = new EndPagePair(endPage);

                    Function<Integer, String> stripPage = (page) -> {
                        String text = null;

                        try {
                            PDFTextStripper textStripper = new PDFTextStripper();
                            textStripper.setStartPage(page);
                            textStripper.setEndPage(page);
                            text = textStripper.getText(doc);
                        } catch (IOException e) {
                            e.printStackTrace();
                            System.exit(-1);
                        }
                        return text;
                    };
                    String assumedEndPageText = stripPage.apply(endPage);
                    Optional<String> maybeRecommendedLine = assumedEndPageText.lines().filter(s -> s.contains(" people have recommended ")).findAny();
                    if (maybeRecommendedLine.isPresent()) {
                        String recommendedLine = maybeRecommendedLine.get();
                        int numCount = Integer.parseInt(recommendedLine.substring(0, recommendedLine.indexOf(' ')));
                        System.out.println("Old End Page: " + endPage);
                        StringBuilder pageText = new StringBuilder(assumedEndPageText);
                        while (true) {
                            int curIdx = 0;
                            {
                                int endPageTmp = endPage;
                                while (pageText.toString().endsWith("\"\r\n")) {
                                    pageText.append(stripPage.apply(endPageTmp + 1));
                                    endPageTmp += 1;
                                }
                            }
                            while (true) {
                                curIdx = pageText.indexOf("\"\r\n\u2014", curIdx);
                                if (curIdx == -1) {
                                    break;
                                }
                                curIdx += 1;
                                numCount -= 1;
                            }
                            if (numCount == 0) {
                                break;
                            }
                            endPage += 1;
                            pageText = new StringBuilder(stripPage.apply(endPage));
                        }
                        System.out.println("New End Page: " + endPage);
                    }
                    pair.lastPageOfRecommends = endPage;
                    return pair;
                }).collect(Collectors
                        .toCollection(ArrayList::new));


        int start = 1;

        PDDocument[] docs = new PDDocument[splitPages.size()];

        for (int i = 0; i < splitPages.size(); i += 1) {
            Splitter pdfSpliiter = new Splitter();
            EndPagePair endPage = splitPages.get(i);
            pdfSpliiter.setStartPage(start);
            pdfSpliiter.setEndPage(endPage.lastPageOfResume);
            pdfSpliiter.setSplitAtPage(endPage.lastPageOfResume - start);
            List<PDDocument> splitDocs = pdfSpliiter.split(doc);

            assert splitDocs.size() == 1;
            start = endPage.lastPageOfRecommends + 1;
            docs[i] = splitDocs.get(0);
        }

        return docs;

    }

    @Override
    public void close() throws Exception {
        if (doc != null) {
            doc.close();
        }
    }
}
