package io.github.regulacao_marcarcao.regulacao_marcacao.dto.fila;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.FilaEsperaPacienteProjection;

/**
 * Uma linha da fila de espera: um paciente (solicitacao) com os pedidos que
 * batem com os filtros. A espera exibida e a do pedido mais antigo entre eles.
 * {@code unidadeId} nulo = solicitacao sem unidade vinculada.
 */
public record FilaEsperaPacienteViewDTO(
    Long solicitacaoId,
    String nomePaciente,
    String cpfPaciente,
    String cns,
    LocalDate dataNascimento,
    Long unidadeId,
    String unidadeNome,
    LocalDateTime entradaMaisAntiga,
    long diasEspera,
    List<FilaEsperaItemViewDTO> itens
) {
    public static FilaEsperaPacienteViewDTO from(FilaEsperaPacienteProjection p, long diasEspera,
            List<FilaEsperaItemViewDTO> itens) {
        return new FilaEsperaPacienteViewDTO(
            p.getSolicitacaoId(),
            p.getNomePaciente(),
            p.getCpfPaciente(),
            p.getCns(),
            p.getDataNascimento(),
            p.getUnidadeId(),
            p.getUnidadeNome(),
            p.getEntradaMaisAntiga(),
            diasEspera,
            itens);
    }
}
