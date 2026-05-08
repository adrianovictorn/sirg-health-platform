package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
import org.springframework.web.multipart.MultipartFile;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.UserCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.UserUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.UserViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserViewDTO> createUser(@RequestBody @Valid UserCreateDTO createDTO) {
        UserViewDTO createdUser = userService.criarUsuario(createDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @GetMapping("/me")
    public ResponseEntity<UserViewDTO> getMe(Authentication authentication) {
        return ResponseEntity.ok(userService.buscarPorCpf(authentication.getName()));
    }

    @GetMapping("/medicos")
    public ResponseEntity<List<UserViewDTO>> listarMedicos() {
        return ResponseEntity.ok(userService.listarRoleMedico());
    }

    @GetMapping("/enfermeiros")
    public ResponseEntity<List<UserViewDTO>> listarEnfermeiros() {
        return ResponseEntity.ok(userService.listarRoleEnfermeiro());
    }

    @GetMapping("/recepcionistas")
    public ResponseEntity<List<UserViewDTO>> listarRecepcionista() {
        return ResponseEntity.ok(userService.listarRoleRecepcionista());
    }

    @GetMapping
    public ResponseEntity<List<UserViewDTO>> listarTodosUsuarios() {
        return ResponseEntity.ok(userService.listarUsuarios());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserViewDTO> atualizarUsuario(@PathVariable Long id, @RequestBody UserUpdateDTO user) {
        return ResponseEntity.ok(userService.atualizarUser(id, user));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleStatus(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(userService.toggleStatus(id));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/foto")
    public ResponseEntity<UserViewDTO> uploadFoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        try {
            UserViewDTO updated = userService.atualizarFotoPerfil(id, file);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}/foto")
    public ResponseEntity<UserViewDTO> removerFoto(@PathVariable Long id) {
        return ResponseEntity.ok(userService.removerFotoPerfil(id));
    }
}
