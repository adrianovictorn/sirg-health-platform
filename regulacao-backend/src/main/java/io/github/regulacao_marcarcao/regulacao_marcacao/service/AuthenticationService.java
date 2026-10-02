package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationService implements UserDetailsService {
    private final UserRepository userRepository;

    /**
     * RF06: aceita CPF com ou sem pontuacao. Busca pelo valor exato primeiro —
     * preserva logins nao-numericos (ex.: sirg_adm) e evita uma segunda
     * consulta quando o valor ja bate — e so tenta a versao normalizada (so
     * digitos) se a exata nao encontrar nada. Nunca normaliza incondicionalmente:
     * remover tudo que nao e digito de "sirg_adm" resultaria em string vazia.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByCpf(username)
                .or(() -> {
                    String normalizado = username == null ? "" : username.replaceAll("\\D", "");
                    if (normalizado.isEmpty() || normalizado.equals(username)) {
                        return java.util.Optional.empty();
                    }
                    return userRepository.findByCpf(normalizado);
                })
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não existe!"));
    }
    
}
