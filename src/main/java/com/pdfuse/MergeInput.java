package com.pdfuse;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Une entrée du merge : un chemin et, éventuellement, une plage à extraire. */
public record MergeInput(Path path, PageRange range) {

    /**
     * Syntaxe {@code chemin[:plage]}, ex. {@code rapport.pdf:2-5}.
     * Le deux-points est cherché par la droite pour préserver les chemins
     * Windows ({@code C:\docs\a.pdf:1-3}).
     */
    public static MergeInput parse(String raw) throws MergeException {
        if (raw == null || raw.isBlank()) {
            throw new MergeException("empty input");
        }
        int colon = raw.lastIndexOf(':');
        if (colon > 1) {
            String suffix = raw.substring(colon + 1).trim();
            if (!suffix.isEmpty() && suffix.matches("[0-9,\\s-]+")) {
                String path = raw.substring(0, colon);
                return new MergeInput(toPath(path), PageRange.parse(suffix));
            }
        }
        return new MergeInput(toPath(raw), null);
    }

    private static Path toPath(String value) throws MergeException {
        try {
            return Path.of(value);
        } catch (InvalidPathException e) {
            throw new MergeException("invalid path: " + value);
        }
    }
}
