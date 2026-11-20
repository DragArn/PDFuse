package com.pdfuse.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.pdfuse.MergeController;
import com.pdfuse.MergeException;
import com.pdfuse.PdfMerger;

/** Fenêtre principale de PDFuse. */
final class MainFrame extends JFrame {

    private static final Set<String> INPUT_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff");

    private final BannerPanel banner = new BannerPanel();
    private final JPanel rowsPanel = new JPanel();
    private final JScrollPane scrollPane;
    private final List<InputRow> rows = new ArrayList<>();
    private final JTextField outputField = new JTextField();
    private final JCheckBox overwrite = new JCheckBox("Overwrite");
    private final JButton addButton = new JButton("Add row");
    private final JButton removeButton = new JButton("Remove row");
    private final JButton saveButton = new JButton("Save as...");
    private final JButton mergeButton = new JButton("Merge");
    private final JLabel status = new JLabel("Ready");
    private final JProgressBar progress = new JProgressBar();
    private int pulse;
    private final Timer mergePulse = new Timer(350, event -> {
        status.setText("Merging" + ".".repeat(1 + pulse % 3));
        pulse++;
    });

    MainFrame(List<String> initialPaths) {
        super("PDFuse");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1000, 760);
        setLocationRelativeTo(null);
        mergePulse.setCoalesce(true);

        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        JPanel rowsWrapper = new JPanel(new BorderLayout());
        rowsWrapper.add(rowsPanel, BorderLayout.NORTH);

        scrollPane = new JScrollPane(rowsWrapper);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel rowButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rowButtons.add(addButton);
        rowButtons.add(removeButton);

        JLabel filesLabel = new JLabel("Files to merge");
        filesLabel.setFont(filesLabel.getFont().deriveFont(Font.BOLD));
        JPanel listHeader = new JPanel(new BorderLayout());
        listHeader.add(filesLabel, BorderLayout.WEST);
        listHeader.add(rowButtons, BorderLayout.EAST);

        JPanel listPanel = new JPanel(new BorderLayout(0, 8));
        listPanel.add(listHeader, BorderLayout.NORTH);
        listPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel outputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.insets = new Insets(0, 0, 0, 6);

        constraints.gridx = 0;
        outputPanel.add(new JLabel("Output"), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        outputField.putClientProperty("JTextField.placeholderText", "merged.pdf");
        outputPanel.add(outputField, constraints);

        constraints.gridx = 2;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        outputPanel.add(saveButton, constraints);

        constraints.gridx = 3;
        constraints.insets = new Insets(0, 10, 0, 0);
        outputPanel.add(overwrite, constraints);

        constraints.gridx = 4;
        constraints.insets = new Insets(0, 14, 0, 0);
        mergeButton.setFont(mergeButton.getFont().deriveFont(Font.BOLD));
        outputPanel.add(mergeButton, constraints);

        progress.setIndeterminate(true);
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(140, 16));

        JPanel statusPanel = new JPanel(new BorderLayout(8, 0));
        statusPanel.add(status, BorderLayout.CENTER);
        statusPanel.add(progress, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout(0, 10));
        south.add(outputPanel, BorderLayout.NORTH);
        south.add(statusPanel, BorderLayout.SOUTH);

