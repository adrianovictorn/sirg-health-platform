package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cbo.CboCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cbo.CboViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Cbo;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CboRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalVinculoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Ocupacoes (CBO) usadas nos vinculos de profissional.
 *
 * <p>A tabela nasceu vazia na V87 e se enche por duas vias: o upsert da
 * importacao do CNES ({@link #buscarOuCriar}), que traz codigo e descricao do
 * proprio DATASUS, e o cadastro avulso do administrador. Nao ha seed com codigos
 * digitados a mao — CBO errado rotula o profissional com a ocupacao de outra
 * pessoa e nada acusa o erro.
 */
@Service
@RequiredArgsConstructor
public class CboService {

    private final CboRepository cboRepository;
    private final ProfissionalVinculoRepository vinculoRepository;

    /** Resultado do upsert: interessa saber se o CBO e novo, para o resumo da importacao. */
    public record Upsert(Cbo cbo, boolean criado) {
    }

    @Transactional(readOnly = true)
    public List<CboViewDTO> listar(boolean apenasAtivos) {
        List<Cbo> cbos = apenasAtivos
                ? cboRepository.findByAtivoTrueOrderByDescricaoAsc()
                : cboRepository.findAllByOrderByDescricaoAsc();
        return cbos.stream().map(CboViewDTO::from).toList();
    }

    @Transactional
    public CboViewDTO criar(CboCreateDTO dto) {
        String codigo = somenteDigitos(dto.codigo());
        cboRepository.findByCodigo(codigo).ifPresent(existente -> {
            throw new IllegalStateException(
                    "O CBO " + codigo + " já está cadastrado como \"" + existente.getDescricao() + "\".");
        });

        Cbo cbo = new Cbo();
        cbo.setCodigo(codigo);
        cbo.setDescricao(dto.descricao().trim());
        cbo.setAtivo(true);
        return CboViewDTO.from(cboRepository.save(cbo));
    }

    /**
     * Acha o CBO pelo codigo ou cria com a descricao do arquivo.
     *
     * <p>Usado pela importacao. Nao sobrescreve a descricao de um CBO que ja
     * existe: se o administrador ajustou o texto na tela, o arquivo do DATASUS nao
     * tem por que desfazer o ajuste — o que identifica a ocupacao e o codigo.
     */
    @Transactional
    public Upsert buscarOuCriar(String codigo, String descricao) {
        String limpo = somenteDigitos(codigo);
        if (limpo == null) {
            return new Upsert(null, false);
        }

        return cboRepository.findByCodigo(limpo)
                .map(existente -> new Upsert(existente, false))
                .orElseGet(() -> {
                    Cbo novo = new Cbo();
                    novo.setCodigo(limpo);
                    // Sem descricao no arquivo, o codigo vira o rotulo provisorio: melhor
                    // um CBO identificavel e corrigivel na tela do que vinculo sem ocupacao.
                    novo.setDescricao((descricao == null || descricao.isBlank())
                            ? "CBO " + limpo
                            : descricao.trim());
                    novo.setAtivo(true);
                    return new Upsert(cboRepository.save(novo), true);
                });
    }

    @Transactional
    public CboViewDTO toggleAtivo(Long id) {
        Cbo cbo = cboRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("CBO não encontrado."));
        cbo.setAtivo(!cbo.isAtivo());
        return CboViewDTO.from(cboRepository.save(cbo));
    }

    /**
     * Exclui um CBO que ninguem usa.
     *
     * <p>Com vinculo apontando para ele, a exclusao para aqui com 409 em vez de
     * estourar a constraint no banco (a FK e RESTRICT, V88): o operador precisa
     * ler "há vínculos usando esta ocupação", nao um erro de integridade.
     */
    @Transactional
    public void deletar(Long id) {
        Cbo cbo = cboRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("CBO não encontrado."));
        if (vinculoRepository.existsByCboId(id)) {
            throw new IllegalStateException(
                    "Há vínculos de profissional usando o CBO " + cbo.getCodigo()
                            + ". Desative a ocupação em vez de excluí-la.");
        }
        cboRepository.delete(cbo);
    }

    private String somenteDigitos(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.replaceAll("\\D", "");
        return limpo.isEmpty() ? null : limpo;
    }
}
