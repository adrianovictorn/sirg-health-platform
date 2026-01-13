package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.InstanceContext;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.pacto.join.*;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Municipio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Pacto;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.PactoJoinRequest;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PactoConviteStatus;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.PactoJoinRequestRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.PactoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.regulacao_marcarcao.regulacao_marcacao.config.RabbitMQConfig.EXCHANGE_NAME;

@Service
@RequiredArgsConstructor
public class PactoJoinService {

    private final PactoJoinRequestRepository joinRepository;
    private final PactoRepository pactoRepository;
    private final InstanceContext instanceContext;
    private final RabbitTemplate rabbitTemplate;
    private final PactoService pactoService;

    @Transactional
    public JoinRequestViewDTO solicitar(Long pactoId, String mensagem) {
        Pacto pacto = pactoRepository.findById(pactoId).orElseThrow(() -> new IllegalArgumentException("Pacto não encontrado"));
        Municipio local = instanceContext.getMunicipioLocal();

        // cria local
        PactoJoinRequest r = new PactoJoinRequest();
        r.setPactoIdRemoto(pacto.getId());
        r.setSolicitanteMunicipioId(local.getId());
        r.setSolicitanteNome(local.getNome());
        r.setMensagem(mensagem);
        r.setStatus(PactoConviteStatus.PENDENTE);
        r = joinRepository.save(r);

        // envia ao criador do pacto
        String destinoNome = pacto.getMunicipioCriador() != null ? pacto.getMunicipioCriador().getNome() : null;
        if (destinoNome != null) {
            String routingKey = String.format("ingresso.%s.pacto.%d", destinoNome.toUpperCase(), pacto.getId());
            var msg = new PactoJoinRequestMensagemDTO(r.getToken(), pacto.getId(), local.getId(), local.getNome(), mensagem, r.getCreatedAt());
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, routingKey, msg);
        }

        return JoinRequestViewDTO.from(r);
    }

    @Transactional
    public void registrarRecebido(PactoJoinRequestMensagemDTO msg) {
        // idempotência
        if (joinRepository.findByToken(msg.token()).isPresent()) return;
        PactoJoinRequest r = new PactoJoinRequest();
        r.setToken(msg.token());
        r.setPactoIdRemoto(msg.pactoId());
        r.setSolicitanteMunicipioId(msg.solicitanteMunicipioId());
        r.setSolicitanteNome(msg.solicitanteNome());
        r.setMensagem(msg.mensagem());
        r.setStatus(PactoConviteStatus.PENDENTE);
        r.setCreatedAt(msg.createdAt() != null ? msg.createdAt() : LocalDateTime.now());
        joinRepository.save(r);
    }

    @Transactional
    public List<JoinRequestViewDTO> listarPorPacto(Long pactoId) {
        return joinRepository.findByPactoIdRemoto(pactoId).stream().map(JoinRequestViewDTO::from).collect(Collectors.toList());
    }

    @Transactional
    public List<JoinRequestViewDTO> listarMinhasSolicitacoes(PactoConviteStatus status) {
        var local = instanceContext.getMunicipioLocal();
        var list = (status != null)
                ? joinRepository.findBySolicitanteMunicipioIdAndStatus(local.getId(), status)
                : joinRepository.findBySolicitanteMunicipioId(local.getId());
        return list.stream().map(JoinRequestViewDTO::from).collect(Collectors.toList());
    }

    @Transactional
    public JoinRequestViewDTO responder(UUID token, boolean aceitar) {
        PactoJoinRequest r = joinRepository.findByToken(token).orElseThrow(() -> new IllegalArgumentException("Solicitação inválida"));
        r.setStatus(aceitar ? PactoConviteStatus.ACEITO : PactoConviteStatus.RECUSADO);
        r.setRespondedAt(LocalDateTime.now());
        joinRepository.save(r);

        if (aceitar) {
            // adiciona membro ao pacto local
            pactoService.adicionarMembros(r.getPactoIdRemoto(), List.of(r.getSolicitanteMunicipioId()));
            // notifica solicitante
            String routingKey = String.format("ingresso-aceite.%s.pacto.%d", r.getSolicitanteNome().toUpperCase(), r.getPactoIdRemoto());
            var msg = new PactoJoinAceiteMensagemDTO(r.getToken(), r.getPactoIdRemoto(), r.getSolicitanteMunicipioId(), r.getSolicitanteNome());
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, routingKey, msg);
        }
        return JoinRequestViewDTO.from(r);
    }

    @Transactional
    public void processarAceite(PactoJoinAceiteMensagemDTO msg) {
        joinRepository.findByToken(msg.token()).ifPresent(r -> {
            r.setStatus(PactoConviteStatus.ACEITO);
            r.setRespondedAt(LocalDateTime.now());
            joinRepository.save(r);
        });
    }
}
