package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import java.time.LocalDate;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.EspecialidadesEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;

public record SolicitacaoListFiltersDTO(
        String nomePaciente,
        Long unidadeId,
        List<EspecialidadesEnum> especialidadeSolicitada,
        StatusDaMarcacao status,
        List<PrioridadeDaMarcacaoEnum> prioridade,
        LocalDate datainicio,
        LocalDate dataFim) {
}
