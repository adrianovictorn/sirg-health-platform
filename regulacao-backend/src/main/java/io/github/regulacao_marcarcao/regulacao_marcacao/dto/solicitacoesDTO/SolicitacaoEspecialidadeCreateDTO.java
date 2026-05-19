package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.EspecialidadesEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;

// Compatível com ambos: pode enviar o ID da especialidade do catálogo
// ou o código (enum legacy). Se ambos vierem, o ID tem prioridade.
// profissionalId é opcional — nulo significa sem profissional solicitante.
public record SolicitacaoEspecialidadeCreateDTO(
    Long especialidadeId,
    EspecialidadesEnum especialidadeSolicitada,
    Long profissionalId,
    StatusDaMarcacao status,
    PrioridadeDaMarcacaoEnum prioridade
) { }
