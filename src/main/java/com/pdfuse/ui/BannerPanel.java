package com.pdfuse.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.Timer;

/** Bandeau illustré, avec un reflet animé pendant le merge. */
final class BannerPanel extends JComponent {

    private static final int HEIGHT = 124;

    private final BufferedImage image;
    private final Timer animation;
    private double phase;
    private boolean busy;

    BannerPanel() {
        image = loadImage();
        setPreferredSize(new Dimension(0, HEIGHT));
        setMinimumSize(new Dimension(0, 90));

        animation = new Timer(33, event -> {
            phase += 0.022;
            if (phase > 1.0) {
                phase = 0;
            }
            repaint();
        });
        animation.setCoalesce(true);
    }

    private static BufferedImage loadImage() {
        try (InputStream stream = BannerPanel.class.getResourceAsStream("/ui/banner.png")) {
            return stream == null ? null : ImageIO.read(stream);
        } catch (IOException e) {
            return null;
        }
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        if (busy) {
            phase = 0;
            animation.start();
        } else {
            animation.stop();
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (image == null) {
            g.setColor(new Color(0xF2F4F8));
            g.fillRoundRect(0, 0, width, height, 20, 20);
            g.dispose();
            return;
        }

        double scale = Math.min(width / (double) image.getWidth(),
                height / (double) image.getHeight());
        int imageWidth = (int) Math.round(image.getWidth() * scale);
        int imageHeight = (int) Math.round(image.getHeight() * scale);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        Shape clip = new RoundRectangle2D.Float(x, y, imageWidth, imageHeight, 20, 20);
        g.setClip(clip);
        g.drawImage(image, x, y, imageWidth, imageHeight, null);

        if (busy) {
            int band = Math.max(120, imageWidth / 5);
            int start = (int) (-band + (imageWidth + 2.0 * band) * phase);
            int middle = start + band / 2;
            Color transparent = new Color(255, 255, 255, 0);
            Color shine = new Color(255, 255, 255, 150);

            g.setPaint(new GradientPaint(start, 0, transparent, middle, 0, shine));
            g.fillRect(start, y, band / 2 + 1, imageHeight);
            g.setPaint(new GradientPaint(middle, 0, shine, start + band, 0, transparent));
            g.fillRect(middle, y, band / 2 + 1, imageHeight);
        }

        g.dispose();
    }
}
