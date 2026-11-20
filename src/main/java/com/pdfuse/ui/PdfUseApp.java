package com.pdfuse.ui;

import java.util.List;

import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatLightLaf;

/** Point d'entrée de l'interface graphique. */
public final class PdfUseApp {

    private PdfUseApp() {
    }

    public static void main(String[] args) {
        FlatLightLaf.setup();
        List<String> paths = List.of(args);
        SwingUtilities.invokeLater(() -> new MainFrame(paths).setVisible(true));
    }
}
