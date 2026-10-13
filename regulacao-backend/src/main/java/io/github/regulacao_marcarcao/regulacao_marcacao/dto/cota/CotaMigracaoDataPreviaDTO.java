package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

/**
 * O que a migracao faria, sem alterar nada. Somente contagens: nenhum dado
 * nominal de paciente e nenhum valor em reais.
 *
 * @param agendamentos             0 = a cota nao tem agendamentos (a tela segue com a edicao normal)
 * @param divergente               o numero de itens que incidem na cota difere de
 *                                 {@code quantidadeUtilizada} (ex.: agendados pela administracao)
 * @param existeCotaNoDestino      ja ha cota com a mesma chave na data nova: as duas serao unificadas
 * @param solicitacoesFederadas    solicitacoes de paciente de outro municipio, que recebem aviso
 */
public record CotaMigracaoDataPreviaDTO(
        int agendamentos,
        int itens,
        int itensAgendados,
        int itensRealizados,
        int itensCanceladosOuFaltosos,
        int quantidadeUtilizada,
        boolean divergente,
        boolean existeCotaNoDestino,
        Integer destinoQuantidadeTotal,
        Integer destinoQuantidadeUtilizada,
        int solicitacoesFederadas) {
}
