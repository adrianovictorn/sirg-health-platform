package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoSendDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoSolicitacaoSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.ContagemPainelDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.PacienteAgendadoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoResumoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.EspecialidadesEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.AgendamentoService; // Mantido AgendamentoService
import io.github.regulacao_marcarcao.regulacao_marcacao.service.SolicitacaoService; // Importar SolicitacaoService também
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/agendamentos")
@RequiredArgsConstructor
public class AgendamentoController {


    private final AgendamentoService agendamentoService;
    private final SolicitacaoService solicitacaoService;
    private final SolicitacaoEspecialidadeRepository solicitacaoRepo;
    private final EspecialidadeRepository especialidadeRepository;


    /**
     * Lista todas as solicitações pendentes para agendamento.
     */
    @GetMapping("/pendentes/buscar")
    public ResponseEntity<Page<SolicitacaoResumoDTO>> buscarPendentes(@RequestParam(required = false, defaultValue = "") String termo, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size ) {
        
        PageRequest pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(agendamentoService.buscarPendentesParaAutoComplete(termo, pageable));
    }

    /**
     * Lista todos os agendamentos já criados.
     */
    @GetMapping
    public ResponseEntity<List<AgendamentoSolicitacaoSimpleViewDTO>> listarTodosAgendamentos() {
        List<AgendamentoSolicitacaoSimpleViewDTO> agendamentos = agendamentoService.listAll();
        return ResponseEntity.ok(agendamentos);
    }

    /**
     * Cria um agendamento para uma solicitação específica.
     */
    @PostMapping("/{solicitacaoId}")
    public ResponseEntity<AgendamentoSolicitacaoSimpleViewDTO> criarAgendamento(
            @PathVariable Long solicitacaoId,
            @RequestBody MultiAgendamentoCreateDTO dto) { // Usa o novo DTO
        // Chama o novo método criado no AgendamentoService
        AgendamentoSolicitacaoSimpleViewDTO agendamentoCriado = agendamentoService.criarAgendamentoParaMultiplosExames(solicitacaoId, dto);
        return new ResponseEntity<>(agendamentoCriado, HttpStatus.CREATED);
    }

