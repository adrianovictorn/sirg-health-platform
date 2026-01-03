package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade.EspecialidadeDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.EspecialidadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/catalog/especialidades")
@RequiredArgsConstructor
public class EspecialidadeController {

    private final EspecialidadeRepository repository;
    private final EspecialidadeService service;

    @GetMapping("/listar/exames")
    public ResponseEntity<List<EspecialidadeDTO>> listarExames(){
        return ResponseEntity.ok(service.listarEspecialidades());
    }

    @GetMapping
    public List<EspecialidadeDTO> listar() {
        return repository.findAll().stream().map(EspecialidadeDTO::fromEntity).collect(Collectors.toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EspecialidadeDTO> criar(@RequestBody CriarEspecialidadeRequest req) {
        Especialidade e = new Especialidade();
        e.setNome(req.nome());
        e.setCodigo(req.codigo() != null && !req.codigo().isBlank() ? req.codigo() : gerarCodigo(req.nome()));
        e.setCategoria(req.categoria());
        e.setAtivo(req.ativo() != null ? req.ativo() : Boolean.TRUE);
        e = repository.save(e);
        return ResponseEntity.ok(EspecialidadeDTO.fromEntity(e));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EspecialidadeDTO> atualizar(@PathVariable Long id, @RequestBody AtualizarEspecialidadeRequest req) {
        Especialidade e = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Especialidade não encontrada"));
        if (req.nome() != null) e.setNome(req.nome());
        if (req.codigo() != null) e.setCodigo(req.codigo());
        if (req.categoria() != null) e.setCategoria(req.categoria());
        if (req.ativo() != null) e.setAtivo(req.ativo());
        e = repository.save(e);
        return ResponseEntity.ok(EspecialidadeDTO.fromEntity(e));
    }

    private String gerarCodigo(String nome) {
        String base = nome == null ? "ESPECIALIDADE" : nome;
        return base.trim().toUpperCase(Locale.ROOT)
                .replace('Á','A').replace('À','A').replace('Ã','A').replace('Â','A')
                .replace('É','E').replace('Ê','E')
                .replace('Í','I')
                .replace('Ó','O').replace('Õ','O').replace('Ô','O')
                .replace('Ú','U')
                .replace('Ç','C')
                .replaceAll("[^A-Z0-9]+","_");
    }

    public record CriarEspecialidadeRequest(String codigo, String nome, ItemCategoria categoria, Boolean ativo) {}
    public record AtualizarEspecialidadeRequest(String codigo, String nome, ItemCategoria categoria, Boolean ativo) {}
}
