package com.petnose.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticFileResourceConfig implements WebMvcConfigurer {

    private final Path uploadBasePath;

    public StaticFileResourceConfig(@Value("${upload.base-path:/var/uploads}") String uploadBasePath) {
        this.uploadBasePath = Paths.get(uploadBasePath).normalize().toAbsolutePath();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/**")
                .addResourceLocations(uploadBasePath.toUri().toString());
    }
}
