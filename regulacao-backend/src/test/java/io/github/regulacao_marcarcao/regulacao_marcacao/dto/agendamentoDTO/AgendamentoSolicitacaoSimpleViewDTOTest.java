package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Cidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.LocalDeAgendamentoEnum;

/**
 * Cobre resolveLocal — mudou para incluir a cidade no label (pedido de
 * re-download de comprovante em /paciente/[id]) e passou a refletir em todas
 * as telas que exibem esse DTO, nao so a nova.
 */
class AgendamentoSolicitacaoSimpleViewDTOTest {

    @Test
    void localComCidade_devolveNomeLocalMaisCidade() {
        Cidade cidade = new Cidade();
        cidade.setNomeCidade("Conceição do Almeida");

        LocalAgendamento local = new LocalAgendamento();
        local.setNomeLocal("Hospital Municipal");
        local.setCidade(cidade);

        AgendamentoSolicitacao agendamento = new AgendamentoSolicitacao();
        agendamento.setLocalAgendamento(local);

        var dto = AgendamentoSolicitacaoSimpleViewDTO.fromAgendamentoSolicitacao(agendamento);

        assertThat(dto.localAgendado()).isEqualTo("Hospital Municipal - Conceição do Almeida");
    }

    @Test
    void localSemCidade_devolveSoNomeLocal() {
        LocalAgendamento local = new LocalAgendamento();
        local.setNomeLocal("Hospital Municipal");

        AgendamentoSolicitacao agendamento = new AgendamentoSolicitacao();
        agendamento.setLocalAgendamento(local);

        var dto = AgendamentoSolicitacaoSimpleViewDTO.fromAgendamentoSolicitacao(agendamento);

        assertThat(dto.localAgendado()).isEqualTo("Hospital Municipal");
    }

    @Test
    void semLocalAgendamento_caiNoEnumLegado() {
        AgendamentoSolicitacao agendamento = new AgendamentoSolicitacao();
        agendamento.setLocalAgendado(LocalDeAgendamentoEnum.HOSPITAL_MUNICIPAL);

        var dto = AgendamentoSolicitacaoSimpleViewDTO.fromAgendamentoSolicitacao(agendamento);

        assertThat(dto.localAgendado()).isEqualTo("HOSPITAL MUNICIPAL");
    }

    @Test
    void semLocalNenhum_devolveNull() {
        AgendamentoSolicitacao agendamento = new AgendamentoSolicitacao();

        var dto = AgendamentoSolicitacaoSimpleViewDTO.fromAgendamentoSolicitacao(agendamento);

        assertThat(dto.localAgendado()).isNull();
    }
}
