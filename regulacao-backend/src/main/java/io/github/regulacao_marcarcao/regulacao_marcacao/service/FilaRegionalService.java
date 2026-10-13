package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.pacto.PactoEventoResumoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.regional.EncaminhamentoRegionalDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Municipio;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.regional.NotificacaoRegionalDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.pacto.convite.PactoConviteMensagemDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.pacto.convite.PactoConviteAceiteMensagemDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.pacto.PactoEventoClaimAceiteMensagemDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.MunicipioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.pacto.join.PactoJoinRequestMensagemDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.pacto.join.PactoJoinAceiteMensagemDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.notificacao.AgendamentoExternoMensagemDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.notificacao.AgendamentoRemarcadoMensagemDTO;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.beans.factory.annotation.Value;
import io.github.regulacao_marcarcao.regulacao_marcacao.config.InstanceContext;
import org.springframework.stereotype.Service;

@Service
public class FilaRegionalService {

    private final RabbitTemplate rabbitTemplate;
    private final SolicitacaoEspecialidadeRepository especialidadeRepository;
    private final MunicipioRepository municipioRepository;
    private final ObjectMapper objectMapper;
    private final String nomeMunicipioLocal; // Campo final para armazenar o valor
    private final InstanceContext instanceContext;
    private final PactoEventoService pactoEventoService;
    private final PactoConviteService pactoConviteService;
    private final PactoJoinService pactoJoinService;
    private final NotificacaoService notificacaoService;
    private final boolean ignoreSelfExecutor;

    private static final String EXCHANGE_NAME = "regional_topic_exchange";

    // Injeção de todas as dependências via construtor
    public FilaRegionalService(RabbitTemplate rabbitTemplate,
                               SolicitacaoEspecialidadeRepository especialidadeRepository,
                               MunicipioRepository municipioRepository,
                               ObjectMapper objectMapper,
                               @Value("${app.municipio.nome-identificador}") String nomeMunicipioLocal,
                               PactoEventoService pactoEventoService,
                               InstanceContext instanceContext,
                               PactoConviteService pactoConviteService,
                               PactoJoinService pactoJoinService,
                               NotificacaoService notificacaoService,
                               @Value("${app.notifications.ignore-self-executor:true}") boolean ignoreSelfExecutor) {
        this.rabbitTemplate = rabbitTemplate;
        this.especialidadeRepository = especialidadeRepository;
        this.municipioRepository = municipioRepository;
        this.objectMapper = objectMapper;
        this.nomeMunicipioLocal = nomeMunicipioLocal;
        this.pactoEventoService = pactoEventoService;
        this.instanceContext = instanceContext;
        this.pactoConviteService = pactoConviteService;
        this.pactoJoinService = pactoJoinService;
        this.notificacaoService = notificacaoService;
        this.ignoreSelfExecutor = ignoreSelfExecutor;
    }

