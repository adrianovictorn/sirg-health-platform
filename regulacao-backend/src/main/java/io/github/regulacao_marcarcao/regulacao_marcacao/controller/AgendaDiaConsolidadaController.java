package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendaDia.AgendaDiaConsolidadaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendaDia.AgendaDiaItemViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.AgendaDiaConsolidadaService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/agenda-dia")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AgendaDiaConsolidadaController {

    private final AgendaDiaConsolidadaService service;

    @GetMapping("/consolidado")
    public ResponseEntity<AgendaDiaConsolidadaViewDTO> consolidar(
            @RequestParam(required = false, name = "data") LocalDate data,
            @RequestParam(required = false, name = "unidadeId") Long unidadeId,
            @RequestParam(required = false, name = "grupoId") Long grupoId,
            @RequestParam(required = false, name = "especialidadeId") Long especialidadeId,
            Authentication authentication) {
        String callerCpf = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.consolidar(data, unidadeId, grupoId, especialidadeId, callerCpf));
    }

    @GetMapping("/consolidado/pacientes")
    public ResponseEntity<Page<AgendaDiaItemViewDTO>> listarPacientes(
            @RequestParam(required = false, name = "data") LocalDate data,
            @RequestParam(required = true, name = "especialidadeId") Long especialidadeId,
            @RequestParam(required = false, name = "unidadeId") Long unidadeId,
            @RequestParam(defaultValue = "false", name = "semUnidade") boolean semUnidade,
            @RequestParam(defaultValue = "0", name = "page") int page,
            @RequestParam(defaultValue = "20", name = "size") int size,
            Authentication authentication) {
        String callerCpf = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.listarPacientes(data, especialidadeId, unidadeId, semUnidade, page, size, callerCpf));
    }
}
