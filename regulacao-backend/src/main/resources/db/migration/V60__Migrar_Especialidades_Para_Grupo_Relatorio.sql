-- Migracao dos grupos de relatorio a partir do enum de especialidades
-- Preenche grupo_relatorio e associa especialidades existentes

-- Grupo: Laboratorio
INSERT INTO grupo_relatorio (codigo, nome, ativo)
SELECT 'laboratorio', 'Laboratorio', TRUE
WHERE NOT EXISTS (SELECT 1 FROM grupo_relatorio WHERE codigo = 'laboratorio');

UPDATE especialidade e
SET grupo_relatorio_id = gr.id
FROM grupo_relatorio gr
WHERE gr.codigo = 'laboratorio'
  AND e.codigo IN (
      'ASLO',
      'ACIDO_URICO',
      'ANALISE_CARACTERES_FISICOS_ELEMENTOS_SEDIMENTO_URINA',
      'ANTI_HBS',
      'ANTI_HCV',
      'ANTIBIOGRAMA',
      'BACILOSCOPIA_DE_ESCARRO_BAAR',
      'BACILOSCOPIA_DIRETA_BAAR_TUBERCULOS_CONTROLE',
      'BACILOSCOPIA_DIRETA_BAAR_TUBERCULOSE_DIAGNOSTICA',
      'BACTEROSCOPIA_GRAM',
      'BILIRRUBINA_TOTAL_FRACOES',
      'CALCIO',
      'CLEARANCE_CREATININA',
      'COAGULOGRAMA',
      'COLESTEROL_TOTAL',
      'CONTAGEM_PLAQUETAS',
      'CONTAGEM_RETICULOCITOS',
      'CREATININA',
      'CULTURA_BACTERIAS_IDENTIFICACAO_UROCULTURA',
      'CULTURA_DE_ESCARRO',
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
      'FATOR_REUMATOIDE',
      'FOSFATASE_ALCALINA',
      'GAMA_GT',
      'GLICEMIA_JEJUM',
      'GLICOSE',
      'HBSAG',
      'HDL_COLESTEROL',
      'HEMATOCRITO',
      'HEMOCULTURA',
      'HEMOGLOBINA_GLICADA_HBA1C',
      'HEMOGRAMA_COMPLETO',
      'IMUNOELETROFORESE_DE_PROTEINAS',
      'LDL_COLESTEROL',
      'LEUCOGRAMA',
      'PSA_LIVRE',
      'PSA_TOTAL',
      'PARASITOLOGICO_DE_FEZES',
      'PESQUISA_ANTICORPO_IGG_ANTICARDIOLIPINA',
      'PESQUISA_ANTICORPO_IGM_DE_ANTICARDIOLIPINA',
      'PESQUISA_ANTICORPOS_ANTI_HELICOBACTER_PYLORI',
      'PESQUISA_ANTICORPOS_ANTI_HIV_1_HIV_2_ELISA',
      'PESQUISA_ANTICORPOS_ANTI_HTLV_1_HTLV_2',
      'PESQUISA_ANTICORPOS_ANTI_SM',
      'PESQUISA_ANTICORPOS_ANTI_SS_A_RO',
      'PESQUISA_ANTICORPOS_ANTI_SS_B_LA',
      'PESQUISA_ANTICORPOS_ANTINUCLEO',
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
      'PESQUISA_FATOR_RH_INCLUI_D_FRACO',
      'PESQUISA_FATOR_REUMATOIDE_WAALER_ROSE',
      'PESQUISA_HEMOGLOBINA_S',
      'PESQUISA_LARVAS_NAS_FEZES',
      'PESQUISA_OVOS_CISTOS_PARASITAS',
      'PESQUISA_SANGUE_OCULTO_FEZES',
      'PESQUISA_TRIPANOSSOMA',
      'PESQUISA_TROFOZOITAS_NAS_FEZES',
      'POTASSIO',
      'PROVA_RETRACAO_COAGULO',
      'PROVA_DO_LACO',
      'REACAO_HEMAGLUTINACAO_TPHA_DIAGNOSTICO_SIFILIS',
      'REACAO_MONTENEGRO_ID',
      'SODIO',
      'SOROLOGIA_HIV',
      'SUMARIO_DE_URINA_EAS',
      'T4_LIVRE',
      'TGO',
      'TGP',
      'TP',
      'TSH_HORMONIO_TIREOESTIMULANTE',
      'TTPA',
      'TESTE_VDRL_DETECCAO_SIFILIS',
      'TESTE_DIRETO_ANTIGLOBULINA_HUMANA_TAD',
      'TESTE_FTA_ABS_IGG_DIAGNOSTICO_SIFILIS',
      'TESTE_FTA_ABS_IGM_DIAGNOSTICO_SIFILIS',
      'TESTE_RAPIDO_GRAVIDEZ_TIG',
      'TESTE_RAPIDO_HEPATITE_B',
      'TESTE_RAPIDO_HEPATITE_C',
      'TESTE_RAPIDO_HIV',
      'TESTE_RAPIDO_SIFILIS',
      'TESTE_TUBERCULINICO_PPD',
      'TIPO_SANGUINEO',
      'TRIGLICERIDEOS',
      'UREIA',
      'UROCULTURA_COM_ANTIBIOGRAMA',
      'VDRL',
      'VDRL_DETECCAO_SIFILIS_EM_GESTANTE',
      'VLDL_COLESTEROL'
  )
  AND e.grupo_relatorio_id IS NULL;

