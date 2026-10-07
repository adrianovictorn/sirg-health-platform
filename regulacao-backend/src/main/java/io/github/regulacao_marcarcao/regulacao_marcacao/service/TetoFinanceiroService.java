package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.TetoFinanceiroCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.TetoFinanceiroUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.TetoFinanceiroViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.TetoFinanceiro;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.TetoFinanceiroRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Teto financeiro por unidade, grupo de especialidades e mes (V107).
 *
 * <p>Espelha a cota onde faz sentido — debito ao agendar, estorno ao cancelar,
 * 409 quando nao ha saldo, ausencia de teto = sem restricao — mas e um motor a
 * parte: {@link CotaUnidadeService} nao sabe que ele existe.
 *
 * <p>Diferenca deliberada em relacao a cota: quem nao e barrado (ADMIN, GESTOR)
 * <b>debita mesmo assim</b>, sem bloqueio. Na cota esses perfis nao consomem; no
 * teto o gasto precisa aparecer, senao o saldo ficaria menor que o gasto real.
 */
@Service
@RequiredArgsConstructor
public class TetoFinanceiroService {

    private static final DateTimeFormatter PERIODO_MENSAL = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter PERIODO_EXIBICAO = DateTimeFormatter.ofPattern("MM/yyyy");
    /** NUMERIC(14,2): ate 12 digitos inteiros. */
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("999999999999.99");
    private static final String MENSAGEM_EDICAO_CONCORRENTE =
            "Este teto foi alterado por outra pessoa. Recarregue a tela e tente de novo.";

    private final TetoFinanceiroRepository tetoRepository;
    private final UnidadeRepository unidadeRepository;
    private final GrupoRelatorioRepository grupoRelatorioRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final UserRepository userRepository;

    // ------------------------------------------------------------------
    // Cadastro
    // ------------------------------------------------------------------

