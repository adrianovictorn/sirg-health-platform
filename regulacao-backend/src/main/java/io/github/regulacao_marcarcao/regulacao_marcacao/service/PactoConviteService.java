package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.InstanceContext;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.pacto.convite.*;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Pacto;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.PactoConvite;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PactoConviteStatus;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.PactoConviteRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.PactoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.MunicipioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Municipio;
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
public class PactoConviteService {

    private final PactoConviteRepository conviteRepository;
    private final PactoRepository pactoRepository;
    private final MunicipioRepository municipioRepository;
    private final RabbitTemplate rabbitTemplate;
    private final InstanceContext instanceContext;
    private final PactoService pactoService;

    @Transactional
    public List<ConviteViewDTO> listarMeusConvites(io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PactoConviteStatus status) {
        var local = instanceContext.getMunicipioLocal();
        var list = (status != null)
                ? conviteRepository.findByConvidadoMunicipioIdAndStatus(local.getId(), status)
                : conviteRepository.findByConvidadoMunicipioId(local.getId());
        return list.stream().map(ConviteViewDTO::from).collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public List<ConviteViewDTO> criarConvites(Long pactoId, List<UUID> convidados, String mensagem) {
        Pacto pacto = pactoRepository.findById(pactoId).orElseThrow(() -> new IllegalArgumentException("Pacto não encontrado"));
        var remetente = instanceContext.getMunicipioLocal();

        var convites = convidados.stream().map(destinoId -> {
            PactoConvite c = new PactoConvite();
            c.setPactoIdRemoto(pacto.getId());
            c.setPactoNome(pacto.getNome());
            c.setConvidadoMunicipioId(destinoId);
            c.setRemetenteMunicipioId(remetente.getId());
            c.setRemetenteNome(remetente.getNome());
            c.setMensagem(mensagem);
            c.setStatus(PactoConviteStatus.PENDENTE);
            c = conviteRepository.save(c);

            // envia mensagem para o convidado
            var destino = municipioRepository.findById(destinoId).orElse(null);
            String routingKey = String.format("convite.%s.pacto.%d", (destino != null ? destino.getNome() : destinoId.toString()).toUpperCase(), pacto.getId());
            var msg = new PactoConviteMensagemDTO(c.getToken(), pacto.getId(), pacto.getNome(), destinoId, remetente.getId(), remetente.getNome(), mensagem, c.getCreatedAt());
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, routingKey, msg);
            return ConviteViewDTO.from(c);
        }).collect(Collectors.toList());

        return convites;
    }

    @Transactional
    public void registrarConviteRecebido(PactoConviteMensagemDTO msg) {
        // idempotente por token
        var existente = conviteRepository.findByToken(msg.token());
        if (existente.isPresent()) return;

        PactoConvite c = new PactoConvite();
        c.setToken(msg.token());
        c.setPactoIdRemoto(msg.pactoId());
        c.setPactoNome(msg.pactoNome());
        c.setConvidadoMunicipioId(msg.convidadoMunicipioId());
        c.setRemetenteMunicipioId(msg.remetenteMunicipioId());
        c.setRemetenteNome(msg.remetenteNome());
        c.setMensagem(msg.mensagem());
        c.setStatus(PactoConviteStatus.PENDENTE);
        c.setCreatedAt(msg.createdAt() != null ? msg.createdAt() : LocalDateTime.now());
        conviteRepository.save(c);
    }

    @Transactional
    public List<ConviteViewDTO> listarPorPacto(Long pactoId) {
        return conviteRepository.findByPactoIdRemoto(pactoId).stream().map(ConviteViewDTO::from).collect(Collectors.toList());
    }

    @Transactional
    public ConviteViewDTO responder(UUID token, boolean aceitar) {
        PactoConvite c = conviteRepository.findByToken(token).orElseThrow(() -> new IllegalArgumentException("Convite inválido"));
        c.setStatus(aceitar ? PactoConviteStatus.ACEITO : PactoConviteStatus.RECUSADO);
        c.setRespondedAt(LocalDateTime.now());
        conviteRepository.save(c);

        if (aceitar) {
            // Notifica origem para adicionar membro ao pacto
            String routingKey = String.format("convite-aceite.%s.pacto.%d", c.getRemetenteNome().toUpperCase(), c.getPactoIdRemoto());
            var msg = new PactoConviteAceiteMensagemDTO(c.getToken(), c.getPactoIdRemoto(), c.getConvidadoMunicipioId(),
                    municipioRepository.findById(c.getConvidadoMunicipioId()).map(Municipio::getNome).orElse(""));
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, routingKey, msg);

            // Garante espelho local do pacto e vínculo com este município
            pactoService.espelharPactoRemoto(c.getPactoIdRemoto(), c.getPactoNome(), c.getRemetenteMunicipioId(), c.getConvidadoMunicipioId());
        }
        return ConviteViewDTO.from(c);
    }

    @Transactional
    public void processarAceite(PactoConviteAceiteMensagemDTO msg) {
        // marca convite como ACEITO antes para cumprir a regra de adicionar após aceite
        conviteRepository.findByToken(msg.token()).ifPresent(c -> {
            c.setStatus(PactoConviteStatus.ACEITO);
            c.setRespondedAt(LocalDateTime.now());
            conviteRepository.save(c);
        });
        // adiciona membro ao pacto local (origem)
        pactoService.adicionarMembros(msg.pactoId(), List.of(msg.convidadoMunicipioId()));
    }
}
