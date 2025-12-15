<script lang="ts">
  import { onMount } from "svelte";
  import { getApi } from '$lib/api';
  import jsPDF from 'jspdf';
  import autoTable from 'jspdf-autotable';
  import { opcoesEspecialidades } from '$lib/Especialidades.js';
  import Menu from "$lib/Menu.svelte";
  import UserMenu from "$lib/UserMenu.svelte";

  // --- Estado do Componente ---
  let dataSelecionada = $state(new Date().toISOString().split('T')[0]);
  let loadingType = $state<string | null>(null);
  let allSolicitacoes = $state<any[]>([]);
  let isLoadingData = $state(true);

  // --- ESTRUTURA DE RELATÓRIOS ---
  const tiposDeProcedimento = [
    {
      label: 'Laboratório',
      color: 'bg-emerald-600',
      hover: 'hover:bg-emerald-700',
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
        'BACILOSCOPIA_DE_ESCARRO_BAAR', 'CULTURA_DE_ESCARRO','ANALISE_CARACTERES_FISICOS_ELEMENTOS_SEDIMENTO_URINA',
    'ANTIBIOGRAMA',
    'BACILOSCOPIA_DIRETA_BAAR_TUBERCULOS_CONTROLE',
    'BACILOSCOPIA_DIRETA_BAAR_TUBERCULOSE_DIAGNOSTICA',
    'BACTEROSCOPIA_GRAM',
    'CLEARANCE_CREATININA',
    'CONTAGEM_PLAQUETAS',
    'CONTAGEM_RETICULOCITOS',
    'CULTURA_BACTERIAS_IDENTIFICACAO_UROCULTURA',
    'DETERMINACAO_CAPACIDADE_FIXACAO_FERRO',
    'DETERMINACAO_CURVA_GLICEMICA_2_DOSAGENS',
    'DETERMINACAO_LATEX',
    'DETERMINACAO_TEMPO_COAGULACAO',
    'DETERMINACAO_TEMPO_SANGRAMENTO_IVY',
    'DETERMINACAO_TEMPO_SANGRAMENTO_DUKE',
    'DETERMINACAO_TEMPO_TROMBINA',
    'DETERMINACAO_VELOCIDADE_HEMOSSEDIMENTACAO_VHS',
    'DETERMINACAO_DIRETA_REVERSA_GRUPO_ABO',
    'DETERMINACAO_QUANTITATIVA_PROTEINA_C_REATIVA',
    'DOSAGEM_25_HIDROXIVITAMINA_D',
    'DOSAGEM_ALBUMINA',
    'DOSAGEM_ALFA_1_ANTITRIPSINA',
    'DOSAGEM_ALFA_FETOPROTEINA',
    'DOSAGEM_AMILASE',
    'DOSAGEM_CALCIO_IONIZAVEL',
    'DOSAGEM_COMPLEMENTO_C3',
    'DOSAGEM_COMPLEMENTO_C4',
    'DOSAGEM_CORTISOL',
    'DOSAGEM_CREATINOFOSFOQUINASE_CPK',
    'DOSAGEM_CREATINOFOSFOQUINASE_FRACAO_MB',
    'DOSAGEM_DEHIDROEPIANDROSTERONA_DHEA',
    'DOSAGEM_DESIDROGENASE_LATICA',
    'DOSAGEM_ESTRADIOL',
    'DOSAGEM_ESTRIOL',
    'DOSAGEM_ESTRONA',
    'DOSAGEM_FERRITINA',
    'DOSAGEM_FERRO_SERICO',
    'DOSAGEM_FOSFATASE_ACIDA_TOTAL',
    'DOSAGEM_FOSFORO',
    'DOSAGEM_GONADOTROFINA_CORIONICA_HUMANA_HCG_BETA_HCG',
    'DOSAGEM_HAPTOGLOBINA',
    'DOSAGEM_HEMOGLOBINA',
    'DOSAGEM_HEMOGLOBINA_GLICOSILADA',
    'DOSAGEM_HORMONIO_FOLICULO_ESTIMULANTE_FSH',
    'DOSAGEM_HORMONIO_LUTEINIZANTE_LH',
    'DOSAGEM_IMUNOGLOBULINA_A_IGA',
    'DOSAGEM_IMUNOGLOBULINA_E_IGE',
    'DOSAGEM_IMUNOGLOBULINA_M_IGM',
    'DOSAGEM_INSULINA',
    'DOSAGEM_LACTATO',
    'DOSAGEM_LIPASE',
    'DOSAGEM_LITIO',
    'DOSAGEM_MAGNESIO',
    'DOSAGEM_MICROALBUMINA_NA_URINA',
    'DOSAGEM_MUCO_PROTEINAS',
    'DOSAGEM_PARATORMONIO',
    'DOSAGEM_PROGESTERONA',
    'DOSAGEM_PROLACTINA',
    'DOSAGEM_PROTEINAS_URINA_24_HORAS',
    'DOSAGEM_PROTEINAS_TOTAIS',
    'DOSAGEM_PROTEINAS_TOTAIS_E_FRACOES',
    'DOSAGEM_SULFATO_DE_HIDROEPIANDROSTERONA_DHEAS',
    'DOSAGEM_TESTOSTERONA',
    'DOSAGEM_TESTOSTERONA_LIVRE',
    'DOSAGEM_TIREOGLOBULINA',
    'DOSAGEM_TIROXINA_T4',
    'DOSAGEM_TRANSFERRINA',
    'DOSAGEM_TRIIODOTIRONINA_T3',
    'DOSAGEM_TROPONINA',
    'DOSAGEM_VITAMINA_B12',
    'DOSAGEM_ZINCO',
    'ELETROFORESE_DE_HEMOGLOBINA',
    'ELETROFORESE_DE_LIPOPROTEINAS',
    'ERITROGRAMA',
    'EXAME_CARACTERES_FISICOS_CONTAGEM_GLOBAL_ESPECIFICA_CELULAS',
    'HEMATOCRITO',
    'HEMOCULTURA',
    'IMUNOELETROFORESE_DE_PROTEINAS',
    'LEUCOGRAMA',
    'PESQUISA_ANTICORPO_IGG_ANTICARDIOLIPINA',
    'PESQUISA_ANTICORPO_IGM_DE_ANTICARDIOLIPINA',
    'PESQUISA_ANTICORPOS_ANTI_HELICOBACTER_PYLORI',
    'PESQUISA_ANTICORPOS_ANTI_HIV_1_HIV_2_ELISA',
    'PESQUISA_ANTICORPOS_ANTI_HTLV_1_HTLV_2',
    'PESQUISA_ANTICORPOS_ANTINUCLEO',
    'PESQUISA_ANTICORPOS_ANTI_SM',
    'PESQUISA_ANTICORPOS_ANTI_SS_A_RO',
    'PESQUISA_ANTICORPOS_ANTI_SS_B_LA',
    'PESQUISA_ANTICORPOS_ANTITIREOGLOBULINA',
    'PESQUISA_ANTICORPOS_CONTRA_ANTIGENO_E_VIRUS_HEPATITE_B_ANTI_HBE',
    'PESQUISA_ANTICORPOS_CONTRA_VIRUS_HEPATITE_D_ANTI_HDV',
    'PESQUISA_ANTICORPOS_CONTRA_VIRUS_SARAMPO',
    'PESQUISA_ANTICORPOS_IGG_ANTICITOMEGALOVIRUS',
    'PESQUISA_ANTICORPOS_IGG_ANTILEISHMANIAS',
    'PESQUISA_ANTICORPOS_IGG_ANTITOXOPLASMA',
    'PESQUISA_ANTICORPOS_IGG_ANTITRYPANOSOMA_CRUZI',
    'PESQUISA_ANTICORPOS_IGG_CONTRA_ANTIGENO_CENTRAL_VIRUS_HEPATITE_B_ANTI_HBC_IGG',
    'PESQUISA_ANTICORPOS_IGG_CONTRA_VIRUS_HEPATITE_A_HAV_IGG',
    'PESQUISA_ANTICORPOS_IGG_CONTRA_VIRUS_RUBEOLA',
    'PESQUISA_ANTICORPOS_IGG_CONTRA_VIRUS_VARICELA_HERPES_ZOSTER',
    'PESQUISA_ANTICORPOS_IGG_CONTRA_VIRUS_EPSTEIN_BARR',
    'PESQUISA_ANTICORPOS_IGM_ANTICITOMEGALOVIRUS',
    'PESQUISA_ANTICORPOS_IGM_ANTILEISHMANIAS',
    'PESQUISA_ANTICORPOS_IGM_ANTITOXOPLASMA',
    'PESQUISA_ANTICORPOS_IGM_ANTITRYPANOSOMA_CRUZI',
    'PESQUISA_ANTICORPOS_IGM_CONTRA_ANTIGENO_CENTRAL_VIRUS_HEPATITE_B_ANTI_HBC_IGM',
    'PESQUISA_ANTICORPOS_IGM_CONTRA_VIRUS_HEPATITE_A_HAV_IGG',
    'PESQUISA_ANTICORPOS_IGM_CONTRA_VIRUS_RUBEOLA',
    'PESQUISA_ANTIGENO_CARCINOEMBRIONARIO_CEA',
    'PESQUISA_ANTIGENO_E_VIRUS_HEPATITE_B_HBEAG',
    'PESQUISA_FATOR_REUMATOIDE_WAALER_ROSE',
    'PESQUISA_FATOR_RH_INCLUI_D_FRACO',
    'PESQUISA_HEMOGLOBINA_S',
    'PESQUISA_LARVAS_NAS_FEZES',
    'PESQUISA_TRIPANOSSOMA',
    'PESQUISA_TROFOZOITAS_NAS_FEZES',
    'PROVA_RETRACAO_COAGULO',
    'PROVA_DO_LACO',
    'REACAO_HEMAGLUTINACAO_TPHA_DIAGNOSTICO_SIFILIS',
    'REACAO_MONTENEGRO_ID',
    'TESTE_VDRL_DETECCAO_SIFILIS',
    'TESTE_DIRETO_ANTIGLOBULINA_HUMANA_TAD',
    'TESTE_FTA_ABS_IGG_DIAGNOSTICO_SIFILIS',
    'TESTE_FTA_ABS_IGM_DIAGNOSTICO_SIFILIS',
    'TESTE_TUBERCULINICO_PPD',
    'GLICOSE',
    'VDRL_DETECCAO_SIFILIS_EM_GESTANTE'
      ]
    },
    {
      label: 'Raio X',
      color: 'bg-sky-600',
      hover: 'hover:bg-sky-700',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75l3 3m0 0l3-3m-3 3v-7.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />`,
      especialidades: [
        'RAIO_X_TORAX_PA', 'RAIO_X_TORAX_PA_PERFIL', 'RAIO_X_SEIOS_DA_FACE', 
        'RAIO_X_COLUNA_CERVICAL', 'RAIO_X_COLUNA_DORSAL', 'RAIO_X_COLUNA_LOMBO_SACRA', 
        'RAIO_X_ABDOMEN_SIMPLES', 'RAIO_X_ABDOMEN_AGUDO', 'RAIO_X_ARTICULACAO_COXO_FEMURAL_BACIA', 
        'RAIO_X_JOELHO', 'RAIO_X_MAO_OU_QUIRODACTILOS', 'RAIO_X_PE_OU_PODODACTILOS'
      ]
    },
    {
      label: 'Ultrassonografia',
      color: 'bg-indigo-600',
      hover: 'hover:bg-indigo-700',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M9 4.5v15m6-15v15m-10.875 0h15.75c.621 0 1.125-.504 1.125-1.125V5.625c0-.621-.504-1.125-1.125-1.125H4.125C3.504 4.5 3 5.004 3 5.625v12.75c0 .621.504 1.125 1.125 1.125z" />`,
      especialidades: [
        'ULTRASSONOGRAFIA_PARTES_MOLES', 'ULTRASSONOGRAFIA_ABDOMINAL_TOTAL', 
        'ULTRASSONOGRAFIA_ABDOMEN_SUPERIOR', 'ULTRASSONOGRAFIA_PELVICA_VIA_ABDOMINAL', 
        'ULTRASSONOGRAFIA_PELVICA_TRANSVAGINAL', 'ULTRASSONOGRAFIA_OBSTETRICA', 
        'ULTRASSONOGRAFIA_VIAS_URINARIAS', 'ULTRASSONOGRAFIA_PROSTATA_VIA_ABDOMINAL', 
        'ULTRASSONOGRAFIA_TIREOIDE', 'ULTRASSONOGRAFIA_ARTICULAR'
      ]
    },
    {
      label: 'Doppler',
      color: 'bg-teal-600',
      hover: 'hover:bg-teal-700',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />`,
      especialidades: [
        'ULTRASSONOGRAFIA_DOPPLER_ARTERIAL_MEMBRO_INFERIOR_UNILATERAL',
        'ULTRASSONOGRAFIA_DOPPLER_ARTERIAL_MEMBRO_INFERIOR_BILATERAL',
        'ULTRASSONOGRAFIA_DOPPLER_VENOSO_MEMBRO_INFERIOR_UNILATERAL',
        'ULTRASSONOGRAFIA_DOPPLER_VENOSO_MEMBRO_INFERIOR_BILATERAL',
        'ULTRASSONOGRAFIA_DOPPLER_CAROTIDAS_E_VERTEBRAIS',
        'DOPPLER'
      ]
    },
    {
      label: 'Eletrocardiograma',
      color: 'bg-red-500',
      hover: 'hover:bg-red-600',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12Z">`,
      especialidades: [
        'ELETROCARDIOGRAMA_ECG'
      ]
    }, {
  label: 'Ressonância',
  color: 'bg-purple-400',
  hover: 'hover:bg-purple-500',
  icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z"></path>
  <path stroke-linecap="round" stroke-linejoin="round" d="M9 9.563C9 9.252 9.252 9 9.563 9h4.874c.311 0 .563.252.563.563v4.874c0 .311-.252.563-.563.563H9.564A.562.562 0 0 1 9 14.437V9.564Z">`,
  especialidades: [
    'RESSONANCIA_MAGNETICA_ARTICULACAO_TEMPORO_MANDIBULAR_BILATERAL',
    'RESSONANCIA_MAGNETICA_BACIA_PELVE_ABDOMEN_INFERIOR',
    'RESSONANCIA_MAGNETICA_COLUNA_TORACICA',
    'RESSONANCIA_MAGNETICA_CORACAO_AORTA_COM_CINE',
    'RESSONANCIA_MAGNETICA_MEMBRO_INFERIOR_UNILATERAL',
    'RESSONANCIA_MAGNETICA_MEMBRO_SUPERIOR_UNILATERAL',
    'RESSONANCIA_MAGNETICA_SELA_TURCICA',
    'RESSONANCIA_MAGNETICA_TORAX',
    'RESSONANCIA_MAGNETICA_VIAS_BILIARES_COLANGIORESSONANCIA'
  ]
  },
  {
  label: 'Tomografia',
  color: 'bg-green-400',
  hover: 'hover:bg-green-500',
  icon: `  <path stroke-linecap="round" stroke-linejoin="round" d="M9 8.25H7.5a2.25 2.25 0 0 0-2.25 2.25v9a2.25 2.25 0 0 0 2.25 2.25h9a2.25 2.25 0 0 0 2.25-2.25v-9a2.25 2.25 0 0 0-2.25-2.25H15M9 12l3 3m0 0 3-3m-3 3V2.25">
`,
  especialidades: [
    'TOMOGRAFIA',
    'TOMOGRAFIA_TORAX',
    'TOMOGRAFIA_COMPUTADORIZADA_CRANIO',
    'TOMOGRAFIA_COMPUTADORIZADA_DO_CRANIO',
    'TOMOGRAFIA_COMPUTADORIZADA_TORAX',
    'TOMOGRAFIA_COMPUTADORIZADA_ABDOME_SUPERIOR',
    'TOMOGRAFIA_COMPUTADORIZADA_ABDOMEN_TOTAL',
    'TOMOGRAFIA_COMPUTADORIZADA_ARTICULACOES_MEMBRO_INFERIOR',
    'TOMOGRAFIA_COMPUTADORIZADA_ARTICULACOES_MEMBRO_SUPERIOR',
    'TOMOGRAFIA_COLUNA_CERVICAL',
    'TOMOGRAFIA_COMPUTADORIZADA_COLUNA_CERVICAL_COM_OU_SEM_CONTRASTE',
    'TOMOGRAFIA_COMPUTADORIZADA_COLUNA_LOMBOSACRA_COM_OU_SEM_CONTRASTE',
    'TOMOGRAFIA_COMPUTADORIZADA_COLUNA_TORACICA_COM_OU_SEM_CONTRASTE',
    'TOMOGRAFIA_COMPUTADORIZADA_FACE_SEIOS_DA_FACE_ARTICULACOES_TEMPO_MANDIBULARES',
    'TOMOGRAFIA_COMPUTADORIZADA_PELVE_BACIA_ABDOMEN_INFERIOR',
    'TOMOGRAFIA_COMPUTADORIZADA_SEGMENTOS_APENDICULARES',
    'TOMOGRAFIA_COMPUTADORIZADA_SELA_TURCICA',
    'TOMOGRAFIA_COMPUTADORIZADA_PESCOCO',
    'TOMOGRAFIA_POR_EMISSAO_DE_POSITRONS_PET_CT',
    'ANGIOTOMOGRAFIA',
    'TOMOMIELOGRAFIA_COMPUTADORIZADA'
  ]
  },
    {
  label: 'Ecocardiograma',
  color: 'bg-red-700',
  hover: 'hover:bg-red-800',
  icon: `  <path stroke-linecap="round" stroke-linejoin="round" d="M9 8.25H7.5a2.25 2.25 0 0 0-2.25 2.25v9a2.25 2.25 0 0 0 2.25 2.25h9a2.25 2.25 0 0 0 2.25-2.25v-9a2.25 2.25 0 0 0-2.25-2.25H15M9 12l3 3m0 0 3-3m-3 3V2.25">
`,
  especialidades: [
    'ECOCARDIOGRAMA_TRANSTORACICO_MODO_M_BIDIMENSIONAL_DOPPLER'
  ]
  }
  ];

  const tiposDeConsulta = [
    {
      label: 'Cardiologista',
      color: 'bg-rose-600',
      hover: 'hover:bg-rose-700',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" />`,
      especialidades: ['CARDIOLOGISTA']
    },
    {
      label: 'Pediatra',
      color: 'bg-amber-500',
      hover: 'hover:bg-amber-600',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M15.182 15.182a4.5 4.5 0 01-6.364 0M21 12a9 9 0 11-18 0 9 9 0 0118 0zM9 9.75h.008v.008H9v-.008zm6 0h.008v.008H15v-.008z" />`,
      especialidades: ['PEDIATRIA']
    },
    {
      label: 'Ortopedista',
      color: 'bg-cyan-600',
      hover: 'hover:bg-cyan-700',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M19.5 12c0-1.232-.046-2.453-.138-3.662a4.006 4.006 0 00-3.7-3.7 48.678 48.678 0 00-7.324 0 4.006 4.006 0 00-3.7 3.7c-.092 1.21-.138 2.43-.138 3.662a4.006 4.006 0 003.7 3.7 48.656 48.656 0 007.324 0 4.006 4.006 0 003.7-3.7zM12 12h.008v.008H12V12z" />`,
      especialidades: ['ORTOPEDISTA']
    },
    {
      label: 'Alergologista',
      color: 'bg-lime-500',
      hover: 'hover:bg-lime-600',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M12 3v.01M12 6c-2.485 0-4.5 2.015-4.5 4.5S9.515 18 12 18s4.5-2.015 4.5-4.5S14.485 6 12 6zm0 15a9 9 0 110-18 9 9 0 010 18z" />`,
      especialidades: ['ALERGOLOGISTA']
    },
    {
      label: 'Angiologista',
      color: 'bg-fuchsia-600',
      hover: 'hover:bg-fuchsia-700',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M4.5 12h15m-15 0a7.5 7.5 0 1115 0 7.5 7.5 0 01-15 0z" />`,
      especialidades: ['ANGIOLOGISTA']
    },
    {
      label: 'Cirurgião Geral',
      color: 'bg-orange-500',
      hover: 'hover:bg-orange-600',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />`,
      especialidades: ['CIRURGIAO_GERAL']
    },
    {
      label: 'Endocrinologista',
      color: 'bg-pink-500',
      hover: 'hover:bg-pink-600',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />`,
      especialidades: ['ENDOCRINOLOGISTA']
    },
    {
      label: 'Gastroenterologista',
      color: 'bg-yellow-500',
      hover: 'hover:bg-yellow-600',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M12 6v12m-6-6h12" />`,
      especialidades: ['GASTROENTEROLOGISTA']
    },
    {
      label: 'Mastologista',
      color: 'bg-red-400',
      hover: 'hover:bg-red-500',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M12 18h.01M8.001 21.999a8.966 8.966 0 0011.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632zM12 3a9 9 0 100 18 9 9 0 000-18z" />`,
      especialidades: ['MASTOLOGISTA']
    },
    {
      label: 'Dermatologista',
      color: 'bg-stone-500',
      hover: 'hover:bg-stone-600',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M12 12.75c-1.657 0-3-1.343-3-3s1.343-3 3-3 3 1.343 3 3-1.343 3-3 3z" />`,
      especialidades: ['DERMATOLOGISTA']
    },
    {
      label: 'Nefrologista',
      color: 'bg-cyan-500',
      hover: 'hover:bg-cyan-600',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M12 6.75a5.25 5.25 0 010 10.5 5.25 5.25 0 010-10.5z" />`,
      especialidades: ['NEFROLOGISTA']
    },
    {
      label: 'Oftalmologista',
      color: 'bg-violet-500',
      hover: 'hover:bg-violet-600',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639l11.568-6.678A1.012 1.012 0 0115 6.002v11.996a1.012 1.012 0 01-1.401.841L2.036 12.322z" />`,
      especialidades: ['OFTALMOLOGISTA']
    },
    {
      label: 'Neurologista',
      color: 'bg-green-500',
      hover: 'hover:bg-green-600',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M12 18h.01M8.001 21.999a8.966 8.966 0 0011.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632zM12 3a9 9 0 100 18 9 9 0 000-18z" />`,
      especialidades: ['NEUROLOGISTA']
    },
    {
      label: 'Neuropediatria',
      color: 'bg-blue-400',
      hover: 'hover:bg-blue-500',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M10.5 6h9m-9 6h9m-9 6h9M4.5 6.75h.008v.008H4.5v-.008zm0 5.5h.008v.008H4.5v-.008zm0 5.5h.008v.008H4.5v-.008z" />`,
      especialidades: ['NEUROPEDIATRIA']
    },
    {
      label: 'Otorrino',
      color: 'bg-indigo-400',
      hover: 'hover:bg-indigo-500',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />`,
      especialidades: ['OTORRINOLARINGOLOGISTA']
    },
    {
      label: 'Urologista',
      color: 'bg-purple-500',
      hover: 'hover:bg-purple-600',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 3.75v4.5m0-4.5h4.5m-4.5 0L9 9m12-3.75v4.5m0-4.5h-4.5m4.5 0L15 9" />`,
      especialidades: ['UROLOGISTA']
    },
    {
      label: 'Reumatologista',
      color: 'bg-pink-400',
      hover: 'hover:bg-pink-500',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M6.75 3v18M17.25 3v18" />`,
      especialidades: ['REUMATOLOGISTA']
    },
    {
      label: 'Pneumologista',
      color: 'bg-rose-400',
      hover: 'hover:bg-rose-500',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6A2.25 2.25 0 016 3.75h12A2.25 2.25 0 0120.25 6v12A2.25 2.25 0 0118 20.25H6A2.25 2.25 0 013.75 18V6z" />`,
      especialidades: ['PNEUMOLOGISTA']
    },
    {
      label: 'Hematologista',
      color: 'bg-red-600',
      hover: 'hover:bg-red-700',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />`,
      especialidades: ['HEMATOLOGISTA']
    },
    {
      label: 'Oncologista',
      color: 'bg-red-600',
      hover: 'hover:bg-red-700',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />`,
      especialidades: ['ONCOLOGISTA']
    },
    {
      label: 'Procedimento Dermatológico',
      color: 'bg-red-600',
      hover: 'hover:bg-red-700',
      icon: `<path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />`,
      especialidades: ['PROCEDIMENTO_DERMATOLOGISTA']
    }
  ];

  onMount(async () => {
    try {
      const res = await getApi('solicitacoes');
      if (!res.ok) throw new Error('Falha ao carregar dados do servidor.');
      allSolicitacoes = await res.json();
    } catch (e) {
      alert('Não foi possível carregar os dados das solicitações.');
      console.error(e);
    } finally {
      isLoadingData = false;
    }
  });

  async function gerarPlanilha(especialidades: string[], label: string) {
    if (!dataSelecionada) {
      alert('Por favor, selecione uma data.');
      return;
    }
    loadingType = label;

    try {
      const tiposQueryParam = especialidades.join(',');
      
      const checkUrl = `exportar/verificar-dados?tipos=${encodeURIComponent(tiposQueryParam)}&data=${dataSelecionada}&label=${encodeURIComponent(label)}`;
      const response = await getApi(checkUrl);
      
      if (!response.ok) throw new Error('Falha ao verificar dados com o servidor.');
      
      const result = await response.json();
      if (result.dadosDisponiveis) {
        const downloadUrl = `exportar/planilha?tipos=${encodeURIComponent(tiposQueryParam)}&data=${dataSelecionada}&label=${encodeURIComponent(label)}`;
        const fileResponse = await getApi(downloadUrl);
        const blob = await fileResponse.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `relatorio_${label.replace(/\s+/g, '_').toLowerCase()}_${dataSelecionada}.xlsx`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
      } else {
        alert(`Nenhum dado encontrado para o relatório de ${label} na data ${new Date(dataSelecionada + 'T12:00:00').toLocaleDateString('pt-BR')}.`);
      }
    } catch (error) {
      console.error(`Erro ao gerar planilha de ${label}:`, error);
      alert("Ocorreu um erro de comunicação com o servidor. Tente novamente.");
    } finally {
      setTimeout(() => { loadingType = null; }, 1000);
    }
  }

  async function gerarPlanilhaAgendamentosDoDia() {
    try {
      const res = await getApi("relatorio/producao/excel")

      const blob = await res.blob();
      const url = URL.createObjectURL(blob);

      const a = document.createElement("a");
      a.href = url;
      a.download = "relatorio_producao.xlsx"
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);

    } catch (error) {
     console.error(error) 
    }
  }

  async function gerarPlanilhaAguardando(especialidades: string[], label: string) {
    loadingType = `AGUARDANDO_${label}`;

    try {
      const tiposQueryParam = especialidades.join(',');
      const checkUrl = `exportar/verificar-dados-aguardando?tipos=${encodeURIComponent(tiposQueryParam)}&label=${encodeURIComponent(label)}`;
      const response = await getApi(checkUrl);

      if (!response.ok) throw new Error('Falha ao verificar dados de pendências com o servidor.');

      const result = await response.json();
      if (result.dadosDisponiveis) {
        const downloadUrl = `exportar/planilha-aguardando?tipos=${encodeURIComponent(tiposQueryParam)}&label=${encodeURIComponent(label)}`;
        const fileResponse = await getApi(downloadUrl);
        const blob = await fileResponse.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `relatorio_pendentes_${label.replace(/\s+/g, '_').toLowerCase()}.xlsx`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
      } else {
        alert(`Nenhum dado pendente encontrado para o relatório de ${label}.`);
      }
    } catch (error) {
      console.error(`Erro ao gerar planilha de pendentes para ${label}:`, error);
      alert("Ocorreu um erro de comunicação com o servidor. Tente novamente.");
    } finally {
      setTimeout(() => { loadingType = null; }, 1000);
    }
  }

  function gerarPDFGeral() {
      // ... (nenhuma alteração nesta função)
      if (isLoadingData || allSolicitacoes.length === 0) {
          alert('Não há dados carregados para gerar o PDF.');
          return;
      }
      loadingType = 'PDF_GERAL';

      const doc = new jsPDF();
      const tableColumn = ["ID", "Paciente", "CPF", "USF", "Data Malote", "Especialidades Pendentes"];
      const tableRows: any[][] = [];

      allSolicitacoes.forEach(sol => {
          const pendentes = sol.especialidades
              .filter((e: any) => e.status === 'AGUARDANDO')
              .map((e: any) => e.especialidadeSolicitada)
              .join(', ');

          if (pendentes) {
              const solicitacaoData = [
                  sol.id,
                  sol.nomePaciente,
                  sol.cpfPaciente,
                  sol.usfOrigem,
                  new Date(sol.dataMalote + 'T12:00:00').toLocaleDateString('pt-BR'),
                  pendentes
              ];
              tableRows.push(solicitacaoData);
          }
      });
      
      if(tableRows.length === 0){
          alert("Nenhuma solicitação com pendências encontrada para gerar o relatório.");
          loadingType = null;
          return;
      }

      autoTable(doc, {
          head: [tableColumn],
          body: tableRows,
          startY: 20,
          theme: 'striped',
          headStyles: { fillColor: [22, 160, 133] }
      });
      
      doc.text("Relatório Geral de Solicitações Pendentes", 14, 15);
      doc.save(`relatorio_geral_pendentes_${new Date().toISOString().split('T')[0]}.pdf`);

      setTimeout(() => { loadingType = null; }, 1000);
  }

