package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.io.IOException;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.UserCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.UserUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.UserViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    public UserViewDTO criarUsuario(UserCreateDTO dto) {
        userRepository.findByCpf(dto.getCpf()).ifPresent(user -> {
            throw new IllegalArgumentException("CPF já cadastrado como usuário");
        });

        User novoUsuario = new User();
        novoUsuario.setCpf(dto.getCpf());
        novoUsuario.setNome(dto.getNome());
        novoUsuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        novoUsuario.setRole(dto.getCargo());
        novoUsuario.setAtivo(true);

        User usuarioSalvo = userRepository.save(novoUsuario);
        return UserViewDTO.from(usuarioSalvo);
    }

    public UserViewDTO buscarPorCpf(String cpf) {
        User user = userRepository.findByCpf(cpf)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        return UserViewDTO.from(user);
    }

    // [LISTAS]

    public List<UserViewDTO> listarUsuarios() {
        return userRepository.findAll().stream().map(UserViewDTO::from).toList();
    }

    public List<UserViewDTO> listarAdministradores() {
        return userRepository.findAll().stream()
            .filter(user -> user.getRole() == Roles.ADMIN)
            .map(UserViewDTO::from).toList();
    }

    public List<UserViewDTO> listarRoleUsers() {
        return userRepository.findAll().stream()
            .filter(user -> user.getRole() == Roles.USER)
            .map(UserViewDTO::from).toList();
    }

    public List<UserViewDTO> listarRoleEnfermeiro() {
        return userRepository.findAll().stream()
            .filter(users -> users.getRole() == Roles.ENFERMEIRO)
            .map(UserViewDTO::from).toList();
    }

    public List<UserViewDTO> listarRoleMedico() {
        return userRepository.findAll().stream()
            .filter(medico -> medico.getRole() == Roles.MEDICO)
            .map(UserViewDTO::from).toList();
    }

    public List<UserViewDTO> listarRoleRecepcionista() {
        return userRepository.findAll().stream()
            .filter(recepcao -> recepcao.getRole() == Roles.RECEPCAO)
            .map(UserViewDTO::from).toList();
    }

    // [ATUALIZAR]

    public UserViewDTO atualizarUser(Long id, UserUpdateDTO user) {
        User usuarioExistente = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        usuarioExistente.setCpf(user.cpf());
        usuarioExistente.setNome(user.nome());
        if (user.password() != null && !user.password().isEmpty()) {
            usuarioExistente.setPassword(passwordEncoder.encode(user.password()));
        }
        usuarioExistente.setRole(user.role());

        User usuarioAtualizado = userRepository.save(usuarioExistente);
        return UserViewDTO.from(usuarioAtualizado);
    }

    public UserViewDTO atualizarFotoPerfil(Long id, MultipartFile file) throws IOException {
        User usuario = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (usuario.getFotoPerfil() != null) {
            fileStorageService.deletarFoto(usuario.getFotoPerfil());
        }

        String fotoUrl = fileStorageService.salvarFoto(file, id);
        usuario.setFotoPerfil(fotoUrl);

        User usuarioAtualizado = userRepository.save(usuario);
        return UserViewDTO.from(usuarioAtualizado);
    }

    public UserViewDTO removerFotoPerfil(Long id) {
        User usuario = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (usuario.getFotoPerfil() != null) {
            fileStorageService.deletarFoto(usuario.getFotoPerfil());
            usuario.setFotoPerfil(null);
            userRepository.save(usuario);
        }

        return UserViewDTO.from(usuario);
    }

    // [STATUS]

    public UserViewDTO toggleStatus(Long targetId) {
        User target = userRepository.findById(targetId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        // Safety: cannot deactivate the last active ADMIN
        if (target.isAtivo() && target.getRole() == Roles.ADMIN) {
            long activeAdmins = userRepository.countByRoleAndAtivoTrue(Roles.ADMIN);
            if (activeAdmins <= 1) {
                throw new IllegalStateException(
                    "Não é possível desativar o único administrador ativo do sistema."
                );
            }
        }

        target.setAtivo(!target.isAtivo());
        return UserViewDTO.from(userRepository.save(target));
    }

    // [DELETAR]

    public void deletarUsuario(Long id) {
        userRepository.findById(id).ifPresent(u -> {
            if (u.getFotoPerfil() != null) {
                fileStorageService.deletarFoto(u.getFotoPerfil());
            }
        });
        userRepository.deleteById(id);
    }
}
