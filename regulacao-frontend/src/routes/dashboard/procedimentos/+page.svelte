<script lang="ts">
  import { onMount } from 'svelte';
  import { getApi } from '$lib/api.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';

  // --- Estado do Componente ---
  let isLoading = $state(true);
  let error = $state('');
  let agendamentosDoDia = $state([]);

  // --- Definição dos Painéis de Procedimentos ---
  const paineisDeProcedimentos = [
    {
      label: 'Laboratório',
      href: '/agendas/laboratorio',
      color: 'bg-sky-500',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M7.5 21L3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5" />`,
      especialidades: [
        'HEMOGRAMA_COMPLETO', 'GLICEMIA_JEJUM', 'HEMOGLOBINA_GLICADA_HBA1C', 
        'COLESTEROL_TOTAL', 'HDL_COLESTEROL', 'LDL_COLESTEROL', 'VLDL_COLESTEROL', 
        'TRIGLICERIDEOS', 'UREIA', 'CREATININA', 'SODIO', 'POTASSIO', 'ACIDO_URICO', 
        'SUMARIO_DE_URINA_EAS', 'UROCULTURA_COM_ANTIBIOGRAMA', 'PARASITOLOGICO_DE_FEZES', 
        'PESQUISA_SANGUE_OCULTO_FEZES', 'TESTE_RAPIDO_GRAVIDEZ_TIG', 'TESTE_RAPIDO_HIV', 
        'SOROLOGIA_HIV', 'TESTE_RAPIDO_SIFILIS', 'VDRL', 'TESTE_RAPIDO_HEPATITE_B', 
        'HBSAG', 'ANTI_HBS', 'TESTE_RAPIDO_HEPATITE_C', 'ANTI_HCV', 
        'TSH_HORMONIO_TIREOESTIMULANTE', 'T4_LIVRE', 'PSA_TOTAL', 'PSA_LIVRE', 
        'BACILOSCOPIA_DE_ESCARRO_BAAR', 'CULTURA_DE_ESCARRO','ANALISE_CARACTERES_FISICOS_ELEMENTOS_SEDIMENTO_URINA', 'ANTIBIOGRAMA', 'BACILOSCOPIA_DIRETA_BAAR_TUBERCULOS_CONTROLE', 'BACILOSCOPIA_DIRETA_BAAR_TUBERCULOSE_DIAGNOSTICA', 'BACTEROSCOPIA_GRAM', 'CLEARANCE_CREATININA', 'CONTAGEM_PLAQUETAS', 'CONTAGEM_RETICULOCITOS', 'CULTURA_BACTERIAS_IDENTIFICACAO_UROCULTURA', 'DETERMINACAO_CAPACIDADE_FIXACAO_FERRO', 'DETERMINACAO_CURVA_GLICEMICA_2_DOSAGENS', 'DETERMINACAO_LATEX', 'DETERMINACAO_TEMPO_COAGULACAO', 'DETERMINACAO_TEMPO_SANGRAMENTO_IVY', 'DETERMINACAO_TEMPO_SANGRAMENTO_DUKE', 'DETERMINACAO_TEMPO_TROMBINA', 'DETERMINACAO_VELOCIDADE_HEMOSSEDIMENTACAO_VHS', 'DETERMINACAO_DIRETA_REVERSA_GRUPO_ABO', 'DETERMINACAO_QUANTITATIVA_PROTEINA_C_REATIVA', 'DOSAGEM_25_HIDROXIVITAMINA_D', 'DOSAGEM_ALBUMINA', 'DOSAGEM_ALFA_1_ANTITRIPSINA', 'DOSAGEM_ALFA_FETOPROTEINA', 'DOSAGEM_AMILASE', 'DOSAGEM_CALCIO_IONIZAVEL', 'DOSAGEM_COMPLEMENTO_C3', 'DOSAGEM_COMPLEMENTO_C4', 'DOSAGEM_CORTISOL', 'DOSAGEM_CREATINOFOSFOQUINASE_CPK', 'DOSAGEM_CREATINOFOSFOQUINASE_FRACAO_MB', 'DOSAGEM_DEHIDROEPIANDROSTERONA_DHEA', 'DOSAGEM_DESIDROGENASE_LATICA', 'DOSAGEM_ESTRADIOL', 'DOSAGEM_ESTRIOL', 'DOSAGEM_ESTRONA', 'DOSAGEM_FERRITINA', 'DOSAGEM_FERRO_SERICO', 'DOSAGEM_FOSFATASE_ACIDA_TOTAL', 'DOSAGEM_FOSFORO', 'DOSAGEM_GONADOTROFINA_CORIONICA_HUMANA_HCG_BETA_HCG', 'DOSAGEM_HAPTOGLOBINA', 'DOSAGEM_HEMOGLOBINA', 'DOSAGEM_HEMOGLOBINA_GLICOSILADA', 'DOSAGEM_HORMONIO_FOLICULO_ESTIMULANTE_FSH', 'DOSAGEM_HORMONIO_LUTEINIZANTE_LH', 'DOSAGEM_IMUNOGLOBULINA_A_IGA', 'DOSAGEM_IMUNOGLOBULINA_E_IGE', 'DOSAGEM_IMUNOGLOBULINA_M_IGM', 'DOSAGEM_INSULINA', 'DOSAGEM_LACTATO', 'DOSAGEM_LIPASE', 'DOSAGEM_LITIO', 'DOSAGEM_MAGNESIO', 'DOSAGEM_MICROALBUMINA_NA_URINA', 'DOSAGEM_MUCO_PROTEINAS', 'DOSAGEM_PARATORMONIO', 'DOSAGEM_PROGESTERONA', 'DOSAGEM_PROLACTINA', 'DOSAGEM_PROTEINAS_URINA_24_HORAS', 'DOSAGEM_PROTEINAS_TOTAIS', 'DOSAGEM_PROTEINAS_TOTAIS_E_FRACOES', 'DOSAGEM_SULFATO_DE_HIDROEPIANDROSTERONA_DHEAS', 'DOSAGEM_TESTOSTERONA', 'DOSAGEM_TESTOSTERONA_LIVRE', 'DOSAGEM_TIREOGLOBULINA', 'DOSAGEM_TIROXINA_T4', 'DOSAGEM_TRANSFERRINA', 'DOSAGEM_TRIIODOTIRONINA_T3', 'DOSAGEM_TROPONINA', 'DOSAGEM_VITAMINA_B12', 'DOSAGEM_ZINCO', 'ELETROFORESE_DE_HEMOGLOBINA', 'ELETROFORESE_DE_LIPOPROTEINAS', 'ERITROGRAMA', 'EXAME_CARACTERES_FISICOS_CONTAGEM_GLOBAL_ESPECIFICA_CELULAS', 'HEMATOCRITO', 'HEMOCULTURA', 'IMUNOELETROFORESE_DE_PROTEINAS', 'LEUCOGRAMA', 'PESQUISA_ANTICORPO_IGG_ANTICARDIOLIPINA', 'PESQUISA_ANTICORPO_IGM_DE_ANTICARDIOLIPINA', 'PESQUISA_ANTICORPOS_ANTI_HELICOBACTER_PYLORI', 'PESQUISA_ANTICORPOS_ANTI_HIV_1_HIV_2_ELISA', 'PESQUISA_ANTICORPOS_ANTI_HTLV_1_HTLV_2', 'PESQUISA_ANTICORPOS_ANTINUCLEO', 'PESQUISA_ANTICORPOS_ANTI_SM', 'PESQUISA_ANTICORPOS_ANTI_SS_A_RO', 'PESQUISA_ANTICORPOS_ANTI_SS_B_LA', 'PESQUISA_ANTICORPOS_ANTITIREOGLOBULINA', 'PESQUISA_ANTICORPOS_CONTRA_ANTIGENO_E_VIRUS_HEPATITE_B_ANTI_HBE', 'PESQUISA_ANTICORPOS_CONTRA_VIRUS_HEPATITE_D_ANTI_HDV', 'PESQUISA_ANTICORPOS_CONTRA_VIRUS_SARAMPO', 'PESQUISA_ANTICORPOS_IGG_ANTICITOMEGALOVIRUS', 'PESQUISA_ANTICORPOS_IGG_ANTILEISHMANIAS', 'PESQUISA_ANTICORPOS_IGG_ANTITOXOPLASMA', 'PESQUISA_ANTICORPOS_IGG_ANTITRYPANOSOMA_CRUZI', 'PESQUISA_ANTICORPOS_IGG_CONTRA_ANTIGeno_CENTRAL_VIRUS_HEPATITE_B_ANTI_HBC_IGG', 'PESQUISA_ANTICORPOS_IGG_CONTRA_VIRUS_HEPATITE_A_HAV_IGG', 'PESQUISA_ANTICORPOS_IGG_CONTRA_VIRUS_RUBEOLA', 'PESQUISA_ANTICORPOS_IGG_CONTRA_VIRUS_VARICELA_HERPES_ZOSTER', 'PESQUISA_ANTICORPOS_IGG_CONTRA_VIRUS_EPSTEIN_BARR', 'PESQUISA_ANTICORPOS_IGM_ANTICITOMEGALOVIRUS', 'PESQUISA_ANTICORPOS_IGM_ANTILEISHMANIAS', 'PESQUISA_ANTICORPOS_IGM_ANTITOXOPLASMA', 'PESQUISA_ANTICORPOS_IGM_ANTITRYPANOSOMA_CRUZI', 'PESQUISA_ANTICORPOS_IGM_CONTRA_ANTIGENO_CENTRAL_VIRUS_HEPATITE_B_ANTI_HBC_IGM', 'PESQUISA_ANTICORPOS_IGM_CONTRA_VIRUS_HEPATITE_A_HAV_IGG', 'PESQUISA_ANTICORPOS_IGM_CONTRA_VIRUS_RUBEOLA', 'PESQUISA_ANTIGENO_CARCINOEMBRIONARIO_CEA', 'PESQUISA_ANTIGENO_E_VIRUS_HEPATITE_B_HBEAG', 'PESQUISA_FATOR_REUMATOIDE_WAALER_ROSE', 'PESQUISA_FATOR_RH_INCLUI_D_FRACO', 'PESQUISA_HEMOGLOBINA_S', 'PESQUISA_LARVAS_NAS_FEZES', 'PESQUISA_TRIPANOSSOMA', 'PESQUISA_TROFOZOITAS_NAS_FEZES', 'PROVA_RETRACAO_COAGULO', 'PROVA_DO_LACO', 'REACAO_HEMAGLUTINACAO_TPHA_DIAGNOSTICO_SIFILIS', 'REACAO_MONTENEGRO_ID', 'TESTE_VDRL_DETECCAO_SIFILIS', 'TESTE_DIRETO_ANTIGLOBULINA_HUMANA_TAD', 'TESTE_FTA_ABS_IGG_DIAGNOSTICO_SIFILIS', 'TESTE_FTA_ABS_IGM_DIAGNOSTICO_SIFILIS', 'TESTE_TUBERCULINICO_PPD', 'GLICOSE', 'VDRL_DETECCAO_SIFILIS_EM_GESTANTE']
    },
    {
      label: 'Raio X',
      href: '/agendas/raio-x',
      color: 'bg-indigo-500',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75l3 3m0 0l3-3m-3 3v-7.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />`,
      especialidades: ['RAIO_X_TORAX_PA', 'RAIO_X_TORAX_PA_PERFIL', 'RAIO_X_SEIOS_DA_FACE', 'RAIO_X_COLUNA_CERVICAL', 'RAIO_X_COLUNA_DORSAL', 'RAIO_X_COLUNA_LOMBO_SACRA', 'RAIO_X_ABDOMEN_SIMPLES', 'RAIO_X_ABDOMEN_AGUDO', 'RAIO_X_ARTICULACAO_COXO_FEMURAL_BACIA', 'RAIO_X_JOELHO', 'RAIO_X_MAO_OU_QUIRODACTILOS', 'RAIO_X_PE_OU_PODODACTILOS']
    },
    {
      label: 'Ultrassonografia',
      href: '/agendas/ultrasom',
      color: 'bg-purple-500',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M9 4.5v15m6-15v15m-10.875 0h15.75c.621 0 1.125-.504 1.125-1.125V5.625c0-.621-.504-1.125-1.125-1.125H4.125C3.504 4.5 3 5.004 3 5.625v12.75c0 .621.504 1.125 1.125 1.125z" />`,
      especialidades: ['ULTRASSONOGRAFIA_PARTES_MOLES', 'ULTRASSONOGRAFIA_ABDOMINAL_TOTAL', 'ULTRASSONOGRAFIA_ABDOMEN_SUPERIOR', 'ULTRASSONOGRAFIA_PELVICA_VIA_ABDOMINAL', 'ULTRASSONOGRAFIA_PELVICA_TRANSVAGINAL', 'ULTRASSONOGRAFIA_OBSTETRICA', 'ULTRASSONOGRAFIA_VIAS_URINARIAS', 'ULTRASSONOGRAFIA_PROSTATA_VIA_ABDOMINAL', 'ULTRASSONOGRAFIA_TIREOIDE', 'ULTRASSONOGRAFIA_ARTICULAR']
    },
    {
      label: 'Doppler',
      href: '/agendas/doppler',
      color: 'bg-teal-500',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />`,
      especialidades: ['ULTRASSONOGRAFIA_DOPPLER_ARTERIAL_MEMBRO_INFERIOR_UNILATERAL', 'ULTRASSONOGRAFIA_DOPPLER_ARTERIAL_MEMBRO_INFERIOR_BILATERAL', 'ULTRASSONOGRAFIA_DOPPLER_VENOSO_MEMBRO_INFERIOR_UNILATERAL', 'ULTRASSONOGRAFIA_DOPPLER_VENOSO_MEMBRO_INFERIOR_BILATERAL', 'ULTRASSONOGRAFIA_DOPPLER_CAROTIDAS_E_VERTEBRAIS','DOPPLER']
    },
    {
      label: 'Eletrocardiograma',
      href: '/agendas/eletrocardiograma',
      color: 'bg-rose-500',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12Z" />`,
      especialidades: ['ELETROCARDIOGRAMA_ECG']
    },
     {
      label: 'Pediatra',
      href: '/agendas/pediatra',
      color: 'bg-amber-500',
      hover: 'hover:bg-amber-600',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M15.182 15.182a4.5 4.5 0 01-6.364 0M21 12a9 9 0 11-18 0 9 9 0 0118 0zM9 9.75h.008v.008H9v-.008zm6 0h.008v.008H15v-.008z" />`,
      especialidades: ['PEDIATRIA']
    },
    {
      label: 'Ortopedista',
      href: '/agendas/ortopedista',
      color: 'bg-cyan-600',
      hover: 'hover:bg-cyan-700',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M19.5 12c0-1.232-.046-2.453-.138-3.662a4.006 4.006 0 00-3.7-3.7 48.678 48.678 0 00-7.324 0 4.006 4.006 0 00-3.7 3.7c-.092 1.21-.138 2.43-.138 3.662a4.006 4.006 0 003.7 3.7 48.656 48.656 0 007.324 0 4.006 4.006 0 003.7-3.7zM12 12h.008v.008H12V12z" />`,
      especialidades: ['ORTOPEDISTA']
    },
    {
      label: 'Cardiologista',
      href: '/agendas/cardiologista',
      color: 'bg-rose-600',
      hover: 'hover:bg-rose-700',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" />`,
      especialidades: ['CARDIOLOGISTA']
    }
  ];

  function getHojeFormatado() {
    const hoje = new Date();
    const ano = hoje.getFullYear();
    const mes = String(hoje.getMonth() + 1).padStart(2, '0');
    const dia = String(hoje.getDate()).padStart(2, '0');
    return `${ano}-${mes}-${dia}`;
  }

  onMount(async () => {
    try {
      const response = await getApi('solicitacoes?size=1000');
      if (!response.ok) throw new Error('Falha ao carregar dados.');
      
      const solicitacoes = await response.json();
      const todasSolicitacoes = solicitacoes.content
      const hoje = getHojeFormatado();

      // Calcula a contagem para cada painel
      agendamentosDoDia = paineisDeProcedimentos.map(painel => {
        const solicitacoesComProcedimentoHoje = new Set();

        todasSolicitacoes.forEach(sol => {
          const temProcedimentoHoje = sol.especialidades.some(esp => {
            const agendamento = sol.agendamentos.find(ag => ag.id === esp.agendamentoId);
            return esp.status === 'AGENDADO' &&
                   agendamento && 
                   agendamento.dataAgendada === hoje &&
                   painel.especialidades.includes(esp.especialidadeSolicitada);
          });

          if (temProcedimentoHoje) {
            solicitacoesComProcedimentoHoje.add(sol.id);
          }
        });

        return { ...painel, count: solicitacoesComProcedimentoHoje.size };
      });

    } catch (e) {
      error = e.message;
    } finally {
      isLoading = false;
    }
  });


  const dataFormatada = new Date().toLocaleDateString('pt-BR', {
    weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'
  });
