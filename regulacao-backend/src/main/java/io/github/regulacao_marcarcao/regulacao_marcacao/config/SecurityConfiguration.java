package io.github.regulacao_marcarcao.regulacao_marcacao.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, 
            AuthenticationProvider authenticationProvider,
            JwtAuthenticationFilter jwtAuthFilter // Recebe o filtro como parâmetro
    ) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            // 1. Define a política de sessão como STATELESS (correto para JWT)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                // 2. Define as rotas públicas: login e actuator.
                auth.requestMatchers("/api/auth/**").permitAll();
                auth.requestMatchers("/actuator/**").permitAll();
                auth.requestMatchers("/api/transparencia/**").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/api/solicitacoes/public/**").permitAll();
                auth.requestMatchers( 
                    "/swagger-ui.html",
                                "/swagger-ui/index.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**").permitAll();
                // Fluxo de agendamento: busca pendentes e detalhes de solicitação
                auth.requestMatchers(HttpMethod.GET, "/api/agendamentos/pendentes/buscar").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/api/agendamentos/pendentes/**").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/api/solicitacoes/buscar/**").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/pactos/convites/*/responder").permitAll();
                auth.requestMatchers("/api/registry/**").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/registry/pactos/*/join-requests").permitAll();
                // 3. Todas as outras requisições exigem autenticação
                auth.anyRequest().authenticated();
            })
            .authenticationProvider(authenticationProvider)
            // 4. Adiciona nosso filtro JWT para ser executado
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // 5. REMOVIDO: .formLogin(...) não é usado com JWT
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService, // O Spring injeta seu AuthenticationService
            PasswordEncoder passwordEncoder        // O Spring injeta o PasswordEncoder
    ) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
