package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.whatsapp.WhatsAppConfigViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.whatsapp.WhatsAppEnvioUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.whatsapp.WhatsAppIndicadoresDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.whatsapp.WhatsAppLoteResultadoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.whatsapp.WhatsAppMensagemViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.whatsapp.WhatsAppReenvioCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppOrigem;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppResultado;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppTipoMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.WhatsAppConfigService;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.WhatsAppLembreteService;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.WhatsAppMensagemService;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.WhatsAppPainelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Painel de admin do WhatsApp: chave do envio, lista de operacao, volume e
 * disparos manuais.
 *
 * <p>Tudo aqui e so ADMIN: ligar o envio e disparar mensagem a paciente sao
 * decisoes de quem responde pela instancia. A rota do webhook
 * ({@code /api/webhooks/whatsapp}) e outra, publica e autenticada pela Meta.
 */
@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class WhatsAppAdminController {

    private static final int TAMANHO_MAXIMO_DA_PAGINA = 100;

    private final WhatsAppPainelService painelService;
    private final WhatsAppConfigService configService;
    private final WhatsAppMensagemService mensagemService;
    private final WhatsAppLembreteService lembreteService;

    @GetMapping("/config")
    public ResponseEntity<WhatsAppConfigViewDTO> estado() {
        return ResponseEntity.ok(painelService.estado());
    }

    /** Liga ou desliga o envio. Sem credenciais na instancia, ligar devolve 409. */
    @PutMapping("/config/envio")
    public ResponseEntity<WhatsAppConfigViewDTO> alterarEnvio(@Valid @RequestBody WhatsAppEnvioUpdateDTO dto,
                                                              Authentication authentication) {
        configService.alterarEnvio(dto.ligado(), authentication.getName());
        return ResponseEntity.ok(painelService.estado());
    }

    @GetMapping("/mensagens")
    public ResponseEntity<Page<WhatsAppMensagemViewDTO>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) WhatsAppTipoMensagem tipo,
            @RequestParam(required = false) WhatsAppResultado resultado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pagina = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), TAMANHO_MAXIMO_DA_PAGINA),
                Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(painelService.listar(de, ate, tipo, resultado, pagina));
    }

    @GetMapping("/indicadores")
    public ResponseEntity<WhatsAppIndicadoresDTO> indicadores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ResponseEntity.ok(painelService.indicadores(de, ate));
    }

    /** Reenvia a confirmacao ou o lembrete de um agendamento. Envio desligado: 409. */
    @PostMapping("/mensagens/reenviar")
    public ResponseEntity<WhatsAppMensagemViewDTO> reenviar(@Valid @RequestBody WhatsAppReenvioCreateDTO dto,
                                                            Authentication authentication) {
        var mensagem = mensagemService.reenviar(dto.agendamentoId(), dto.tipo(), authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(WhatsAppMensagemViewDTO.from(mensagem));
    }

    /** Roda agora o lote de lembretes. Quem ja recebeu o lembrete nao recebe de novo. */
    @PostMapping("/lembretes/executar")
    public ResponseEntity<WhatsAppLoteResultadoDTO> executarLembretes(Authentication authentication) {
        int enfileirados = lembreteService.executarLote(WhatsAppOrigem.MANUAL, authentication.getName());
        return ResponseEntity.ok(new WhatsAppLoteResultadoDTO(enfileirados));
    }
}