</script>
<svelte:head>
    <title>Relatórios</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
    <Menu activePage="/exportar" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Gerar Relatórios</h1>
          <UserMenu/>
    </header>

    <main class="flex-1 p-6 overflow-auto">
      <div class="max-w-5xl mx-auto space-y-8">
        
        <section>
          <h2 class="text-xl font-bold text-gray-700 mb-3">Relatórios de Agendados</h2>
          <div class="bg-white p-4 rounded-lg shadow-md mb-6">
            <label for="data" class="block text-base font-semibold text-gray-800 mb-2">
              🗓️ Selecione a data para os relatórios de agendados:
            </label>
            <input
              type="date"
              id="data"
              bind:value={dataSelecionada}
              class="w-full md:w-auto border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500"
            />
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-5 gap-5">
            {#each [...tiposDeConsulta, ...tiposDeProcedimento] as relatorio}
              <button 
                onclick={() => gerarPlanilha(relatorio.especialidades, relatorio.label)} 
                disabled={loadingType === relatorio.label} 
                class="p-4 {relatorio.color} text-white rounded-lg shadow-lg {relatorio.hover} hover:scale-105 transform transition-all duration-200 flex flex-col items-center justify-center h-32 disabled:bg-gray-400 disabled:cursor-wait disabled:scale-100"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10 mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  {@html relatorio.icon}
                </svg>
                <span class="text-lg font-bold text-center">{relatorio.label}</span>
                {#if loadingType === relatorio.label}
                  <span class="text-xs mt-1 animate-pulse">Gerando...</span>
                {/if}
              </button>
            {/each}
          </div>
        </section>

        <section>
          <h2 class="text-xl font-bold text-gray-700 mb-3">Relatórios de Pendentes</h2>
          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-5 gap-5">
            {#each [...tiposDeConsulta, ...tiposDeProcedimento] as relatorio}
              <button 
                onclick={() => gerarPlanilhaAguardando(relatorio.especialidades, relatorio.label)} 
                disabled={loadingType === `AGUARDANDO_${relatorio.label}`} 
                class="p-4 bg-gray-500 text-white rounded-lg shadow-lg hover:bg-gray-600 hover:scale-105 transform transition-all duration-200 flex flex-col items-center justify-center h-32 disabled:bg-gray-400 disabled:cursor-wait disabled:scale-100"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10 mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  {@html relatorio.icon}
                </svg>
                <span class="text-lg font-bold text-center">{relatorio.label}</span>
                {#if loadingType === `AGUARDANDO_${relatorio.label}`}
                  <span class="text-xs mt-1 animate-pulse">Gerando...</span>
                {/if}
              </button>
            {/each}
          </div>
        </section>
        
        <section>
          <h2 class="text-xl font-bold text-gray-700 mb-3">Relatórios Gerais</h2>
          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-5">
            <button onclick={gerarPDFGeral} disabled={isLoadingData || loadingType === 'PDF_GERAL'} class="p-4 bg-blue-600 text-white rounded-lg shadow-lg hover:bg-blue-700 hover:scale-105 transform transition-all duration-200 flex flex-col items-center justify-center h-32 disabled:bg-gray-400 disabled:cursor-wait disabled:scale-100">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10 mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <span class="text-lg font-bold text-center">PDF Geral de Pendentes</span>
              {#if loadingType === 'PDF_GERAL'}<span class="text-xs mt-1 animate-pulse">Gerando...</span>{/if}
            </button>

            <button class="flex flex-col justify-center items-center bg-red-600 rounded-lg py-3 text-white" onclick={gerarPlanilhaAgendamentosDoDia}>
              <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10 mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <span class="text-[1.1rem] font-bold text-center"> Excel Agendamentos Do Dia </span>
              {#if loadingType === 'PDF_GERAL'}<span class="text-xs mt-1 animate-pulse">Gerando...</span>{/if}
            </button>
          </div>
        </section>
      </div>
    </main>
    </div>
</div>
