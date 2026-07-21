package org.nors.dev.codes.lpu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nors.dev.codes.lpu.config.UploadProperties;

class ImageOptimizationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void resizeScalesDownLargeImages() {
        BufferedImage source = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 1600, 1200);
        graphics.dispose();

        BufferedImage resized = ImageOptimizationService.resize(source, 800);

        assertEquals(800, resized.getWidth());
        assertEquals(600, resized.getHeight());
    }

    @Test
    void optimizeToJpegWritesSmallerFile(@TempDir Path outputDir) throws Exception {
        UploadProperties properties = new UploadProperties();
        properties.setPhotoOptimizationEnabled(true);
        properties.setPhotoMaxDimension(400);
        properties.setPhotoJpegQuality(0.75f);
        ImageOptimizationService service = new ImageOptimizationService(properties);

        BufferedImage source = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 1200, 900);
        graphics.dispose();

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        ImageIO.write(source, "png", raw);

        Path destination = outputDir.resolve("optimized.jpg");
        boolean optimized = service.optimizeToJpeg(new ByteArrayInputStream(raw.toByteArray()), destination);

        assertTrue(optimized);
        assertTrue(Files.exists(destination));
        assertTrue(Files.size(destination) < raw.size());
    }
}