    /**
     * Lista todos os agendamentos criados para uma solicitação específica.
     */
    @GetMapping("/solicitacao/{solicitacaoId}")
    public ResponseEntity<List<AgendamentoSolicitacaoSimpleViewDTO>> listarAgendamentosPorSolicitacaoId(@PathVariable Long solicitacaoId) {
        List<AgendamentoSolicitacaoSimpleViewDTO> agendamentos = solicitacaoService.listarAgendamentosPorSolicitacaoId(solicitacaoId);
        return ResponseEntity.ok(agendamentos);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarAgendamento(@PathVariable Long id){
        agendamentoService.deleteAgendamento(id);
        return ResponseEntity.noContent().build();
    }

    private static final Map<String, List<EspecialidadesEnum>> PAINEIS_ENUMS = Map.ofEntries(
        Map.entry("Laboratório", List.of(
            EspecialidadesEnum.ASLO, EspecialidadesEnum.ACIDO_URICO, EspecialidadesEnum.ANALISE_CARACTERES_FISICOS_ELEMENTOS_SEDIMENTO_URINA,
            EspecialidadesEnum.ANTI_HBS, EspecialidadesEnum.ANTI_HCV, EspecialidadesEnum.ANTIBIOGRAMA, EspecialidadesEnum.BACILOSCOPIA_DE_ESCARRO_BAAR,
            EspecialidadesEnum.BACILOSCOPIA_DIRETA_BAAR_TUBERCULOS_CONTROLE, EspecialidadesEnum.BACILOSCOPIA_DIRETA_BAAR_TUBERCULOSE_DIAGNOSTICA,
            EspecialidadesEnum.BACTEROSCOPIA_GRAM, EspecialidadesEnum.BILIRRUBINA_TOTAL_FRACOES, EspecialidadesEnum.CALCIO,
            EspecialidadesEnum.CLEARANCE_CREATININA, EspecialidadesEnum.COAGULOGRAMA, EspecialidadesEnum.COLESTEROL_TOTAL,
            EspecialidadesEnum.CONTAGEM_PLAQUETAS, EspecialidadesEnum.CONTAGEM_RETICULOCITOS, EspecialidadesEnum.CREATININA,
            EspecialidadesEnum.CULTURA_BACTERIAS_IDENTIFICACAO_UROCULTURA, EspecialidadesEnum.CULTURA_DE_ESCARRO,
            EspecialidadesEnum.DETERMINACAO_CAPACIDADE_FIXACAO_FERRO, EspecialidadesEnum.DETERMINACAO_CURVA_GLICEMICA_2_DOSAGENS,
            EspecialidadesEnum.DETERMINACAO_LATEX, EspecialidadesEnum.DETERMINACAO_TEMPO_COAGULACAO,
            EspecialidadesEnum.DETERMINACAO_TEMPO_SANGRAMENTO_IVY, EspecialidadesEnum.DETERMINACAO_TEMPO_SANGRAMENTO_DUKE,
            EspecialidadesEnum.DETERMINACAO_TEMPO_TROMBINA, EspecialidadesEnum.DETERMINACAO_VELOCIDADE_HEMOSSEDIMENTACAO_VHS,
            EspecialidadesEnum.DETERMINACAO_DIRETA_REVERSA_GRUPO_ABO, EspecialidadesEnum.DETERMINACAO_QUANTITATIVA_PROTEINA_C_REATIVA,
            EspecialidadesEnum.DOSAGEM_25_HIDROXIVITAMINA_D, EspecialidadesEnum.DOSAGEM_ALBUMINA, EspecialidadesEnum.DOSAGEM_ALFA_1_ANTITRIPSINA,
            EspecialidadesEnum.DOSAGEM_ALFA_FETOPROTEINA, EspecialidadesEnum.DOSAGEM_AMILASE, EspecialidadesEnum.DOSAGEM_CALCIO_IONIZAVEL,
            EspecialidadesEnum.DOSAGEM_COMPLEMENTO_C3, EspecialidadesEnum.DOSAGEM_COMPLEMENTO_C4, EspecialidadesEnum.DOSAGEM_CORTISOL,
            EspecialidadesEnum.DOSAGEM_CREATINOFOSFOQUINASE_CPK, EspecialidadesEnum.DOSAGEM_CREATINOFOSFOQUINASE_FRACAO_MB,
            EspecialidadesEnum.DOSAGEM_DEHIDROEPIANDROSTERONA_DHEA, EspecialidadesEnum.DOSAGEM_DESIDROGENASE_LATICA,
            EspecialidadesEnum.DOSAGEM_ESTRADIOL, EspecialidadesEnum.DOSAGEM_ESTRIOL, EspecialidadesEnum.DOSAGEM_ESTRONA,
            EspecialidadesEnum.DOSAGEM_FERRITINA, EspecialidadesEnum.DOSAGEM_FERRO_SERICO, EspecialidadesEnum.DOSAGEM_FOSFATASE_ACIDA_TOTAL,
            EspecialidadesEnum.DOSAGEM_FOSFORO, EspecialidadesEnum.DOSAGEM_GONADOTROFINA_CORIONICA_HUMANA_HCG_BETA_HCG,
            EspecialidadesEnum.DOSAGEM_HAPTOGLOBINA, EspecialidadesEnum.DOSAGEM_HEMOGLOBINA, EspecialidadesEnum.DOSAGEM_HEMOGLOBINA_GLICOSILADA,
            EspecialidadesEnum.DOSAGEM_HORMONIO_FOLICULO_ESTIMULANTE_FSH, EspecialidadesEnum.DOSAGEM_HORMONIO_LUTEINIZANTE_LH,
            EspecialidadesEnum.DOSAGEM_IMUNOGLOBULINA_A_IGA, EspecialidadesEnum.DOSAGEM_IMUNOGLOBULINA_E_IGE,
            EspecialidadesEnum.DOSAGEM_IMUNOGLOBULINA_M_IGM, EspecialidadesEnum.DOSAGEM_INSULINA, EspecialidadesEnum.DOSAGEM_LACTATO,
            EspecialidadesEnum.DOSAGEM_LIPASE, EspecialidadesEnum.DOSAGEM_LITIO, EspecialidadesEnum.DOSAGEM_MAGNESIO,
            EspecialidadesEnum.DOSAGEM_MICROALBUMINA_NA_URINA, EspecialidadesEnum.DOSAGEM_MUCO_PROTEINAS,
            EspecialidadesEnum.DOSAGEM_PARATORMONIO, EspecialidadesEnum.DOSAGEM_PROGESTERONA, EspecialidadesEnum.DOSAGEM_PROLACTINA,
            EspecialidadesEnum.DOSAGEM_PROTEINAS_URINA_24_HORAS, EspecialidadesEnum.DOSAGEM_PROTEINAS_TOTAIS,
            EspecialidadesEnum.DOSAGEM_PROTEINAS_TOTAIS_E_FRACOES, EspecialidadesEnum.DOSAGEM_SULFATO_DE_HIDROEPIANDROSTERONA_DHEAS,
            EspecialidadesEnum.DOSAGEM_TESTOSTERONA, EspecialidadesEnum.DOSAGEM_TESTOSTERONA_LIVRE, EspecialidadesEnum.DOSAGEM_TIREOGLOBULINA,
            EspecialidadesEnum.DOSAGEM_TIROXINA_T4, EspecialidadesEnum.DOSAGEM_TRANSFERRINA, EspecialidadesEnum.DOSAGEM_TRIIODOTIRONINA_T3,
            EspecialidadesEnum.DOSAGEM_TROPONINA, EspecialidadesEnum.DOSAGEM_VITAMINA_B12, EspecialidadesEnum.DOSAGEM_ZINCO,
            EspecialidadesEnum.ELETROFORESE_DE_HEMOGLOBINA, EspecialidadesEnum.ELETROFORESE_DE_LIPOPROTEINAS, EspecialidadesEnum.ERITROGRAMA,
            EspecialidadesEnum.EXAME_CARACTERES_FISICOS_CONTAGEM_GLOBAL_ESPECIFICA_CELULAS, EspecialidadesEnum.FATOR_REUMATOIDE,
            EspecialidadesEnum.FOSFATASE_ALCALINA, EspecialidadesEnum.GAMA_GT, EspecialidadesEnum.GLICEMIA_JEJUM,
            EspecialidadesEnum.GLICOSE, EspecialidadesEnum.HBSAG, EspecialidadesEnum.HDL_COLESTEROL, EspecialidadesEnum.HEMATOCRITO,
            EspecialidadesEnum.HEMOCULTURA, EspecialidadesEnum.HEMOGLOBINA_GLICADA_HBA1C, EspecialidadesEnum.HEMOGRAMA_COMPLETO,
            EspecialidadesEnum.IMUNOELETROFORESE_DE_PROTEINAS, EspecialidadesEnum.LDL_COLESTEROL, EspecialidadesEnum.LEUCOGRAMA,
            EspecialidadesEnum.PSA_LIVRE, EspecialidadesEnum.PSA_TOTAL, EspecialidadesEnum.PARASITOLOGICO_DE_FEZES,
            EspecialidadesEnum.PESQUISA_ANTICORPO_IGG_ANTICARDIOLIPINA, EspecialidadesEnum.PESQUISA_ANTICORPO_IGM_DE_ANTICARDIOLIPINA,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_ANTI_HELICOBACTER_PYLORI, EspecialidadesEnum.PESQUISA_ANTICORPOS_ANTI_HIV_1_HIV_2_ELISA,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_ANTI_HTLV_1_HTLV_2, EspecialidadesEnum.PESQUISA_ANTICORPOS_ANTI_SM,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_ANTI_SS_A_RO, EspecialidadesEnum.PESQUISA_ANTICORPOS_ANTI_SS_B_LA,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_ANTINUCLEO, EspecialidadesEnum.PESQUISA_ANTICORPOS_ANTITIREOGLOBULINA,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_CONTRA_ANTIGENO_E_VIRUS_HEPATITE_B_ANTI_HBE,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_CONTRA_VIRUS_HEPATITE_D_ANTI_HDV, EspecialidadesEnum.PESQUISA_ANTICORPOS_CONTRA_VIRUS_SARAMPO,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_IGG_ANTICITOMEGALOVIRUS, EspecialidadesEnum.PESQUISA_ANTICORPOS_IGG_ANTILEISHMANIAS,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_IGG_ANTITOXOPLASMA, EspecialidadesEnum.PESQUISA_ANTICORPOS_IGG_ANTITRYPANOSOMA_CRUZI,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_IGG_CONTRA_ANTIGENO_CENTRAL_VIRUS_HEPATITE_B_ANTI_HBC_IGG,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_IGG_CONTRA_VIRUS_HEPATITE_A_HAV_IGG,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_IGG_CONTRA_VIRUS_RUBEOLA,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_IGG_CONTRA_VIRUS_VARICELA_HERPES_ZOSTER,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_IGG_CONTRA_VIRUS_EPSTEIN_BARR,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_IGM_ANTICITOMEGALOVIRUS, EspecialidadesEnum.PESQUISA_ANTICORPOS_IGM_ANTILEISHMANIAS,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_IGM_ANTITOXOPLASMA, EspecialidadesEnum.PESQUISA_ANTICORPOS_IGM_ANTITRYPANOSOMA_CRUZI,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_IGM_CONTRA_ANTIGENO_CENTRAL_VIRUS_HEPATITE_B_ANTI_HBC_IGM,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_IGM_CONTRA_VIRUS_HEPATITE_A_HAV_IGG,
            EspecialidadesEnum.PESQUISA_ANTICORPOS_IGM_CONTRA_VIRUS_RUBEOLA, EspecialidadesEnum.PESQUISA_ANTIGENO_CARCINOEMBRIONARIO_CEA,
            EspecialidadesEnum.PESQUISA_ANTIGENO_E_VIRUS_HEPATITE_B_HBEAG, EspecialidadesEnum.PESQUISA_FATOR_RH_INCLUI_D_FRACO,
            EspecialidadesEnum.PESQUISA_FATOR_REUMATOIDE_WAALER_ROSE, EspecialidadesEnum.PESQUISA_HEMOGLOBINA_S,
            EspecialidadesEnum.PESQUISA_LARVAS_NAS_FEZES, EspecialidadesEnum.PESQUISA_OVOS_CISTOS_PARASITAS,
            EspecialidadesEnum.PESQUISA_SANGUE_OCULTO_FEZES, EspecialidadesEnum.PESQUISA_TRIPANOSSOMA,
            EspecialidadesEnum.PESQUISA_TROFOZOITAS_NAS_FEZES, EspecialidadesEnum.POTASSIO, EspecialidadesEnum.PROVA_RETRACAO_COAGULO,
            EspecialidadesEnum.PROVA_DO_LACO, EspecialidadesEnum.REACAO_HEMAGLUTINACAO_TPHA_DIAGNOSTICO_SIFILIS,
            EspecialidadesEnum.REACAO_MONTENEGRO_ID, EspecialidadesEnum.SODIO, EspecialidadesEnum.SOROLOGIA_HIV,
            EspecialidadesEnum.SUMARIO_DE_URINA_EAS, EspecialidadesEnum.T4_LIVRE, EspecialidadesEnum.TGO, EspecialidadesEnum.TGP,
            EspecialidadesEnum.TP, EspecialidadesEnum.TSH_HORMONIO_TIREOESTIMULANTE, EspecialidadesEnum.TTPA,
            EspecialidadesEnum.TESTE_VDRL_DETECCAO_SIFILIS, EspecialidadesEnum.TESTE_DIRETO_ANTIGLOBULINA_HUMANA_TAD,
            EspecialidadesEnum.TESTE_FTA_ABS_IGG_DIAGNOSTICO_SIFILIS, EspecialidadesEnum.TESTE_FTA_ABS_IGM_DIAGNOSTICO_SIFILIS,
            EspecialidadesEnum.TESTE_RAPIDO_GRAVIDEZ_TIG, EspecialidadesEnum.TESTE_RAPIDO_HEPATITE_B,
            EspecialidadesEnum.TESTE_RAPIDO_HEPATITE_C, EspecialidadesEnum.TESTE_RAPIDO_HIV, EspecialidadesEnum.TESTE_RAPIDO_SIFILIS,
            EspecialidadesEnum.TESTE_TUBERCULINICO_PPD, EspecialidadesEnum.TIPO_SANGUINEO, EspecialidadesEnum.TRIGLICERIDEOS,
            EspecialidadesEnum.UREIA, EspecialidadesEnum.UROCULTURA_COM_ANTIBIOGRAMA, EspecialidadesEnum.VDRL,
            EspecialidadesEnum.VDRL_DETECCAO_SIFILIS_EM_GESTANTE, EspecialidadesEnum.VLDL_COLESTEROL
        )),
         Map.entry("Raio X", List.of(
            EspecialidadesEnum.RAIO_X_ABDOMEN_AGUDO,
            EspecialidadesEnum.RAIO_X_ABDOMEN_SIMPLES,
            EspecialidadesEnum.RAIO_X_ARTICULACAO_COXO_FEMURAL_BACIA,
            EspecialidadesEnum.RAIO_X_CALCANEO,
            EspecialidadesEnum.RAIO_X_CAVUM,
            EspecialidadesEnum.RAIO_X_COLUNA_CERVICAL,
            EspecialidadesEnum.RAIO_X_COLUNA_DORSAL,
            EspecialidadesEnum.RAIO_X_COLUNA_LOMBO_SACRA,
            EspecialidadesEnum.RAIO_X_COLUNA_TORACICA,
            EspecialidadesEnum.RAIO_X_ESCAPULA,
            EspecialidadesEnum.RAIO_X_JOELHO,
            EspecialidadesEnum.RAIO_X_OMBRO,
            EspecialidadesEnum.RAIO_X_MAO_OU_QUIRODACTILOS,
            EspecialidadesEnum.RAIO_X_PE_OU_PODODACTILOS,
            EspecialidadesEnum.RAIO_X_SEIOS_DA_FACE,
            EspecialidadesEnum.RAIO_X_REGIAO_CERVICAL,
            EspecialidadesEnum.RAIO_X_TORAX_PA,
            EspecialidadesEnum.RAIO_X_TORAX_PA_PERFIL
        )),
    Map.entry("Ultrassonografia", List.of(
            EspecialidadesEnum.ULTRASSONOGRAFIA_ABDOMEN_SUPERIOR,
            EspecialidadesEnum.ULTRASSONOGRAFIA_ABDOMINAL_TOTAL,
            EspecialidadesEnum.ULTRASSONOGRAFIA_ARTICULAR,
            EspecialidadesEnum.ULTRASSONOGRAFIA_OBSTETRICA,
            EspecialidadesEnum.ULTRASSONOGRAFIA_PARTES_MOLES,
            EspecialidadesEnum.ULTRASSONOGRAFIA_PELVICA_VIA_ABDOMINAL,
            EspecialidadesEnum.ULTRASSONOGRAFIA_PELVICA_TRANSVAGINAL,
            EspecialidadesEnum.ULTRASSONOGRAFIA_PROSTATA_VIA_ABDOMINAL,
            EspecialidadesEnum.ULTRASSONOGRAFIA_TIREOIDE,
            EspecialidadesEnum.ULTRASSONOGRAFIA_VIAS_URINARIAS
        )),
        Map.entry("Doppler", List.of(
            EspecialidadesEnum.DOPPLER,
            EspecialidadesEnum.ULTRASSONOGRAFIA_DOPPLER_ARTERIAL_MEMBRO_INFERIOR_BILATERAL,
            EspecialidadesEnum.ULTRASSONOGRAFIA_DOPPLER_ARTERIAL_MEMBRO_INFERIOR_UNILATERAL,
            EspecialidadesEnum.ULTRASSONOGRAFIA_DOPPLER_CAROTIDAS_E_VERTEBRAIS,
            EspecialidadesEnum.ULTRASSONOGRAFIA_DOPPLER_VENOSO_MEMBRO_INFERIOR_BILATERAL,
            EspecialidadesEnum.ULTRASSONOGRAFIA_DOPPLER_VENOSO_MEMBRO_INFERIOR_UNILATERAL
        )),
        Map.entry("Eletrocardiograma", List.of(EspecialidadesEnum.ELETROCARDIOGRAMA_ECG)),
        Map.entry("Pediatra", List.of(EspecialidadesEnum.PEDIATRIA)),
        Map.entry("Ortopedista", List.of(EspecialidadesEnum.ORTOPEDISTA)),
        Map.entry("Cardiologista", List.of(EspecialidadesEnum.CARDIOLOGISTA)),
        Map.entry("Cirurgião Geral", List.of(EspecialidadesEnum.CIRURGIAO_GERAL)),
        Map.entry("Dermatologista", List.of(EspecialidadesEnum.DERMATOLOGISTA)),
        Map.entry("Ecocardiograma", List.of(EspecialidadesEnum.ECOCARDIOGRAMA_TRANSTORACICO_MODO_M_BIDIMENSIONAL_DOPPLER)),
        Map.entry("Neuropediatra", List.of(EspecialidadesEnum.NEUROPEDIATRIA)),
        Map.entry("Procedimento Dermatologista", List.of(EspecialidadesEnum.PROCEDIMENTO_DERMATOLOGISTA))
    );

    @GetMapping("/contagem-por-data")
    public List<ContagemPainelDTO> consultarAgendamentoPorData(
            @RequestParam("data") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(value = "grupo", required = false) String grupo) {
        
        List<ContagemPainelDTO> resultados = new ArrayList<>();

        List<Map.Entry<String, List<EspecialidadesEnum>>> paineis = new ArrayList<>(PAINEIS_ENUMS.entrySet());
        if (grupo != null && !grupo.isBlank()) {
            paineis = PAINEIS_ENUMS.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(grupo))
                .toList();
        }

        for (Map.Entry<String, List<EspecialidadesEnum>> painel : paineis) {
            String label = painel.getKey();
            List<EspecialidadesEnum> enums = painel.getValue();
            List<String> codigos = enums.stream().map(Enum::name).toList();

            long agendados = solicitacaoRepo.countDistinctSolicitacoesPorDataECodigos(data, codigos);

            var especialidadesDoGrupo = especialidadeRepository.findByCodigoIn(codigos);
            boolean semLimite = especialidadesDoGrupo.stream().anyMatch(e -> e.getVagas() == null || e.getVagas() == 0);
            long capacidade = semLimite
                ? 0
                : especialidadesDoGrupo.stream().mapToLong(e -> e.getVagas() != null ? e.getVagas() : 0).sum();

            long restante = semLimite ? -1 : Math.max(0, capacidade - agendados);

            resultados.add(new ContagemPainelDTO(label, agendados, capacidade, restante));
        }
        
        return resultados;
    }

