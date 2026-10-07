package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.PeriodoIndicador;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.WhatsAppAlcanceViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.WhatsAppTelefoneInvalidoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.IndicadoresWhatsAppRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.WhatsAppIndicadorProjections.SituacaoDoAviso;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.UnidadeAcessoService;
import lombok.RequiredArgsConstructor;

/**
 * Indicadores gerenciais de WhatsApp: alcance do aviso de agendamento e
 * telefone invalido por unidade. Somente leitura e somente contagens — nao
 * enfileira, nao envia, nao le o painel do administrador.
 *
 * <p>Quem pode chamar e decidido no controller (ADMIN e GESTOR); o servico
 * confere de novo, como segunda barreira.
 *
 * <p>As respostas dizem se a instancia esta configurada e com o envio ligado:
 * sem isso nao ha o que medir, e a tela precisa dizer "nao avaliado" em vez de
 * mostrar zero.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppAlcanceService {

    private static final String ALCANCADO = "ALCANCADO";
    private static final String ACEITA = "ACEITA";
    private static final String PENDENTE = "PENDENTE";
    private static final String FALHOU = "FALHOU";

    private final IndicadoresWhatsAppRepository repository;
    private final WhatsAppEnvioProperties props;
    private final WhatsAppConfigService configService;
    private final UnidadeAcessoService unidadeAcessoService;

    @Transactional(readOnly = true)
    public WhatsAppAlcanceViewDTO alcance(Long unidadeId, String de, String ate, String callerCpf) {
        exigirGestao(callerCpf);
        PeriodoIndicador periodo = PeriodoIndicador.de(de, ate);

        long agendamentos = 0;
        long alcancados = 0;
        long lidos = 0;
        long aceitas = 0;
        long pendentes = 0;
        long falhas = 0;
        // Ordenado pela chave: a ordem dos motivos nao muda de uma consulta para outra.
        Map<String, Long> naoEnviados = new TreeMap<>();

        for (SituacaoDoAviso linha : repository.alcancePorSituacao(
                unidadeId, periodo.inicioInstant(), periodo.fimExclusivoInstant())) {
            long total = linha.getTotal() != null ? linha.getTotal() : 0L;
            agendamentos += total;
            switch (linha.getSituacao()) {
                case ALCANCADO -> {
                    alcancados += total;
                    lidos += linha.getLidos() != null ? linha.getLidos() : 0L;
                }
                case ACEITA -> aceitas += total;
                case PENDENTE -> pendentes += total;
                case FALHOU -> falhas += total;
                default -> naoEnviados.merge(linha.getSituacao(), total, Long::sum);
            }
        }

        return new WhatsAppAlcanceViewDTO(periodo.de(), periodo.ate(), props.configurado(),
                configService.envioLigado(), agendamentos, alcancados, lidos, aceitas, pendentes, falhas,
                naoEnviados);
    }

    @Transactional(readOnly = true)
    public WhatsAppTelefoneInvalidoViewDTO telefoneInvalido(Long unidadeId, String de, String ate,
            String callerCpf) {
        exigirGestao(callerCpf);
        PeriodoIndicador periodo = PeriodoIndicador.de(de, ate);

        List<WhatsAppTelefoneInvalidoViewDTO.Linha> porUnidade = repository
                .telefoneInvalidoPorUnidade(unidadeId, periodo.inicioInstant(), periodo.fimExclusivoInstant())
                .stream()
                .map(WhatsAppTelefoneInvalidoViewDTO.Linha::from)
                // Onde ha mais cadastro a corrigir aparece primeiro; "Sem unidade" (nome nulo) por ultimo.
                .sorted(Comparator.comparingLong(WhatsAppTelefoneInvalidoViewDTO.Linha::invalidos).reversed()
                        .thenComparing(WhatsAppTelefoneInvalidoViewDTO.Linha::nome,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        return new WhatsAppTelefoneInvalidoViewDTO(periodo.de(), periodo.ate(), props.configurado(),
                configService.envioLigado(), WhatsAppTelefoneInvalidoViewDTO.Linha.somar(porUnidade), porUnidade);
    }

    private void exigirGestao(String callerCpf) {
        if (!unidadeAcessoService.isAdminOuGestor(callerCpf)) {
            throw new AccessDeniedException("Acesso restrito ao administrador e ao gestor.");
        }
    }
}
