package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.AntecedenciaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.BalancoFilaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.CotaUtilizacaoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.FilaEnvelhecimentoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.OcupacaoProfissionalViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.WhatsAppAlcanceViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.WhatsAppTelefoneInvalidoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.IndicadoresCotaService;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.IndicadoresFilaService;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.WhatsAppAlcanceService;
import lombok.RequiredArgsConstructor;

/**
 * Indicadores gerenciais (somente leitura, somente agregados) — ADMIN e GESTOR.
 *
 * <p>Ficam fora de {@code /api/fechamento}, de proposito: aquele controller so
 * nega ADMIN_UNIDADE e responde a qualquer outro perfil autenticado. Aqui a
 * regra e positiva e vale para a classe inteira, entao um endpoint novo ja
 * nasce restrito.
 *
 * <p>Nenhuma resposta daqui traz valor em reais: os indicadores financeiros
 * ficam em {@code /api/custos/indicadores}.
 *
 * <p>As datas chegam como texto e sao validadas no servico, para que formato
 * invalido volte como 400 com mensagem.
 */
@RestController
@RequestMapping("/api/indicadores")
@PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
@RequiredArgsConstructor
public class IndicadoresGerenciaisController {

    private final IndicadoresFilaService filaService;
    private final IndicadoresCotaService cotaService;
    private final WhatsAppAlcanceService whatsAppService;

    @GetMapping("/fila/envelhecimento")
    public ResponseEntity<FilaEnvelhecimentoViewDTO> envelhecimentoDaFila(
            @RequestParam(required = false) Long unidadeId,
            Authentication authentication) {
        return ResponseEntity.ok(filaService.envelhecimento(unidadeId, cpf(authentication)));
    }

    @GetMapping("/agendamentos/antecedencia")
    public ResponseEntity<AntecedenciaViewDTO> antecedenciaDoAgendamento(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate,
            Authentication authentication) {
        return ResponseEntity.ok(filaService.antecedencia(unidadeId, de, ate, cpf(authentication)));
    }

    @GetMapping("/fila/balanco")
    public ResponseEntity<BalancoFilaViewDTO> balancoDaFila(
            @RequestParam(required = false) Long unidadeId,
            Authentication authentication) {
        return ResponseEntity.ok(filaService.balanco(unidadeId, cpf(authentication)));
    }

    @GetMapping("/cotas/utilizacao")
    public ResponseEntity<CotaUtilizacaoViewDTO> utilizacaoDeCota(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate,
            Authentication authentication) {
        return ResponseEntity.ok(cotaService.utilizacao(unidadeId, de, ate, cpf(authentication)));
    }

    @GetMapping("/cotas/ocupacao-profissional")
    public ResponseEntity<OcupacaoProfissionalViewDTO> ocupacaoPorProfissional(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate,
            Authentication authentication) {
        return ResponseEntity.ok(cotaService.ocupacaoPorProfissional(unidadeId, de, ate, cpf(authentication)));
    }

    // WhatsApp: agregados para a gestao. O painel operacional (fila, mensagens,
    // reenvio, cobranca) continua em /api/whatsapp, so para ADMIN.

    @GetMapping("/whatsapp/alcance")
    public ResponseEntity<WhatsAppAlcanceViewDTO> alcanceDoWhatsApp(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate,
            Authentication authentication) {
        return ResponseEntity.ok(whatsAppService.alcance(unidadeId, de, ate, cpf(authentication)));
    }

    @GetMapping("/whatsapp/telefone-invalido")
    public ResponseEntity<WhatsAppTelefoneInvalidoViewDTO> telefoneInvalido(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate,
            Authentication authentication) {
        return ResponseEntity.ok(whatsAppService.telefoneInvalido(unidadeId, de, ate, cpf(authentication)));
    }

    private static String cpf(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
