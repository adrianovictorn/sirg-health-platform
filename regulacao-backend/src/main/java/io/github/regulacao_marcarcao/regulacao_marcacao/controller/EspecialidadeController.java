package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade.EspecialidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade.EspecialidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade.EspecialidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.EspecialidadeService;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/catalog/especialidades")
public class EspecialidadeController {

    private final EspecialidadeService service;

    public EspecialidadeController(EspecialidadeService service) {
        this.service = service;
    }

    @GetMapping("/buscar")
    public ResponseEntity<Page<EspecialidadeViewDTO>> buscarPorEspecialidade(
        @RequestParam(defaultValue = "0", name = "page") int page,
        @RequestParam(defaultValue = "10", name ="size") int size,
        @RequestParam(required = false, name = "termo") String termo
    ){
        return ResponseEntity.ok(service.listarTodasEspecialidadesPaginado(page, size, termo));
    }

    @GetMapping("/listar")
    public ResponseEntity<List<EspecialidadeViewDTO>> listarEspecialidades(){
        return ResponseEntity.ok(service.listarTodasEspecialidades());
    }
    
    @GetMapping("/listar/especialidades-medicas")
    public ResponseEntity<List<EspecialidadeViewDTO>> listarEspecialidadesMedicas(){
        return ResponseEntity.ok(service.listarEspecialidadesMedicas());
    }

    @GetMapping("/listar/exames")
    public ResponseEntity<List<EspecialidadeViewDTO>> listarExames(){
        return ResponseEntity.ok(service.listarEspecialidadesExames());
    }

    @GetMapping("/buscar/por/nome")
    public ResponseEntity<Page<EspecialidadeViewDTO>> buscarEspecialidades(
        @RequestParam(defaultValue = "0", name = "page") int page,
        @RequestParam(defaultValue = "10", name = "size") int size,
        @RequestParam(required = false, name = "nome") String nome
    ){
        return ResponseEntity.ok(service.listarTodasEspecialidadesPaginado(page, size, nome));
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EspecialidadeViewDTO> criar(@RequestBody EspecialidadeCreateDTO dto) {
        return ResponseEntity.ok(service.criarEspecialidade(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EspecialidadeViewDTO> atualizar(@PathVariable Long id, @RequestBody EspecialidadeUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizarEspecialidade(id, dto));  
    }

    @PatchMapping("/ativo/{id}")
    public ResponseEntity<EspecialidadeViewDTO> AtivarOrDesativar(@PathVariable Long id){
        return ResponseEntity.ok(service.ativarOrDesativarEspecialidade(id));
    }

}
