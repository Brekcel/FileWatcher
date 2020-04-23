package com.senior_design.filewatcher;

import nu.pattern.OpenCV;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PDFSplitter implements AutoCloseable {
    private static class EndPagePair {
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
    final Object renderMutex = new Object();

    public PDFSplitter(File file) throws IOException {
        doc = PDDocument.load(file);
        pdr = new PDFRenderer(doc);
        synchronized (MUTEX) {
            if (linkedInTemplate == null) {
                OpenCV.loadLocally();
                linkedInTemplate = Imgcodecs.imread(Arguments.the().getWatermark(), Imgcodecs.IMREAD_GRAYSCALE);
            }
        }
    }

    public PDDocument[] splitDoc() throws IOException {
        ArrayList<EndPagePair> splitPages = IntStream.range(0, doc.getNumberOfPages())
                .parallel()
                .filter(i -> {
                    BufferedImage bi = null;
                    try {
                        synchronized (renderMutex) {
                            bi = pdr.renderImage(i, 1, ImageType.GRAY);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                    Mat page = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC1);
                    page.put(0, 0, ((DataBufferByte) bi.getData().getDataBuffer()).getData());

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
            start = endPage.lastPageOfRecommends + 1;
            docs[i] = splitDocs.get(0);
            for (int j = 1; j < splitDocs.size(); j += 1) {
                splitDocs.get(j).close();
            }
        }

        return docs;

    }

    @Override
    public void close() throws Exception {
        if (doc != null) {
            doc.close();
            doc = null;
        }
    }
}
