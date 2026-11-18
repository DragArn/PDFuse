package com.pdfuse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;

/** Cœur du backend : fusionne une liste de PDF et d'images en un seul PDF. */
public final class PdfMerger {

    public record Result(Path output, int inputs, int pages, long bytes, long millis) {}

    private PdfMerger() {
    }

    public static Result merge(List<MergeInput> inputs, Path output, boolean overwrite)
            throws MergeException {
        long start = System.nanoTime();

        if (inputs.size() < 2) {
            throw new MergeException("at least two inputs are required");
        }
        Path out = output.toAbsolutePath().normalize();
        for (int i = 0; i < inputs.size(); i++) {
            checkInput(inputs.get(i).path(), "input #" + (i + 1));
        }
        checkOutput(out, inputs, overwrite);

        Path temp = null;
        try (PDDocument destination = new PDDocument()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            for (MergeInput input : inputs) {
                append(merger, destination, input);
            }

            // PDFBox recopie les pièces jointes telles quelles : plusieurs fichiers
            // portant le même nom produisent un arbre /EmbeddedFiles invalide.
            dedupeAttachmentNames(destination);
            ensurePageLabelsStartAtZero(destination);

            // CREATE_NEW applique l'umask : le fichier final n'hérite pas du 0600
            // que Files.createTempFile impose aux fichiers temporaires.
            temp = out.getParent().resolve(".pdfuse-" + UUID.randomUUID() + ".tmp");
            try (OutputStream stream = Files.newOutputStream(temp,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                destination.save(stream, CompressParameters.DEFAULT_COMPRESSION);
            }
            move(temp, out, overwrite);

            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            return new Result(out, inputs.size(), destination.getNumberOfPages(),
                    Files.size(out), millis);
        } catch (IOException e) {
            throw new MergeException("merge failed: " + e.getMessage(), e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // le fichier temporaire sera nettoyé par l'OS
                }
            }
        }
    }

    private static void checkInput(Path path, String label) throws MergeException {
        if (!Files.exists(path)) {
            throw new MergeException(label + " input does not exist: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new MergeException(label + " input is not a file: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new MergeException(label + " input is not readable: " + path);
        }
    }

    private static void checkOutput(Path out, List<MergeInput> inputs, boolean overwrite)
            throws MergeException {
        for (MergeInput input : inputs) {
            if (out.equals(input.path().toAbsolutePath().normalize())) {
                throw new MergeException("output must not overwrite an input: " + out);
            }
        }
        Path parent = out.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new MergeException("output directory does not exist: " + parent);
        }
        if (!Files.isWritable(parent)) {
            throw new MergeException("output directory is not writable: " + parent);
        }
        if (Files.exists(out) && !overwrite) {
            throw new MergeException("output already exists (use --force): " + out);
        }
    }

    private static void append(PDFMergerUtility merger, PDDocument destination, MergeInput input)
            throws IOException, MergeException {
        Path source = input.path();
        try (PDDocument document = ImageToPdf.supports(source)
                ? ImageToPdf.convert(source)
                : Loader.loadPDF(source.toFile())) {
            if (document.getNumberOfPages() == 0) {
                throw new IOException("input has no pages: " + source);
            }
            if (input.range() != null) {
                List<Integer> selected = input.range().resolve(document.getNumberOfPages());
                if (selected.size() < document.getNumberOfPages()) {
                    extract(document, selected);
                }
            }
            renameCollidingFields(destination, document);
            // appendDocument (mode legacy) conserve signets, labels, formulaires
            // et pièces jointes ; mergeDocuments/OPTIMIZE_RESOURCES non.
            merger.appendDocument(destination, document);
        }
    }

    /**
     * Réduit le document aux pages demandées et nettoie les références qui
     * pointeraient vers des pages supprimées.
     */
    private static void extract(PDDocument document, List<Integer> selected) throws IOException {
        int oldCount = document.getNumberOfPages();
        boolean[] keep = new boolean[oldCount];
        int[] remap = new int[oldCount];
        Arrays.fill(remap, -1);
        for (int i = 0; i < selected.size(); i++) {
            int old = selected.get(i);
            keep[old] = true;
            remap[old] = i;
        }

        // Les labels décrivent la pagination d'origine : on les retire.
        document.getDocumentCatalog().getCOSObject().removeItem(COSName.PAGE_LABELS);

        rebuildOutline(document, keep);
        pruneLinks(document, keep, remap);

        for (int i = oldCount - 1; i >= 0; i--) {
            if (!keep[i]) {
                document.getPages().remove(i);
            }
        }

        pruneForms(document);
    }

