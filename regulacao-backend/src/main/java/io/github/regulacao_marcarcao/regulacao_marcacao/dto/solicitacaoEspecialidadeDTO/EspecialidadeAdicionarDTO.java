package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacaoEspecialidadeDTO;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.EspecialidadesEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import jakarta.validation.constraints.NotNull;

// Compatível com a nova entidade Especialidade: pode enviar o ID do catálogo
// ou manter o envio do Enum antigo (mapeado por código). Se ambos vierem, o ID tem prioridade.
// profissionalId é opcional.
public record EspecialidadeAdicionarDTO(
    Long especialidadeId,
    @NotNull
    EspecialidadesEnum especialidadeSolicitada,
    Long profissionalId,
    @NotNull StatusDaMarcacao status,
    @NotNull PrioridadeDaMarcacaoEnum prioridade
) {
}