-- Grupo: Raio X
INSERT INTO grupo_relatorio (codigo, nome, ativo)
SELECT 'raio-x', 'Raio X', TRUE
WHERE NOT EXISTS (SELECT 1 FROM grupo_relatorio WHERE codigo = 'raio-x');

UPDATE especialidade e
SET grupo_relatorio_id = gr.id
FROM grupo_relatorio gr
WHERE gr.codigo = 'raio-x'
  AND e.codigo IN (
      'RAIO_X_ABDOMEN_AGUDO',
      'RAIO_X_ABDOMEN_SIMPLES',
      'RAIO_X_ARTICULACAO_COXO_FEMURAL_BACIA',
      'RAIO_X_CALCANEO',
      'RAIO_X_CAVUM',
      'RAIO_X_COLUNA_CERVICAL',
      'RAIO_X_COLUNA_DORSAL',
      'RAIO_X_COLUNA_LOMBO_SACRA',
      'RAIO_X_COLUNA_TORACICA',
      'RAIO_X_ESCAPULA',
      'RAIO_X_JOELHO',
      'RAIO_X_OMBRO',
      'RAIO_X_MAO_OU_QUIRODACTILOS',
      'RAIO_X_PE_OU_PODODACTILOS',
      'RAIO_X_SEIOS_DA_FACE',
      'RAIO_X_REGIAO_CERVICAL',
      'RAIO_X_TORAX_PA',
      'RAIO_X_TORAX_PA_PERFIL'
  )
  AND e.grupo_relatorio_id IS NULL;

-- Grupo: Ultrassonografia
INSERT INTO grupo_relatorio (codigo, nome, ativo)
SELECT 'ultrasom', 'Ultrassonografia', TRUE
WHERE NOT EXISTS (SELECT 1 FROM grupo_relatorio WHERE codigo = 'ultrasom');

UPDATE especialidade e
SET grupo_relatorio_id = gr.id
FROM grupo_relatorio gr
WHERE gr.codigo = 'ultrasom'
  AND e.codigo IN (
      'ULTRASSONOGRAFIA_ABDOMEN_SUPERIOR',
      'ULTRASSONOGRAFIA_ABDOMINAL_TOTAL',
      'ULTRASSONOGRAFIA_ARTICULAR',
      'ULTRASSONOGRAFIA_OBSTETRICA',
      'ULTRASSONOGRAFIA_PARTES_MOLES',
      'ULTRASSONOGRAFIA_PELVICA_VIA_ABDOMINAL',
      'ULTRASSONOGRAFIA_PELVICA_TRANSVAGINAL',
      'ULTRASSONOGRAFIA_PROSTATA_VIA_ABDOMINAL',
      'ULTRASSONOGRAFIA_TIREOIDE',
      'ULTRASSONOGRAFIA_VIAS_URINARIAS'
  )
  AND e.grupo_relatorio_id IS NULL;

-- Grupo: Doppler
INSERT INTO grupo_relatorio (codigo, nome, ativo)
SELECT 'doppler', 'Doppler', TRUE
WHERE NOT EXISTS (SELECT 1 FROM grupo_relatorio WHERE codigo = 'doppler');

UPDATE especialidade e
SET grupo_relatorio_id = gr.id
FROM grupo_relatorio gr
WHERE gr.codigo = 'doppler'
  AND e.codigo IN (
      'DOPPLER',
      'ULTRASSONOGRAFIA_DOPPLER_ARTERIAL_MEMBRO_INFERIOR_BILATERAL',
      'ULTRASSONOGRAFIA_DOPPLER_ARTERIAL_MEMBRO_INFERIOR_UNILATERAL',
      'ULTRASSONOGRAFIA_DOPPLER_CAROTIDAS_E_VERTEBRAIS',
      'ULTRASSONOGRAFIA_DOPPLER_VENOSO_MEMBRO_INFERIOR_BILATERAL',
      'ULTRASSONOGRAFIA_DOPPLER_VENOSO_MEMBRO_INFERIOR_UNILATERAL'
  )
  AND e.grupo_relatorio_id IS NULL;

-- Grupo: Eletrocardiograma
INSERT INTO grupo_relatorio (codigo, nome, ativo)
SELECT 'eletrocardiograma', 'Eletrocardiograma', TRUE
WHERE NOT EXISTS (SELECT 1 FROM grupo_relatorio WHERE codigo = 'eletrocardiograma');

UPDATE especialidade e
SET grupo_relatorio_id = gr.id
FROM grupo_relatorio gr
WHERE gr.codigo = 'eletrocardiograma'
  AND e.codigo IN (
      'ELETROCARDIOGRAMA_ECG'
  )
  AND e.grupo_relatorio_id IS NULL;

