package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.UserCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.UserUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.UserViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final UnidadeRepository unidadeRepository;
    private final TokenService tokenService;

    public UserViewDTO criarUsuario(UserCreateDTO dto) {
        userRepository.findByCpf(dto.getCpf()).ifPresent(user -> {
            throw new IllegalArgumentException("CPF já cadastrado como usuário");
        });

        User novoUsuario = new User();
        novoUsuario.setCpf(dto.getCpf());
        novoUsuario.setNome(dto.getNome());
        novoUsuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        novoUsuario.setRole(dto.getCargo());
        novoUsuario.setPerfis(montarPerfis(dto.getCargo(), dto.getPerfis()));
        novoUsuario.setAtivo(true);

        exigirUnidadeParaPerfisDeUnidade(novoUsuario.getPerfisConcedidos(), dto.getUnidadeId());

        if (dto.getUnidadeId() != null) {
            Unidade unidade = unidadeRepository.findById(dto.getUnidadeId())
                    .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada."));
            novoUsuario.setUnidade(unidade);
        }

        User usuarioSalvo = userRepository.save(novoUsuario);
        return UserViewDTO.from(usuarioSalvo);
    }

    /**
     * Troca o perfil ativo e devolve um token novo já emitido com ele.
     *
     * Nada é gravado: o perfil ativo vive no token. O perfil principal
     * (`usuarios.cargo`) continua sendo o do login, então sair e entrar de novo
     * devolve a pessoa ao ponto de partida — e a troca vale por sessão, não
     * para todos os dispositivos de uma vez.
     */
    public String trocarPerfilAtivo(String cpf, Roles perfilDesejado) {
        User usuario = userRepository.findByCpf(cpf)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        if (!usuario.podeAssumir(perfilDesejado)) {
            throw new AccessDeniedException(
                    "Seu usuário não tem o perfil " + perfilDesejado + " liberado.");
        }

        return tokenService.generateToken(usuario, perfilDesejado);
    }

    /**
     * Conjunto final de perfis concedidos: os escolhidos no formulário mais o
     * principal, que entra sempre. Sem isso daria para cadastrar alguém cujo
     * perfil de login não está entre os perfis liberados — a pessoa entraria
     * com um perfil que o sistema consideraria revogado a cada requisição.
     */
    private Set<Roles> montarPerfis(Roles principal, Set<Roles> escolhidos) {
        Set<Roles> perfis = new LinkedHashSet<>();
        if (principal != null) {
            perfis.add(principal);
        }
        if (escolhidos != null) {
            perfis.addAll(escolhidos);
        }
        return perfis;
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
            .filter(user -> user.podeAssumir(Roles.ADMIN))
            .map(UserViewDTO::from).toList();
    }

    public List<UserViewDTO> listarRoleUsers() {
        return userRepository.findAll().stream()
            .filter(user -> user.podeAssumir(Roles.USER))
            .map(UserViewDTO::from).toList();
    }

    public List<UserViewDTO> listarRoleEnfermeiro() {
        return userRepository.findAll().stream()
            .filter(users -> users.podeAssumir(Roles.ENFERMEIRO))
            .map(UserViewDTO::from).toList();
    }

    public List<UserViewDTO> listarRoleMedico() {
        return userRepository.findAll().stream()
            .filter(medico -> medico.podeAssumir(Roles.MEDICO))
            .map(UserViewDTO::from).toList();
    }

    public List<UserViewDTO> listarRoleRecepcionista() {
        return userRepository.findAll().stream()
            .filter(recepcao -> recepcao.podeAssumir(Roles.RECEPCAO))
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
        usuarioExistente.setPerfis(montarPerfis(user.role(), user.perfis()));

        exigirUnidadeParaPerfisDeUnidade(usuarioExistente.getPerfisConcedidos(), user.unidadeId());

        if (user.unidadeId() != null) {
            Unidade unidade = unidadeRepository.findById(user.unidadeId())
                    .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada."));
            usuarioExistente.setUnidade(unidade);
        } else {
            usuarioExistente.setUnidade(null);
        }

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

    /**
     * O perfil ADMIN_UNIDADE só existe em relação a uma unidade: todo o seu acesso
     * é filtrado pela unidade de lotação. Criá-lo sem vínculo produziria um usuário
     * que recebe 403 em qualquer operação, então a exigência é validada aqui, no
     * momento do cadastro, em vez de falhar depois no uso.
     */
    /**
     * A exigência de unidade vale para o CONJUNTO de perfis, não só para o
     * principal: quem tem ADMIN_UNIDADE como perfil secundário também precisa da
     * lotação, senão ao alternar para ele o acesso seria negado
     * ({@code UnidadeAcessoService} recusa ADMIN_UNIDADE sem unidade).
     */
    private void exigirUnidadeParaPerfisDeUnidade(Set<Roles> perfis, Long unidadeId) {
        if (perfis != null && perfis.contains(Roles.ADMIN_UNIDADE) && unidadeId == null) {
            throw new IllegalArgumentException(
                    "O perfil Administrador da Unidade exige uma unidade de lotação vinculada.");
        }
    }
}
