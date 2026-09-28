package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.localAgendamentoDTO.LocalAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.localAgendamentoDTO.LocalAgendamentoListDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.localAgendamentoDTO.LocalAgendamentoUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.localAgendamentoDTO.LocalAgendamentoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.LocalAgendamentoRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LocalAgendamentoService {

    private final LocalAgendamentoRepository localAgendamentoRepository;
    private final CidadeRepository cidadeRepository;

    @Transactional
    public LocalAgendamentoViewDTO cadastrarLocalAgendamento(LocalAgendamentoCreateDTO dto){
        LocalAgendamento novoLocalAgendamento = new LocalAgendamento();
        novoLocalAgendamento.setNomeLocal(dto.nomeLocal());
        novoLocalAgendamento.setCidade(
            dto.cidadeId() != null ? cidadeRepository.findById(dto.cidadeId())
                .orElseThrow(() -> new EntityNotFoundException("Cidade não encontrada.")) : null
        );
        novoLocalAgendamento.setNumero(dto.numero());
        novoLocalAgendamento.setEndereco(dto.endereco());
        aplicarDadosEstabelecimento(novoLocalAgendamento, dto.cnes(), dto.cnpj(), dto.razaoSocial(),
                dto.nomeFantasia(), dto.bairro(), dto.cep(), dto.telefone(), dto.email(),
                dto.importadoDoCnes());
        return LocalAgendamentoViewDTO.fromEntity(localAgendamentoRepository.save(novoLocalAgendamento));
    }

    /**
     * Aplica os dados cadastrais do estabelecimento vindos da busca por CNES
     * (V91) — só usada no cadastro; a edição inline da tela continua sem
     * esses campos, por decisão explícita (editar CNES exige recriar o
     * registro, não editar em linha).
     */
    private void aplicarDadosEstabelecimento(LocalAgendamento local, String cnes, String cnpj,
            String razaoSocial, String nomeFantasia, String bairro, String cep, String telefone,
            String email, Boolean importadoDoCnes) {

        String cnesLimpo = blankToNull(cnes);
        if (cnesLimpo != null) {
            localAgendamentoRepository.findByCnes(cnesLimpo).ifPresent(outro -> {
                throw new IllegalArgumentException(
                        "Já existe um local de atendimento com o CNES " + cnesLimpo + ".");
            });
        }

        local.setCnes(cnesLimpo);
        local.setCnpj(somenteDigitos(cnpj));
        local.setRazaoSocial(blankToNull(razaoSocial));
        local.setNomeFantasia(blankToNull(nomeFantasia));
        local.setBairro(blankToNull(bairro));
        local.setCep(somenteDigitos(cep));
        local.setTelefone(blankToNull(telefone));
        local.setEmail(blankToNull(email));

        if (Boolean.TRUE.equals(importadoDoCnes)) {
            local.setSincronizadoCnesEm(LocalDateTime.now());
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

    public List<LocalAgendamentoListDTO> listarAgendamentoListDTOs(Long cidadeId){
        List<LocalAgendamento> localAgendamentoExistente = cidadeId != null
            ? localAgendamentoRepository.findByCidade_Id(cidadeId)
            : localAgendamentoRepository.findAll();
        List<LocalAgendamentoListDTO> listaLocalAgendamentos = localAgendamentoExistente.stream().map(LocalAgendamentoListDTO::fromEntity).toList();
        return listaLocalAgendamentos;
    }

    @Transactional
    public LocalAgendamentoViewDTO atualizarLocalAgendamento(Long id, LocalAgendamentoUpdateDTO dto){
        LocalAgendamento localAgendamentoExistente = localAgendamentoRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Local de agendamento não encontrado."));
        localAgendamentoExistente.setCidade(
            dto.cidadeId() != null ? cidadeRepository.findById(dto.cidadeId())
                .orElseThrow(() -> new EntityNotFoundException("Cidade não encontrada.")) : null
        );
        localAgendamentoExistente.setNomeLocal(dto.nomeLocal());
        localAgendamentoExistente.setEndereco(dto.endereco());
        localAgendamentoExistente.setNumero(dto.numero());

        return LocalAgendamentoViewDTO.fromEntity(localAgendamentoRepository.save(localAgendamentoExistente));
    }

    @Transactional
    public void deletarLocalAgendamento(Long id){
        LocalAgendamento localAgendamentoExistente = localAgendamentoRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Local de agendamento não encontrado."));
        localAgendamentoRepository.delete(localAgendamentoExistente);
    }
}
