package com.pdfuse;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDPageLabelRange;
import org.apache.pdfbox.pdmodel.common.PDPageLabels;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

/** Fabrique de PDF de test : aucun fichier externe requis. */
final class TestPdf {

    private TestPdf() {
    }

    static Path simple(Path dir, String name, String title, int pages) throws IOException {
        return save(dir.resolve(name), build(title, pages, false), false);
    }

    static Path rich(Path dir, String name, String title) throws IOException {
        return save(dir.resolve(name), build(title, 2, true), false);
    }

    static Path encrypted(Path dir, String name, String title) throws IOException {
        return save(dir.resolve(name), build(title, 2, false), true);
    }

    static Path image(Path dir, String name, int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(30, 90, 200));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        Path path = dir.resolve(name);
        if (!ImageIO.write(image, format, path.toFile())) {
            throw new IOException("unsupported image format: " + format);
        }
        return path;
    }

    private static Path save(Path path, PDDocument document, boolean encrypt) throws IOException {
        try (document) {
            if (encrypt) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy(
                        "owner-secret", "", new AccessPermission());
                policy.setEncryptionKeyLength(256);
                policy.setPreferAES(true);
                document.protect(policy);
            }
            document.save(path.toFile());
        }
        return path;
    }

    private static PDDocument build(String title, int pages, boolean rich) throws IOException {
        PDDocument document = new PDDocument();
        for (int i = 0; i < pages; i++) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(title + " - page " + (i + 1));
                content.endText();
            }
        }
        document.getDocumentInformation().setTitle(title);
        document.getDocumentInformation().setAuthor("PDFuse tests");
        if (rich) {
            addOutline(document, title);
            addLabels(document);
            addForm(document);
            addAttachment(document);
            addInternalLink(document);
        }
        return document;
    }

    private static void addOutline(PDDocument document, String title) {
        PDDocumentOutline outline = new PDDocumentOutline();
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title + " chapter");
        PDPageFitDestination destination = new PDPageFitDestination();
        destination.setPage(document.getPage(0));
        item.setDestination(destination);
        outline.addLast(item);
        document.getDocumentCatalog().setDocumentOutline(outline);
    }

    private static void addLabels(PDDocument document) {
        PDPageLabels labels = new PDPageLabels(document);
        PDPageLabelRange range = new PDPageLabelRange();
        range.setStyle(PDPageLabelRange.STYLE_ROMAN_LOWER);
        range.setStart(1);
        labels.setLabelItem(0, range);
        document.getDocumentCatalog().setPageLabels(labels);
    }

    private static void addForm(PDDocument document) throws IOException {
        PDAcroForm form = new PDAcroForm(document);
        document.getDocumentCatalog().setAcroForm(form);

        PDTextField field = new PDTextField(form);
        field.setPartialName("customer_name");
        field.setDefaultAppearance("/Helv 12 Tf 0 g");
        form.getFields().add(field);

        PDAnnotationWidget widget = field.getWidgets().get(0);
        widget.setRectangle(new PDRectangle(72, 640, 200, 20));
        widget.setPage(document.getPage(0));
        document.getPage(0).getAnnotations().add(widget);
    }

    private static void addAttachment(PDDocument document) throws IOException {
        String name = "readme.txt";
        byte[] payload = "embedded attachment payload".getBytes(StandardCharsets.UTF_8);

        PDComplexFileSpecification spec = new PDComplexFileSpecification();
        spec.setFile(name);
        spec.setFileUnicode(name);

        PDEmbeddedFile embedded = new PDEmbeddedFile(document, new ByteArrayInputStream(payload));
        embedded.setSize(payload.length);
        embedded.setCreationDate(Calendar.getInstance());
        spec.setEmbeddedFile(embedded);

        Map<String, PDComplexFileSpecification> entries = new LinkedHashMap<>();
        entries.put(name, spec);
        PDEmbeddedFilesNameTreeNode tree = new PDEmbeddedFilesNameTreeNode();
        tree.setNames(entries);

        PDDocumentNameDictionary names = new PDDocumentNameDictionary(document.getDocumentCatalog());
        names.setEmbeddedFiles(tree);
        document.getDocumentCatalog().setNames(names);
    }

    private static void addInternalLink(PDDocument document) throws IOException {
        PDAnnotationLink link = new PDAnnotationLink();
        link.setRectangle(new PDRectangle(72, 600, 150, 20));

        PDActionGoTo action = new PDActionGoTo();
        PDPageFitDestination destination = new PDPageFitDestination();
        destination.setPage(document.getPage(1));
        action.setDestination(destination);
        link.setAction(action);

        document.getPage(0).getAnnotations().add(link);
    }
}
