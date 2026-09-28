package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda.AgendaCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda.AgendaRemanejarDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda.AgendaUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda.AgendaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.AgendaService;
import lombok.RequiredArgsConstructor;

/**
 * Liberacao de Agenda (V89/V90) — abertura de agenda, materializacao de
 * ocorrencias e geracao de cota.
 *
 * <p>Acesso restrito a ADMIN: decisao explicita registrada em
 * docs/decisoes/0001-agenda-alimenta-cota-acesso-admin-tela-propria.md,
 * divergindo da regra R5 da especificacao original (que previa tambem GESTOR
 * e ADMIN_UNIDADE restrito a propria unidade).
 */
@RestController
@RequestMapping("/api/agendas")
@RequiredArgsConstructor
public class AgendaController {

    private final AgendaService agendaService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgendaViewDTO> criar(@RequestBody AgendaCreateDTO dto, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(agendaService.criar(dto, authentication.getName()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AgendaViewDTO>> listar() {
        return ResponseEntity.ok(agendaService.listar());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgendaViewDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(agendaService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgendaViewDTO> atualizar(@PathVariable Long id, @RequestBody AgendaUpdateDTO dto) {
        return ResponseEntity.ok(agendaService.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgendaViewDTO> desativar(@PathVariable Long id) {
        return ResponseEntity.ok(agendaService.desativar(id));
    }

    @PostMapping("/{id}/ocorrencias/{ocorrenciaId}/cancelar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgendaViewDTO> cancelarOcorrencia(
            @PathVariable Long id, @PathVariable Long ocorrenciaId) {
        return ResponseEntity.ok(agendaService.cancelarOcorrencia(id, ocorrenciaId));
    }

    @PostMapping("/{id}/remanejar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> remanejar(@PathVariable Long id, @RequestBody AgendaRemanejarDTO dto) {
        agendaService.remanejar(id, dto);
        return ResponseEntity.noContent().build();
    }
}
