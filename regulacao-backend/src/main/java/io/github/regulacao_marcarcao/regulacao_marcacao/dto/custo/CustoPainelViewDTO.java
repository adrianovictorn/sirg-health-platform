package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.time.LocalDate;
import java.util.List;

/**
 * Painel de custos: so numeros agregados, nenhum dado de paciente.
 *
 * @param dataDe          inicio do periodo aplicado a agendado e concluido (data agendada)
 * @param dataAte         fim do periodo
 * @param total           as tres visoes para o filtro inteiro
 * @param porUnidade      as tres visoes por unidade; solicitacao sem unidade vem com id nulo
 * @param porEspecialidade as tres visoes por especialidade, da mais cara para a mais barata
 */
public record CustoPainelViewDTO(
        LocalDate dataDe,
        LocalDate dataAte,
        CustoPainelLinhaViewDTO total,
        List<CustoPainelLinhaViewDTO> porUnidade,
        List<CustoPainelLinhaViewDTO> porEspecialidade) {
}