    @Transactional
    public List<TetoFinanceiroViewDTO> criar(TetoFinanceiroCreateDTO dto, String callerCpf) {
        String periodo = validarPeriodo(dto.periodo());
        BigDecimal valorTotal = validarValor(dto.valorTotal());

        GrupoRelatorio grupo = grupoRelatorioRepository.findById(dto.grupoEspecialidadesId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Grupo de especialidades não encontrado: " + dto.grupoEspecialidadesId()));
        // Teto de grupo vazio nunca seria debitado: quase sempre e o grupo errado.
        if (especialidadeRepository.countByGrupoRelatorioId(grupo.getId()) == 0) {
            throw new IllegalArgumentException(
                    "O grupo \"" + grupo.getNome() + "\" não tem nenhuma especialidade. "
                            + "Escolha o grupo que contém os exames que devem debitar o teto.");
        }

        Set<Long> unidadeIds = new LinkedHashSet<>(dto.unidadeIds());
        List<Unidade> unidades = new ArrayList<>();
        for (Long unidadeId : unidadeIds) {
            unidades.add(unidadeRepository.findById(unidadeId)
                    .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada: " + unidadeId)));
        }

        // Tudo ou nada: liberar para 9 unidades e falhar na 10a deixaria o lote pela metade.
        List<Long> jaTem = tetoRepository.buscarUnidadesComTeto(grupo.getId(), periodo, unidadeIds);
        if (!jaTem.isEmpty()) {
            List<String> nomes = unidades.stream()
                    .filter(u -> jaTem.contains(u.getId()))
                    .map(Unidade::getNome)
                    .toList();
            throw new IllegalStateException(
                    "Já existe teto de " + grupo.getNome() + " para " + exibir(periodo) + " em: "
                            + String.join(", ", nomes) + ". Edite o teto existente em vez de criar outro.");
        }

        User autor = callerCpf != null ? userRepository.findByCpf(callerCpf).orElse(null) : null;
        List<TetoFinanceiroViewDTO> criados = new ArrayList<>();
        for (Unidade unidade : unidades) {
            TetoFinanceiro teto = new TetoFinanceiro();
            teto.setUnidade(unidade);
            teto.setGrupoEspecialidades(grupo);
            teto.setPeriodo(periodo);
            teto.setValorTotal(valorTotal);
            teto.setValorUtilizado(BigDecimal.ZERO.setScale(2));
            teto.setAtivo(true);
            teto.setCriadoPor(autor);
            criados.add(TetoFinanceiroViewDTO.from(tetoRepository.save(teto)));
        }
        return criados;
    }

    @Transactional
    public TetoFinanceiroViewDTO atualizar(Long id, TetoFinanceiroUpdateDTO dto) {
        TetoFinanceiro teto = tetoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Teto financeiro não encontrado: " + id));

        if (dto.version() != null && !dto.version().equals(teto.getVersion())) {
            throw new IllegalStateException(MENSAGEM_EDICAO_CONCORRENTE);
        }

        BigDecimal valorTotal = validarValor(dto.valorTotal());
        // Mesma regra da cota: nao se REDUZ o limite para baixo do que ja foi usado.
        // Aumentar e sempre aceito, mesmo que continue abaixo do utilizado — o teto
        // pode ter sido ultrapassado por agendamento de ADMIN/GESTOR, que nao e barrado.
        if (valorTotal.compareTo(teto.getValorTotal()) < 0 && valorTotal.compareTo(teto.getValorUtilizado()) < 0) {
            throw new IllegalStateException(
                    "O novo valor do teto é menor do que o já utilizado no mês. "
                            + "Informe um valor igual ou maior que o utilizado.");
        }

        teto.setValorTotal(valorTotal);
        teto.setAtivo(dto.ativo());
        try {
            return TetoFinanceiroViewDTO.from(tetoRepository.saveAndFlush(teto));
        } catch (ObjectOptimisticLockingFailureException e) {
            // Regra de negocio nunca vira 500 silencioso: duas edicoes ao mesmo tempo e 409.
            throw new IllegalStateException(MENSAGEM_EDICAO_CONCORRENTE);
        }
    }

    @Transactional(readOnly = true)
    public List<TetoFinanceiroViewDTO> listar(String periodo) {
        String mes = periodo == null || periodo.isBlank()
                ? LocalDate.now().format(PERIODO_MENSAL)
                : validarPeriodo(periodo);
        return tetoRepository.listarPorPeriodo(mes).stream().map(TetoFinanceiroViewDTO::from).toList();
    }

    // ------------------------------------------------------------------
    // Debito e estorno — chamados pelo agendamento, na transacao dele
    // ------------------------------------------------------------------

    /** Teto ativo que incide sobre a unidade, o grupo e o mes da data agendada, se houver. */
    @Transactional(readOnly = true)
    public Optional<Long> buscarTetoAplicavel(Long unidadeId, Long grupoEspecialidadesId, LocalDate dataAgendada) {
        if (unidadeId == null || grupoEspecialidadesId == null || dataAgendada == null) {
            return Optional.empty();
        }
        return tetoRepository.buscarIdAtivo(unidadeId, grupoEspecialidadesId, dataAgendada.format(PERIODO_MENSAL));
    }

    /**
     * Debita o valor do teto.
     *
     * @param bloquear true para quem esta sujeito ao teto: sem saldo, lanca
     *                 {@link IllegalStateException} (409) e a transacao do
     *                 agendamento inteira desfaz — inclusive a cota ja consumida.
     *                 false para quem nao e barrado: debita mesmo sem saldo.
     */
    @Transactional
    public void debitar(Long tetoId, BigDecimal valor, boolean bloquear) {
        if (valor == null || valor.signum() <= 0) {
            return;
        }
        if (!bloquear) {
            tetoRepository.debitarSemBloqueio(tetoId, valor);
            return;
        }
        if (tetoRepository.debitar(tetoId, valor) == 0) {
            throw new IllegalStateException(mensagemEsgotado(tetoId));
        }
    }

    /**
     * Diz se {@link #debitar} com {@code bloquear = true} recusaria o valor, SEM
     * debitar — usado pela pre-verificacao do agendamento em lote. Vazio quando
     * o teto comporta; senao, a mesma mensagem do bloqueio (sem valores).
     */
    @Transactional(readOnly = true)
    public Optional<String> motivoSeNaoComporta(Long tetoId, BigDecimal valor) {
        if (tetoId == null || valor == null || valor.signum() <= 0) {
            return Optional.empty();
        }
        // Mesmo criterio do WHERE de TetoFinanceiroRepository#debitar.
        boolean comporta = tetoRepository.findById(tetoId)
                .map(t -> t.isAtivo() && t.getValorUtilizado().add(valor).compareTo(t.getValorTotal()) <= 0)
                .orElse(false);
        return comporta ? Optional.empty() : Optional.of(mensagemEsgotado(tetoId));
    }

    @Transactional
    public void estornar(Long tetoId, BigDecimal valor) {
        if (tetoId == null || valor == null || valor.signum() <= 0) {
            return;
        }
        tetoRepository.estornar(tetoId, valor);
    }

    /**
     * Mensagem do bloqueio. NAO cita nenhum valor em reais, de proposito: quem a
     * recebe e o perfil de unidade, que nao pode ver custo — nem saldo, nem
     * limite, nem o preco do exame.
     */
    private String mensagemEsgotado(Long tetoId) {
        return tetoRepository.findById(tetoId)
                .map(t -> "Teto financeiro de " + t.getGrupoEspecialidades().getNome()
                        + " da unidade " + t.getUnidade().getNome()
                        + " esgotado para " + exibir(t.getPeriodo())
                        + " — agendamento bloqueado. Procure a Central de Regulação.")
                .orElse("Teto financeiro esgotado — agendamento bloqueado. Procure a Central de Regulação.");
    }

    // ------------------------------------------------------------------
    // Validacao
    // ------------------------------------------------------------------

    private static String validarPeriodo(String periodo) {
        if (periodo == null || !periodo.trim().matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("Mês inválido: \"" + periodo + "\". Use o formato AAAA-MM.");
        }
        return periodo.trim();
    }

    private static BigDecimal validarValor(BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException("Informe o valor do teto.");
        }
        if (valor.signum() < 0) {
            throw new IllegalArgumentException("O valor do teto não pode ser negativo.");
        }
        if (valor.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("O valor do teto aceita no máximo duas casas decimais.");
        }
        if (valor.compareTo(VALOR_MAXIMO) > 0) {
            throw new IllegalArgumentException("O valor do teto é maior do que o sistema comporta.");
        }
        return valor.setScale(2);
    }

    private static String exibir(String periodo) {
        try {
            return YearMonth.parse(periodo).format(PERIODO_EXIBICAO);
        } catch (Exception e) {
            return periodo;
        }
    }
}
