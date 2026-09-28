package io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional;

import jakarta.validation.constraints.NotNull;

/**
 * Vinculo criado a mao na tela de profissionais.
 *
 * <p>{@code cboId} e opcional: a coordenacao as vezes cadastra o vinculo antes de
 * saber a ocupacao registrada no CNES, e barrar isso empurraria o operador a
 * escolher um CBO qualquer.
 */
public record ProfissionalVinculoCreateDTO(

        @NotNull(message = "Informe o estabelecimento do vínculo.")
        Long unidadeId,

        Long cboId) {
}
