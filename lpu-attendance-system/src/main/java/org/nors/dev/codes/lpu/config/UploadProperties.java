package org.nors.dev.codes.lpu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /**
     * Directory on disk where student photos are stored.
     * Override with APP_PICTURES_DIR (e.g. /usr/local/tomcat/pictures).
     */
    private String picturesDir = "pictures";

    /**
     * Directory on disk where guard display videos are stored.
     * Override with APP_VIDEOS_DIR (e.g. /usr/local/tomcat/videos).
     */
    private String videosDir = "videos";

    /**
     * Directory on disk where gate event tones are stored.
     * Override with APP_TONES_DIR (e.g. /usr/local/tomcat/tones).
     */
    private String tonesDir = "tones";

    /** Resize and re-encode uploaded profile photos to JPEG. */
    private boolean photoOptimizationEnabled = true;

    /** Longest edge in pixels for stored profile photos. */
    private int photoMaxDimension = 960;

    /** JPEG quality from 0.1 (smallest) to 1.0 (largest). */
    private float photoJpegQuality = 0.84f;

    /** Transcode guard videos to H.264 MP4 when FFmpeg is installed. */
    private boolean videoOptimizationEnabled = true;

    /** Maximum output width in pixels for transcoded videos. */
    private int videoMaxWidth = 1280;

    /** FFmpeg constant rate factor (lower = higher quality, larger files). */
    private int videoCrf = 28;

    /** FFmpeg binary name or absolute path. */
    private String ffmpegPath = "ffmpeg";

    public String getPicturesDir() {
        return picturesDir;
    }

    public void setPicturesDir(String picturesDir) {
        this.picturesDir = picturesDir;
    }

    public String getVideosDir() {
        return videosDir;
    }

    public void setVideosDir(String videosDir) {
        this.videosDir = videosDir;
    }

    public String getTonesDir() {
        return tonesDir;
    }

    public void setTonesDir(String tonesDir) {
        this.tonesDir = tonesDir;
    }

    public boolean isPhotoOptimizationEnabled() {
        return photoOptimizationEnabled;
    }

    public void setPhotoOptimizationEnabled(boolean photoOptimizationEnabled) {
        this.photoOptimizationEnabled = photoOptimizationEnabled;
    }

    public int getPhotoMaxDimension() {
        return photoMaxDimension;
    }

    public void setPhotoMaxDimension(int photoMaxDimension) {
        this.photoMaxDimension = photoMaxDimension;
    }

    public float getPhotoJpegQuality() {
        return photoJpegQuality;
    }

    public void setPhotoJpegQuality(float photoJpegQuality) {
        this.photoJpegQuality = photoJpegQuality;
    }

    public boolean isVideoOptimizationEnabled() {
        return videoOptimizationEnabled;
    }

    public void setVideoOptimizationEnabled(boolean videoOptimizationEnabled) {
        this.videoOptimizationEnabled = videoOptimizationEnabled;
    }

    public int getVideoMaxWidth() {
        return videoMaxWidth;
    }

    public void setVideoMaxWidth(int videoMaxWidth) {
        this.videoMaxWidth = videoMaxWidth;
    }

    public int getVideoCrf() {
        return videoCrf;
    }

    public void setVideoCrf(int videoCrf) {
        this.videoCrf = videoCrf;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }
}
