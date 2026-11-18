package com.pdfuse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfMergerTest {

    @TempDir
    Path tmp;

    @Test
    void mergesTwoDocumentsInOrder() throws Exception {
        Path a = TestPdf.simple(tmp, "a.pdf", "Alpha", 2);
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 3);
        Path out = tmp.resolve("out.pdf");

        PdfMerger.Result result = PdfMerger.merge(List.of(all(a), all(b)), out, false);

        assertEquals(2, result.inputs());
        assertEquals(5, result.pages());
        assertTrue(Files.exists(out));
        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            assertEquals(5, document.getNumberOfPages());
            PDFTextStripper text = new PDFTextStripper();
            text.setStartPage(1);
            text.setEndPage(1);
            assertTrue(text.getText(document).contains("Alpha"));
            text.setStartPage(3);
            text.setEndPage(3);
            assertTrue(text.getText(document).contains("Bravo"));
        }
    }

    @Test
    void mergesThreeDocuments() throws Exception {
        Path a = TestPdf.simple(tmp, "a.pdf", "Alpha", 2);
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 3);
        Path c = TestPdf.simple(tmp, "c.pdf", "Charlie", 1);

        PdfMerger.Result result = PdfMerger.merge(
                List.of(all(a), all(b), all(c)), tmp.resolve("out.pdf"), false);

        assertEquals(3, result.inputs());
        assertEquals(6, result.pages());
    }

    @Test
    void extractsOnlySelectedPages() throws Exception {
        Path rich = TestPdf.rich(tmp, "a.pdf", "Alpha");
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 1);
        Path out = tmp.resolve("out.pdf");

        PdfMerger.Result result = PdfMerger.merge(
                List.of(new MergeInput(rich, PageRange.parse("1")), all(b)), out, false);

        assertEquals(2, result.pages());
        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            PDFTextStripper text = new PDFTextStripper();
            text.setStartPage(1);
            text.setEndPage(1);
            assertTrue(text.getText(document).contains("Alpha - page 1"));
            text.setStartPage(2);
            text.setEndPage(2);
            assertTrue(text.getText(document).contains("Bravo - page 1"));
        }
    }

    @Test
    void extractsRangesInDocumentOrder() throws Exception {
        Path a = TestPdf.simple(tmp, "a.pdf", "Alpha", 4);
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 1);
        Path out = tmp.resolve("out.pdf");

        PdfMerger.Result result = PdfMerger.merge(
                List.of(new MergeInput(a, PageRange.parse("4,2-3")), all(b)), out, false);

        assertEquals(4, result.pages());
        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            PDFTextStripper text = new PDFTextStripper();
            for (int page = 1; page <= 3; page++) {
                text.setStartPage(page);
                text.setEndPage(page);
                assertTrue(text.getText(document).contains("Alpha - page " + (page + 1)));
            }
            text.setStartPage(4);
            text.setEndPage(4);
            assertTrue(text.getText(document).contains("Bravo - page 1"));
        }
    }

    @Test
    void extractionDropsLinksPointingOutsideRange() throws Exception {
        Path rich = TestPdf.rich(tmp, "a.pdf", "Alpha");
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 1);
        Path out = tmp.resolve("out.pdf");

        PdfMerger.merge(
                List.of(new MergeInput(rich, PageRange.parse("1")), all(b)), out, false);

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            assertEquals(1, outlineCount(document));
            int links = 0;
            for (PDPage page : document.getPages()) {
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if (annotation instanceof PDAnnotationLink) {
                        links++;
                    }
                }
            }
            assertEquals(0, links);
        }
    }

    @Test
    void extractionDropsOutlineWhenItsPageIsRemoved() throws Exception {
        Path rich = TestPdf.rich(tmp, "a.pdf", "Alpha");
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 1);
        Path out = tmp.resolve("out.pdf");

        PdfMerger.merge(
                List.of(new MergeInput(rich, PageRange.parse("2")), all(b)), out, false);

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            assertEquals(0, outlineCount(document));
        }
    }

    @Test
    void extractionKeepsFormsOnSelectedPagesAndDropsOrphans() throws Exception {
        Path rich = TestPdf.rich(tmp, "a.pdf", "Alpha");
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 1);

        Path kept = tmp.resolve("kept.pdf");
        PdfMerger.merge(
                List.of(new MergeInput(rich, PageRange.parse("1")), all(b)), kept, false);
        try (PDDocument document = Loader.loadPDF(kept.toFile())) {
            assertEquals(List.of("customer_name"), fieldNames(document));
        }

        Path dropped = tmp.resolve("dropped.pdf");
        PdfMerger.merge(
                List.of(new MergeInput(rich, PageRange.parse("2")), all(b)), dropped, false);
        try (PDDocument document = Loader.loadPDF(dropped.toFile())) {
            if (document.getDocumentCatalog().getAcroForm() != null) {
                assertTrue(fieldNames(document).isEmpty());
            }
        }
    }

    @Test
    void extractionKeepsAttachments() throws Exception {
        Path rich = TestPdf.rich(tmp, "a.pdf", "Alpha");
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 1);
        Path out = tmp.resolve("out.pdf");

        PdfMerger.merge(
                List.of(new MergeInput(rich, PageRange.parse("1")), all(b)), out, false);

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            var embedded = document.getDocumentCatalog().getNames()
                    .getEmbeddedFiles().getNames();
            assertEquals(Set.of("readme.txt"), embedded.keySet());
        }
    }

    @Test
    void rejectsOutOfRangePageSelection() throws Exception {
        Path rich = TestPdf.rich(tmp, "a.pdf", "Alpha");
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 1);

        MergeException error = assertThrows(MergeException.class,
                () -> PdfMerger.merge(
                        List.of(new MergeInput(rich, PageRange.parse("3")), all(b)),
                        tmp.resolve("out.pdf"), false));

        assertTrue(error.getMessage().contains("exceeds"));
    }

    @Test
    void pageRangeResolvesSortedUniqueIndices() throws Exception {
        assertEquals(List.of(0, 1, 3), PageRange.parse("1-2,4").resolve(5));
        assertEquals(List.of(1), PageRange.parse("2,2").resolve(5));
    }

    @Test
    void pageRangeRejectsInvalidInput() {
        assertThrows(MergeException.class, () -> PageRange.parse("3-1"));
        assertThrows(MergeException.class, () -> PageRange.parse("0"));
        assertThrows(MergeException.class, () -> PageRange.parse("1-"));
        assertThrows(MergeException.class, () -> PageRange.parse("1,,2"));
    }

    @Test
    void mergeInputParsesPathAndOptionalRange() throws Exception {
        MergeInput withRange = MergeInput.parse("rapport.pdf:2-5");
        assertEquals(Path.of("rapport.pdf"), withRange.path());
        assertEquals(List.of(1, 2, 3, 4), withRange.range().resolve(10));

        MergeInput allPages = MergeInput.parse("rapport.pdf");
        assertNull(allPages.range());

        MergeInput windows = MergeInput.parse("C:\\docs\\a.pdf:2");
        assertEquals(Path.of("C:\\docs\\a.pdf"), windows.path());
        assertEquals(List.of(1), windows.range().resolve(5));
    }

    @Test
    void preservesOutlinesLabelsFormsAndAttachments() throws Exception {
        Path a = TestPdf.rich(tmp, "a.pdf", "Alpha");
        Path b = TestPdf.rich(tmp, "b.pdf", "Bravo");
        Path out = tmp.resolve("out.pdf");

        PdfMerger.merge(List.of(all(a), all(b)), out, false);

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            assertEquals("Alpha", document.getDocumentInformation().getTitle());
            assertEquals(2, outlineCount(document));
            assertEquals(2, document.getDocumentCatalog().getPageLabels().getPageRangeCount());
            assertEquals(List.of("customer_name", "customer_name_2"), fieldNames(document));

            var embedded = document.getDocumentCatalog().getNames().getEmbeddedFiles().getNames();
            assertNotNull(embedded);
            assertEquals(Set.of("readme.txt", "readme_2.txt"), embedded.keySet());
        }
    }

    @Test
    void remapsInternalLinksAcrossDocuments() throws Exception {
        Path a = TestPdf.rich(tmp, "a.pdf", "Alpha");
        Path b = TestPdf.rich(tmp, "b.pdf", "Bravo");
        Path out = tmp.resolve("out.pdf");

        PdfMerger.merge(List.of(all(a), all(b)), out, false);

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            List<Integer> targets = new ArrayList<>();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                PDPage page = document.getPage(i);
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if (annotation instanceof PDAnnotationLink link
                            && link.getAction() instanceof PDActionGoTo goTo
                            && goTo.getDestination() instanceof PDPageDestination destination) {
                        targets.add(document.getPages().indexOf(destination.getPage()));
                    }
                }
            }
            assertEquals(List.of(1, 3), targets);
        }
    }

    @Test
    void refusesExistingOutputWithoutForce() throws Exception {
        Path a = TestPdf.simple(tmp, "a.pdf", "Alpha", 1);
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 1);
        Path out = Files.writeString(tmp.resolve("out.pdf"), "occupied");

        MergeException error = assertThrows(MergeException.class,
                () -> PdfMerger.merge(List.of(all(a), all(b)), out, false));

        assertTrue(error.getMessage().contains("already exists"));
        assertEquals("occupied", Files.readString(out));
    }

    @Test
    void overwritesExistingOutputWithForce() throws Exception {
        Path a = TestPdf.simple(tmp, "a.pdf", "Alpha", 1);
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 1);
        Path out = Files.writeString(tmp.resolve("out.pdf"), "old");

        PdfMerger.Result result = PdfMerger.merge(List.of(all(a), all(b)), out, true);

        assertEquals(2, result.pages());
        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            assertEquals(2, document.getNumberOfPages());
        }
    }

    @Test
    void refusesOutputEqualToInput() throws Exception {
        Path a = TestPdf.simple(tmp, "a.pdf", "Alpha", 1);
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 1);

        MergeException error = assertThrows(MergeException.class,
                () -> PdfMerger.merge(List.of(all(a), all(b)), a, true));

        assertTrue(error.getMessage().contains("must not overwrite an input"));
    }

    @Test
    void detectsImageExtensionsCaseInsensitively() {
        assertTrue(ImageToPdf.supports(Path.of("photo.JPG")));
        assertTrue(ImageToPdf.supports(Path.of("scan.tiff")));
        assertFalse(ImageToPdf.supports(Path.of("doc.pdf")));
        assertFalse(ImageToPdf.supports(Path.of("photo.webp")));
        assertFalse(ImageToPdf.supports(Path.of("no-extension")));
    }

    @Test
    void mergesPdfAndImagesInOrder() throws Exception {
        Path pdf = TestPdf.simple(tmp, "a.pdf", "Alpha", 2);
        Path wide = TestPdf.image(tmp, "wide.PNG", 400, 200, "png");
        Path tall = TestPdf.image(tmp, "tall.jpg", 200, 400, "jpg");
        Path out = tmp.resolve("out.pdf");

        PdfMerger.Result result = PdfMerger.merge(List.of(all(pdf), all(wide), all(tall)), out, false);

        assertEquals(4, result.pages());
        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            PDPage widePage = document.getPage(2);
            assertTrue(widePage.getMediaBox().getWidth() > widePage.getMediaBox().getHeight());
            assertTrue(widePage.getResources().getXObjectNames().iterator().hasNext());

            PDPage tallPage = document.getPage(3);
            assertTrue(tallPage.getMediaBox().getHeight() > tallPage.getMediaBox().getWidth());
        }
    }

    @Test
    void prependsPageLabelRangeWhenImageComesFirst() throws Exception {
        Path image = TestPdf.image(tmp, "cover.png", 100, 100, "png");
        Path rich = TestPdf.rich(tmp, "a.pdf", "Alpha");
        Path out = tmp.resolve("out.pdf");

        PdfMerger.merge(List.of(all(image), all(rich)), out, false);

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            COSDictionary labels = document.getDocumentCatalog().getCOSObject()
                    .getCOSDictionary(COSName.PAGE_LABELS);
            assertNotNull(labels);
            COSArray numbers = labels.getCOSArray(COSName.NUMS);
            assertNotNull(numbers);
            assertEquals(0, numbers.getInt(0));
            assertEquals(4, numbers.size());
        }
    }

    @Test
    void failsCleanlyOnInvalidImage() throws Exception {
        Path pdf = TestPdf.simple(tmp, "a.pdf", "Alpha", 1);
        Path fake = Files.writeString(tmp.resolve("fake.png"), "not an image");
        Path out = tmp.resolve("out.pdf");

        assertThrows(MergeException.class, () -> PdfMerger.merge(List.of(all(pdf), all(fake)), out, false));

        assertFalse(Files.exists(out));
        assertNoTempFile();
    }

    @Test
    void rejectsFewerThanTwoInputs() throws Exception {
        Path a = TestPdf.simple(tmp, "a.pdf", "Alpha", 1);

        MergeException error = assertThrows(MergeException.class,
                () -> PdfMerger.merge(List.of(all(a)), tmp.resolve("out.pdf"), false));

        assertTrue(error.getMessage().contains("at least two"));
    }

    @Test
    void rejectsMissingInput() throws Exception {
        Path a = TestPdf.simple(tmp, "a.pdf", "Alpha", 1);
        Path missing = tmp.resolve("missing.pdf");

        MergeException error = assertThrows(MergeException.class,
                () -> PdfMerger.merge(List.of(all(a), all(missing)), tmp.resolve("out.pdf"), false));

        assertTrue(error.getMessage().contains("does not exist"));
    }

    @Test
    void protectsOutputWithPassword() throws Exception {
        Path a = TestPdf.simple(tmp, "a.pdf", "Alpha", 1);
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 1);
        Path out = tmp.resolve("out.pdf");

        PdfMerger.merge(List.of(all(a), all(b)), out, false, "secret");

        assertThrows(InvalidPasswordException.class, () -> Loader.loadPDF(out.toFile()));
        try (PDDocument document = Loader.loadPDF(out.toFile(), "secret")) {
            assertTrue(document.isEncrypted());
            assertEquals(256, document.getEncryption().getLength());
            assertEquals(2, document.getNumberOfPages());
        }
    }

    @Test
    void rejectsEmptyOutputPassword() throws Exception {
        Path a = TestPdf.simple(tmp, "a.pdf", "Alpha", 1);
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 1);
        Path out = tmp.resolve("out.pdf");

        MergeException error = assertThrows(MergeException.class,
                () -> PdfMerger.merge(List.of(all(a), all(b)), out, false, ""));

        assertTrue(error.getMessage().contains("password"));
        assertFalse(Files.exists(out));
    }

    @Test
    void acceptsEncryptedInputWithEmptyUserPassword() throws Exception {
        Path a = TestPdf.simple(tmp, "a.pdf", "Alpha", 2);
        Path encrypted = TestPdf.encrypted(tmp, "enc.pdf", "Charlie");

        PdfMerger.Result result = PdfMerger.merge(
                List.of(all(a), all(encrypted)), tmp.resolve("out.pdf"), false);

        assertEquals(4, result.pages());
    }

    @Test
    void failsCleanlyOnInvalidPdf() throws Exception {
        Path a = TestPdf.simple(tmp, "a.pdf", "Alpha", 1);
        Path bad = Files.writeString(tmp.resolve("bad.pdf"), "not a pdf");
        Path out = tmp.resolve("out.pdf");

        assertThrows(MergeException.class, () -> PdfMerger.merge(List.of(all(a), all(bad)), out, false));

        assertFalse(Files.exists(out));
        assertNoTempFile();
    }

    @Test
    void leavesNoTempFileBehind() throws Exception {
        Path a = TestPdf.simple(tmp, "a.pdf", "Alpha", 1);
        Path b = TestPdf.simple(tmp, "b.pdf", "Bravo", 1);

        PdfMerger.merge(List.of(all(a), all(b)), tmp.resolve("out.pdf"), false);

        assertNoTempFile();
    }

    private static MergeInput all(Path path) {
        return new MergeInput(path, null);
    }

    private static int outlineCount(PDDocument document) {
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline == null) {
            return 0;
        }
        int count = 0;
        for (PDOutlineItem ignored : outline.children()) {
            count++;
        }
        return count;
    }

    private static List<String> fieldNames(PDDocument document) {
        List<String> names = new ArrayList<>();
        for (PDField field : document.getDocumentCatalog().getAcroForm().getFieldTree()) {
            names.add(field.getFullyQualifiedName());
        }
        return names;
    }

    private void assertNoTempFile() throws Exception {
        try (var files = Files.list(tmp)) {
            assertTrue(files.noneMatch(
                    path -> path.getFileName().toString().startsWith(".pdfuse-")));
        }
    }
}
