package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;

public record EspecialidadeAgendamentoDTO(
    Long id,
    String codigo,
    String nome
) {

    public static EspecialidadeAgendamentoDTO fromEntity(SolicitacaoEspecialidade especialidade) {
        if (especialidade == null) {
            return new EspecialidadeAgendamentoDTO(null, null, null);
        }

        String codigo = especialidade.getEspecialidadeSolicitada() != null
            ? especialidade.getEspecialidadeSolicitada().getCodigo()
            : especialidade.getEspecialidadeCodigoLegacy();

        String nome = especialidade.getEspecialidadeSolicitada() != null
            ? especialidade.getEspecialidadeSolicitada().getNome()
            : codigo;

        return new EspecialidadeAgendamentoDTO(especialidade.getId(), codigo, nome);
    }
}
