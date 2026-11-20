package bench.swing;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Prototype de benchmark Swing + FlatLaf. */
public final class Main {

    private static final long START = System.nanoTime();
    private static final int THUMBS = 20;
    private static final int THUMB_WIDTH = 120;

    private record Item(String name, ImageIcon icon) {
    }

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: Main <pdf> [--bench]");
            System.exit(2);
        }
        Path pdf = Path.of(args[0]);
        boolean bench = args.length > 1 && args[1].equals("--bench");
        FlatLightLaf.setup();
        SwingUtilities.invokeAndWait(() -> build(pdf, bench));
    }

    private static void build(Path pdf, boolean bench) {
        JFrame frame = new JFrame("PDFuse — Swing benchmark");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1100, 700);
        frame.setLocationRelativeTo(null);

        DefaultListModel<Item> model = new DefaultListModel<>();
        JList<Item> list = new JList<>(model);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> target, Object value,
                    int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(target, value, index, selected, focused);
                Item item = (Item) value;
                setText(item.name());
                setIcon(item.icon());
                setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                return this;
            }
        });
        list.setFixedCellHeight(90);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setDropMode(javax.swing.DropMode.ON);
        list.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                try {
                    Transferable transferable = support.getTransferable();
                    List<File> files = (List<File>) transferable.getTransferData(
                            DataFlavor.javaFileListFlavor);
                    for (File file : files) {
                        model.addElement(new Item(file.getName(), null));
                    }
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });

        JLabel preview = new JLabel("Preview", SwingConstants.CENTER);
        preview.setPreferredSize(new Dimension(560, 620));
        JScrollPane previewScroll = new JScrollPane(preview);

        JButton add = new JButton("Add files");
        JButton merge = new JButton("Merge");
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.add(add);
        top.add(merge);

        JLabel status = new JLabel("Ready");

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(top, BorderLayout.NORTH);
        content.add(new JScrollPane(list), BorderLayout.WEST);
        content.add(previewScroll, BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);

        frame.setContentPane(content);
        frame.setVisible(true);
        long uiMs = (System.nanoTime() - START) / 1_000_000;

        add.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setMultiSelectionEnabled(true);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                for (File file : chooser.getSelectedFiles()) {
                    model.addElement(new Item(file.getName(), null));
                }
            }
        });
        merge.addActionListener(event -> status.setText("merge not implemented in benchmark"));
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                Item item = list.getSelectedValue();
                if (item != null && item.icon() != null) {
                    Image scaled = item.icon().getImage()
                            .getScaledInstance(540, -1, Image.SCALE_SMOOTH);
                    preview.setIcon(new ImageIcon(scaled));
                }
            }
        });

        new SwingWorker<Void, Item>() {
            private long renderStart;
            private ImageIcon firstPage;

            @Override
            protected Void doInBackground() throws Exception {
                renderStart = System.nanoTime();
                try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
                    PDFRenderer renderer = new PDFRenderer(document);
                    int pages = document.getNumberOfPages();
                    BufferedImage first = renderer.renderImageWithDPI(0, 110);
                    firstPage = new ImageIcon(first);
                    for (int i = 0; i < THUMBS; i++) {
                        BufferedImage image = renderer.renderImageWithDPI(i % pages, 96);
                        publish(new Item("Page " + (i + 1),
                                new ImageIcon(scale(image, THUMB_WIDTH))));
                    }
                }
                return null;
            }

            @Override
            protected void process(List<Item> chunks) {
                for (Item item : chunks) {
                    model.addElement(item);
                }
            }

            @Override
            protected void done() {
                long renderMs = (System.nanoTime() - renderStart) / 1_000_000;
                preview.setIcon(firstPage);
                long rss = rssMb();
                status.setText(String.format("Ready — %d thumbnails", model.size()));
                if (bench) {
                    System.out.printf("SWING ui_ms=%d render_ms=%d rss_mb=%d%n",
                            uiMs, renderMs, rss);
                    Timer timer = new Timer(300, event -> System.exit(0));
                    timer.setRepeats(false);
                    timer.start();
                }
            }
        }.execute();
    }

    private static BufferedImage scale(BufferedImage source, int width) {
        int height = (int) Math.round(source.getHeight() * (width / (double) source.getWidth()));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    private static long rssMb() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                if (line.startsWith("VmRSS:")) {
                    return Long.parseLong(line.replaceAll("[^0-9]", "")) / 1024;
                }
            }
        } catch (Exception ignored) {
            // hors Linux, pas de mesure
        }
        return -1;
    }
}
