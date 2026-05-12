package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeSaldoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.CotaUnidadeService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cotas")
@RequiredArgsConstructor
public class CotaUnidadeController {

    private final CotaUnidadeService cotaService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CotaUnidadeViewDTO> criar(@RequestBody CotaUnidadeCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cotaService.criar(dto));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CotaUnidadeViewDTO>> listarTodas() {
        return ResponseEntity.ok(cotaService.listarTodas());
    }

    @GetMapping("/unidade/{unidadeId}")
    public ResponseEntity<List<CotaUnidadeViewDTO>> listarPorUnidade(@PathVariable Long unidadeId) {
        return ResponseEntity.ok(cotaService.listarPorUnidade(unidadeId));
    }

    @GetMapping("/unidade/{unidadeId}/periodo/{periodo}")
    public ResponseEntity<List<CotaUnidadeViewDTO>> listarPorUnidadeEPeriodo(
            @PathVariable Long unidadeId,
            @PathVariable String periodo) {
        return ResponseEntity.ok(cotaService.listarPorUnidadeEPeriodo(unidadeId, periodo));
    }

    @GetMapping("/saldo")
    public ResponseEntity<CotaUnidadeSaldoDTO> consultarSaldo(
            @RequestParam Long unidadeId,
            @RequestParam(required = false) Long especialidadeId,
            @RequestParam String periodo) {
        return ResponseEntity.ok(cotaService.consultarSaldo(unidadeId, especialidadeId, periodo));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CotaUnidadeViewDTO> atualizar(
            @PathVariable Long id,
            @RequestBody CotaUnidadeUpdateDTO dto) {
        return ResponseEntity.ok(cotaService.atualizar(id, dto));
    }
}
