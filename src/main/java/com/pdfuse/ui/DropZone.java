package com.pdfuse.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

/** Zone de dépôt de fichiers : remplit la première ligne vide ou en ajoute une. */
final class DropZone extends JPanel {

    private static final DataFlavor FILES = DataFlavor.javaFileListFlavor;
    private static final Color IDLE_BG = new Color(0xF7F8FC);
    private static final Color ARMED_BG = new Color(0xEEF2FF);
    private static final Color IDLE_BORDER = new Color(0xC8CDD8);
    private static final Color ARMED_BORDER = new Color(0x5C7CFA);
    private static final Color IDLE_TEXT = new Color(0x2D3446);
    private static final Color ARMED_TEXT = new Color(0x3E5EDC);

    private final JLabel title = new JLabel("Drop files here or click to browse");
    private final JLabel hint = new JLabel("PDF or images — added to the list");
    private final Border idleBorder;
    private final Border armedBorder;

    DropZone(Consumer<List<File>> onDrop, Runnable onBrowse) {
        setLayout(new GridBagLayout());
        setOpaque(true);
        setBackground(IDLE_BG);
        setPreferredSize(new Dimension(0, 62));
        setToolTipText("Drop PDF or image files here, or click to browse");

        idleBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(IDLE_BORDER, 2f, 7f, 5f, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12));
        armedBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(ARMED_BORDER, 2f, 7f, 5f, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12));
        setBorder(idleBorder);

        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setForeground(IDLE_TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(new Color(0x777F8F));
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(title);
        text.add(Box.createVerticalStrut(2));
        text.add(hint);
        add(text);

        MouseAdapter browseOnClick = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    onBrowse.run();
                }
            }
        };
        Cursor hand = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        for (Component component : new Component[] {this, text, title, hint}) {
            component.addMouseListener(browseOnClick);
            component.setCursor(hand);
        }

        new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent event) {
                if (event.isDataFlavorSupported(FILES)) {
                    event.acceptDrag(DnDConstants.ACTION_COPY);
                    setArmed(true);
                } else {
                    event.rejectDrag();
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent event) {
                if (event.isDataFlavorSupported(FILES)) {
                    event.acceptDrag(DnDConstants.ACTION_COPY);
                } else {
                    event.rejectDrag();
                }
            }

            @Override
            public void dragExit(DropTargetEvent event) {
                setArmed(false);
            }

            @Override
            public void drop(DropTargetDropEvent event) {
                setArmed(false);
                if (!event.isDataFlavorSupported(FILES)) {
                    event.rejectDrop();
                    return;
                }
                event.acceptDrop(DnDConstants.ACTION_COPY);
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) event.getTransferable()
                            .getTransferData(FILES);
                    SwingUtilities.invokeLater(() -> onDrop.accept(files));
                    event.dropComplete(true);
                } catch (Exception e) {
                    event.dropComplete(false);
                }
            }
        }, true);
    }

    private void setArmed(boolean armed) {
        setBackground(armed ? ARMED_BG : IDLE_BG);
        setBorder(armed ? armedBorder : idleBorder);
        title.setForeground(armed ? ARMED_TEXT : IDLE_TEXT);
        repaint();
    }
}