    @GetMapping("/contagem-por-data-especialidade")
    public ContagemPainelDTO consultarAgendamentoPorDataEspecialidade(
            @RequestParam("data") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam("codigo") String codigo) {

        long agendados = solicitacaoRepo.countDistinctSolicitacoesPorDataECodigos(data, List.of(codigo.toUpperCase()));

        long capacidade = especialidadeRepository.findByCodigoIn(List.of(codigo.toUpperCase())).stream()
            .mapToLong(e -> e.getVagas() != null ? e.getVagas() : 0)
            .sum();

        long restante = capacidade > 0 ? Math.max(0, capacidade - agendados) : -1;

        return new ContagemPainelDTO(codigo, agendados, capacidade, restante);
    }

    @GetMapping("/buscar/por/data")
    public ResponseEntity<List<AgendamentoSendDTO>> buscarAgendamentoPorData(@RequestParam int dias){
        return ResponseEntity.ok(agendamentoService.buscarPorDataparaEnvio(dias));
    }
    


    @GetMapping("/pacientes-por-data")
    public List<PacienteAgendadoDTO> getPacientesPorData(
            @RequestParam("data") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam("categoria") String categoria) {
        
        // Pega a lista de enums correspondente à categoria
        List<EspecialidadesEnum> enums = PAINEIS_ENUMS.get(categoria);
        if (enums == null) {
            return List.of(); // Retorna lista vazia se a categoria for inválida
        }

        // Busca as solicitações no banco
        List<String> codigos = enums.stream().map(Enum::name).toList();
        List<SolicitacaoEspecialidade> agendamentos = solicitacaoRepo.findAgendadasCompletasPorDataECodigos(data, codigos);

        // Converte a lista de entidades para uma lista de DTOs
        return agendamentos.stream()
            .map(se -> new PacienteAgendadoDTO(
                se.getSolicitacao().getNomePaciente(),
                se.getEspecialidadeSolicitada() != null ? se.getEspecialidadeSolicitada().getNome() : se.getEspecialidadeCodigoLegacy(),
                se.getStatus().toString(),
                se.getSolicitacao().getId()
            ))
            .distinct() // Garante que a mesma solicitação não apareça duplicada se tiver mais de um exame na mesma categoria
            .toList();
    }

    

   
}