    /** Reconstruit l'outline en ne gardant que les cibles encore présentes. */
    private static void rebuildOutline(PDDocument document, boolean[] keep) throws IOException {
        PDDocumentCatalog catalog = document.getDocumentCatalog();
        PDDocumentOutline source = catalog.getDocumentOutline();
        if (source == null) {
            return;
        }

        PDDocumentOutline target = new PDDocumentOutline();
        copyOutline(source, target, document, keep);
        if (target.getFirstChild() == null) {
            catalog.getCOSObject().removeItem(COSName.OUTLINES);
        } else {
            catalog.setDocumentOutline(target);
        }
    }

    private static void copyOutline(PDOutlineNode source, PDOutlineNode target,
            PDDocument document, boolean[] keep) throws IOException {
        for (PDOutlineItem item : source.children()) {
            PDPage page = item.findDestinationPage(document);
            boolean validDestination = false;
            if (page != null) {
                int old = document.getPages().indexOf(page);
                validDestination = old >= 0 && keep[old];
            }
            PDAction action = item.getAction();
            boolean nonGoToAction = action != null && !(action instanceof PDActionGoTo);

            PDOutlineItem copy = new PDOutlineItem();
            copy.setTitle(item.getTitle());
            if (item.getTextColor() != null) {
                copy.setTextColor(item.getTextColor());
            }
            copy.setBold(item.isBold());
            copy.setItalic(item.isItalic());
            if (validDestination) {
                copy.setDestination(page);
            } else if (nonGoToAction) {
                copy.setAction(action);
            }

            copyOutline(item, copy, document, keep);
            if (validDestination || nonGoToAction || copy.getFirstChild() != null) {
                target.addLast(copy);
            }
        }
    }

