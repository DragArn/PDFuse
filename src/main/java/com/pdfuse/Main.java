package com.pdfuse;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Point d'entrée CLI : pdfuse &lt;input.pdf&gt;... -o &lt;output.pdf&gt; [--force] */
public final class Main {

    private static final String VERSION = "0.6.0";

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--gui")) {
            com.pdfuse.ui.PdfUseApp.main(new String[0]);
            return;
        }
        if (args.length == 0) {
            usage(System.out);
            return;
        }

        List<String> files = new ArrayList<>();
        String output = null;
        String password = null;
        boolean force = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> {
                    usage(System.out);
                    return;
                }
                case "-V", "--version" -> {
                    System.out.println("pdfuse " + VERSION);
                    return;
                }
                case "-f", "--force" -> force = true;
                case "-o", "--output" -> {
                    if (i + 1 >= args.length) {
                        fail("missing value for " + arg, 2);
                    }
                    if (output != null) {
                        fail("output specified more than once", 2);
                    }
                    output = args[++i];
                }
                case "--password" -> {
                    if (i + 1 >= args.length) {
                        fail("missing value for --password", 2);
                    }
                    if (password != null) {
                        fail("password specified more than once", 2);
                    }
                    password = args[++i];
                }
                default -> {
                    if (arg.startsWith("-")) {
                        fail("unknown option: " + arg, 2);
                    }
                    files.add(arg);
                }
            }
        }
        if (output == null) {
            fail("missing -o <output.pdf>", 2);
        }
        if (files.size() < 2) {
            fail("at least two inputs are required", 2);
        }

        try {
            PdfMerger.Result result = MergeController.merge(files, Path.of(output), force, password);
            System.out.printf("merged %d files, %d pages -> %s (%s, %d ms)%n",
                    result.inputs(), result.pages(), result.output(),
                    humanSize(result.bytes()), result.millis());
        } catch (MergeException e) {
            fail(e.getMessage(), 1);
        }
    }

    private static void fail(String message, int code) {
        System.err.println("pdfuse: " + message);
        if (code == 2) {
            System.err.println("usage: pdfuse <input[:pages]> <input[:pages]> [...] -o <output.pdf> [--force]");
        }
        System.exit(code);
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

    private static void usage(java.io.PrintStream out) {
        out.println("pdfuse " + VERSION + " - merge PDF files and images");
        out.println();
        out.println("usage: pdfuse <input[:pages]> <input[:pages]> [...] -o <output.pdf> [--force]");
        out.println();
        out.println("inputs may be PDFs or images (jpg, png, gif, bmp, tif, tiff)");
        out.println("page ranges are 1-based, e.g. report.pdf:2-5,8");
        out.println();
        out.println("  -o, --output    merged output file (required)");
        out.println("  -f, --force     overwrite an existing output file");
        out.println("      --password  protect the output with AES-256");
        out.println("      --gui       open the graphical interface");
        out.println("  -h, --help      show this help");
        out.println("  -V, --version   show the version");
    }
}
