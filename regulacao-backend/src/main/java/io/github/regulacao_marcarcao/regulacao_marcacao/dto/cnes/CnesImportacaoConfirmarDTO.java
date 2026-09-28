package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/**
 * As linhas que o operador marcou na previa.
 *
 * <p>{@code unidadeIdPadrao} cobre o caso em que o arquivo nao traz CNES
 * reconhecivel (ou traz o de uma unidade ainda nao cadastrada) e a coordenacao
 * escolhe o estabelecimento na mao. Quando informado, vale para as linhas sem
 * unidade resolvida.
 */
public record CnesImportacaoConfirmarDTO(

        Long unidadeIdPadrao,

        @NotEmpty(message = "Selecione ao menos um profissional para importar.")
        List<CnesProfissionalLinhaDTO> linhas) {
}
