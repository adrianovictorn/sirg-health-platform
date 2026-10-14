package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante.LocalExecutanteVinculoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante.OperadorExecutanteViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante.UnidadeExecutanteResumoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante.UnidadeExecutanteUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante.UnidadeExecutanteViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.UnidadeExecutanteService;
import lombok.RequiredArgsConstructor;

/**
 * Parametrizacao das unidades executantes (V110). Cadastro de base que define o
 * que cada operador enxerga na tela de Atendimentos: so ADMIN, leitura e escrita.
 *
 * Prefixo distinto de {@code GET /api/unidades/executantes} (combo da Agenda), que
 * nao muda.
 */
@RestController
@RequestMapping("/api/unidades-executantes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UnidadeExecutanteController {

    private final UnidadeExecutanteService service;

    @GetMapping
    public ResponseEntity<List<UnidadeExecutanteResumoViewDTO>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/operadores")
    public ResponseEntity<List<OperadorExecutanteViewDTO>> listarOperadores() {
        return ResponseEntity.ok(service.listarOperadoresDisponiveis());
    }

    @GetMapping("/{unidadeId}")
    public ResponseEntity<UnidadeExecutanteViewDTO> buscar(@PathVariable Long unidadeId) {
        return ResponseEntity.ok(service.buscar(unidadeId));
    }

    @PutMapping("/{unidadeId}")
    public ResponseEntity<UnidadeExecutanteViewDTO> atualizar(@PathVariable Long unidadeId,
            @RequestBody UnidadeExecutanteUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizar(unidadeId, dto));
    }

    @PatchMapping("/locais/{localId}")
    public ResponseEntity<Void> vincularLocal(@PathVariable Long localId,
            @RequestBody(required = false) LocalExecutanteVinculoDTO dto) {
        service.vincularLocal(localId, dto);
        return ResponseEntity.noContent().build();
    }
}
