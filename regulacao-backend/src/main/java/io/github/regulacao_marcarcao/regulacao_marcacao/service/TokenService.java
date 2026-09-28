package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;

@Service
public class TokenService {
   
    @Value("${api.security.token.secret}")
    private String secret;

    /** Token com o perfil principal do usuario — o caminho do login. */
    public String generateToken(User usuario){
        return generateToken(usuario, usuario.getRole());
    }

    /**
     * Token com um perfil ativo especifico — o caminho da alternancia de perfil.
     *
     * O perfil ativo vai no claim `perfilAtivo`; `role` continua sendo emitido
     * com o mesmo valor porque o frontend antigo le esse campo. Quem autoriza de
     * fato e o {@code JwtAuthenticationFilter}, que reconfere o perfil contra os
     * perfis concedidos ao usuario a cada requisicao — o claim sozinho nao da
     * acesso a nada.
     */
    public String generateToken(User usuario, Roles perfilAtivo){
        try{
            Algorithm algorithm = Algorithm.HMAC256(secret);

            Roles perfil = perfilAtivo != null ? perfilAtivo : usuario.getRole();

            String token = JWT.create()
            .withIssuer("regulacao-api")
            .withSubject(usuario.getCpf())
            .withClaim("role", perfil.name())
            .withClaim("perfilAtivo", perfil.name())
            .withClaim("nome", usuario.getNome())
            .withExpiresAt(genExpirationDate())
            .sign(algorithm);
            return token;
        }catch(JWTCreationException exception){
            throw new RuntimeException("Erro ao gerar Token", exception);
        }

    }

    public String validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                .withIssuer("regulacao-api") 
                .build()
                .verify(token) 
                .getSubject(); 
        } catch (JWTVerificationException exception) {
            return "";
        }
    }

    /**
     * Perfil ativo declarado no token, ou {@code null}.
     *
     * Devolve nulo para token invalido, para token emitido antes da v1.7 (que
     * nao tem o claim) e para valor que nao corresponde a nenhum perfil — em
     * todos esses casos o chamador cai no perfil principal do usuario.
     */
    public Roles getPerfilAtivo(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            String valor = JWT.require(algorithm)
                .withIssuer("regulacao-api")
                .build()
                .verify(token)
                .getClaim("perfilAtivo")
                .asString();

            return valor == null ? null : Roles.valueOf(valor);
        } catch (JWTVerificationException | IllegalArgumentException exception) {
            return null;
        }
    }

    private Instant genExpirationDate() {
        return LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.of("-03:00"));
    }
}
