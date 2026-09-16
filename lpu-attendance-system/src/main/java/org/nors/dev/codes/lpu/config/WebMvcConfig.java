package org.nors.dev.codes.lpu.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(UploadProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    public WebMvcConfig(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // Backup zip streaming can take a long time on large attendance + media sets.
        configurer.setDefaultTimeout(3_600_000L);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path picturesPath = Paths.get(uploadProperties.getPicturesDir()).toAbsolutePath().normalize();
        String location = picturesPath.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/pictures/**")
                .addResourceLocations(location)
                .setCacheControl(mediaCacheControl());

        Path videosPath = Paths.get(uploadProperties.getVideosDir()).toAbsolutePath().normalize();
        String videosLocation = videosPath.toUri().toString();
        if (!videosLocation.endsWith("/")) {
            videosLocation = videosLocation + "/";
        }
        registry.addResourceHandler("/videos/**")
                .addResourceLocations(videosLocation)
                .setCacheControl(mediaCacheControl());

        Path tonesPath = Paths.get(uploadProperties.getTonesDir()).toAbsolutePath().normalize();
        String tonesLocation = tonesPath.toUri().toString();
        if (!tonesLocation.endsWith("/")) {
            tonesLocation = tonesLocation + "/";
        }
        registry.addResourceHandler("/tones/**")
                .addResourceLocations(tonesLocation)
                .setCacheControl(mediaCacheControl());
    }

    /** UUID media filenames never change in place, so kiosks can keep the file locally. */
    private static CacheControl mediaCacheControl() {
        return CacheControl.maxAge(Duration.ofDays(365)).cachePublic();
    }
}
