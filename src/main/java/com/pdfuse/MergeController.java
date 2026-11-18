package com.pdfuse;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Petit contrôleur : traduit les entrées CLI puis délègue au moteur. */
public final class MergeController {

    private MergeController() {
    }

    public static PdfMerger.Result merge(List<String> rawInputs, Path output, boolean overwrite)
            throws MergeException {
        return merge(rawInputs, output, overwrite, null);
    }

    public static PdfMerger.Result merge(List<String> rawInputs, Path output, boolean overwrite,
            String password) throws MergeException {
        List<MergeInput> inputs = new ArrayList<>(rawInputs.size());
        for (String raw : rawInputs) {
            inputs.add(MergeInput.parse(raw));
        }
        return PdfMerger.merge(inputs, output, overwrite, password);
    }
}
