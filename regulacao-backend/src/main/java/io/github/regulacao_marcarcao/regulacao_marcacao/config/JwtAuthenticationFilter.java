package io.github.regulacao_marcarcao.regulacao_marcacao.config;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        
        // 1. Recupera o token do cabeçalho da requisição
        var token = this.recoverToken(request);

        // 2. Se um token foi encontrado...
        if (token != null) {
            // 3. Valida o token e pega o CPF (subject)
            var subject = tokenService.validateToken(token);

            // 4. Se o token for válido, busca o usuário no banco de dados
            User user = userRepository.findByCpf(subject).orElse(null);

            // Only authenticate users whose account is active (enabled)
            if (user != null && user.isEnabled()) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, autoridadesDoPerfilAtivo(user, token));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        
        // 7. Continua a cadeia de filtros. Se o usuário não foi autenticado acima,
        //    o acesso será negado posteriormente pelo Spring Security.
        filterChain.doFilter(request, response);
    }

    /**
     * Autoridades da requisição: o perfil ATIVO declarado no token, e não
     * necessariamente o perfil principal gravado em `usuarios.cargo`.
     *
     * O perfil do token só vale se ainda estiver entre os perfis concedidos ao
     * usuário — por isso a conferência acontece aqui, a cada requisição, e não
     * na emissão. Assim, tirar um perfil de alguém tem efeito imediato, sem
     * depender de o token expirar ou de a pessoa sair e entrar de novo.
     *
     * Sem claim de perfil (token emitido antes da v1.7) ou com perfil revogado,
     * cai no principal — o comportamento anterior, preservado.
     */
    private Collection<? extends GrantedAuthority> autoridadesDoPerfilAtivo(User user, String token) {
        Roles perfilAtivo = tokenService.getPerfilAtivo(token);

        if (perfilAtivo != null && user.podeAssumir(perfilAtivo)) {
            return List.of(new SimpleGrantedAuthority("ROLE_" + perfilAtivo.name()));
        }
        return user.getAuthorities();
    }

    /**
     * Método auxiliar para extrair o token do cabeçalho "Authorization".
     */
    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        // Remove o prefixo "Bearer " para obter apenas o token
        return authHeader.replace("Bearer ", "");
    }
}