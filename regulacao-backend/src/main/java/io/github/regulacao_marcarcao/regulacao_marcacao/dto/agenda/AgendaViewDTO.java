package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Agenda;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoOfertaAgendaEnum;

public record AgendaViewDTO(
        Long id,
        Long estabelecimentoExecutanteId,
        String estabelecimentoExecutanteNome,
        Long profissionalId,
        String profissionalNome,
        Long cboId,
        String cboDescricao,
        Long localAgendamentoId,
        String localAgendamentoNome,
        String localDescricao,
        TipoOfertaAgendaEnum tipoOferta,
        Long grupoEspecialidadesId,
        String grupoEspecialidadesNome,
        List<AgendaEspecialidadeViewDTO> especialidades,
        LocalDate vigenciaInicio,
        LocalDate vigenciaFim,
        String diasSemana,
        LocalTime horaInicial,
        LocalTime horaFinal,
        String observacao,
        boolean ativo,
        List<AgendaDistribuicaoViewDTO> distribuicoes,
        List<AgendaOcorrenciaViewDTO> ocorrencias,
        LocalDateTime criadoEm) {

    public static AgendaViewDTO from(Agenda a) {
        return new AgendaViewDTO(
                a.getId(),
                a.getEstabelecimentoExecutante().getId(),
                a.getEstabelecimentoExecutante().getNome(),
                a.getProfissional().getId(),
                a.getProfissional().getNome(),
                a.getCbo() != null ? a.getCbo().getId() : null,
                a.getCbo() != null ? a.getCbo().getDescricao() : null,
                a.getLocalAgendamento() != null ? a.getLocalAgendamento().getId() : null,
                a.getLocalAgendamento() != null ? a.getLocalAgendamento().getNomeLocal() : null,
                a.getLocalDescricao(),
                a.getTipoOferta(),
                a.getGrupoEspecialidades() != null ? a.getGrupoEspecialidades().getId() : null,
                a.getGrupoEspecialidades() != null ? a.getGrupoEspecialidades().getNome() : null,
                a.getEspecialidades().stream().map(AgendaEspecialidadeViewDTO::from).toList(),
                a.getVigenciaInicio(),
                a.getVigenciaFim(),
                a.getDiasSemana(),
                a.getHoraInicial(),
                a.getHoraFinal(),
                a.getObservacao(),
                a.isAtivo(),
                a.getDistribuicoes().stream().map(AgendaDistribuicaoViewDTO::from).toList(),
                a.getOcorrencias().stream().map(AgendaOcorrenciaViewDTO::from).toList(),
                a.getCriadoEm());
    }
}
