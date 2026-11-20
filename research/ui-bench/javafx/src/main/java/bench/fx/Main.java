package bench.fx;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Prototype de benchmark JavaFX. */
public final class Main {

    private static final long START = System.nanoTime();

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("usage: Main <pdf> [--bench]");
            System.exit(2);
        }
        BenchApp.pdf = Path.of(args[0]);
        BenchApp.bench = args.length > 1 && args[1].equals("--bench");
        Application.launch(BenchApp.class);
    }

    public static final class BenchApp extends Application {

        static Path pdf;
        static boolean bench;

        private record Item(String name, Image image) {
        }

        @Override
        public void start(Stage stage) {
            ListView<Item> list = new ListView<>();
            list.setPrefWidth(360);
            list.setFixedCellSize(90);
            list.setCellFactory(view -> new ListCell<>() {
                private final ImageView image = new ImageView();

                @Override
                protected void updateItem(Item item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    image.setImage(item.image());
                    image.setFitWidth(120);
                    image.setPreserveRatio(true);
                    setText(item.name());
                    setGraphic(image);
                }
            });

            ImageView preview = new ImageView();
            preview.setPreserveRatio(true);
            preview.setFitWidth(620);
            StackPane previewPane = new StackPane(preview);
            previewPane.setPadding(new Insets(8));
            previewPane.setStyle("-fx-background-color: #f4f4f4;");

            Button add = new Button("Add files");
            Button merge = new Button("Merge");
            HBox top = new HBox(8, add, merge);
            top.setPadding(new Insets(8));

            Label status = new Label("Ready");
            status.setPadding(new Insets(4, 8, 4, 8));

            BorderPane root = new BorderPane();
            root.setTop(top);
            root.setLeft(list);
            root.setCenter(previewPane);
            root.setBottom(status);

            stage.setTitle("PDFuse — JavaFX benchmark");
            stage.setScene(new Scene(root, 1100, 700));
            stage.show();
            long uiMs = (System.nanoTime() - START) / 1_000_000;

            list.setOnDragOver(event -> {
                if (event.getDragboard().hasFiles()) {
                    event.acceptTransferModes(TransferMode.COPY);
                }
                event.consume();
            });
            list.setOnDragDropped(event -> {
                if (event.getDragboard().hasFiles()) {
                    for (File file : event.getDragboard().getFiles()) {
                        list.getItems().add(new Item(file.getName(), null));
                    }
                    event.setDropCompleted(true);
                }
                event.consume();
            });

            add.setOnAction(event -> {
                FileChooser chooser = new FileChooser();
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("PDF", "*.pdf"));
                List<File> files = chooser.showOpenMultipleDialog(stage);
                if (files != null) {
                    for (File file : files) {
                        list.getItems().add(new Item(file.getName(), null));
                    }
                }
            });
            merge.setOnAction(event -> status.setText("merge not implemented in benchmark"));

            Task<Void> task = new Task<>() {
                private long renderStart;

                @Override
                protected Void call() throws Exception {
                    renderStart = System.nanoTime();
                    try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
                        PDFRenderer renderer = new PDFRenderer(document);
                        int pages = document.getNumberOfPages();
                        BufferedImage first = renderer.renderImageWithDPI(0, 110);
                        Image firstImage = SwingFXUtils.toFXImage(first, null);
                        Platform.runLater(() -> preview.setImage(firstImage));
                        for (int i = 0; i < 20; i++) {
                            BufferedImage image = renderer.renderImageWithDPI(i % pages, 96);
                            Image fxImage = SwingFXUtils.toFXImage(scale(image, 120), null);
                            int number = i + 1;
                            Platform.runLater(() -> list.getItems().add(
                                    new Item("Page " + number, fxImage)));
                        }
                    }
                    return null;
                }

                @Override
                protected void succeeded() {
                    long renderMs = (System.nanoTime() - renderStart) / 1_000_000;
                    long rss = rssMb();
                    status.setText("Ready — " + list.getItems().size() + " thumbnails");
                    if (bench) {
                        System.out.printf("JAVAFX ui_ms=%d render_ms=%d rss_mb=%d%n",
                                uiMs, renderMs, rss);
                        new Thread(() -> {
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                            Platform.exit();
                        }, "exit").start();
                    }
                }
            };
            Thread thread = new Thread(task, "render");
            thread.setDaemon(true);
            thread.start();
        }

        private static BufferedImage scale(BufferedImage source, int width) {
            int height = (int) Math.round(
                    source.getHeight() * (width / (double) source.getWidth()));
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
                // hors Linux
            }
            return -1;
        }
    }
}
