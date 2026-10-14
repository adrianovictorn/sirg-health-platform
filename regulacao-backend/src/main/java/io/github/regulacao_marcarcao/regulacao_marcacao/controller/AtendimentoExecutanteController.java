package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AgendaGrupoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoCancelamentoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoColetaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoFaltaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoPacienteViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoResumoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.AtendimentoExecutanteService;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.ExcelService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/atendimentos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
public class AtendimentoExecutanteController {

    private final AtendimentoExecutanteService service;
    private final ExcelService excelService;

    @GetMapping("/resumo")
    public ResponseEntity<AtendimentoResumoViewDTO> resumir(
            @RequestParam(required = false, name = "data") LocalDate data,
            @RequestParam(required = false, name = "grupo") String grupo,
            Authentication authentication) {
        String callerCpf = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.resumir(data, grupo, callerCpf));
    }

    @GetMapping
    public ResponseEntity<Page<AtendimentoPacienteViewDTO>> listar(
            @RequestParam(required = false, name = "data") LocalDate data,
            @RequestParam(required = false, name = "grupo") String grupo,
            @RequestParam(required = false, name = "unidadeId") Long unidadeId,
            @RequestParam(defaultValue = "false", name = "semUnidade") boolean semUnidade,
            @RequestParam(required = false, name = "status") String status,
            @RequestParam(required = false, name = "termo") String termo,
            @RequestParam(defaultValue = "0", name = "page") int page,
            @RequestParam(defaultValue = "20", name = "size") int size,
            Authentication authentication) {
        String callerCpf = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.listar(data, grupo, unidadeId, semUnidade, status, termo, page, size, callerCpf));
    }

    /** Agendas (Grupos de Relatorio) do menu e dos seletores, ja no escopo do chamador (V110). */
    @GetMapping("/agendas")
    public ResponseEntity<List<AgendaGrupoViewDTO>> listarAgendas(Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.listarAgendas(cpfDe(authentication)));
    }

    /**
     * Relatorio de Atendimento em Excel: nominal, com CPF e CNS completos. Fica aqui,
     * e nao em /api/exportar, para herdar a regra de perfil desta classe.
     */
    @GetMapping("/relatorio")
    public ResponseEntity<InputStreamResource> relatorio(
            @RequestParam(name = "inicio") LocalDate inicio,
            @RequestParam(name = "fim") LocalDate fim,
            @RequestParam(required = false, name = "grupo") String grupo,
            Authentication authentication) throws IOException {
        ByteArrayInputStream planilha = excelService.gerarPlanilhaAtendimentos(inicio, fim, grupo, cpfDe(authentication));
        String arquivo = "relatorio_atendimentos_" + inicio + "_a_" + fim + ".xlsx";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + arquivo + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(planilha));
    }

    // Desfecho do atendimento: so o operador da unidade executante. O ADMIN le a
    // tela (regra da classe), mas nao lanca nem desfaz — o service repete a regra.

    @PatchMapping("/{id}/confirmar")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AtendimentoViewDTO> confirmar(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(service.confirmar(id, cpfDe(authentication)));
    }

    @PatchMapping("/{id}/falta")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AtendimentoViewDTO> registrarFalta(@PathVariable Long id,
            @RequestBody(required = false) AtendimentoFaltaDTO dto, Authentication authentication) {
        return ResponseEntity.ok(service.registrarFalta(id, dto, cpfDe(authentication)));
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AtendimentoViewDTO> cancelar(@PathVariable Long id,
            @RequestBody(required = false) AtendimentoCancelamentoDTO dto, Authentication authentication) {
        return ResponseEntity.ok(service.cancelar(id, dto, cpfDe(authentication)));
    }

    @PatchMapping("/{id}/data-coleta")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AtendimentoViewDTO> informarDataColeta(@PathVariable Long id,
            @RequestBody(required = false) AtendimentoColetaDTO dto, Authentication authentication) {
        return ResponseEntity.ok(service.informarDataColeta(id, dto, cpfDe(authentication)));
    }

    @PatchMapping("/{id}/desfazer")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AtendimentoViewDTO> desfazer(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(service.desfazer(id, cpfDe(authentication)));
    }

    private static String cpfDe(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
