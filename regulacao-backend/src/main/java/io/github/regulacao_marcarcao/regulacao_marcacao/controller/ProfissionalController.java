package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.ProfissionalService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/profissionais")
@RequiredArgsConstructor
public class ProfissionalController {

    private final ProfissionalService profissionalService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO')")
    public ResponseEntity<ProfissionalViewDTO> criar(@RequestBody ProfissionalCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profissionalService.criar(dto));
    }

    @GetMapping("/buscar")
    public ResponseEntity<Page<ProfissionalViewDTO>> buscar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String nome) {
        return ResponseEntity.ok(profissionalService.buscar(page, size, nome));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfissionalViewDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(profissionalService.buscarPorId(id));
    }

    @GetMapping("/unidade/{unidadeId}/ativos")
    public ResponseEntity<List<ProfissionalSimpleViewDTO>> listarAtivosPorUnidade(@PathVariable Long unidadeId) {
        return ResponseEntity.ok(profissionalService.listarAtivosPorUnidade(unidadeId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO')")
    public ResponseEntity<ProfissionalViewDTO> atualizar(
            @PathVariable Long id,
            @RequestBody ProfissionalUpdateDTO dto) {
        return ResponseEntity.ok(profissionalService.atualizar(id, dto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO')")
    public ResponseEntity<ProfissionalViewDTO> toggleAtivo(@PathVariable Long id) {
        return ResponseEntity.ok(profissionalService.toggleAtivo(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        profissionalService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
