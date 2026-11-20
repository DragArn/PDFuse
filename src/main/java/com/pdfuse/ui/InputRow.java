package com.pdfuse.ui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Une ligne de saisie : chemin + bouton parcourir + plage de pages. */
final class InputRow extends JPanel {

    private final JTextField pathField = new JTextField();
    private final JTextField pagesField = new JTextField();
    private final JButton browseButton = new JButton("Browse...");

    InputRow(String path) {
        super(new GridBagLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        pathField.setText(path);
        pathField.putClientProperty("JTextField.placeholderText", "PDF or image path");
        pathField.setToolTipText("Path to a PDF or an image");

        pagesField.setColumns(8);
        pagesField.putClientProperty("JTextField.placeholderText", "all");
        pagesField.setToolTipText("Pages to keep, 1-based: 1-3,5. Empty = all pages");

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.insets = new Insets(0, 0, 0, 6);

        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        add(pathField, constraints);

        constraints.gridx = 1;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        add(browseButton, constraints);

        constraints.gridx = 2;
        constraints.insets = new Insets(0, 8, 0, 6);
        add(new JLabel("Pages"), constraints);

        constraints.gridx = 3;
        constraints.insets = new Insets(0, 0, 0, 0);
        add(pagesField, constraints);

        browseButton.addActionListener(event -> chooseFile());
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "PDF and images", "pdf", "jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff");
        chooser.addChoosableFileFilter(filter);
        chooser.setFileFilter(filter);

        String current = path();
        if (!current.isEmpty()) {
            File file = new File(current);
            File directory = file.isDirectory() ? file : file.getParentFile();
            if (directory != null && directory.isDirectory()) {
                chooser.setCurrentDirectory(directory);
            }
        }

        Window owner = SwingUtilities.getWindowAncestor(this);
        if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    String path() {
        return pathField.getText().trim();
    }

    String pages() {
        return pagesField.getText().trim();
    }

    boolean isBlank() {
        return path().isEmpty() && pages().isEmpty();
    }

    void setPath(String path) {
        pathField.setText(path);
    }

    void focusPath() {
        pathField.requestFocusInWindow();
    }

    void setEditable(boolean editable) {
        pathField.setEnabled(editable);
        pagesField.setEnabled(editable);
        browseButton.setEnabled(editable);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