-- Grupo: Pediatra
INSERT INTO grupo_relatorio (codigo, nome, ativo)
SELECT 'pediatra', 'Pediatra', TRUE
WHERE NOT EXISTS (SELECT 1 FROM grupo_relatorio WHERE codigo = 'pediatra');

UPDATE especialidade e
SET grupo_relatorio_id = gr.id
FROM grupo_relatorio gr
WHERE gr.codigo = 'pediatra'
  AND e.codigo IN (
      'PEDIATRIA'
  )
  AND e.grupo_relatorio_id IS NULL;

-- Grupo: Ortopedista
INSERT INTO grupo_relatorio (codigo, nome, ativo)
SELECT 'ortopedista', 'Ortopedista', TRUE
WHERE NOT EXISTS (SELECT 1 FROM grupo_relatorio WHERE codigo = 'ortopedista');

UPDATE especialidade e
SET grupo_relatorio_id = gr.id
FROM grupo_relatorio gr
WHERE gr.codigo = 'ortopedista'
  AND e.codigo IN (
      'ORTOPEDISTA'
  )
  AND e.grupo_relatorio_id IS NULL;

-- Grupo: Cardiologista
INSERT INTO grupo_relatorio (codigo, nome, ativo)
SELECT 'cardiologista', 'Cardiologista', TRUE
WHERE NOT EXISTS (SELECT 1 FROM grupo_relatorio WHERE codigo = 'cardiologista');

UPDATE especialidade e
SET grupo_relatorio_id = gr.id
FROM grupo_relatorio gr
WHERE gr.codigo = 'cardiologista'
  AND e.codigo IN (
      'CARDIOLOGISTA'
  )
  AND e.grupo_relatorio_id IS NULL;

-- Grupo: Cirurgiao Geral
INSERT INTO grupo_relatorio (codigo, nome, ativo)
SELECT 'cirurgiao-geral', 'Cirurgiao Geral', TRUE
WHERE NOT EXISTS (SELECT 1 FROM grupo_relatorio WHERE codigo = 'cirurgiao-geral');

UPDATE especialidade e
SET grupo_relatorio_id = gr.id
FROM grupo_relatorio gr
WHERE gr.codigo = 'cirurgiao-geral'
  AND e.codigo IN (
      'CIRURGIAO_GERAL'
  )
  AND e.grupo_relatorio_id IS NULL;

-- Grupo: Dermatologista
INSERT INTO grupo_relatorio (codigo, nome, ativo)
SELECT 'dermatologista', 'Dermatologista', TRUE
WHERE NOT EXISTS (SELECT 1 FROM grupo_relatorio WHERE codigo = 'dermatologista');

UPDATE especialidade e
SET grupo_relatorio_id = gr.id
FROM grupo_relatorio gr
WHERE gr.codigo = 'dermatologista'
  AND e.codigo IN (
      'DERMATOLOGISTA'
  )
  AND e.grupo_relatorio_id IS NULL;

-- Grupo: Ecocardiograma
INSERT INTO grupo_relatorio (codigo, nome, ativo)
SELECT 'ecocardiograma', 'Ecocardiograma', TRUE
WHERE NOT EXISTS (SELECT 1 FROM grupo_relatorio WHERE codigo = 'ecocardiograma');

UPDATE especialidade e
SET grupo_relatorio_id = gr.id
FROM grupo_relatorio gr
WHERE gr.codigo = 'ecocardiograma'
  AND e.codigo IN (
      'ECOCARDIOGRAMA_TRANSTORACICO_MODO_M_BIDIMENSIONAL_DOPPLER'
  )
  AND e.grupo_relatorio_id IS NULL;

-- Grupo: Neuropediatria
INSERT INTO grupo_relatorio (codigo, nome, ativo)
SELECT 'neuropediatria', 'Neuropediatria', TRUE
WHERE NOT EXISTS (SELECT 1 FROM grupo_relatorio WHERE codigo = 'neuropediatria');

UPDATE especialidade e
SET grupo_relatorio_id = gr.id
FROM grupo_relatorio gr
WHERE gr.codigo = 'neuropediatria'
  AND e.codigo IN (
      'NEUROPEDIATRIA'
  )
  AND e.grupo_relatorio_id IS NULL;

-- Grupo: Procedimento Dermatologista
INSERT INTO grupo_relatorio (codigo, nome, ativo)
SELECT 'procedimento_dermatologista', 'Procedimento Dermatologista', TRUE
WHERE NOT EXISTS (SELECT 1 FROM grupo_relatorio WHERE codigo = 'procedimento_dermatologista');

UPDATE especialidade e
SET grupo_relatorio_id = gr.id
FROM grupo_relatorio gr
WHERE gr.codigo = 'procedimento_dermatologista'
  AND e.codigo IN (
      'PROCEDIMENTO_DERMATOLOGISTA'
  )
  AND e.grupo_relatorio_id IS NULL;
