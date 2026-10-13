// Testes de src/lib/buscaPaciente.js — a separação "nome + documento" e o filtro
// da busca de paciente de agendar/+page.svelte.
//
// Roda com `node --test src/lib/buscaPaciente.test.mjs`.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  soDigitos,
  normalizarNome,
  separarConsulta,
  termoParaServidor,
  termoDaSegundaBusca,
  casaComConsulta,
} from "./buscaPaciente.js";

const casa = (solicitacao, texto) =>
  casaComConsulta(solicitacao, separarConsulta(texto));

const adriano = {
  nomePaciente: "Adriano Victor Ribeiro",
  cpfPaciente: "07612345678",
};
const adrianoMascara = {
  nomePaciente: "Adriano Victor Souza",
  cpfPaciente: "076.999.456-78",
};
const jose = { nomePaciente: "José Antônio", cpfPaciente: "111.222.333-44" };
const bebe = { nomePaciente: "RN 1 de Maria", cpfPaciente: null };
const bebe2 = { nomePaciente: "RN de Maria 2", cpfPaciente: null };
const todos = [adriano, adrianoMascara, jose, bebe, bebe2];

// Cópia do filtro que estava embutido na tela antes deste ajuste
// (solicitacoesFiltradas). Serve de referência para a caracterização abaixo.
function filtroAnterior(s, valorBusca) {
  if (!valorBusca) return true;
  const termo = valorBusca.toLowerCase();
  const nome = (s.nomePaciente || "").toLowerCase();
  const cpf = s.cpfPaciente || "";
  return nome.includes(termo) || cpf.includes(valorBusca);
}

test("caracterização — o que a busca mostrava antes continua aparecendo", () => {
  const buscas = [
    "",
    "adriano",
    "Adriano Victor",
    "victor r",
    "josé",
    "076",
    "07612345678",
    "076.999",
    "333-44",
    "rn",
    "RN 1",
    "maria 2",
    "2",
    "xyz",
  ];
  for (const busca of buscas) {
    for (const s of todos) {
      if (filtroAnterior(s, busca)) {
        assert.equal(
          casa(s, busca),
          true,
          `"${busca}" deixou de mostrar ${s.nomePaciente}`,
        );
      }
    }
  }
});

test("separarConsulta — só nome", () => {
  assert.deepEqual(separarConsulta("Adriano Victor"), {
    nome: "Adriano Victor",
    documento: "",
    texto: "Adriano Victor",
  });
});

test("separarConsulta — nome, separador e documento", () => {
  const esperado = { nome: "Adriano Victor", documento: "076.123" };
  for (const texto of [
    "Adriano Victor - 076.123",
    "Adriano Victor-076.123",
    "Adriano Victor 076.123",
    "  Adriano Victor  -  076.123  ",
  ]) {
    const { nome, documento } = separarConsulta(texto);
    assert.deepEqual({ nome, documento }, esperado, texto);
  }
});

test("separarConsulta — documento com pontuação sobrando no fim", () => {
  assert.equal(separarConsulta("Adriano Victor - 076.....").documento, "076");
  assert.equal(
    separarConsulta("Adriano Victor - 076.123.456-78").documento,
    "076.123.456-78",
  );
});

test("separarConsulta — só documento", () => {
  assert.deepEqual(separarConsulta("07612345678"), {
    nome: "",
    documento: "07612345678",
    texto: "07612345678",
  });
  assert.equal(separarConsulta("076.123.456-78").nome, "");
});

test("separarConsulta — hífen sem dígito fica no nome", () => {
  assert.equal(separarConsulta("Ana-Maria Souza").nome, "Ana-Maria Souza");
  assert.equal(separarConsulta("Maria - Silva").documento, "");
});

test("separarConsulta — dígito no meio do nome não é documento", () => {
  assert.deepEqual(separarConsulta("RN 1 de Maria"), {
    nome: "RN 1 de Maria",
    documento: "",
    texto: "RN 1 de Maria",
  });
});

