package com.pdfuse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Transforme une image en un PDF d'une page, prêt à être fusionné. */
final class ImageToPdf {

    private static final Set<String> EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff");
    private static final float MARGIN = 18f;

    private ImageToPdf() {
    }

    /** Détection par extension, insensible à la casse. */
    static boolean supports(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1));
    }

    static PDDocument convert(Path path) throws IOException {
        PDDocument document = new PDDocument();
        try {
            PDImageXObject image = PDImageXObject.createFromFileByContent(path.toFile(), document);

            // A4 dans le bon sens, image centrée et proportionnée.
            PDRectangle format = image.getWidth() > image.getHeight()
                    ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
                    : PDRectangle.A4;
            PDPage page = new PDPage(format);
            document.addPage(page);

            float maxWidth = format.getWidth() - 2 * MARGIN;
            float maxHeight = format.getHeight() - 2 * MARGIN;
            float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
            float width = image.getWidth() * scale;
            float height = image.getHeight() * scale;
            float x = (format.getWidth() - width) / 2;
            float y = (format.getHeight() - height) / 2;

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, x, y, width, height);
            }
            return document;
        } catch (IOException | RuntimeException e) {
            try {
                document.close();
            } catch (IOException ignored) {
                // rien à faire de plus
            }
            throw new IOException("cannot read image " + path.getFileName() + ": "
                    + e.getMessage(), e);
        }
    }
}
