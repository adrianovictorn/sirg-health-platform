package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante.LocalExecutanteVinculoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante.OperadorExecutanteViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante.UnidadeExecutanteResumoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante.UnidadeExecutanteUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante.UnidadeExecutanteViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.UnidadeExecutanteEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.UnidadeExecutanteGrupo;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.UsuarioUnidadeExecutante;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoUnidadeEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.LocalAgendamentoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeExecutanteEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeExecutanteGrupoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UsuarioUnidadeExecutanteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Parametrizacao da unidade executante (V110): o que ela realiza (grupos inteiros
 * + especialidades avulsas), em quais locais de agendamento atende e quais
 * operadores (USER) a representam na tela de Atendimentos.
 *
 * Grupo e especialidade avulsa podem estar em mais de uma executante (V111, ADR
 * 0013); o indice unico no par so impede o vinculo repetido na mesma executante.
 * Quem desempata um item e o local do agendamento; item sem local de
 * especialidade compartilhada so aparece na visao municipal (ver
 * AtendimentoExecutanteRepository.FILTRO_EXECUTANTE).
 *
 * Local ja ligado a outra executante e MOVIDO, nao recusado. Nada aqui toca em
 * cota, teto, agendamento ou federacao.
 */
@Service
@RequiredArgsConstructor
public class UnidadeExecutanteService {

    private static final List<TipoUnidadeEnum> TIPOS_EXECUTANTES =
            List.of(TipoUnidadeEnum.EXECUTANTE, TipoUnidadeEnum.AMBOS);

    private final UnidadeRepository unidadeRepository;
    private final GrupoRelatorioRepository grupoRelatorioRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final LocalAgendamentoRepository localAgendamentoRepository;
    private final UserRepository userRepository;
    private final UnidadeExecutanteGrupoRepository grupoVinculoRepository;
    private final UnidadeExecutanteEspecialidadeRepository especialidadeVinculoRepository;
    private final UsuarioUnidadeExecutanteRepository operadorVinculoRepository;

