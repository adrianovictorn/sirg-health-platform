package io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores;

import java.time.LocalDate;
import java.time.LocalDateTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.localAgendamentoDTO.LocalAgendamentoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.UserViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.FechamentoIndicadoresDia;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;


public record FechamentoIndicadoresDiaDTO(
    Long id,
    LocalDate dataReferencia,
    LocalAgendamentoViewDTO localAgendamento,
    GrupoRelatorioSimpleViewDTO grupoRelatorio,
    int agendadosTotal,
    int atendidosTotal,
    int faltososTotal,
    int canceladosTotal,
    int solicitacoesCadastradasTotal,
    LocalDateTime fechadoEm,
    UserViewDTO fechadoPor
){
    public static FechamentoIndicadoresDiaDTO fromDTO(FechamentoIndicadoresDia entity){
        GrupoRelatorio grupoRelatorio = entity.getGrupoRelatorio();
        LocalAgendamento localAgendamento = entity.getLocalAgendamento();
        User usuario = entity.getFechadoPor();
        UserViewDTO fechadoPor = usuario == null ? null : UserViewDTO.from(usuario);
        
        return new FechamentoIndicadoresDiaDTO(
            entity.getId(),
            entity.getDataReferencia(),
            LocalAgendamentoViewDTO.fromEntity(localAgendamento),
            GrupoRelatorioSimpleViewDTO.fromEntity(grupoRelatorio),
            entity.getAgendadosTotal(),
            entity.getAtendidosTotal(),
            entity.getFaltososTotal(),
            entity.getCanceladosTotal(),
            entity.getSolicitacoesCadastradasTotal(),
            entity.getFechadoEm(),
            fechadoPor
        );
    }

}

