package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoPainelViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.CustoPainelService;
import lombok.RequiredArgsConstructor;

/**
 * Painel de custos (somente leitura, somente agregados) — ADMIN e GESTOR.
 *
 * <p>O {@code @PreAuthorize} e a barreira: o escopo de listagem do service
 * tambem daria visao global a COORD_TRANSPORTE sem lotacao.
 */
@RestController
@RequestMapping("/api/custos/painel")
@RequiredArgsConstructor
public class CustoPainelController {

    private final CustoPainelService custoPainelService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    public ResponseEntity<CustoPainelViewDTO> painel(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) Long grupoRelatorioId,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataAte,
            Authentication authentication) {
        return ResponseEntity.ok(custoPainelService.painel(
                unidadeId, grupoRelatorioId, categoria, dataDe, dataAte,
                authentication != null ? authentication.getName() : null));
    }
}
