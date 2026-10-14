package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoCancelamentoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoColetaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoFaltaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoPacienteViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacaoEspecialidadeDTO.SolicitacaoEspecialidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;

/**
 * FLUXO REAL: desfecho do atendimento lancado pelo operador da unidade executante
 * (V109) e devolucao do paciente a fila pela unidade solicitante.
 *
 * Valida as transicoes, quem pode lancar, que cota e observacao do agendamento
 * ficam intactas, que so o que foi lancado na tela pode ser desfeito, e que
 * devolver a fila cria um item novo sem tocar o antigo.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class AtendimentoDesfechoIT {

    private static final String OBSERVACAO_DE_QUEM_AGENDOU = "agendado no teste de desfecho";

    @Autowired private AtendimentoExecutanteService service;
    @Autowired private SolicitacaoService solicitacaoService;
    @Autowired private SolicitacaoEspecialidadeService legadoService;
    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private SolicitacaoEspecialidadeRepository itemRepository;
    @Autowired private AgendamentoSolicitacaoRepository agendamentoRepository;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private String sufixo;
    private Unidade unidade;
    private Unidade outraUnidade;
    private Especialidade hemograma;
    private Especialidade cardiologia;
    private CotaUnidade cotaHemograma;
    private User recepcao;
    private User executante;
    private LocalDate dia;

    @BeforeEach
    void setUp() {
        sufixo = "_DESFECHO_IT_" + System.nanoTime();
        dia = LocalDate.now(AtendimentoExecutanteService.FUSO);

        unidade = unidade("Unidade A");
        outraUnidade = unidade("Unidade B");
        hemograma = especialidade("HEMO", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        cardiologia = especialidade("CARDIO", ItemCategoria.ESPECIALIDADE_MEDICA);
        cotaHemograma = cota(hemograma);
        cota(cardiologia);

        // Quem agenda e da unidade solicitante (consome cota); quem atende e o USER,
        // lotado em outra unidade — a executante.
        recepcao = usuario(Roles.ADMIN_UNIDADE, unidade);
        executante = usuario(Roles.USER, outraUnidade);
    }

    @Test
    @DisplayName("Confirmar: AGENDADO vira REALIZADO, com autoria; cota e observacao do agendamento intactas")
    void confirmar() {
        Long id = agendar(hemograma);

        AtendimentoViewDTO view = service.confirmar(id, executante.getCpf());
        limpar();

        assertThat(view.status()).isEqualTo("REALIZADO");
        assertThat(view.registradoPorNome()).isEqualTo(executante.getNome());
        assertThat(view.registradoEm()).isNotNull();
        assertThat(view.observacao()).isNull();
        assertThat(view.podeDesfazer()).isTrue();
        assertThat(view.solicitacaoId()).isEqualTo(item(id).getSolicitacao().getId());

        SolicitacaoEspecialidade item = item(id);
        assertThat(item.getStatus()).isEqualTo(StatusDaMarcacao.REALIZADO);
        assertThat(item.getAtendimentoRegistradoPor().getId()).isEqualTo(executante.getId());
        assertThat(item.getAgendamentoSolicitacao().getObservacoes()).isEqualTo(OBSERVACAO_DE_QUEM_AGENDOU);
        assertThat(utilizada(cotaHemograma)).isEqualTo(1);
    }

    @Test
    @DisplayName("Falta: grava FALTOU (nao CANCELADO), observacao opcional no proprio item, vaga continua ocupada")
    void registrarFalta() {
        Long comObservacao = agendar(hemograma);
        Long semObservacao = agendar(hemograma);
        Long semCorpo = agendar(hemograma);

        AtendimentoViewDTO view = service.registrarFalta(comObservacao,
                new AtendimentoFaltaDTO("  paciente não compareceu  "), executante.getCpf());
        service.registrarFalta(semObservacao, new AtendimentoFaltaDTO("   "), executante.getCpf());
        service.registrarFalta(semCorpo, null, executante.getCpf());
        limpar();

        assertThat(view.status()).isEqualTo("FALTOU");
        assertThat(view.observacao()).isEqualTo("paciente não compareceu");
        for (Long id : List.of(comObservacao, semObservacao, semCorpo)) {
            assertThat(item(id).getStatus()).isEqualTo(StatusDaMarcacao.FALTOU);
            assertThat(item(id).getAtendimentoRegistradoEm()).isNotNull();
            assertThat(item(id).getAgendamentoSolicitacao().getObservacoes()).isEqualTo(OBSERVACAO_DE_QUEM_AGENDOU);
        }
        assertThat(item(semObservacao).getAtendimentoObservacao()).isNull();
        assertThat(item(semCorpo).getAtendimentoObservacao()).isNull();
        assertThat(utilizada(cotaHemograma)).isEqualTo(3);
    }

    @Test
    @DisplayName("Cancelar: motivo obrigatorio; sem motivo e 400 e nada muda")
    void cancelarExigeMotivo() {
        Long id = agendar(hemograma);
        String cpf = executante.getCpf();

        for (AtendimentoCancelamentoDTO semMotivo : new AtendimentoCancelamentoDTO[] {
                null, new AtendimentoCancelamentoDTO(null), new AtendimentoCancelamentoDTO("   ") }) {
            assertThatThrownBy(() -> service.cancelar(id, semMotivo, cpf))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo");
        }
        assertThatThrownBy(() -> service.cancelar(id, new AtendimentoCancelamentoDTO("x".repeat(501)), cpf))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("500");
        limpar();
        assertThat(item(id).getStatus()).isEqualTo(StatusDaMarcacao.AGENDADO);

        AtendimentoViewDTO view = service.cancelar(id, new AtendimentoCancelamentoDTO("aparelho quebrado"), cpf);
        limpar();

        assertThat(view.status()).isEqualTo("CANCELADO");
        assertThat(view.observacao()).isEqualTo("aparelho quebrado");
        assertThat(item(id).getAtendimentoObservacao()).isEqualTo("aparelho quebrado");
        assertThat(utilizada(cotaHemograma)).isEqualTo(1);
    }

    @Test
    @DisplayName("Um desfecho nao troca direto por outro, nem se lanca duas vezes: 409")
    void desfechoNaoETrocadoDireto() {
        Long id = agendar(hemograma);
        String cpf = executante.getCpf();
        service.confirmar(id, cpf);

        assertThatThrownBy(() -> service.confirmar(id, cpf)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.registrarFalta(id, null, cpf)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.cancelar(id, new AtendimentoCancelamentoDTO("motivo"), cpf))
                .isInstanceOf(IllegalStateException.class);
        limpar();
        assertThat(item(id).getStatus()).isEqualTo(StatusDaMarcacao.REALIZADO);
    }

    @Test
    @DisplayName("Desfazer: volta a AGENDADO e apaga observacao e autoria; depois aceita outro desfecho")
    void desfazer() {
        Long id = agendar(hemograma);
        String cpf = executante.getCpf();
        service.cancelar(id, new AtendimentoCancelamentoDTO("lancado por engano"), cpf);

        AtendimentoViewDTO view = service.desfazer(id, cpf);
        limpar();

        assertThat(view.status()).isEqualTo("AGENDADO");
        assertThat(view.podeDesfazer()).isFalse();
        SolicitacaoEspecialidade item = item(id);
        assertThat(item.getStatus()).isEqualTo(StatusDaMarcacao.AGENDADO);
        assertThat(item.getAtendimentoObservacao()).isNull();
        assertThat(item.getAtendimentoRegistradoPor()).isNull();
        assertThat(item.getAtendimentoRegistradoEm()).isNull();
        assertThat(utilizada(cotaHemograma)).isEqualTo(1);

        assertThatThrownBy(() -> service.desfazer(id, cpf)).isInstanceOf(IllegalStateException.class);
        assertThat(service.confirmar(id, cpf).status()).isEqualTo("REALIZADO");
    }

    @Test
    @DisplayName("Desfazer recusa o que nao foi lancado na tela: falta do fluxo legado (CANCELADO sem autoria)")
    void desfazerRecusaRegistroDeOutroFluxo() {
        Long id = agendar(hemograma);
        legadoService.faltouProcedimento(id, null);
        limpar();

        assertThatThrownBy(() -> service.desfazer(id, executante.getCpf()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tela de Atendimentos");
        limpar();
        assertThat(item(id).getStatus()).isEqualTo(StatusDaMarcacao.CANCELADO);

        var pagina = service.listar(dia, null, unidade.getId(), false, "CANCELADO", null, 0, 50, executante.getCpf());
        assertThat(pagina.getContent()).flatExtracting(AtendimentoPacienteViewDTO::itens)
                .filteredOn(a -> a.id().equals(id)).singleElement()
                .satisfies(a -> assertThat(a.podeDesfazer()).isFalse());
    }

    @Test
    @DisplayName("Data da coleta: so exame/procedimento; nao muda o status; nulo limpa")
    void dataDaColeta() {
        Long exame = agendar(hemograma);
        Long consulta = agendar(cardiologia);
        String cpf = executante.getCpf();

        AtendimentoViewDTO view = service.informarDataColeta(exame, new AtendimentoColetaDTO(dia), cpf);
        assertThat(view.dataColeta()).isEqualTo(dia);
        assertThat(view.status()).isEqualTo("AGENDADO");
        limpar();
        assertThat(item(exame).getDataColeta()).isEqualTo(dia);

        // Sem corpo nao e "limpar": a data pode ter sido informada pela unidade solicitante.
        assertThatThrownBy(() -> service.informarDataColeta(exame, null, cpf))
                .isInstanceOf(IllegalArgumentException.class);
        limpar();
        assertThat(item(exame).getDataColeta()).isEqualTo(dia);
        assertThat(service.informarDataColeta(exame, new AtendimentoColetaDTO(null), cpf).dataColeta()).isNull();

        assertThatThrownBy(() -> service.informarDataColeta(consulta, new AtendimentoColetaDTO(dia), cpf))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exame");

        service.registrarFalta(exame, null, cpf);
        assertThatThrownBy(() -> service.informarDataColeta(exame, new AtendimentoColetaDTO(dia), cpf))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("A lista devolve observacao, autoria e podeDesfazer do que foi lancado")
    void listaTrazODesfecho() {
        Long id = agendar(hemograma);
        service.registrarFalta(id, new AtendimentoFaltaDTO("nao veio"), executante.getCpf());
        limpar();

        var pagina = service.listar(dia, null, unidade.getId(), false, "FALTOU", null, 0, 50, executante.getCpf());

        assertThat(pagina.getContent()).flatExtracting(AtendimentoPacienteViewDTO::itens)
                .filteredOn(a -> a.id().equals(id)).singleElement().satisfies(a -> {
            assertThat(a.observacao()).isEqualTo("nao veio");
            assertThat(a.registradoPorNome()).isEqualTo(executante.getNome());
            assertThat(a.registradoEm()).isNotNull();
            assertThat(a.podeDesfazer()).isTrue();
        });
    }

    @Test
    @DisplayName("So o Usuario Padrao lanca e desfaz: ADMIN e os demais perfis levam acesso negado")
    void soOUsuarioPadraoRegistra() {
        Long id = agendar(hemograma);

        for (Roles role : List.of(Roles.ADMIN, Roles.GESTOR, Roles.ADMIN_UNIDADE, Roles.RECEPCAO,
                Roles.ENFERMEIRO, Roles.MEDICO, Roles.COORD_TRANSPORTE, Roles.PACIENTE)) {
            String cpf = usuario(role, unidade).getCpf();
            assertThatThrownBy(() -> service.confirmar(id, cpf)).as(role.name()).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.registrarFalta(id, null, cpf)).as(role.name())
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.cancelar(id, new AtendimentoCancelamentoDTO("motivo"), cpf))
                    .as(role.name()).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.informarDataColeta(id, new AtendimentoColetaDTO(dia), cpf))
                    .as(role.name()).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.desfazer(id, cpf)).as(role.name()).isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(() -> service.confirmar(id, null)).isInstanceOf(AccessDeniedException.class);
        limpar();
        assertThat(item(id).getStatus()).isEqualTo(StatusDaMarcacao.AGENDADO);
    }

    @Test
    @DisplayName("Item inexistente e 404; item que nao esta agendado e 409")
    void itemInexistenteOuForaDaAgenda() {
        String cpf = executante.getCpf();
        assertThatThrownBy(() -> service.confirmar(999_999_999L, cpf)).isInstanceOf(EntityNotFoundException.class);

        Long naFila = pedir(hemograma).getEspecialidades().get(0).getId();
        assertThatThrownBy(() -> service.confirmar(naFila, cpf)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.desfazer(naFila, cpf)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Excluir o agendamento apaga o registro do desfecho: o item volta a fila sem autoria antiga")
    void excluirAgendamentoLimpaODesfecho() {
        Long id = agendar(hemograma);
        service.cancelar(id, new AtendimentoCancelamentoDTO("aparelho quebrado"), executante.getCpf());
        limpar();
        Long agendamentoId = item(id).getAgendamentoSolicitacao().getId();

        agendamentoService.deleteAgendamento(agendamentoId, recepcao.getCpf());
        limpar();

        SolicitacaoEspecialidade item = item(id);
        assertThat(item.getStatus()).isEqualTo(StatusDaMarcacao.AGUARDANDO);
        assertThat(item.getAtendimentoObservacao()).isNull();
        assertThat(item.getAtendimentoRegistradoPor()).isNull();
        assertThat(item.getAtendimentoRegistradoEm()).isNull();
        assertThat(utilizada(cotaHemograma)).isZero();
    }

    // ------------------------------------------------------------------
    // Devolver a fila
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Devolver a fila cria item novo AGUARDANDO com a data original; o antigo, a cota e o agendamento ficam intactos")
    void devolverAFila() {
        Long id = agendar(hemograma);
        service.registrarFalta(id, new AtendimentoFaltaDTO("nao veio"), executante.getCpf());
        limpar();
        SolicitacaoEspecialidade antes = item(id);
        var dataOriginal = antes.getDataDeCadastro();
        Long agendamentoId = antes.getAgendamentoSolicitacao().getId();
        Long solicitacaoId = antes.getSolicitacao().getId();
        limpar();

        SolicitacaoViewDTO ficha = solicitacaoService.devolverItemAFila(id, recepcao.getCpf());
        limpar();

        assertThat(ficha.especialidades()).hasSize(2);
        var antigoNaFicha = ficha.especialidades().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
        var novoNaFicha = ficha.especialidades().stream().filter(e -> !e.id().equals(id)).findFirst().orElseThrow();
        assertThat(antigoNaFicha.status()).isEqualTo("FALTOU");
        assertThat(antigoNaFicha.devolvidoAFila()).isTrue();
        assertThat(antigoNaFicha.atendimentoObservacao()).isEqualTo("nao veio");
        assertThat(antigoNaFicha.atendimentoRegistradoPorNome()).isEqualTo(executante.getNome());
        assertThat(novoNaFicha.status()).isEqualTo("AGUARDANDO");
        assertThat(novoNaFicha.agendamentoId()).isNull();
        assertThat(novoNaFicha.devolvidoAFila()).isFalse();

        SolicitacaoEspecialidade antigo = item(id);
        assertThat(antigo.getStatus()).isEqualTo(StatusDaMarcacao.FALTOU);
        assertThat(antigo.getAgendamentoSolicitacao().getId()).isEqualTo(agendamentoId);
        assertThat(antigo.getAtendimentoRegistradoEm()).isNotNull();

        SolicitacaoEspecialidade novo = antigo.getDevolvidoComo();
        assertThat(novo.getSolicitacao().getId()).isEqualTo(solicitacaoId);
        assertThat(novo.getStatus()).isEqualTo(StatusDaMarcacao.AGUARDANDO);
        assertThat(novo.getEspecialidadeSolicitada().getId()).isEqualTo(hemograma.getId());
        assertThat(novo.getPrioridade()).isEqualTo(PrioridadeDaMarcacaoEnum.NORMAL);
        assertThat(novo.getDataDeCadastro()).isEqualTo(dataOriginal);
        assertThat(novo.getAgendamentoSolicitacao()).isNull();
        assertThat(novo.getCotaUnidade()).isNull();
        assertThat(novo.getDataColeta()).isNull();
        assertThat(novo.getAtendimentoRegistradoEm()).isNull();
        assertThat(novo.getCriadoPor().getId()).isEqualTo(recepcao.getId());

        assertThat(agendamentoRepository.findById(agendamentoId)).isPresent();
        assertThat(utilizada(cotaHemograma)).isEqualTo(1);
    }

    @Test
    @DisplayName("Depois de devolvido: nao devolve de novo, o executante nao desfaz, e o item novo agenda consumindo outra vaga")
    void depoisDeDevolvido() {
        Long id = agendar(hemograma);
        service.cancelar(id, new AtendimentoCancelamentoDTO("aparelho quebrado"), executante.getCpf());
        limpar();
        Long solicitacaoId = item(id).getSolicitacao().getId();
        solicitacaoService.devolverItemAFila(id, recepcao.getCpf());
        limpar();

        assertThatThrownBy(() -> solicitacaoService.devolverItemAFila(id, recepcao.getCpf()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("já foi devolvido");
        assertThatThrownBy(() -> service.desfazer(id, executante.getCpf()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("devolvido à fila");
        limpar();

        agendamentoService.criarAgendamentoParaMultiplosExames(solicitacaoId, dto(hemograma), recepcao.getCpf());
        limpar();

        SolicitacaoEspecialidade antigo = item(id);
        assertThat(antigo.getStatus()).isEqualTo(StatusDaMarcacao.CANCELADO);
        assertThat(antigo.getDevolvidoComo().getStatus()).isEqualTo(StatusDaMarcacao.AGENDADO);
        assertThat(antigo.getDevolvidoComo().getAgendamentoSolicitacao().getId())
                .isNotEqualTo(antigo.getAgendamentoSolicitacao().getId());
        assertThat(utilizada(cotaHemograma)).isEqualTo(2);
    }

    @Test
    @DisplayName("Agendamento com item ja devolvido a fila nao e excluido inteiro: reabriria o item e duplicaria o pedido")
    void excluirAgendamentoComItemDevolvidoERecusado() {
        Long id = agendar(hemograma);
        service.registrarFalta(id, null, executante.getCpf());
        limpar();
        Long agendamentoId = item(id).getAgendamentoSolicitacao().getId();
        solicitacaoService.devolverItemAFila(id, recepcao.getCpf());
        limpar();

        assertThatThrownBy(() -> agendamentoService.deleteAgendamento(agendamentoId, recepcao.getCpf()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("devolvido à fila");
        limpar();

        // Nada foi estornado nem reaberto: o antigo segue FALTOU e so o item novo esta pendente.
        assertThat(agendamentoRepository.findById(agendamentoId)).isPresent();
        SolicitacaoEspecialidade antigo = item(id);
        assertThat(antigo.getStatus()).isEqualTo(StatusDaMarcacao.FALTOU);
        assertThat(antigo.getDevolvidoComo().getStatus()).isEqualTo(StatusDaMarcacao.AGUARDANDO);
        assertThat(utilizada(cotaHemograma)).isEqualTo(1);
    }

    @Test
    @DisplayName("Remover o pedido pendente criado na devolucao desfaz o vinculo: o agendamento volta a ser excluivel")
    void removerOItemNovoLiberaAExclusao() {
        Long id = agendar(hemograma);
        service.registrarFalta(id, null, executante.getCpf());
        limpar();
        Long agendamentoId = item(id).getAgendamentoSolicitacao().getId();
        solicitacaoService.devolverItemAFila(id, recepcao.getCpf());
        limpar();
        Long novoId = item(id).getDevolvidoComo().getId();
        limpar();

        solicitacaoService.removerEspecialidade(novoId, recepcao.getCpf());
        limpar();

        assertThat(itemRepository.findById(novoId)).isEmpty();
        assertThat(item(id).getDevolvidoComo()).isNull();
        assertThat(item(id).getStatus()).isEqualTo(StatusDaMarcacao.FALTOU);
        assertThat(utilizada(cotaHemograma)).isEqualTo(1);
        limpar();

        agendamentoService.deleteAgendamento(agendamentoId, recepcao.getCpf());
        limpar();

        // Um pedido so, de volta a fila, com a vaga devolvida.
        assertThat(item(id).getStatus()).isEqualTo(StatusDaMarcacao.AGUARDANDO);
        assertThat(utilizada(cotaHemograma)).isZero();
    }

    @Test
    @DisplayName("Reagendar um item que voltou a fila por troca manual de status apaga o desfecho antigo")
    void reagendarApagaODesfechoAntigo() {
        Long id = agendar(hemograma);
        service.registrarFalta(id, new AtendimentoFaltaDTO("nao veio"), executante.getCpf());
        limpar();
        // Troca manual pelo fluxo legado: so muda o status, sem limpar nada.
        SolicitacaoEspecialidade item = item(id);
        item.setStatus(StatusDaMarcacao.AGUARDANDO);
        Long solicitacaoId = item.getSolicitacao().getId();
        limpar();

        agendamentoService.criarAgendamentoParaMultiplosExames(solicitacaoId, dto(hemograma), recepcao.getCpf());
        limpar();

        SolicitacaoEspecialidade reagendado = item(id);
        assertThat(reagendado.getStatus()).isEqualTo(StatusDaMarcacao.AGENDADO);
        assertThat(reagendado.getAtendimentoObservacao()).isNull();
        assertThat(reagendado.getAtendimentoRegistradoPor()).isNull();
        assertThat(reagendado.getAtendimentoRegistradoEm()).isNull();

        // Um desfecho lancado depois por fluxo legado nao herda a marca: nao e desfazivel aqui.
        legadoService.faltouProcedimento(id, null);
        limpar();
        assertThatThrownBy(() -> service.desfazer(id, executante.getCpf())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("A observacao do executante so sai pela ficha: as listagens e a federacao recebem o item sem ela")
    void observacaoSoSaiPelaFicha() {
        Long id = agendar(hemograma);
        service.cancelar(id, new AtendimentoCancelamentoDTO("aparelho quebrado"), executante.getCpf());
        limpar();
        SolicitacaoEspecialidade item = item(id);

        var naFicha = solicitacaoService.getSolicitacaoById(item.getSolicitacao().getId(), recepcao.getCpf())
                .especialidades().get(0);
        assertThat(naFicha.atendimentoObservacao()).isEqualTo("aparelho quebrado");
        assertThat(naFicha.atendimentoRegistradoPorNome()).isEqualTo(executante.getNome());
        assertThat(naFicha.atendimentoRegistradoEm()).isNotNull();

        for (var semDesfecho : List.of(
                SolicitacaoEspecialidadeViewDTO.fromSolicitacaoEspecialidade(item),
                SolicitacaoViewDTO.fromSolicitacao(item.getSolicitacao()).especialidades().get(0))) {
            assertThat(semDesfecho.status()).isEqualTo("CANCELADO");
            assertThat(semDesfecho.atendimentoObservacao()).isNull();
            assertThat(semDesfecho.atendimentoRegistradoPorNome()).isNull();
            assertThat(semDesfecho.atendimentoRegistradoEm()).isNull();
        }
    }

    @Test
    @DisplayName("Devolver a fila: falta antiga do fluxo legado tambem pode; outros status e pendente duplicado, nao")
    void devolverAFilaRegras() {
        Long legado = agendar(hemograma);
        legadoService.faltouProcedimento(legado, null);
        limpar();
        solicitacaoService.devolverItemAFila(legado, recepcao.getCpf());
        limpar();
        assertThat(item(legado).getDevolvidoComo()).isNotNull();

        Long agendado = agendar(hemograma);
        assertThatThrownBy(() -> solicitacaoService.devolverItemAFila(agendado, recepcao.getCpf()))
                .isInstanceOf(IllegalStateException.class);
        service.confirmar(agendado, executante.getCpf());
        assertThatThrownBy(() -> solicitacaoService.devolverItemAFila(agendado, recepcao.getCpf()))
                .isInstanceOf(IllegalStateException.class);

        // Ficha que ja tem outro pedido pendente da mesma especialidade.
        Solicitacao comDois = pedir(hemograma, hemograma);
        agendamentoService.criarAgendamentoParaMultiplosExames(comDois.getId(), dto(hemograma), recepcao.getCpf());
        limpar();
        Long itemAgendado = itemRepository.findAll().stream()
                .filter(i -> i.getSolicitacao().getId().equals(comDois.getId()))
                .filter(i -> i.getStatus() == StatusDaMarcacao.AGENDADO).findFirst().orElseThrow().getId();
        service.registrarFalta(itemAgendado, null, executante.getCpf());
        assertThatThrownBy(() -> solicitacaoService.devolverItemAFila(itemAgendado, recepcao.getCpf()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("pendente");

        assertThatThrownBy(() -> solicitacaoService.devolverItemAFila(999_999_999L, recepcao.getCpf()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Devolver a fila respeita a unidade: operador de outra unidade leva acesso negado")
    void devolverAFilaRespeitaAUnidade() {
        Long id = agendar(hemograma);
        service.registrarFalta(id, null, executante.getCpf());
        limpar();

        String deOutraUnidade = usuario(Roles.RECEPCAO, outraUnidade).getCpf();
        assertThatThrownBy(() -> solicitacaoService.devolverItemAFila(id, deOutraUnidade))
                .isInstanceOf(AccessDeniedException.class);
        limpar();
        assertThat(item(id).getDevolvidoComo()).isNull();
    }

    // ------------------------------------------------------------------

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private void limpar() {
        em.flush();
        em.clear();
    }

    private SolicitacaoEspecialidade item(Long id) {
        return itemRepository.findById(id).orElseThrow();
    }

    private Unidade unidade(String nome) {
        Unidade u = new Unidade();
        u.setNome(nome + sufixo);
        u.setCodigo(nome.replace(" ", "") + sufixo);
        u.setAtivo(true);
        return unidadeRepository.saveAndFlush(u);
    }

    private Especialidade especialidade(String prefixo, ItemCategoria categoria) {
        Especialidade e = new Especialidade();
        e.setCodigo(prefixo + sufixo);
        e.setNome(prefixo + sufixo);
        e.setCategoria(categoria);
        e.setAtivo(true);
        e.setVagas(0);
        return especialidadeRepository.saveAndFlush(e);
    }

    private CotaUnidade cota(Especialidade item) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidade);
        c.setEspecialidade(item);
        c.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        c.setPeriodo(dia.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        c.setQuantidadeTotal(20);
        c.setQuantidadeUtilizada(0);
        c.setAtivo(true);
        return cotaRepository.saveAndFlush(c);
    }

    private int utilizada(CotaUnidade cota) {
        return cotaRepository.findById(cota.getId()).orElseThrow().getQuantidadeUtilizada();
    }

    private User usuario(Roles role, Unidade daUnidade) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        u.setUnidade(daUnidade);
        return userRepository.saveAndFlush(u);
    }

    private MultiAgendamentoCreateDTO dto(Especialidade item) {
        return new MultiAgendamentoCreateDTO(List.of(item.getCodigo()), dia, null, null, TurnoEnum.MANHA,
                OBSERVACAO_DE_QUEM_AGENDOU, null, null, null);
    }

    /** Ficha da unidade com um item pendente por especialidade informada. */
    private Solicitacao pedir(Especialidade... itensPedidos) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente" + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(unidade);

        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        for (Especialidade pedido : itensPedidos) {
            SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
            se.setSolicitacao(s);
            se.setEspecialidadeSolicitada(pedido);
            se.setEspecialidadeCodigoLegacy(pedido.getCodigo());
            se.setStatus(StatusDaMarcacao.AGUARDANDO);
            se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
            itens.add(se);
        }
        s.setEspecialidades(itens);
        return solicitacaoRepository.saveAndFlush(s);
    }

    /** Pede e agenda para hoje pelo fluxo real (consome cota). Devolve o id do item agendado. */
    private Long agendar(Especialidade item) {
        Solicitacao s = pedir(item);
        Long agendamentoId = agendamentoService
                .criarAgendamentoParaMultiplosExames(s.getId(), dto(item), recepcao.getCpf()).id();
        em.flush();
        return itemRepository.findByAgendamentoSolicitacaoId(agendamentoId).get(0).getId();
    }
}
