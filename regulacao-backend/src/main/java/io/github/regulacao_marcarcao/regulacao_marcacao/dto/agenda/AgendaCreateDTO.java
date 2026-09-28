package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoOfertaAgendaEnum;

/**
 * Abertura de agenda: executante -> profissional/cbo -> procedimento
 * (individual ou grupo) -> vigencia e dias -> horario -> distribuicao de vagas
 * por unidade solicitante.
 *
 * <p>{@code diasSemana} usa as siglas SEG,TER,QUA,QUI,SEX,SAB,DOM. Data unica
 * = {@code vigenciaInicio} igual a {@code vigenciaFim} com um unico dia.
 */
public record AgendaCreateDTO(
        Long estabelecimentoExecutanteId,
        Long profissionalId,
        Long cboId,
        Long localAgendamentoId,
        String localDescricao,
        TipoOfertaAgendaEnum tipoOferta,
        Long grupoEspecialidadesId,
        List<Long> especialidadeIds,
        LocalDate vigenciaInicio,
        LocalDate vigenciaFim,
        List<String> diasSemana,
        LocalTime horaInicial,
        LocalTime horaFinal,
        String observacao,
        List<AgendaDistribuicaoInputDTO> distribuicoes) {
}
