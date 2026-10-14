package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UsuarioUnidadeExecutanteRepository;
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
    private final UsuarioUnidadeExecutanteRepository usuarioUnidadeExecutanteRepository;

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
        if (isPerfilGlobal(perfil)) {
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
     * Perfis que enxergam todas as unidades por definição, com ou sem lotação.
     *
     * ADMIN administra o município. GESTOR acompanha o desempenho COMPARANDO
     * unidades — a unidade de lotação dele é só administrativa e não deve
     * estreitar o que ele enxerga. Predicado único, usado por {@link #contextoDe}
     * e por {@link #escopoDeListagem}, para as duas regras não divergirem.
     */
    private boolean isPerfilGlobal(Roles perfil) {
        return perfil == Roles.ADMIN || perfil == Roles.GESTOR;
    }

    /**
     * Escopo de uma LISTAGEM ou CONTAGEM nominal de pacientes (fila de espera,
     * resumo do dashboard e as listas que os cards abrem).
     *
     * Diferente de {@link UnidadeContexto}: aqui quem não tem unidade de lotação
     * não cai em "global" — fica sem acesso, em vez de ver o município inteiro.
     */
    public record EscopoListagem(Tipo tipo, Long unidadeId) {
        public enum Tipo { GLOBAL, UNIDADE, SEM_ACESSO }

        public static EscopoListagem global() {
            return new EscopoListagem(Tipo.GLOBAL, null);
        }

        public static EscopoListagem unidade(Long unidadeId) {
            return new EscopoListagem(Tipo.UNIDADE, unidadeId);
        }

        public static EscopoListagem semAcesso() {
            return new EscopoListagem(Tipo.SEM_ACESSO, null);
        }

        public boolean isGlobal() {
            return tipo == Tipo.GLOBAL;
        }

        public boolean isSemAcesso() {
            return tipo == Tipo.SEM_ACESSO;
        }
    }

    /**
     * Resolve o que o chamador pode LISTAR. Fronteira com o restante da classe:
     * listagem e contagem nominal usam este método; escrita e acesso por id
     * continuam em {@link #contextoDe}, {@link #resolverUnidadeAlvo} e
     * {@link #exigirAcessoA}.
     *
     * <ul>
     *   <li>ADMIN e GESTOR — todas as unidades, ou só a {@code unidadeSolicitada}
     *       quando informada.</li>
     *   <li>Demais perfis com lotação — sempre a própria unidade; pedir outra é
     *       negado (403), nunca atendido em silêncio.</li>
     *   <li>Demais perfis sem lotação — sem acesso (lista vazia, contagem zero).
     *       Exceção: COORD_TRANSPORTE, que opera o transporte do município inteiro
     *       e mantém a visão global que já tinha.</li>
     *   <li>CPF nulo ou usuário inexistente — sem acesso (falha fechada, ao
     *       contrário de {@link #contextoDe}).</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public EscopoListagem escopoDeListagem(String cpf, Long unidadeSolicitada) {
        if (cpf == null) {
            return EscopoListagem.semAcesso();
        }
        User user = userRepository.findByCpf(cpf).orElse(null);
        if (user == null) {
            return EscopoListagem.semAcesso();
        }
        Roles perfil = perfilEfetivo(user);
        boolean semLotacao = user.getUnidade() == null;
        if (isPerfilGlobal(perfil) || (perfil == Roles.COORD_TRANSPORTE && semLotacao)) {
            return unidadeSolicitada == null
                    ? EscopoListagem.global()
                    : EscopoListagem.unidade(unidadeSolicitada);
        }
        if (semLotacao) {
            return EscopoListagem.semAcesso();
        }
        Long lotacao = user.getUnidade().getId();
        if (unidadeSolicitada != null && !lotacao.equals(unidadeSolicitada)) {
            throw new AccessDeniedException("Acesso negado aos dados de outra unidade.");
        }
        return EscopoListagem.unidade(lotacao);
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
     * True só para os perfis ADMIN e GESTOR (perfil ativo na requisição). Mais
     * estreito que {@link #isAcessoGlobal}, que também vale para quem não tem
     * unidade de lotação: usado pelo teto financeiro, onde só esses dois perfis
     * agendam sem ser barrados. CPF nulo ou usuário inexistente é false.
     */
    @Transactional(readOnly = true)
    public boolean isAdminOuGestor(String cpf) {
        if (cpf == null) {
            return false;
        }
        return userRepository.findByCpf(cpf)
                .map(user -> {
                    Roles perfil = perfilEfetivo(user);
                    return perfil != null && isPerfilGlobal(perfil);
                })
                .orElse(false);
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
     * Exige o perfil ADMIN exato (perfil ativo na requisição). Mais estreito que
     * {@link #isAcessoGlobal}, que também inclui GESTOR e usuários sem unidade de
     * lotação — usado em telas que concentram dados nominais de todas as unidades.
     */
    @Transactional(readOnly = true)
    public void exigirAdminGlobal(String cpf) {
        boolean admin = cpf != null && userRepository.findByCpf(cpf)
                .map(user -> perfilEfetivo(user) == Roles.ADMIN)
                .orElse(false);
        if (!admin) {
            throw new AccessDeniedException("Acesso restrito ao administrador.");
        }
    }

    /**
     * Exige o perfil de quem opera a tela de Atendimentos (perfil ativo na
     * requisição): USER — o operador da unidade executante — ou ADMIN.
     *
     * É uma dimensão à parte do escopo por unidade: o executante atende pacientes
     * vindos de qualquer unidade solicitante — o que ele vê é decidido pela
     * executante ({@link #escopoDeAtendimentos}), não pela unidade solicitante. Não altera
     * {@link #contextoDe} nem {@link #escopoDeListagem}: na fila, na ficha, nos
     * relatórios e no agendamento o USER continua restrito como antes.
     */
    @Transactional(readOnly = true)
    public void exigirOperadorDeAtendimentos(String cpf) {
        boolean operador = cpf != null && userRepository.findByCpf(cpf)
                .map(user -> {
                    Roles perfil = perfilEfetivo(user);
                    return perfil == Roles.USER || perfil == Roles.ADMIN;
                })
                .orElse(false);
        if (!operador) {
            throw new AccessDeniedException("Acesso restrito a quem opera os atendimentos.");
        }
    }

    /**
     * Exige o perfil USER exato (perfil ativo na requisição) — quem de fato lança
     * o desfecho do atendimento. Mais estreito que
     * {@link #exigirOperadorDeAtendimentos}: o ADMIN acompanha a tela, mas não
     * confirma, não registra falta, não cancela nem desfaz.
     */
    @Transactional(readOnly = true)
    public void exigirExecutorDeAtendimentos(String cpf) {
        boolean executor = cpf != null && userRepository.findByCpf(cpf)
                .map(user -> perfilEfetivo(user) == Roles.USER)
                .orElse(false);
        if (!executor) {
            throw new AccessDeniedException("Só o operador da unidade executante registra o atendimento.");
        }
    }

    /**
     * Recorte da tela de Atendimentos por unidade executante (V110). MUNICIPAL = sem
     * recorte; caso contrario, so os itens que pertencem a alguma das executantes
     * informadas — lista vazia = NENHUMA (nao ve nada, ADR 0013), nunca municipal.
     */
    public record EscopoAtendimento(boolean municipal, List<Long> executanteIds) {
        public static final EscopoAtendimento MUNICIPAL = new EscopoAtendimento(true, List.of());
        public static final EscopoAtendimento NENHUMA = new EscopoAtendimento(false, List.of());

        public EscopoAtendimento {
            executanteIds = executanteIds == null ? List.of() : List.copyOf(executanteIds);
        }

        /** Restrito a estas executantes; lista vazia = nenhuma. */
        public EscopoAtendimento(List<Long> executanteIds) {
            this(false, executanteIds);
        }

        public boolean isMunicipal() {
            return municipal;
        }

        public boolean isNenhuma() {
            return !municipal && executanteIds.isEmpty();
        }
    }

    /**
     * O que o chamador enxerga na tela de Atendimentos (resumo, lista, relatório e
     * agendas do menu). Complementa {@link #exigirOperadorDeAtendimentos}, que
     * continua decidindo QUEM entra.
     *
     * <ul>
     *   <li>USER (perfil ativo) — só os itens das executantes CONFIGURADAS dele:
     *       a unidade de lotação, se for executante configurada, mais as da lista
     *       de operadores. Sem nenhuma, NENHUMA — não vê nada (ADR 0013).</li>
     *   <li>ADMIN — município inteiro.</li>
     * </ul>
     *
     * Só recorta LEITURA: o desfecho por id não é restrito à executante nesta
     * versão (decisão do ADR 0011, mantida no ADR 0013).
     */
    @Transactional(readOnly = true)
    public EscopoAtendimento escopoDeAtendimentos(String cpf) {
        if (cpf == null) {
            return EscopoAtendimento.MUNICIPAL;
        }
        return userRepository.findByCpf(cpf)
                .filter(user -> perfilEfetivo(user) == Roles.USER)
                .map(user -> new EscopoAtendimento(
                        usuarioUnidadeExecutanteRepository.findExecutantesConfiguradasDoUsuario(user.getId())))
                .orElse(EscopoAtendimento.MUNICIPAL);
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
