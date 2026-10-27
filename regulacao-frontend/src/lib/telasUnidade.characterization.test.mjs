// Testes de caracterização das telas de Unidade (/admin/unidades, nova, editar e
// executante) — escritos ANTES do redesenho sirg-design + tag de papel (ADR 0012).
// A lógica dessas telas vive dentro dos .svelte, que `node --test` não renderiza;
// por isso o teste lê o arquivo como texto e exige que as regras de hoje continuem
// lá, literalmente: confirmações (texto e condição), regra do botão Parametrizar,
// opções do Tipo, validação do nome, chamadas de API e os binds dos campos.
//
// Espaços e quebras de linha são normalizados: reformatar o markup não quebra o
// teste; apagar ou mudar uma regra quebra.
//
// Roda com `node --test src/lib/telasUnidade.characterization.test.mjs`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const ROTAS = fileURLToPath(new URL("../routes/admin/unidades/", import.meta.url));

function tela(caminho) {
  return readFileSync(ROTAS + caminho, "utf8").replace(/\s+/g, " ");
}

function exigir(texto, trechos, nome) {
  for (const trecho of trechos) {
    assert.ok(
      texto.includes(trecho.replace(/\s+/g, " ")),
      `${nome}: trecho esperado ausente -> ${trecho}`,
    );
  }
}

const AVISO_PERDA_DA_EXECUTANTE =
  "os operadores vinculados a ela (pela lista ou pela lotação) deixam de ver os atendimentos desta executante.";

const CAMPOS_DO_FORMULARIO = [
  "nome",
  "codigo",
  "cnes",
  "telefone",
  "endereco",
  "grupoRelatorioId",
  "tipo",
  "razaoSocial",
  "nomeFantasia",
  "cnpj",
  "numero",
  "bairro",
  "cep",
  "email",
];

const OPCOES_DO_TIPO = [
  "{ valor: 'AMBOS', rotulo: 'Solicitante e executante' }",
  "{ valor: 'SOLICITANTE', rotulo: 'Só solicitante' }",
  "{ valor: 'EXECUTANTE', rotulo: 'Só executante' }",
];

test("lista: desativar executante configurada com operadores pede confirmação, com o mesmo texto", () => {
  exigir(
    tela("+page.svelte"),
    [
      "if (u.ativo && ex?.configurada && ex.operadores > 0",
      "`${u.nome} é uma unidade executante configurada. Ao desativá-la, " + AVISO_PERDA_DA_EXECUTANTE + " Desativar mesmo assim?`",
      "patchApi(`unidades/${u.id}/status`)",
      // Listagem paginada no servidor (substitui getApi('unidades'), que trazia tudo).
      "caminho: 'unidades/pagina'",
      "listarUnidadesExecutantes()",
    ],
    "lista",
  );
});

test("lista: botão Parametrizar executante segue a regra tipo != SOLICITANTE e leva à tela da executante", () => {
  exigir(
    tela("+page.svelte"),
    ["u.tipo !== 'SOLICITANTE'", "`/admin/unidades/${u.id}/executante`", "`/admin/unidades/${u.id}/editar`", "'/admin/unidades/nova'"],
    "lista",
  );
});

test("nova: opções do Tipo (AMBOS padrão), validação do nome, POST e busca CNES", () => {
  const nova = tela("nova/+page.svelte");
  exigir(
    nova,
    [
      ...OPCOES_DO_TIPO,
      "tipo: 'AMBOS'",
      "if (!form.nome.trim())",
      "toast.error('O nome é obrigatório.')",
      "postApi('unidades', form)",
      "async function buscarPorCnes()",
      "!confirm('A busca trouxe dados diferentes dos já preenchidos. Sobrescrever os campos preenchidos?')",
      ...CAMPOS_DO_FORMULARIO.filter((c) => c !== "cnes").map((c) => `bind:value={form.${c}}`),
    ],
    "nova",
  );
});

test("editar: mudar para Só solicitante pede confirmação na mesma condição e com o mesmo texto", () => {
  exigir(
    tela("[id]/editar/+page.svelte"),
    [
      ...OPCOES_DO_TIPO,
      "tipoOriginal = u.tipo ?? 'AMBOS'",
      "if (!ex.configurada || ex.operadores.length === 0) return true;",
      'confirm(`Esta unidade é uma executante configurada. Como "Só solicitante", ' + AVISO_PERDA_DA_EXECUTANTE + " Salvar mesmo assim?`)",
      "if (form.tipo === 'SOLICITANTE' && tipoOriginal !== 'SOLICITANTE' && !(await confirmarPerdaDeExecutante()))",
      "if (!form.nome.trim())",
      "putApi(`unidades/${unidadeId}`, form)",
      "async function buscarPorCnes()",
      ...CAMPOS_DO_FORMULARIO.map((c) => `bind:value={form.${c}}`),
    ],
    "editar",
  );
});

test("executante: carga, salvar e regras de configurada e avulsa coberta continuam", () => {
  exigir(
    tela("[id]/executante/+page.svelte"),
    [
      "carregarUnidadeExecutante(unidadeId)",
      "listarOperadoresDisponiveis()",
      "salvarUnidadeExecutante(unidadeId, {",
      "const ficaraConfigurada = $derived(localIds.length > 0 && (grupoIds.length > 0 || especialidadeIds.length > 0));",
      "const cobertaPorGrupo = (e: Especialidade) => !!e.grupoRelatorio && grupoIds.includes(e.grupoRelatorio.id);",
      "return !e || !cobertaPorGrupo(e);",
      ".filter((o: { podeOperar: boolean }) => !o.podeOperar)",
    ],
    "executante",
  );
});
