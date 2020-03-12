package com.senior_design.filewatcher;

import nu.pattern.OpenCV;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.IntStream;

public class PDFParser implements AutoCloseable {
    static Mat linkedInTemplate;

    static {
        OpenCV.loadLocally();
        linkedInTemplate = Imgcodecs.imread("./linkedin.watermark.png", Imgcodecs.IMREAD_GRAYSCALE);
    }

    PDDocument doc;
    PDFRenderer pdr;


    public PDFParser(File file) throws IOException {
        doc = PDDocument.load(file);
        pdr = new PDFRenderer(doc);
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

    public int[] splitPages() throws IOException {
        ArrayList<Integer> pages = new ArrayList<>();

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
        IntStream.range(0, doc.getNumberOfPages()).parallel().forEach(i -> {
            File tmpFile = indexToFile(i);
            Mat page = Imgcodecs.imread(tmpFile.getPath(), Imgcodecs.IMREAD_GRAYSCALE);

            int result_cols = page.cols() - linkedInTemplate.cols() + 1;
            int result_rows = page.rows() - linkedInTemplate.rows() + 1;

            Mat result = new Mat(result_rows, result_cols, CvType.CV_8UC1);
            Imgproc.matchTemplate(page, linkedInTemplate, result, Imgproc.TM_CCOEFF);
            Core.normalize(result, result, 0, 1, Core.NORM_MINMAX, -1, new Mat());

            Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
            Point matchLoc = mmr.maxLoc;

            if ((int) matchLoc.x != 36) {
                return;
            }

            Imgproc.rectangle(page, matchLoc, new Point(matchLoc.x + linkedInTemplate.cols(),
                    matchLoc.y + linkedInTemplate.rows()), new Scalar(0, 255, 0));
            Imgcodecs.imwrite("./out/page" + i + ".png", page);

        });

        int[] pagesArr = new int[pages.size()];
        for (int i = 0; i < pages.size(); i += 1) {
            pagesArr[i] = pages.get(i);
        }

        return pagesArr;
    }

    @Override
    public void close() throws Exception {
        if (doc != null) {
            doc.close();
        }
    }
}
