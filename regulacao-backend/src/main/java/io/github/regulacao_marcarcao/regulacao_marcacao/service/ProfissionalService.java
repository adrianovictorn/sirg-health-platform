package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalVinculoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalVinculoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Cbo;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.ProfissionalVinculo;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemVinculoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CboRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalVinculoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfissionalService {

    private final ProfissionalRepository profissionalRepository;
    private final UnidadeRepository unidadeRepository;
    private final ProfissionalVinculoRepository vinculoRepository;
    private final CboRepository cboRepository;

    @Transactional
    public ProfissionalViewDTO criar(ProfissionalCreateDTO dto) {
        if (dto.nome() == null || dto.nome().isBlank()) {
            throw new IllegalArgumentException("O nome do profissional é obrigatório.");
        }
        Profissional p = new Profissional();
        p.setNome(dto.nome());
        p.setCpf(normalizarCpf(dto.cpf(), null));
        p.setConselho(dto.conselho());
        p.setNumeroRegistro(dto.numeroRegistro());
        p.setEspecialidadeAtuacao(dto.especialidadeAtuacao());
        p.setTelefone(dto.telefone());
        p.setAtivo(true);
        if (dto.unidadeId() != null) {
            Unidade unidade = unidadeRepository.findById(dto.unidadeId())
                    .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada."));
            p.setUnidade(unidade);
        }
        return ProfissionalViewDTO.from(profissionalRepository.save(p));
    }

    @Transactional
    public ProfissionalViewDTO atualizar(Long id, ProfissionalUpdateDTO dto) {
        Profissional p = profissionalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profissional não encontrado."));
        p.setNome(dto.nome());
        p.setCpf(normalizarCpf(dto.cpf(), id));
        p.setConselho(dto.conselho());
        p.setNumeroRegistro(dto.numeroRegistro());
        p.setEspecialidadeAtuacao(dto.especialidadeAtuacao());
        p.setTelefone(dto.telefone());
        if (dto.unidadeId() != null) {
            Unidade unidade = unidadeRepository.findById(dto.unidadeId())
                    .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada."));
            p.setUnidade(unidade);
        } else {
            p.setUnidade(null);
        }
        return ProfissionalViewDTO.from(profissionalRepository.save(p));
    }

    @Transactional
    public ProfissionalViewDTO toggleAtivo(Long id) {
        Profissional p = profissionalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profissional não encontrado."));
        p.setAtivo(!p.isAtivo());
        return ProfissionalViewDTO.from(profissionalRepository.save(p));
    }

    @Transactional(readOnly = true)
    public ProfissionalViewDTO buscarPorId(Long id) {
        return ProfissionalViewDTO.from(profissionalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profissional não encontrado.")));
    }

    @Transactional(readOnly = true)
    public Page<ProfissionalViewDTO> buscar(int page, int size, String nome) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("nome").ascending());
        if (nome == null || nome.isBlank()) {
            return profissionalRepository.findAll(pageable).map(ProfissionalViewDTO::from);
        }
        return profissionalRepository.findByNomeContainingIgnoreCase(nome, pageable)
                .map(ProfissionalViewDTO::from);
    }

    @Transactional(readOnly = true)
    public List<ProfissionalSimpleViewDTO> listarAtivosPorUnidade(Long unidadeId) {
        return profissionalRepository.findByUnidadeIdAndAtivoTrue(unidadeId).stream()
                .map(ProfissionalSimpleViewDTO::from).toList();
    }

    @Transactional
    public void deletar(Long id) {
        profissionalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profissional não encontrado."));
        profissionalRepository.deleteById(id);
    }

    // ------------------------------------------------------------------
    // Vinculos com estabelecimento executante (V88)
    // ------------------------------------------------------------------

    /**
     * Onde o profissional atua, e em que ocupacao.
     *
     * <p>Substitui a leitura do campo unico {@code profissional.unidade}, que
     * continua populado apenas para as telas que ainda nao migraram.
     */
    @Transactional(readOnly = true)
    public List<ProfissionalVinculoViewDTO> listarVinculos(Long profissionalId) {
        if (!profissionalRepository.existsById(profissionalId)) {
            throw new EntityNotFoundException("Profissional não encontrado.");
        }
        return vinculoRepository.findByProfissionalIdOrderByIdAsc(profissionalId).stream()
                .map(ProfissionalVinculoViewDTO::from).toList();
    }

    /**
     * Quem atende neste estabelecimento executante.
     *
     * <p>E o combo de profissionais da abertura de agenda: escolhido o executante,
     * so aparecem os profissionais com vinculo ativo nele.
     */
    @Transactional(readOnly = true)
    public List<ProfissionalSimpleViewDTO> listarAtivosPorExecutante(Long unidadeId) {
        return vinculoRepository.findProfissionaisAtivosPorUnidade(unidadeId).stream()
                .map(ProfissionalSimpleViewDTO::from).toList();
    }

    @Transactional
    public ProfissionalVinculoViewDTO criarVinculo(Long profissionalId, ProfissionalVinculoCreateDTO dto) {
        Profissional profissional = profissionalRepository.findById(profissionalId)
                .orElseThrow(() -> new EntityNotFoundException("Profissional não encontrado."));

        Unidade unidade = unidadeRepository.findById(dto.unidadeId())
                .orElseThrow(() -> new EntityNotFoundException("Estabelecimento não encontrado."));

        Cbo cbo = null;
        if (dto.cboId() != null) {
            cbo = cboRepository.findById(dto.cboId())
                    .orElseThrow(() -> new EntityNotFoundException("CBO não encontrado."));
        }

        // Checagem antes do insert para o operador ler "já existe" em vez de um erro
        // de constraint: o indice unico da V88 cobre a tripla, mas a mensagem dele
        // nao diz nada a quem esta na tela.
        Long cboId = cbo != null ? cbo.getId() : null;
        boolean jaExiste = (cboId == null
                ? vinculoRepository.findByProfissionalIdAndUnidadeIdAndCboIsNull(profissionalId, unidade.getId())
                : vinculoRepository.findByProfissionalIdAndUnidadeIdAndCboId(profissionalId, unidade.getId(), cboId))
                .isPresent();

        if (jaExiste) {
            throw new IllegalStateException(profissional.getNome() + " já tem vínculo com "
                    + unidade.getNome() + (cbo == null ? "" : " na ocupação " + cbo.getDescricao()) + ".");
        }

        ProfissionalVinculo vinculo = new ProfissionalVinculo();
        vinculo.setProfissional(profissional);
        vinculo.setUnidade(unidade);
        vinculo.setCbo(cbo);
        vinculo.setAtivo(true);
        vinculo.setOrigem(OrigemVinculoEnum.MANUAL);
        return ProfissionalVinculoViewDTO.from(vinculoRepository.save(vinculo));
    }

    @Transactional
    public ProfissionalVinculoViewDTO toggleVinculo(Long vinculoId) {
        ProfissionalVinculo vinculo = vinculoRepository.findById(vinculoId)
                .orElseThrow(() -> new EntityNotFoundException("Vínculo não encontrado."));
        vinculo.setAtivo(!vinculo.isAtivo());
        return ProfissionalVinculoViewDTO.from(vinculoRepository.save(vinculo));
    }

    /**
     * Exclui o vinculo.
     *
     * <p>Desativar costuma ser o certo (preserva o historico de quem atendeu
     * onde); excluir existe para desfazer uma importacao feita no estabelecimento
     * errado, que e o erro comum da tela de importacao em lote.
     */
    @Transactional
    public void deletarVinculo(Long vinculoId) {
        ProfissionalVinculo vinculo = vinculoRepository.findById(vinculoId)
                .orElseThrow(() -> new EntityNotFoundException("Vínculo não encontrado."));
        vinculoRepository.delete(vinculo);
    }

    /**
     * CPF sem mascara, ou nulo quando nao informado.
     *
     * <p>Sem validacao de digito verificador de proposito: o CPF chega do arquivo
     * do CNES, e recusar um numero que o DATASUS aceita impediria a importacao sem
     * dar ao operador nada que ele possa corrigir. O que se garante aqui e o
     * formato (11 digitos) e a unicidade, que e o que a deduplicacao usa.
     *
     * @param idAtual id do profissional em edicao, para ele nao colidir consigo mesmo
     */
    private String normalizarCpf(String cpf, Long idAtual) {
        if (cpf == null || cpf.isBlank()) {
            return null;
        }
        String digitos = cpf.replaceAll("\\D", "");
        if (digitos.length() != 11) {
            throw new IllegalArgumentException("O CPF deve ter 11 dígitos.");
        }
        profissionalRepository.findByCpf(digitos).ifPresent(outro -> {
            if (!outro.getId().equals(idAtual)) {
                throw new IllegalStateException(
                        "O CPF informado já pertence ao profissional " + outro.getNome() + ".");
            }
        });
        return digitos;
    }
}
