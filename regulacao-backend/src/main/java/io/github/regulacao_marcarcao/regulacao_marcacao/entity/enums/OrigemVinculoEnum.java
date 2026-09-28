package io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums;

/**
 * De onde veio um vinculo profissional x estabelecimento.
 *
 * <p>MANUAL foi digitado na tela (ou herdado do antigo {@code profissional.unidade}
 * na V88). IMPORTACAO_CNES veio do arquivo de extracao de profissionais do
 * DATASUS.
 *
 * <p>A distincao existe para a reimportacao: um vinculo importado pode ser
 * atualizado pelo arquivo seguinte sem discussao, enquanto um vinculo digitado a
 * mao representa uma decisao da coordenacao e nao e sobrescrito em silencio.
 */
public enum OrigemVinculoEnum {
    MANUAL,
    IMPORTACAO_CNES
}
