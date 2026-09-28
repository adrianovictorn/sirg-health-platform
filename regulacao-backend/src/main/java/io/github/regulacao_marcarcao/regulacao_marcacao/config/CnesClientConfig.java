package io.github.regulacao_marcarcao.regulacao_marcacao.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP da API de dados abertos do DATASUS (CNES).
 *
 * <p>Timeouts curtos de proposito: a consulta ao CNES e uma CONVENIENCIA do
 * cadastro de estabelecimento, nunca um pre-requisito. Se o DATASUS estiver
 * lento ou fora do ar, a tela precisa cair para o preenchimento manual em
 * poucos segundos em vez de deixar o operador esperando.
 */
@Configuration
public class CnesClientConfig {

    @Bean
    public RestClient cnesRestClient(
            @Value("${app.cnes.base-url}") String baseUrl,
            @Value("${app.cnes.timeout-conexao-ms:3000}") int timeoutConexao,
            @Value("${app.cnes.timeout-leitura-ms:8000}") int timeoutLeitura) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutConexao));
        factory.setReadTimeout(Duration.ofMillis(timeoutLeitura));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
