package io.github.regulacao_marcarcao.regulacao_marcacao.exceptions;

/**
 * Um servico de terceiro nao respondeu (hoje: a API do CNES/DATASUS).
 *
 * <p>Existe para separar "o DATASUS esta fora do ar" de "os dados estao
 * errados": a primeira situacao e temporaria e o operador deve seguir com o
 * cadastro manual, a segunda exige corrigir o que foi digitado. Vira 503 no
 * {@link GlobalExceptionHandler}.
 */
public class ServicoExternoIndisponivelException extends RuntimeException {

    public ServicoExternoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
