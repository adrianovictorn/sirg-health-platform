package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoUnidadeEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UnidadeService {

    private final UnidadeRepository unidadeRepository;
    private final GrupoRelatorioRepository grupoRelatorioRepository;

    @Transactional
    public UnidadeViewDTO criar(UnidadeCreateDTO dto) {
        if (dto.nome() == null || dto.nome().isBlank()) {
            throw new IllegalArgumentException("O nome da unidade é obrigatório.");
        }
        unidadeRepository.findByNome(dto.nome()).ifPresent(u -> {
            throw new IllegalArgumentException("Já existe uma unidade com esse nome.");
        });
        String cnes = blankToNull(dto.cnes());
        if (cnes != null) {
            unidadeRepository.findByCnes(cnes).ifPresent(u -> {
                throw new IllegalArgumentException("Já existe uma unidade com esse CNES.");
            });
        }
        Unidade unidade = new Unidade();
        unidade.setNome(dto.nome());
        unidade.setCodigo(blankToNull(dto.codigo()));
        unidade.setCnes(cnes);
        unidade.setTelefone(blankToNull(dto.telefone()));
        unidade.setEndereco(blankToNull(dto.endereco()));
        aplicarGrupo(unidade, dto.grupoRelatorioId());
        aplicarDadosEstabelecimento(unidade, dto.tipo(), dto.cnpj(), dto.razaoSocial(),
                dto.nomeFantasia(), dto.numero(), dto.bairro(), dto.cep(), dto.email(),
                dto.importadoDoCnes());
        unidade.setAtivo(true);
        return UnidadeViewDTO.from(unidadeRepository.save(unidade));
    }

    @Transactional
    public UnidadeViewDTO atualizar(Long id, UnidadeUpdateDTO dto) {
        Unidade unidade = unidadeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada."));
        String cnes = blankToNull(dto.cnes());
        // O CNES é UNIQUE no banco. Sem esta checagem, apontar duas unidades para o
        // mesmo estabelecimento estourava a constraint e chegava na tela como erro
        // 500 genérico — situação que ficou comum com a busca por CNES.
        if (cnes != null) {
            unidadeRepository.findByCnes(cnes).ifPresent(outra -> {
                if (!outra.getId().equals(id)) {
                    throw new IllegalArgumentException(
                            "O CNES " + cnes + " já está vinculado à unidade " + outra.getNome() + ".");
                }
            });
        }
        unidade.setNome(dto.nome());
        unidade.setCodigo(blankToNull(dto.codigo()));
        unidade.setCnes(cnes);
        unidade.setTelefone(blankToNull(dto.telefone()));
        unidade.setEndereco(blankToNull(dto.endereco()));
        aplicarGrupo(unidade, dto.grupoRelatorioId());
        aplicarDadosEstabelecimento(unidade, dto.tipo(), dto.cnpj(), dto.razaoSocial(),
                dto.nomeFantasia(), dto.numero(), dto.bairro(), dto.cep(), dto.email(),
                dto.importadoDoCnes());
        return UnidadeViewDTO.from(unidadeRepository.save(unidade));
    }

    /** Vincula (ou desvincula, quando o id vem nulo) a unidade a um grupo de cotas. */
    private void aplicarGrupo(Unidade unidade, Long grupoRelatorioId) {
        if (grupoRelatorioId == null) {
            unidade.setGrupoRelatorio(null);
            return;
        }
        unidade.setGrupoRelatorio(grupoRelatorioRepository.findById(grupoRelatorioId)
                .orElseThrow(() -> new EntityNotFoundException("Grupo nao encontrado.")));
    }

    /**
     * Aplica tipo e dados cadastrais do estabelecimento (V86).
     *
     * <p>{@code tipo} nulo ou em branco mantém o que a unidade já tem — que para
     * todas as unidades existentes é AMBOS. Assim a tela antiga de unidades, que
     * não conhece o campo, continua salvando sem apagar a configuração.
     */
    private void aplicarDadosEstabelecimento(Unidade unidade, String tipo, String cnpj,
            String razaoSocial, String nomeFantasia, String numero, String bairro,
            String cep, String email, Boolean importadoDoCnes) {

        if (blankToNull(tipo) != null) {
            try {
                unidade.setTipo(TipoUnidadeEnum.valueOf(tipo.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Tipo de unidade inválido: " + tipo + ". Use SOLICITANTE, EXECUTANTE ou AMBOS.");
            }
        } else if (unidade.getTipo() == null) {
            unidade.setTipo(TipoUnidadeEnum.AMBOS);
        }

        unidade.setCnpj(somenteDigitos(cnpj));
        unidade.setRazaoSocial(blankToNull(razaoSocial));
        unidade.setNomeFantasia(blankToNull(nomeFantasia));
        unidade.setNumero(blankToNull(numero));
        unidade.setBairro(blankToNull(bairro));
        unidade.setCep(somenteDigitos(cep));
        unidade.setEmail(blankToNull(email));

        // Só a origem CNES carimba a data. Edição manual posterior não mexe nela:
        // o carimbo registra de onde os dados vieram, não quando foram tocados.
        if (Boolean.TRUE.equals(importadoDoCnes)) {
            unidade.setSincronizadoCnesEm(LocalDateTime.now());
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    /** CNPJ e CEP são guardados só com dígitos — a tela formata na exibição. */
    private String somenteDigitos(String value) {
        if (value == null) {
            return null;
        }
        String limpo = value.replaceAll("\\D", "");
        return limpo.isEmpty() ? null : limpo;
    }

    @Transactional
    public UnidadeViewDTO toggleAtivo(Long id) {
        Unidade unidade = unidadeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada."));
        unidade.setAtivo(!unidade.isAtivo());
        return UnidadeViewDTO.from(unidadeRepository.save(unidade));
    }

    @Transactional(readOnly = true)
    public UnidadeViewDTO buscarPorId(Long id) {
        return UnidadeViewDTO.from(unidadeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada.")));
    }

    @Transactional(readOnly = true)
    public List<UnidadeViewDTO> listarTodas() {
        return unidadeRepository.findAll().stream().map(UnidadeViewDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UnidadeSimpleViewDTO> listarAtivas() {
        return unidadeRepository.findByAtivoTrue().stream().map(UnidadeSimpleViewDTO::from).toList();
    }

    /** Estabelecimentos que executam atendimento — alimenta a abertura de agenda. */
    @Transactional(readOnly = true)
    public List<UnidadeViewDTO> listarExecutantes() {
        return unidadeRepository
                .findByAtivoTrueAndTipoInOrderByNomeAsc(
                        List.of(TipoUnidadeEnum.EXECUTANTE, TipoUnidadeEnum.AMBOS))
                .stream()
                .map(UnidadeViewDTO::from)
                .toList();
    }
}