    public void enviarParaFilaRegional(String municipioDestino, EncaminhamentoRegionalDTO dto) {
        String routingKey = String.format("encaminhamento.%s", municipioDestino.toUpperCase());
        try {
            String dtoString = objectMapper.writeValueAsString(dto);
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, routingKey, dtoString);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao serializar DTO para enviar para a fila", e);
        }
    }

    // Envia um evento de pacto (resumo) para um município de destino
    public void enviarEventoPacto(String municipioDestino, Long pactoId, PactoEventoResumoDTO resumo) {
        String routingKey = String.format("encaminhamento.%s.pacto.%d.nova", municipioDestino.toUpperCase(), pactoId);
        rabbitTemplate.convertAndSend(EXCHANGE_NAME, routingKey, resumo);
    }

    public void processarMensagemDaFila(String mensagemJson, String municipioAtual) {
         try {
            EncaminhamentoRegionalDTO dto = objectMapper.readValue(mensagemJson, EncaminhamentoRegionalDTO.class);
            // Lógica para salvar a solicitação recebida no banco de dados do município atual
            System.out.println("Processando encaminhamento para o município " + municipioAtual + " com ID de especialidade: " + dto.especialidadeId());
        } catch (Exception e) {
            System.err.println("Erro ao desserializar ou processar mensagem da fila: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Listener para a fila do município que roteia pelo routing key
    @RabbitListener(queues = "${app.municipio.queue-name}")
    public void onMessage(Object payload, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        try {
            if (routingKey == null) return;

            // Eventos de pactos (resumo de solicitações)
            if (routingKey.contains("encaminhamento.") && routingKey.contains(".pacto.") && routingKey.contains(".nova")) {
                PactoEventoResumoDTO resumo = convertPayload(payload, PactoEventoResumoDTO.class);
                pactoEventoService.registrarEventoRecebido(resumo);
                return;
            }

            // Convites de pacto
            if (routingKey.startsWith("convite-aceite.")) {
                PactoConviteAceiteMensagemDTO aceite = convertPayload(payload, PactoConviteAceiteMensagemDTO.class);
                pactoConviteService.processarAceite(aceite);
                return;
            }

            if (routingKey.startsWith("convite.")) {
                PactoConviteMensagemDTO convite = convertPayload(payload, PactoConviteMensagemDTO.class);
                pactoConviteService.registrarConviteRecebido(convite);
                return;
            }

            // Solicitações de ingresso
            if (routingKey.startsWith("ingresso-aceite.")) {
                PactoJoinAceiteMensagemDTO aceite = convertPayload(payload, PactoJoinAceiteMensagemDTO.class);
                pactoJoinService.processarAceite(aceite);
                return;
            }
            if (routingKey.startsWith("ingresso.")) {
                PactoJoinRequestMensagemDTO req = convertPayload(payload, PactoJoinRequestMensagemDTO.class);
                pactoJoinService.registrarRecebido(req);
                return;
            }

            // Evento de pacto consumido (claim aceito) → atualiza origem
            if (routingKey.startsWith("evento-claim-aceite.")) {
                PactoEventoClaimAceiteMensagemDTO msg = convertPayload(payload, PactoEventoClaimAceiteMensagemDTO.class);
                pactoEventoService.registrarClaimRemoto(msg);
                return;
            }

            // Notificação de agendamento externo
            if (routingKey.startsWith("agendamento-externo.")) {
                AgendamentoExternoMensagemDTO dto = convertPayload(payload, AgendamentoExternoMensagemDTO.class);
                // Ignora se o executante for o próprio município
                if (dto.municipioExecutor() != null && dto.municipioExecutor().equalsIgnoreCase(nomeMunicipioLocal)) {
                    if (ignoreSelfExecutor) {
                        return;
                    }
                    // Caso de desenvolvimento: aceita self-broadcast para validar UI
                }
                String resumo = String.format(
                        "Solicitação agendada: CPF %s, %s; executor: %s; data: %s",
                        dto.cpfMascarado(), dto.nomePaciente(), dto.municipioExecutor(), String.valueOf(dto.dataAgendada())
                );
                notificacaoService.criar("AGENDAMENTO_EXTERNO", resumo, "/filas/compartilhadas", java.util.Map.of(
                        "cpf", dto.cpfMascarado(),
                        "nome", dto.nomePaciente(),
                        "data", String.valueOf(dto.dataAgendada()),
                        "executor", dto.municipioExecutor()
                ));
                return;
            }

            // Remarcação de agendamento externo (migração de cota no município executor)
            if (routingKey.startsWith("agendamento-remarcado.")) {
                AgendamentoRemarcadoMensagemDTO dto = convertPayload(payload, AgendamentoRemarcadoMensagemDTO.class);
                if (dto.municipioExecutor() != null && dto.municipioExecutor().equalsIgnoreCase(nomeMunicipioLocal)) {
                    if (ignoreSelfExecutor) {
                        return;
                    }
                }
                String resumo = String.format(
                        "Agendamento remarcado: CPF %s, de %s para %s; executor: %s",
                        dto.cpfMascarado(), String.valueOf(dto.dataAnterior()), String.valueOf(dto.dataNova()),
                        dto.municipioExecutor()
                );
                notificacaoService.criar("AGENDAMENTO_REMARCADO", resumo, "/filas/compartilhadas", java.util.Map.of(
                        "cpf", dto.cpfMascarado(),
                        "nome", dto.nomePaciente(),
                        "dataAnterior", String.valueOf(dto.dataAnterior()),
                        "data", String.valueOf(dto.dataNova()),
                        "executor", dto.municipioExecutor()
                ));
                return;
            }

            // Outros tipos de mensagens poderão ser tratados aqui
            System.err.println("Mensagem com routingKey não tratada: " + routingKey + ", payloadType=" + payload.getClass());
        } catch (Exception e) {
            System.err.println("Erro ao processar mensagem da fila (routingKey=" + routingKey + "): " + e.getMessage());
            e.printStackTrace();
        }
    }

    private <T> T convertPayload(Object payload, Class<T> type) throws Exception {
        // Já é do tipo esperado
        if (type.isInstance(payload)) {
            return type.cast(payload);
        }

        // Mensagem Spring Messaging: extrai o payload interno e reconverte
        if (payload instanceof org.springframework.messaging.Message<?> springMsg) {
            return convertPayload(springMsg.getPayload(), type);
        }

        // Mensagem AMQP: extrai o corpo (byte[]) e parseia como JSON
        if (payload instanceof org.springframework.amqp.core.Message amqpMsg) {
            byte[] body = amqpMsg.getBody();
            return objectMapper.readValue(body, type);
        }

        // Casos comuns
        if (payload instanceof byte[] b) {
            return objectMapper.readValue(b, type);
        }
        if (payload instanceof String s) {
            return objectMapper.readValue(s, type);
        }

        // Fallback: tenta converter estruturas simples (Map -> DTO)
        return objectMapper.convertValue(payload, type);
    }

    /**
     * Processa uma notificação recebida via payload FHIR já convertido.
     * Este método é o ponto para aplicar regras de domínio (ex.: criar Solicitação, mapear paciente, etc.).
     */
    public void processarNotificacao(NotificacaoRegionalDTO notificacao, String municipioAtual) {
        // Exemplo: logar e, futuramente, persistir/criar entidades locais
        System.out.println(
                "Processando notificação (FHIR->DTO) para o município " + municipioAtual +
                ": especialidadeId=" + notificacao.especialidadeId() +
                ", descricao='" + notificacao.especialidadeDescricao() + "'" +
                ", origem='" + notificacao.municipioOrigem() + "'"
        );
        // TODO: Implementar criação de entidades locais (Solicitação, Especialidade, etc.)
    }

    private Municipio getMunicipioLocal() {
        return municipioRepository.findByNome(nomeMunicipioLocal)
                .orElseThrow(() -> new RuntimeException("Configuração de município local inválida: " + nomeMunicipioLocal));
    }
}