    @Transactional(readOnly = true)
    public List<UnidadeExecutanteResumoViewDTO> listar() {
        return grupoVinculoRepository.resumirExecutantes().stream()
                .map(UnidadeExecutanteResumoViewDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnidadeExecutanteViewDTO buscar(Long unidadeId) {
        Unidade unidade = carregarUnidade(unidadeId);
        return montarView(unidade);
    }

    /** Quem pode ser vinculado como operador: usuarios ativos com o perfil USER concedido. */
    @Transactional(readOnly = true)
    public List<OperadorExecutanteViewDTO> listarOperadoresDisponiveis() {
        return userRepository.findAtivosQuePodemAssumir(Roles.USER).stream()
                .map(OperadorExecutanteViewDTO::from)
                .toList();
    }

    /** Substitui a parametrizacao inteira. Valida tudo antes de gravar qualquer coisa. */
    @Transactional
    public UnidadeExecutanteViewDTO atualizar(Long unidadeId, UnidadeExecutanteUpdateDTO dto) {
        Unidade unidade = carregarUnidade(unidadeId);
        exigirPodeExecutar(unidade);

        List<GrupoRelatorio> grupos = carregarTodos(grupoRelatorioRepository, ids(dto == null ? null : dto.grupoIds()),
                "Grupo de Relatório");
        List<Especialidade> avulsas = carregarTodos(especialidadeRepository,
                ids(dto == null ? null : dto.especialidadeIds()), "Especialidade");
        List<LocalAgendamento> locais = carregarTodos(localAgendamentoRepository,
                ids(dto == null ? null : dto.localIds()), "Local de agendamento");
        List<User> operadores = carregarTodos(userRepository, ids(dto == null ? null : dto.usuarioIds()), "Usuário");

        // Vinculo que ficou invalido (perfil retirado, usuario desativado) aparece na
        // tela marcado, para o administrador desmarcar; aqui nao e regravado.
        for (User operador : operadores) {
            if (!operador.isAtivo()) {
                throw new IllegalArgumentException(
                        "O usuário " + operador.getNome() + " está inativo e não pode operar a executante. Desmarque-o.");
            }
            if (!operador.podeAssumir(Roles.USER)) {
                throw new IllegalArgumentException(
                        "O usuário " + operador.getNome() + " não tem o perfil Usuário Padrão e não pode operar a executante. Desmarque-o.");
            }
        }

        // Avulsa ja coberta por um grupo escolhido e redundante: nao e gravada.
        Set<Long> grupoIds = grupos.stream().map(GrupoRelatorio::getId).collect(Collectors.toSet());
        List<Especialidade> avulsasEfetivas = avulsas.stream()
                .filter(e -> e.getGrupoRelatorio() == null || !grupoIds.contains(e.getGrupoRelatorio().getId()))
                .toList();

        grupoVinculoRepository.deleteByUnidadeId(unidade.getId());
        especialidadeVinculoRepository.deleteByUnidadeId(unidade.getId());
        operadorVinculoRepository.deleteByUnidadeId(unidade.getId());

        grupoVinculoRepository.saveAll(grupos.stream().map(g -> new UnidadeExecutanteGrupo(unidade, g)).toList());
        especialidadeVinculoRepository.saveAll(
                avulsasEfetivas.stream().map(e -> new UnidadeExecutanteEspecialidade(unidade, e)).toList());
        operadorVinculoRepository.saveAll(
                operadores.stream().map(u -> new UsuarioUnidadeExecutante(u, unidade)).toList());

        Set<Long> localIds = locais.stream().map(LocalAgendamento::getId).collect(Collectors.toSet());
        for (LocalAgendamento atual : localAgendamentoRepository.findByUnidadeExecutanteIdOrderByNomeLocalAsc(unidade.getId())) {
            if (!localIds.contains(atual.getId())) {
                atual.setUnidadeExecutante(null);
            }
        }
        locais.forEach(l -> l.setUnidadeExecutante(unidade));
        grupoVinculoRepository.flush();

        return montarView(unidade);
    }

    /** Acao "Vincular a unidade executante" da tela de Locais. Move se ja ligado a outra. */
    @Transactional
    public void vincularLocal(Long localId, LocalExecutanteVinculoDTO dto) {
        LocalAgendamento local = localAgendamentoRepository.findById(localId)
                .orElseThrow(() -> new EntityNotFoundException("Local de agendamento não encontrado."));
        Long unidadeId = dto != null ? dto.unidadeId() : null;
        if (unidadeId == null) {
            local.setUnidadeExecutante(null);
            return;
        }
        Unidade unidade = carregarUnidade(unidadeId);
        exigirPodeExecutar(unidade);
        local.setUnidadeExecutante(unidade);
    }

    private UnidadeExecutanteViewDTO montarView(Unidade unidade) {
        return UnidadeExecutanteViewDTO.from(unidade,
                grupoVinculoRepository.findByUnidadeId(unidade.getId()),
                especialidadeVinculoRepository.findByUnidadeId(unidade.getId()),
                localAgendamentoRepository.findByUnidadeExecutanteIdOrderByNomeLocalAsc(unidade.getId()),
                operadorVinculoRepository.findByUnidadeId(unidade.getId()));
    }

    private Unidade carregarUnidade(Long unidadeId) {
        return unidadeRepository.findById(unidadeId)
                .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada."));
    }

    private static void exigirPodeExecutar(Unidade unidade) {
        if (!unidade.isAtivo()) {
            throw new IllegalArgumentException("A unidade " + unidade.getNome() + " está inativa.");
        }
        if (!TIPOS_EXECUTANTES.contains(unidade.getTipo())) {
            throw new IllegalArgumentException("A unidade " + unidade.getNome()
                    + " é só solicitante. Altere o tipo para Executante ou Ambos antes de parametrizá-la.");
        }
    }

    private static Set<Long> ids(List<Long> ids) {
        Set<Long> limpos = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream().filter(id -> id != null).forEach(limpos::add);
        }
        return limpos;
    }

    /** Id inexistente e 400 com mensagem, nao vinculo parcial silencioso. */
    private static <T> List<T> carregarTodos(CrudRepository<T, Long> repository,
            Collection<Long> ids, String rotulo) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<T> encontrados = new ArrayList<>();
        repository.findAllById(ids).forEach(encontrados::add);
        if (encontrados.size() != ids.size()) {
            throw new IllegalArgumentException(rotulo + " informado não existe.");
        }
        return encontrados;
    }
}
