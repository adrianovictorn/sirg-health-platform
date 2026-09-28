package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes;

import java.util.List;

/**
 * O que a importacao gravou de fato.
 *
 * <p>Separa criado de reaproveitado porque a coordenacao reimporta o arquivo
 * inteiro quando muda uma linha: ler "38 vinculos ja existentes, 2 criados"
 * confirma que a reimportacao foi inofensiva.
 */
public record CnesImportacaoResultadoDTO(
        int profissionaisCriados,
        int profissionaisReaproveitados,
        int vinculosCriados,
        int vinculosJaExistentes,
        int cbosCriados,
        int ignoradas,
        List<String> avisos) {
}
