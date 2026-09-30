package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Autoridade central sobre "de qual unidade este usuário pode ver/alterar dados".
 *
 * Antes da v1.6 essa decisão estava duplicada em cada service (um
 * {@code role.name().equals("ADMIN")} solto em SolicitacaoService e outro em
 * AgendamentoService). Com a entrada do perfil {@link Roles#ADMIN_UNIDADE} — que
 * tem poderes de administrador mas só dentro da própria unidade de lotação —
 * essa regra passou a valer para vários fluxos, então ela vive num lugar só.
 *
 * Regras:
 * <ul>
 *   <li>{@link Roles#ADMIN} — acesso global, sem filtro por unidade.</li>
 *   <li>{@link Roles#ADMIN_UNIDADE} — <b>sempre</b> restrito à unidade de lotação.
 *       Se o usuário não tiver unidade vinculada, o acesso é negado em vez de
 *       silenciosamente virar global (seria uma escalada de privilégio).</li>
 *   <li>Demais perfis — restritos à unidade de lotação quando houver; sem unidade
 *       vinculada mantêm o comportamento histórico de acesso global.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UnidadeAcessoService {

    private final UserRepository userRepository;

    /**
     * Contexto de unidade do chamador. {@code id == null} significa acesso global
     * (sem filtro). Qualquer outro valor é o id da unidade a que o acesso se limita.
     */
    public record UnidadeContexto(Long id) {
        public static final UnidadeContexto GLOBAL = new UnidadeContexto(null);

        public boolean isGlobal() {
            return id == null;
        }

        /** True se o contexto permite enxergar a unidade informada. */
        public boolean permite(Long unidadeId) {
            return isGlobal() || (unidadeId != null && id.equals(unidadeId));
        }
    }

    @Transactional(readOnly = true)
    public UnidadeContexto contextoDe(String cpf) {
        if (cpf == null) {
            return UnidadeContexto.GLOBAL;
        }
        return userRepository.findByCpf(cpf)
                .map(this::contextoDe)
                .orElse(UnidadeContexto.GLOBAL);
    }

    public UnidadeContexto contextoDe(User user) {
        if (user == null) {
            return UnidadeContexto.GLOBAL;
        }
        Roles perfil = perfilEfetivo(user);
        if (perfil == null) {
            return UnidadeContexto.GLOBAL;
        }
        if (perfil == Roles.ADMIN) {
            return UnidadeContexto.GLOBAL;
        }
        // GESTOR acompanha o desempenho COMPARANDO unidades, então é global por
        // definição — inclusive quando tem unidade de lotação, que para ele é só
        // lotação administrativa e não deve estreitar o que ele enxerga.
        if (perfil == Roles.GESTOR) {
            return UnidadeContexto.GLOBAL;
        }
        if (user.getUnidade() != null) {
            return new UnidadeContexto(user.getUnidade().getId());
        }
        // ADMIN_UNIDADE sem unidade de lotação não pode cair no caminho global.
        if (perfil == Roles.ADMIN_UNIDADE) {
            throw new AccessDeniedException(
                    "Usuário com perfil de Administrador da Unidade não possui unidade de lotação vinculada. "
                            + "Solicite ao administrador do sistema o vínculo da sua unidade.");
        }
        return UnidadeContexto.GLOBAL;
    }

    /**
     * Perfil que vale para esta requisição.
     *
     * Com a alternância de perfis (v1.7), o escopo tem que seguir o perfil que a
     * pessoa está USANDO agora, não o principal gravado em `usuarios.cargo`: quem
     * tem ADMIN e ADMIN_UNIDADE e alterna para ADMIN_UNIDADE precisa ficar
     * restrito à própria unidade de verdade, senão a alternância seria decorativa.
     *
     * O perfil ativo chega pelas autoridades que o {@code JwtAuthenticationFilter}
     * montou a partir do token. Ainda assim é reconferido contra os perfis
     * concedidos — nunca se confia só no que veio na requisição.
     *
     * Fora de uma requisição autenticada (testes, chamadas internas), cai no
     * perfil principal, que é o comportamento anterior a esta versão.
     */
    private Roles perfilEfetivo(User user) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            for (GrantedAuthority autoridade : auth.getAuthorities()) {
                String nome = autoridade.getAuthority();
                if (nome != null && nome.startsWith("ROLE_")) {
                    nome = nome.substring("ROLE_".length());
                }
                for (Roles concedido : user.getPerfisConcedidos()) {
                    if (concedido.name().equals(nome)) {
                        return concedido;
                    }
                }
            }
        }
        return user.getRole();
    }

    /** True quando o chamador não está sujeito a filtro/cota por unidade. */
    @Transactional(readOnly = true)
    public boolean isAcessoGlobal(String cpf) {
        return contextoDe(cpf).isGlobal();
    }

    /**
     * True especificamente para o perfil ADMIN_UNIDADE — usado onde a regra não é
     * "global ou não" (que também incluiria GESTOR), mas depende do perfil exato,
     * como o bloqueio de agendamento sem cota liberada.
     */
    @Transactional(readOnly = true)
    public boolean isAdminUnidade(String cpf) {
        if (cpf == null) {
            return false;
        }
        return userRepository.findByCpf(cpf)
                .map(user -> perfilEfetivo(user) == Roles.ADMIN_UNIDADE)
                .orElse(false);
    }

    /**
     * Garante que o chamador pode operar sobre a unidade informada.
     * Usado nos endpoints que recebem um {@code unidadeId} vindo do request —
     * sem isto, um ADMIN_UNIDADE poderia ler dados de outra unidade trocando o
     * parâmetro na chamada direta à API.
     */
    @Transactional(readOnly = true)
    public void exigirAcessoA(String cpf, Long unidadeId) {
        UnidadeContexto ctx = contextoDe(cpf);
        if (!ctx.permite(unidadeId)) {
            throw new AccessDeniedException("Acesso negado aos dados de outra unidade.");
        }
    }

    /**
     * Resolve qual unidade deve ser usada numa operação: o chamador restrito a uma
     * unidade sempre opera na sua própria, mesmo que envie outra no corpo da requisição.
     */
    @Transactional(readOnly = true)
    public Long resolverUnidadeAlvo(String cpf, Long unidadeSolicitada) {
        UnidadeContexto ctx = contextoDe(cpf);
        if (ctx.isGlobal()) {
            return unidadeSolicitada;
        }
        if (unidadeSolicitada != null && !ctx.permite(unidadeSolicitada)) {
            throw new AccessDeniedException("Acesso negado aos dados de outra unidade.");
        }
        return ctx.id();
    }
}