test("separarConsulta — separador sem documento, vazio e rótulo de CPF ausente", () => {
  assert.equal(separarConsulta("Adriano - ").nome, "Adriano");
  assert.deepEqual(separarConsulta("   "), {
    nome: "",
    documento: "",
    texto: "",
  });
  assert.equal(separarConsulta("-").nome, "");
  assert.equal(separarConsulta(null).texto, "");
  assert.deepEqual(separarConsulta("Bebê X - CPF não informado"), {
    nome: "Bebê X",
    documento: "",
    texto: "Bebê X",
  });
});

test("termoParaServidor — nome quando há; senão o documento como digitado", () => {
  assert.equal(
    termoParaServidor(separarConsulta("Adriano Victor - 076.123")),
    "Adriano Victor",
  );
  assert.equal(termoParaServidor(separarConsulta("076.123")), "076.123");
  assert.equal(termoParaServidor(separarConsulta("")), "");
});

test("casaComConsulta — nome e documento exigem os dois", () => {
  assert.equal(casa(adriano, "Adriano Victor - 076.123"), true);
  assert.equal(
    casa(adrianoMascara, "Adriano Victor - 076.123"),
    false,
    "homônimo com outro CPF não entra",
  );
  assert.equal(casa(adrianoMascara, "Adriano Victor - 076.999"), true);
  assert.equal(
    casa(jose, "Adriano Victor - 111"),
    false,
    "documento certo com nome errado não entra",
  );
});

test("casaComConsulta — documento independe da máscara e aceita parcial", () => {
  assert.equal(
    casa(adriano, "076.123.456-78"),
    true,
    "digitado com máscara, gravado sem",
  );
  assert.equal(
    casa(adrianoMascara, "07699945678"),
    true,
    "digitado sem máscara, gravado com",
  );
  assert.equal(casa(adriano, "0761"), true);
  assert.equal(casa(adriano, "999"), false);
});

test("casaComConsulta — nome sem diferenciar acento nem caixa", () => {
  assert.equal(casa(jose, "jose antonio"), true);
  assert.equal(casa(jose, "JOSÉ"), true);
  assert.equal(casa(jose, "joão"), false);
});

test("casaComConsulta — paciente sem CPF", () => {
  assert.equal(casa(bebe, "RN 1 de Maria"), true);
  assert.equal(
    casa(bebe2, "RN de Maria 2"),
    true,
    "dígito no fim do nome não esconde o paciente",
  );
  assert.equal(casa(bebe, "RN 1 de Maria - CPF não informado"), true);
  assert.equal(casa(bebe, "076"), false);
});

test("casaComConsulta — consulta vazia casa com todos", () => {
  for (const s of todos) assert.equal(casa(s, ""), true);
});

test("soDigitos e normalizarNome", () => {
  assert.equal(soDigitos("076.123.456-78"), "07612345678");
  assert.equal(soDigitos(null), "");
  assert.equal(normalizarNome("  José   ANTÔNIO "), "jose antonio");
});

test("casaComConsulta — nome com separador e sem documento ainda mostra o paciente", () => {
  for (const texto of ["Adriano - ", "Adriano Victor -", "Adriano Victor - "]) {
    assert.equal(casa(adriano, texto), true, texto);
  }
  assert.equal(casa(jose, "Adriano Victor - "), false);
});

test("separarConsulta — outros separadores e o rótulo CPF antes do número", () => {
  const textos = [
    "Adriano Victor – 076.123",
    "Adriano Victor, 076.123",
    "Adriano Victor CPF: 076.123",
    "Adriano Victor - (CPF: 076.123)",
  ];
  for (const texto of textos) {
    const { nome, documento } = separarConsulta(texto);
    assert.deepEqual({ nome, documento }, { nome: "Adriano Victor", documento: "076.123" }, texto);
    assert.equal(casa(adriano, texto), true, texto);
    assert.equal(casa(adrianoMascara, texto), false, texto);
  }
});

test("termoDaSegundaBusca — documento; texto inteiro quando o dígito parece parte do nome", () => {
  const segundo = (texto) => termoDaSegundaBusca(separarConsulta(texto));
  assert.equal(segundo("Adriano Victor - 076.123"), "076.123");
  assert.equal(segundo("Maria 2"), "Maria 2");
  assert.equal(segundo("Adriano Victor"), "");
  assert.equal(segundo("076.123"), "");
});