    /** Supprime les liens dont la cible disparaît et remappe les cibles numériques. */
    private static void pruneLinks(PDDocument document, boolean[] keep, int[] remap)
            throws IOException {
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            if (!keep[i]) {
                continue;
            }
            List<PDAnnotation> annotations = document.getPage(i).getAnnotations();
            for (int j = annotations.size() - 1; j >= 0; j--) {
                PDAnnotation annotation = annotations.get(j);
                if (!(annotation instanceof PDAnnotationLink link)) {
                    continue;
                }
                PDDestination destination = link.getDestination();
                if (destination == null && link.getAction() instanceof PDActionGoTo goTo) {
                    destination = goTo.getDestination();
                }
                if (destination != null
                        && !keepDestination(destination, document, keep, remap)) {
                    annotations.remove(j);
                }
            }
        }
    }

    private static boolean keepDestination(PDDestination destination, PDDocument document,
            boolean[] keep, int[] remap) throws IOException {
        if (destination instanceof PDPageDestination pageDestination) {
            PDPage page = pageDestination.getPage();
            int old;
            if (page != null) {
                old = document.getPages().indexOf(page);
            } else {
                old = pageDestination.getPageNumber();
                if (old >= 0 && old < keep.length && keep[old]) {
                    pageDestination.setPageNumber(remap[old]);
                }
            }
            if (old < 0 || old >= keep.length) {
                return true; // cible non résolue : on ne casse rien
            }
            return keep[old];
        }
        if (destination instanceof PDNamedDestination named) {
            PDPageDestination resolved = document.getDocumentCatalog()
                    .findNamedDestinationPage(named);
            if (resolved == null) {
                return true;
            }
            return keepDestination(resolved, document, keep, remap);
        }
        return true;
    }

    /** Retire les champs de formulaire dont tous les widgets ont disparu. */
    private static void pruneForms(PDDocument document) {
        PDAcroForm form = document.getDocumentCatalog().getAcroForm();
        if (form == null) {
            return;
        }

        List<PDField> orphans = new ArrayList<>();
        for (PDField field : form.getFieldTree()) {
            if (field instanceof PDNonTerminalField) {
                continue;
            }
            boolean onPage = false;
            for (PDAnnotationWidget widget : field.getWidgets()) {
                PDPage page = widget.getPage();
                if (page == null || document.getPages().indexOf(page) >= 0) {
                    onPage = true;
                    break;
                }
            }
            if (!onPage) {
                orphans.add(field);
            }
        }
        for (PDField field : orphans) {
            removeField(form, field);
        }

        // Groupes devenus vides.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (PDField field : form.getFieldTree()) {
                if (field instanceof PDNonTerminalField group
                        && group.getChildren().isEmpty()) {
                    removeField(form, group);
                    changed = true;
                    break;
                }
            }
        }

        if (form.getFields().isEmpty()) {
            document.getDocumentCatalog().getCOSObject().removeItem(COSName.ACRO_FORM);
        }
    }

    private static void removeField(PDAcroForm form, PDField field) {
        PDNonTerminalField parent = field.getParent();
        COSArray array = parent == null
                ? form.getCOSObject().getCOSArray(COSName.FIELDS)
                : parent.getCOSObject().getCOSArray(COSName.KIDS);
        if (array == null) {
            return;
        }
        for (int i = array.size() - 1; i >= 0; i--) {
            if (array.getObject(i) == field.getCOSObject()) {
                array.remove(i);
                return;
            }
        }
    }

    /**
     * Évite le renommage interne de PDFBox en dummyFieldNameN : on suffixe les
     * champs du document entrant qui entrent en collision avec la destination.
     */
    private static void renameCollidingFields(PDDocument destination, PDDocument source) {
        PDAcroForm sourceForm = source.getDocumentCatalog().getAcroForm();
        PDAcroForm destinationForm = destination.getDocumentCatalog().getAcroForm();
        if (sourceForm == null || destinationForm == null) {
            return;
        }

        Set<String> taken = new HashSet<>();
        for (PDField field : destinationForm.getFieldTree()) {
            if (field.getFullyQualifiedName() != null) {
                taken.add(field.getFullyQualifiedName());
            }
        }

        for (PDField field : sourceForm.getFields()) {
            String name = field.getFullyQualifiedName();
            if (name == null || !taken.contains(name)) {
                if (name != null) {
                    taken.add(name);
                }
                continue;
            }
            String partial = field.getPartialName();
            if (partial == null || partial.isBlank()) {
                partial = "field";
            }
            String prefix = name.length() > partial.length()
                    ? name.substring(0, name.length() - partial.length())
                    : "";
            int index = 2;
            String candidate = partial + "_" + index;
            while (taken.contains(prefix + candidate)) {
                index++;
                candidate = partial + "_" + index;
            }
            field.setPartialName(candidate);
            taken.add(prefix + candidate);
        }
    }

    private static void move(Path temp, Path out, boolean overwrite) throws IOException {
        if (overwrite) {
            try {
                Files.move(temp, out, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, out, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            try {
                Files.move(temp, out, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, out);
            }
        }
    }

    /**
     * Une image en première entrée décale les labels du PDF suivant à l'index 1.
     * Le PDF exige qu'une plage commence à 0 : on ajoute une plage décimale.
     */
    private static void ensurePageLabelsStartAtZero(PDDocument document) {
        COSDictionary pageLabels = document.getDocumentCatalog().getCOSObject()
                .getCOSDictionary(COSName.PAGE_LABELS);
        if (pageLabels == null) {
            return;
        }
        COSArray numbers = pageLabels.getCOSArray(COSName.NUMS);
        if (numbers == null) {
            return;
        }
        for (int i = 0; i + 1 < numbers.size(); i += 2) {
            if (numbers.getInt(i, -1) == 0) {
                return;
            }
        }
        COSDictionary range = new COSDictionary();
        range.setItem(COSName.S, COSName.getPDFName("D"));
        range.setInt(COSName.ST, 1);
        numbers.add(0, COSInteger.get(0));
        numbers.add(1, range);
    }

    /** Renomme les pièces jointes homonymes (readme.txt, readme_2.txt, ...). */
    private static void dedupeAttachmentNames(PDDocument document) {
        COSDictionary names = document.getDocumentCatalog().getCOSObject()
                .getCOSDictionary(COSName.NAMES);
        if (names == null) {
            return;
        }
        COSDictionary embedded = names.getCOSDictionary(COSName.EMBEDDED_FILES);
        if (embedded != null) {
            dedupeNameTree(embedded, new HashSet<>());
        }
    }

    private static void dedupeNameTree(COSDictionary node, Set<String> used) {
        COSArray kids = node.getCOSArray(COSName.KIDS);
        if (kids != null) {
            for (int i = 0; i < kids.size(); i++) {
                COSBase kid = kids.getObject(i);
                if (kid instanceof COSDictionary child) {
                    dedupeNameTree(child, used);
                }
            }
        }

        COSArray entries = node.getCOSArray(COSName.NAMES);
        if (entries == null) {
            return;
        }
        for (int i = 0; i + 1 < entries.size(); i += 2) {
            COSBase key = entries.getObject(i);
            if (!(key instanceof COSString name)) {
                continue;
            }
            if (used.add(name.getString())) {
                continue;
            }
            String unique = uniqueName(name.getString(), used);
            used.add(unique);
            entries.set(i, new COSString(unique));

            COSBase value = entries.getObject(i + 1);
            if (value instanceof COSDictionary spec) {
                spec.setString(COSName.F, unique);
                spec.setString(COSName.UF, unique);
            }
        }
    }

    private static String uniqueName(String name, Set<String> used) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        int index = 2;
        String candidate = base + "_" + index + extension;
        while (used.contains(candidate)) {
            index++;
            candidate = base + "_" + index + extension;
        }
        return candidate;
    }
}
