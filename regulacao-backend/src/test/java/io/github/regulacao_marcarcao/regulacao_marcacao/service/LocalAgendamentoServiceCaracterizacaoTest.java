package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.localAgendamentoDTO.LocalAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.localAgendamentoDTO.LocalAgendamentoUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.localAgendamentoDTO.LocalAgendamentoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Cidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.LocalAgendamentoRepository;
import jakarta.persistence.EntityNotFoundException;

/**
 * Testes de caracterizacao de LocalAgendamentoService — nao existia nenhum
 * teste cobrindo este service antes do ajuste que adiciona campos de CNES
 * (V91). Capturam o comportamento de CRUD simples que JA FUNCIONA hoje, para
 * detectar regressao quando os campos novos (cnes, cnpj, razaoSocial, etc.)
 * forem adicionados ao lado da logica existente.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalAgendamentoServiceCaracterizacaoTest {

    private static final Long CIDADE_ID = 10L;

    @Mock private LocalAgendamentoRepository localAgendamentoRepository;
    @Mock private CidadeRepository cidadeRepository;

    @InjectMocks private LocalAgendamentoService service;

    private Cidade cidade;

    @BeforeEach
    void setUp() {
        cidade = new Cidade();
        cidade.setId(CIDADE_ID);
        cidade.setNomeCidade("São Felipe");

        when(cidadeRepository.findById(CIDADE_ID)).thenReturn(Optional.of(cidade));
        when(localAgendamentoRepository.save(any(LocalAgendamento.class))).thenAnswer(inv -> {
            LocalAgendamento l = inv.getArgument(0);
            if (l.getId() == null) l.setId(999L);
            return l;
        });
    }

    /** Atalho para os testes que nao envolvem os campos de CNES (V91) — todos nulos. */
    private LocalAgendamentoCreateDTO dto(String nomeLocal, Long cidadeId, String endereco, String numero) {
        return new LocalAgendamentoCreateDTO(
                nomeLocal, cidadeId, endereco, numero, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("cadastrar com cidade valida associa a cidade e mapeia os campos basicos")
    void cadastrarComCidadeValida() {
        var dto = dto("Policlínica Central", CIDADE_ID, "Rua A", "123");

        LocalAgendamentoViewDTO resultado = service.cadastrarLocalAgendamento(dto);

        assertThat(resultado.nomeLocal()).isEqualTo("Policlínica Central");
        assertThat(resultado.endereco()).isEqualTo("Rua A");
        assertThat(resultado.numero()).isEqualTo("123");
        assertThat(resultado.cidade()).isNotNull();
        assertThat(resultado.cidade().id()).isEqualTo(CIDADE_ID);
    }

    @Test
    @DisplayName("cadastrar sem cidadeId cria local sem cidade associada")
    void cadastrarSemCidade() {
        var dto = dto("Ponto Avulso", null, "Rua B", "45");

        LocalAgendamentoViewDTO resultado = service.cadastrarLocalAgendamento(dto);

        assertThat(resultado.cidade()).isNull();
        verify(cidadeRepository, never()).findById(any());
    }

    @Test
    @DisplayName("cadastrar com cidadeId inexistente lanca EntityNotFoundException")
    void cadastrarComCidadeInexistente() {
        when(cidadeRepository.findById(999L)).thenReturn(Optional.empty());
        var dto = dto("X", 999L, "Rua C", "1");

        assertThatThrownBy(() -> service.cadastrarLocalAgendamento(dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Cidade");

        verify(localAgendamentoRepository, never()).save(any());
    }

    @Test
    @DisplayName("atualizar troca todos os campos do registro existente")
    void atualizarComSucesso() {
        LocalAgendamento existente = new LocalAgendamento();
        existente.setId(1L);
        existente.setNomeLocal("Nome antigo");
        existente.setEndereco("Endereco antigo");
        existente.setNumero("1");
        when(localAgendamentoRepository.findById(1L)).thenReturn(Optional.of(existente));

        var dto = new LocalAgendamentoUpdateDTO("Nome novo", CIDADE_ID, "Endereco novo", "2");

        LocalAgendamentoViewDTO resultado = service.atualizarLocalAgendamento(1L, dto);

        assertThat(resultado.nomeLocal()).isEqualTo("Nome novo");
        assertThat(resultado.endereco()).isEqualTo("Endereco novo");
        assertThat(resultado.numero()).isEqualTo("2");
        assertThat(resultado.cidade().id()).isEqualTo(CIDADE_ID);
    }

    @Test
    @DisplayName("atualizar registro inexistente lanca EntityNotFoundException")
    void atualizarInexistente() {
        when(localAgendamentoRepository.findById(404L)).thenReturn(Optional.empty());
        var dto = new LocalAgendamentoUpdateDTO("X", null, "Y", "1");

        assertThatThrownBy(() -> service.atualizarLocalAgendamento(404L, dto))
                .isInstanceOf(EntityNotFoundException.class);

        verify(localAgendamentoRepository, never()).save(any());
    }

    @Test
    @DisplayName("deletar remove o registro existente")
    void deletarComSucesso() {
        LocalAgendamento existente = new LocalAgendamento();
        existente.setId(1L);
        when(localAgendamentoRepository.findById(1L)).thenReturn(Optional.of(existente));

        service.deletarLocalAgendamento(1L);

        ArgumentCaptor<LocalAgendamento> captor = ArgumentCaptor.forClass(LocalAgendamento.class);
        verify(localAgendamentoRepository, times(1)).delete(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("deletar registro inexistente lanca EntityNotFoundException")
    void deletarInexistente() {
        when(localAgendamentoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletarLocalAgendamento(404L))
                .isInstanceOf(EntityNotFoundException.class);

        verify(localAgendamentoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("listar sem cidadeId usa findAll")
    void listarSemFiltro() {
        when(localAgendamentoRepository.findAll()).thenReturn(List.of());

        service.listarAgendamentoListDTOs(null);

        verify(localAgendamentoRepository, times(1)).findAll();
        verify(localAgendamentoRepository, never()).findByCidade_Id(any());
    }

    @Test
    @DisplayName("listar com cidadeId usa findByCidade_Id")
    void listarComFiltro() {
        when(localAgendamentoRepository.findByCidade_Id(CIDADE_ID)).thenReturn(List.of());

        service.listarAgendamentoListDTOs(CIDADE_ID);

        verify(localAgendamentoRepository, times(1)).findByCidade_Id(CIDADE_ID);
        verify(localAgendamentoRepository, never()).findAll();
    }

    // ==================================================================
    // Dados do estabelecimento vindos da busca por CNES (V91)
    // ==================================================================

    @Test
    @DisplayName("cadastrar com CNES ja usado por outro local e recusado")
    void cadastrarComCnesDuplicadoERecusado() {
        LocalAgendamento outroLocal = new LocalAgendamento();
        outroLocal.setId(5L);
        outroLocal.setCnes("2514648");
        when(localAgendamentoRepository.findByCnes("2514648")).thenReturn(Optional.of(outroLocal));

        var dto = new LocalAgendamentoCreateDTO(
                "Novo Local", CIDADE_ID, "Rua A", "1",
                "2514648", null, null, null, null, null, null, null, true);

        assertThatThrownBy(() -> service.cadastrarLocalAgendamento(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2514648");

        verify(localAgendamentoRepository, never()).save(any());
    }

    @Test
    @DisplayName("cadastrar sem CNES nao verifica duplicidade")
    void cadastrarSemCnesNaoVerificaDuplicidade() {
        var dto = dto("Ponto sem CNES", CIDADE_ID, "Rua A", "1");

        assertThatCode(() -> service.cadastrarLocalAgendamento(dto)).doesNotThrowAnyException();

        verify(localAgendamentoRepository, never()).findByCnes(any());
    }

    @Test
    @DisplayName("cadastrar com importadoDoCnes=true carimba sincronizadoCnesEm")
    void cadastrarComImportadoDoCnesCarimbaData() {
        var dto = new LocalAgendamentoCreateDTO(
                "Novo Local", CIDADE_ID, "Rua A", "1",
                "2514648", "12345678000199", "Razao Social LTDA", "Nome Fantasia",
                "Centro", "45000000", "75999990000", "contato@exemplo.com", true);

        LocalAgendamentoViewDTO resultado = service.cadastrarLocalAgendamento(dto);

        assertThat(resultado.sincronizadoCnesEm()).isNotNull();
        assertThat(resultado.cnes()).isEqualTo("2514648");
        assertThat(resultado.razaoSocial()).isEqualTo("Razao Social LTDA");
        assertThat(resultado.nomeFantasia()).isEqualTo("Nome Fantasia");
        assertThat(resultado.bairro()).isEqualTo("Centro");
    }

    @Test
    @DisplayName("cadastrar sem importadoDoCnes nao carimba sincronizadoCnesEm")
    void cadastrarSemImportadoDoCnesNaoCarimbaData() {
        var dto = new LocalAgendamentoCreateDTO(
                "Novo Local", CIDADE_ID, "Rua A", "1",
                "2514648", null, null, null, null, null, null, null, null);

        LocalAgendamentoViewDTO resultado = service.cadastrarLocalAgendamento(dto);

        assertThat(resultado.sincronizadoCnesEm()).isNull();
    }

    @Test
    @DisplayName("cadastrar normaliza CNPJ e CEP para somente digitos")
    void cadastrarNormalizaCnpjECep() {
        var dto = new LocalAgendamentoCreateDTO(
                "Novo Local", CIDADE_ID, "Rua A", "1",
                null, "12.345.678/0001-99", null, null, null, "45.000-000", null, null, null);

        LocalAgendamentoViewDTO resultado = service.cadastrarLocalAgendamento(dto);

        assertThat(resultado.cnpj()).isEqualTo("12345678000199");
        assertThat(resultado.cep()).isEqualTo("45000000");
    }
}
