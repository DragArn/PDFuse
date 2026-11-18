package com.pdfuse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;

/** Cœur du backend : fusionne une liste de PDF et d'images en un seul PDF. */
public final class PdfMerger {

    public record Result(Path output, int inputs, int pages, long bytes, long millis) {}

    private PdfMerger() {
    }

    public static Result merge(List<Path> inputs, Path output, boolean overwrite)
            throws MergeException {
        long start = System.nanoTime();

        if (inputs.size() < 2) {
            throw new MergeException("at least two inputs are required");
        }
        Path out = output.toAbsolutePath().normalize();
        for (int i = 0; i < inputs.size(); i++) {
            checkInput(inputs.get(i), "input #" + (i + 1));
        }
        checkOutput(out, inputs, overwrite);

        Path temp = null;
        try (PDDocument destination = new PDDocument()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            for (Path input : inputs) {
                append(merger, destination, input);
            }

            // PDFBox recopie les pièces jointes telles quelles : plusieurs fichiers
            // portant le même nom produisent un arbre /EmbeddedFiles invalide.
            dedupeAttachmentNames(destination);

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

    private static void checkOutput(Path out, List<Path> inputs, boolean overwrite)
            throws MergeException {
        for (Path input : inputs) {
            if (out.equals(input.toAbsolutePath().normalize())) {
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

    private static void append(PDFMergerUtility merger, PDDocument destination, Path source)
            throws IOException {
        try (PDDocument document = ImageToPdf.supports(source)
                ? ImageToPdf.convert(source)
                : Loader.loadPDF(source.toFile())) {
            if (document.getNumberOfPages() == 0) {
                throw new IOException("input has no pages: " + source);
            }
            renameCollidingFields(destination, document);
            // appendDocument (mode legacy) conserve signets, labels, formulaires
            // et pièces jointes ; mergeDocuments/OPTIMIZE_RESOURCES non.
            merger.appendDocument(destination, document);
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
