package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cbo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro avulso de ocupacao, para o que a importacao do CNES nao trouxer.
 *
 * <p>O padrao CBO 2002 tem 6 digitos; validar aqui evita que um codigo truncado
 * entre na base e depois nao case com o codigo do arquivo do DATASUS numa
 * importacao futura — o upsert e feito por codigo.
 */
public record CboCreateDTO(

        @NotBlank(message = "O código do CBO é obrigatório.")
        @Pattern(regexp = "\\d{6}", message = "O código do CBO deve ter exatamente 6 dígitos.")
        String codigo,

        @NotBlank(message = "A descrição da ocupação é obrigatória.")
        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
        String descricao) {
}
