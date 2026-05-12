package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.UnidadeService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/unidades")
@RequiredArgsConstructor
public class UnidadeController {

    private final UnidadeService unidadeService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UnidadeViewDTO> criar(@RequestBody UnidadeCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(unidadeService.criar(dto));
    }

    @GetMapping
    public ResponseEntity<List<UnidadeViewDTO>> listarTodas() {
        return ResponseEntity.ok(unidadeService.listarTodas());
    }

    @GetMapping("/ativas")
    public ResponseEntity<List<UnidadeSimpleViewDTO>> listarAtivas() {
        return ResponseEntity.ok(unidadeService.listarAtivas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UnidadeViewDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(unidadeService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UnidadeViewDTO> atualizar(@PathVariable Long id, @RequestBody UnidadeUpdateDTO dto) {
        return ResponseEntity.ok(unidadeService.atualizar(id, dto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UnidadeViewDTO> toggleAtivo(@PathVariable Long id) {
        return ResponseEntity.ok(unidadeService.toggleAtivo(id));
    }
}
