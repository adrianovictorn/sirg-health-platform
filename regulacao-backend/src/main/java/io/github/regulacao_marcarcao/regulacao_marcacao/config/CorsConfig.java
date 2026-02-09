// Arquivo: regulacao-backend/src/main/java/io/github/regulacao_marcarcao/regulacao_marcacao/config/CorsConfig.java
package io.github.regulacao_marcarcao.regulacao_marcacao.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);

        config.addAllowedOrigin("http://localhost:5173");
        config.addAllowedOrigin("https://sirg.ddns.net"); // Para produção
        config.addAllowedOrigin("http://192.168.1.181:5173");
        config.addAllowedOrigin("http://192.168.1.38:5173");
        config.addAllowedOrigin("http://192.168.0.101:5173");

        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
