package io.github.regulacao_marcarcao.regulacao_marcacao.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "regional_topic_exchange";

 
    @Bean
    public TopicExchange regionalExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }


    
    @Bean
    public Queue muncipioQueue(@Value("${app.municipio.queue-name}") String queueName) {
        return new Queue(queueName, true);
    }

 
    
    @Bean
    public Binding binding(Queue muncipioQueue, TopicExchange regionalExchange, @Value("${app.municipio.nome-identificador}") String nomeMunicipio) {
        String routingKey = String.format("encaminhamento.%s.#", nomeMunicipio.toUpperCase());
        return BindingBuilder.bind(muncipioQueue).to(regionalExchange).with(routingKey);
    }

    @Bean
    public Binding bindingConvite(Queue muncipioQueue, TopicExchange regionalExchange, @Value("${app.municipio.nome-identificador}") String nomeMunicipio) {
        String routingKey = String.format("convite.%s.#", nomeMunicipio.toUpperCase());
        return BindingBuilder.bind(muncipioQueue).to(regionalExchange).with(routingKey);
    }

    @Bean
    public Binding bindingConviteAceite(Queue muncipioQueue, TopicExchange regionalExchange, @Value("${app.municipio.nome-identificador}") String nomeMunicipio) {
        String routingKey = String.format("convite-aceite.%s.#", nomeMunicipio.toUpperCase());
        return BindingBuilder.bind(muncipioQueue).to(regionalExchange).with(routingKey);
    }

    @Bean
    public Binding bindingJoin(Queue muncipioQueue, TopicExchange regionalExchange, @Value("${app.municipio.nome-identificador}") String nomeMunicipio) {
        String routingKey = String.format("ingresso.%s.#", nomeMunicipio.toUpperCase());
        return BindingBuilder.bind(muncipioQueue).to(regionalExchange).with(routingKey);
    }

    @Bean
    public Binding bindingJoinAceite(Queue muncipioQueue, TopicExchange regionalExchange, @Value("${app.municipio.nome-identificador}") String nomeMunicipio) {
        String routingKey = String.format("ingresso-aceite.%s.#", nomeMunicipio.toUpperCase());
        return BindingBuilder.bind(muncipioQueue).to(regionalExchange).with(routingKey);
    }

    // Notificação ao município de origem quando um evento do pacto é consumido (claim aceito)
    @Bean
    public Binding bindingEventoClaimAceite(Queue muncipioQueue, TopicExchange regionalExchange, @Value("${app.municipio.nome-identificador}") String nomeMunicipio) {
        String routingKey = String.format("evento-claim-aceite.%s.#", nomeMunicipio.toUpperCase());
        return BindingBuilder.bind(muncipioQueue).to(regionalExchange).with(routingKey);
    }

    // Notificação de agendamento externo para o município de origem
    @Bean
    public Binding bindingAgendamentoExterno(Queue muncipioQueue, TopicExchange regionalExchange, @Value("${app.municipio.nome-identificador}") String nomeMunicipio) {
        String routingKey = String.format("agendamento-externo.%s.#", nomeMunicipio.toUpperCase());
        return BindingBuilder.bind(muncipioQueue).to(regionalExchange).with(routingKey);
    }

    // Remarcação de agendamento (migração de cota) de paciente deste município, feita em outro
    @Bean
    public Binding bindingAgendamentoRemarcado(Queue muncipioQueue, TopicExchange regionalExchange, @Value("${app.municipio.nome-identificador}") String nomeMunicipio) {
        String routingKey = String.format("agendamento-remarcado.%s.#", nomeMunicipio.toUpperCase());
        return BindingBuilder.bind(muncipioQueue).to(regionalExchange).with(routingKey);
    }

    // Fallback para broadcast de agendamentos externos (origem desconhecida)
    @Bean
    public Binding bindingAgendamentoExternoBroadcast(Queue muncipioQueue, TopicExchange regionalExchange) {
        String routingKey = "agendamento-externo.BROADCAST.#";
        return BindingBuilder.bind(muncipioQueue).to(regionalExchange).with(routingKey);
    }

    /**
     * Define o conversor de mensagens para JSON.
     * Essencial para que o Spring consiga serializar/desserializar os DTOs.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
