package org.nors.dev.codes.lpu.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.config.UploadProperties;
import org.springframework.stereotype.Service;

/**
 * Resizes and re-encodes profile photos to JPEG for smaller storage and faster gate display loads.
 */
@Service
public class ImageOptimizationService {

    private static final Logger log = LogManager.getLogger(ImageOptimizationService.class);
    private static final Set<String> OPTIMIZABLE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final boolean enabled;
    private final int maxDimension;
    private final float jpegQuality;

    public ImageOptimizationService(UploadProperties uploadProperties) {
        this.enabled = uploadProperties.isPhotoOptimizationEnabled();
        this.maxDimension = Math.max(uploadProperties.getPhotoMaxDimension(), 64);
        this.jpegQuality = clampQuality(uploadProperties.getPhotoJpegQuality());
    }

    public boolean canOptimize(String contentType) {
        return enabled
                && contentType != null
                && OPTIMIZABLE_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
    }

    /**
     * Reads an image, scales it down if needed, and writes an optimized JPEG to {@code destination}.
     *
     * @return true when optimization succeeded
     */
    public boolean optimizeToJpeg(InputStream input, Path destination) {
        if (!enabled) {
            return false;
        }
        try {
            BufferedImage source = ImageIO.read(input);
            if (source == null) {
                return false;
            }
            BufferedImage resized = resize(source, maxDimension);
            BufferedImage rgb = stripAlpha(resized);
            writeJpeg(rgb, destination, jpegQuality);
            log.debug(
                    "Optimized image {}x{} -> {} bytes",
                    source.getWidth(),
                    source.getHeight(),
                    Files.size(destination)
            );
            return true;
        } catch (IOException | RuntimeException ex) {
            log.warn("Photo optimization failed, caller should store original: {}", ex.toString());
            try {
                Files.deleteIfExists(destination);
            } catch (IOException ignored) {
                // Best-effort cleanup of partial output.
            }
            return false;
        }
    }

    static BufferedImage resize(BufferedImage source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= maxDimension && height <= maxDimension) {
            return source;
        }
        double scale = Math.min((double) maxDimension / width, (double) maxDimension / height);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        int imageType = source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return scaled;
    }

    static BufferedImage stripAlpha(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return rgb;
    }

    static void writeJpeg(BufferedImage image, Path destination, float quality) throws IOException {
        Files.createDirectories(destination.getParent());
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG ImageWriter available");
        }
        ImageWriter writer = writers.next();
        try (OutputStream output = Files.newOutputStream(destination);
                ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            // Progressive JPEG paints sooner at the gate while the rest of the image loads.
            if (params.canWriteProgressive()) {
                params.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    private static float clampQuality(float quality) {
        if (quality < 0.1f) {
            return 0.1f;
        }
        if (quality > 1.0f) {
            return 1.0f;
        }
        return quality;
    }
}
