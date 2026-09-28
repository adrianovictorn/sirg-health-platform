package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cbo.CboCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cbo.CboViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.CboService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Ocupacoes (CBO) usadas nos vinculos de profissional.
 *
 * <p>Leitura liberada a qualquer usuario autenticado porque alimenta combos das
 * telas de profissional e de agenda. Escrita restrita a ADMIN e GESTOR: e
 * cadastro de base, nao operacao do dia.
 */
@RestController
@RequestMapping("/api/cbos")
@RequiredArgsConstructor
public class CboController {

    private final CboService cboService;

    /** Por padrao so as ativas — e o que os combos devem oferecer. */
    @GetMapping
    public ResponseEntity<List<CboViewDTO>> listar(
            @RequestParam(defaultValue = "true") boolean apenasAtivos) {
        return ResponseEntity.ok(cboService.listar(apenasAtivos));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    public ResponseEntity<CboViewDTO> criar(@Valid @RequestBody CboCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cboService.criar(dto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    public ResponseEntity<CboViewDTO> toggleAtivo(@PathVariable Long id) {
        return ResponseEntity.ok(cboService.toggleAtivo(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        cboService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
