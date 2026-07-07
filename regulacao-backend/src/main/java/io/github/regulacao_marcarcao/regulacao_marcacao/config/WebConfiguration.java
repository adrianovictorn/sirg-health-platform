package io.github.regulacao_marcarcao.regulacao_marcacao.config;

import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

@Configuration
@EnableWebMvc
public class WebConfiguration implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads/profile-pictures}")
    private String uploadDir;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/login")
                .setViewName("forward:/login.html");
        registry.addViewController("/index")
                .setViewName("forward:/index.html");
        registry.addViewController("/dashboard")
                .setViewName("forward:/index.html");
        registry.addViewController("/cadastrarsolicitacao")
                .setViewName("forward:/cadastrarsolicitacao.html");
        registry.addViewController("/listar")
                .setViewName("forward:/lista-solicitacoes.html");
        registry.addViewController("/logout")
                .setViewName("forward:/login.html");
        registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve uploaded profile pictures from the filesystem.
        // uploadDir may be relative (dev, resolved against the working dir) or
        // absolute (prod/Docker, e.g. APP_UPLOAD_DIR=/app/uploads/profile-pictures) —
        // toAbsolutePath() only prepends user.dir when the path isn't already absolute,
        // so this matches exactly what FileStorageService uses to write the files.
        String uploadLocation = "file:" + Paths.get(uploadDir).toAbsolutePath().normalize() + "/";
        registry.addResourceHandler("/api/uploads/profile-pictures/**")
                .addResourceLocations(uploadLocation);

        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");

        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/");

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

    @Bean
    public InternalResourceViewResolver internalResourceViewResolver() {
        InternalResourceViewResolver resolver = new InternalResourceViewResolver();
        resolver.setPrefix("/");
        resolver.setSuffix("");
        resolver.setOrder(Ordered.LOWEST_PRECEDENCE);
        return resolver;
    }
}