</script>

<svelte:head>
    <title>Painel de Procedimentos</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-50">
  <RoleBasedMenu activePage="/dashboard/procedimentos" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Painel de Procedimentos</h1>
      <UserMenu />
    </header>

    <main class="flex-1 p-6 overflow-auto">
      {#if isLoading}
        <div class="text-center py-10"><p>Carregando...</p></div>
      {:else if error}
        <div class="text-center py-10 text-red-600"><p>Erro ao carregar: {error}</p></div>
      {:else}
        <div class="max-w-7xl mx-auto">
          <div class="mb-8 p-4 border-l-4 border-emerald-500 bg-emerald-50 rounded-r-lg">
            <h2 class="text-2xl font-bold text-gray-800">Agendamentos do Dia</h2>
            <p class="text-gray-600">{dataFormatada}</p>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {#each agendamentosDoDia as procedimento}
              <a href={procedimento.href} class="block group">
                <div class="bg-white rounded-xl shadow-md p-6 transform transition-all duration-300 hover:shadow-xl hover:-translate-y-1">
                  <div class="flex items-start justify-between">
                    <div class="flex-shrink-0 w-12 h-12 {procedimento.color} text-white rounded-lg flex items-center justify-center">
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                        {@html procedimento.icon}
                      </svg>
                    </div>
                    <div class="text-right">
                      <p class="text-5xl font-bold text-gray-800">{procedimento.count}</p>
                      <p class="text-sm text-gray-500">agendados</p>
                    </div>
                  </div>
                  <h3 class="mt-4 text-lg font-semibold text-gray-900 group-hover:text-emerald-600 transition-colors">
                    {procedimento.label}
                  </h3>
                </div>
              </a>
            {/each}
          </div>
        </div>
      {/if}
    </main>
  </div>
</div>