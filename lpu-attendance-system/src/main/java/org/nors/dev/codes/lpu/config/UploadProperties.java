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
}
