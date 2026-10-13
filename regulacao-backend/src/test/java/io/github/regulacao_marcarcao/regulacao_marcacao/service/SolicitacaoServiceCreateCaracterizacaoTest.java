package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoEspecialidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CID;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CidRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * CARACTERIZACAO de {@code createSolicitacao}: trava o contrato de que a tela
 * de exames (/exames) passa a depender ao enviar status GEL, prioridade,
 * unidade, observacoes e CIDs na criacao. Nenhum destes testes acompanha
 * mudanca de backend — todos passam contra o codigo como ja estava.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SolicitacaoServiceCreateCaracterizacaoTest {

    @Mock private SolicitacaoRepository solicitacaoRepository;
    @Mock private AgendamentoSolicitacaoRepository agendamentoRepository;
    @Mock private SolicitacaoEspecialidadeRepository especialidadeRepository;
    @Mock private CidRepository cidRepository;
    @Mock private EspecialidadeRepository especialidadeRepo;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private UserRepository userRepository;
    @Mock private UnidadeRepository unidadeRepository;
    @Mock private UnidadeAcessoService unidadeAcessoService;

    @InjectMocks private SolicitacaoService service;

    @BeforeEach
    void setUp() {
        when(unidadeAcessoService.resolverUnidadeAlvo(any(), any())).thenReturn(null);
        when(solicitacaoRepository.save(any(Solicitacao.class))).thenAnswer(inv -> {
            Solicitacao s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(1L);
            }
            return s;
        });

        Especialidade exame = new Especialidade();
        exame.setId(7L);
        exame.setCodigo("HEMOGRAMA");
        when(especialidadeRepo.findById(7L)).thenReturn(Optional.of(exame));
    }

    private SolicitacaoEspecialidadeCreateDTO item(StatusDaMarcacao status, PrioridadeDaMarcacaoEnum prioridade) {
        return new SolicitacaoEspecialidadeCreateDTO(7L, "HEMOGRAMA", null, null, status, prioridade);
    }

    private SolicitacaoCreateDTO dto(Long unidadeId, String observacoes, List<Long> cids,
            List<SolicitacaoEspecialidadeCreateDTO> especialidades) {
        return new SolicitacaoCreateDTO(
                unidadeId, "Paciente Teste", "12345678900", "700000000000000", null,
                "Pai Teste", "Mae Teste", "Rua Teste", null, observacoes, null,
                cids, especialidades, false, null);
    }

    @Test
    @DisplayName("Cadastro grava status e prioridade de cada item exatamente como recebidos")
    void gravaStatusEPrioridadePorItemComoRecebidos() {
        var resultado = service.createSolicitacao(dto(null, null, null, List.of(
                item(StatusDaMarcacao.GEL, PrioridadeDaMarcacaoEnum.URGENTE),
                item(StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL),
                item(StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.EMERGENCIA))), null);

        assertThat(resultado.especialidades()).hasSize(3);
        assertThat(resultado.especialidades().get(0).status()).isEqualTo("GEL");
        assertThat(resultado.especialidades().get(0).prioridade()).isEqualTo("URGENTE");
        assertThat(resultado.especialidades().get(1).status()).isEqualTo("AGUARDANDO");
        assertThat(resultado.especialidades().get(1).prioridade()).isEqualTo("NORMAL");
        assertThat(resultado.especialidades().get(2).prioridade()).isEqualTo("EMERGENCIA");
    }

    @Test
    @DisplayName("Cadastro grava as observacoes recebidas")
    void gravaObservacoes() {
        var resultado = service.createSolicitacao(
                dto(null, "Jejum de 8 horas", null, List.of()), null);

        assertThat(resultado.observacoes()).isEqualTo("Jejum de 8 horas");
    }

    @Test
    @DisplayName("Cadastro sem CID (lista nula) e aceito e nao consulta o repositorio de CID")
    void semCidNuloEAceito() {
        var resultado = service.createSolicitacao(dto(null, null, null, List.of()), null);

        assertThat(resultado.cids()).isEmpty();
        verify(cidRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("Cadastro sem CID (lista vazia) e aceito e nao consulta o repositorio de CID")
    void semCidVazioEAceito() {
        var resultado = service.createSolicitacao(dto(null, null, List.of(), List.of()), null);

        assertThat(resultado.cids()).isEmpty();
        verify(cidRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("Cadastro com CIDs vincula os CIDs encontrados no banco")
    void comCidsVinculaOsEncontrados() {
        when(cidRepository.findAllById(List.of(3L, 4L))).thenReturn(List.of(new CID(), new CID()));

        var resultado = service.createSolicitacao(dto(null, null, List.of(3L, 4L), List.of()), null);

        assertThat(resultado.cids()).hasSize(2);
    }

    @Test
    @DisplayName("Cadastro vincula a unidade devolvida por resolverUnidadeAlvo")
    void vinculaUnidadeResolvida() {
        Unidade unidade = new Unidade();
        unidade.setId(5L);
        when(unidadeAcessoService.resolverUnidadeAlvo("11122233344", 5L)).thenReturn(5L);
        when(unidadeRepository.findById(5L)).thenReturn(Optional.of(unidade));

        var resultado = service.createSolicitacao(dto(5L, null, null, List.of()), "11122233344");

        assertThat(resultado.unidadeId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Chamador global sem lotacao e sem unidade informada cria solicitacao sem unidade")
    void globalSemLotacaoCriaSemUnidade() {
        when(userRepository.findByCpf("11122233344")).thenReturn(Optional.empty());

        var resultado = service.createSolicitacao(dto(null, null, null, List.of()), "11122233344");

        assertThat(resultado.unidadeId()).isNull();
    }
}