        JPanel topPanel = new JPanel(new BorderLayout(0, 10));
        topPanel.add(banner, BorderLayout.NORTH);
        topPanel.add(new DropZone(this::addFiles, this::chooseFilesForDropZone),
                BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(topPanel, BorderLayout.NORTH);
        content.add(listPanel, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(mergeButton);

        addButton.addActionListener(event -> addRow(""));
        removeButton.addActionListener(event -> removeRow());
        saveButton.addActionListener(event -> chooseOutput());
        mergeButton.addActionListener(event -> merge());

        if (initialPaths.isEmpty()) {
            addRow("");
            addRow("");
        } else {
            for (String path : initialPaths) {
                addRow(path);
            }
        }
    }

    private void addRow(String path) {
        InputRow row = new InputRow(path);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        rows.add(row);
        rowsPanel.add(row);
        updateRowButtons();
        rowsPanel.revalidate();
        rowsPanel.repaint();
        SwingUtilities.invokeLater(() -> {
            row.focusPath();
            scrollPane.getVerticalScrollBar()
                    .setValue(scrollPane.getVerticalScrollBar().getMaximum());
        });
    }

    private void chooseFilesForDropZone() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "PDF and images", "pdf", "jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff");
        chooser.addChoosableFileFilter(filter);
        chooser.setFileFilter(filter);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            addFiles(List.of(chooser.getSelectedFiles()));
        }
    }

    private void addFiles(List<File> files) {
        int added = 0;
        int ignored = 0;
        for (File file : files) {
            Path path = file.toPath();
            if (!Files.isRegularFile(path) || !isSupported(path)) {
                ignored++;
                continue;
            }

            InputRow target = null;
            for (InputRow row : rows) {
                if (row.path().isEmpty()) {
                    target = row;
                    break;
                }
            }
            if (target == null) {
                addRow(path.toAbsolutePath().toString());
            } else {
                target.setPath(path.toAbsolutePath().toString());
                target.focusPath();
                rowsPanel.scrollRectToVisible(target.getBounds());
            }
            added++;
        }

        if (added > 0) {
            status.setText(added == 1 ? "File added" : added + " files added");
        } else if (ignored > 0) {
            status.setText("Ignored " + ignored + " unsupported file(s)");
        }
        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    private static boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && INPUT_EXTENSIONS.contains(name.substring(dot + 1));
    }

    private void removeRow() {
        if (rows.size() <= 1) {
            return;
        }
        int index = focusedRowIndex();
        if (index < 0) {
            index = rows.size() - 1;
        }
        InputRow row = rows.remove(index);
        rowsPanel.remove(row);
        updateRowButtons();
        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    private int focusedRowIndex() {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner == null) {
            return -1;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (SwingUtilities.isDescendingFrom(owner, rows.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private void updateRowButtons() {
        removeButton.setEnabled(rows.size() > 1);
    }

    private void chooseOutput() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("merged.pdf"));
        FileNameExtensionFilter filter = new FileNameExtensionFilter("PDF", "pdf");
        chooser.addChoosableFileFilter(filter);
        chooser.setFileFilter(filter);

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".pdf")) {
                file = new File(file.getParentFile(), file.getName() + ".pdf");
            }
            outputField.setText(file.getAbsolutePath());
        }
    }

    private void merge() {
        List<String> inputs = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            InputRow row = rows.get(i);
            if (row.path().isEmpty()) {
                if (!row.pages().isEmpty() && !row.pages().equalsIgnoreCase("all")) {
                    showError("Row " + (i + 1) + " has pages but no file.");
                    return;
                }
                continue;
            }
            String pages = row.pages().equalsIgnoreCase("all") ? "" : row.pages();
            inputs.add(pages.isEmpty() ? row.path() : row.path() + ":" + pages);
        }

        if (inputs.size() < 2) {
            showError("Add at least two files.");
            return;
        }
        String output = outputField.getText().trim();
        if (output.isEmpty()) {
            showError("Choose an output file.");
            return;
        }

        Path outputPath;
        try {
            outputPath = Path.of(output).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            showError("Invalid output path: " + output);
            return;
        }
        boolean force = overwrite.isSelected();
        setBusy(true);
        status.setText("Merging...");

        new SwingWorker<PdfMerger.Result, Void>() {
            @Override
            protected PdfMerger.Result doInBackground() throws Exception {
                return MergeController.merge(inputs, outputPath, force, null);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    PdfMerger.Result result = get();
                    status.setText(String.format("Merged %d files, %d pages -> %s (%s, %d ms)",
                            result.inputs(), result.pages(), result.output(),
                            humanSize(result.bytes()), result.millis()));
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "PDF created:\n" + result.output(),
                            "Merge complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    String message = cause instanceof MergeException
                            ? cause.getMessage()
                            : String.valueOf(cause);
                    showError(message);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        addButton.setEnabled(!busy);
        removeButton.setEnabled(!busy && rows.size() > 1);
        saveButton.setEnabled(!busy);
        mergeButton.setEnabled(!busy);
        mergeButton.setText(busy ? "Merging..." : "Merge");
        outputField.setEnabled(!busy);
        overwrite.setEnabled(!busy);
        progress.setVisible(busy);
        banner.setBusy(busy);
        if (busy) {
            pulse = 0;
            mergePulse.start();
        } else {
            mergePulse.stop();
        }
        for (InputRow row : rows) {
            row.setEditable(!busy);
        }
    }

    private void showError(String message) {
        status.setText("Error: " + message);
        JOptionPane.showMessageDialog(this, message, "PDFuse", JOptionPane.ERROR_MESSAGE);
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
