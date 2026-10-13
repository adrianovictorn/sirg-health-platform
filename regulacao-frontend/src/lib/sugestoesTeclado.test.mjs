// Roda com `node --test src/lib/sugestoesTeclado.test.mjs`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { proximoIndice, sugestoesTeclado } from "./sugestoesTeclado.js";

test("Seta para baixo sem opção ativa vai para a primeira", () => {
  assert.equal(proximoIndice(-1, 3, "ArrowDown"), 0);
});

test("Seta para baixo avança e dá a volta no fim", () => {
  assert.equal(proximoIndice(0, 3, "ArrowDown"), 1);
  assert.equal(proximoIndice(2, 3, "ArrowDown"), 0);
});

test("Seta para cima sem opção ativa vai para a última", () => {
  assert.equal(proximoIndice(-1, 3, "ArrowUp"), 2);
});

test("Seta para cima recua e dá a volta no início", () => {
  assert.equal(proximoIndice(2, 3, "ArrowUp"), 1);
  assert.equal(proximoIndice(0, 3, "ArrowUp"), 2);
});

test("Lista vazia nunca tem opção ativa", () => {
  assert.equal(proximoIndice(0, 0, "ArrowDown"), -1);
  assert.equal(proximoIndice(-1, 0, "ArrowUp"), -1);
});

test("Outras teclas mantêm a opção ativa", () => {
  assert.equal(proximoIndice(1, 3, "Enter"), 1);
  assert.equal(proximoIndice(-1, 3, "Tab"), -1);
});

test("Índice além da lista (lista encolheu) deixa de valer", () => {
  assert.equal(proximoIndice(5, 3, "Enter"), -1);
  assert.equal(proximoIndice(5, 3, "ArrowDown"), 0);
});

// A action só usa uma fatia pequena do DOM; estes dublês cobrem essa fatia.
function botaoFalso(nome) {
  const atributos = {};
  const classes = new Set();
  return {
    nome,
    id: "",
    cliques: 0,
    classList: { toggle: (c, liga) => (liga ? classes.add(c) : classes.delete(c)) },
    setAttribute: (n, v) => (atributos[n] = String(v)),
    getAttribute: (n) => atributos[n] ?? null,
    removeAttribute(n) {
      if (n === "id") this.id = "";
      else delete atributos[n];
    },
    scrollIntoView() {},
    dispatchEvent() {},
    click() {
      this.cliques++;
    },
  };
}

function montar(nomes) {
  const ouvintes = {};
  const tela = { botoes: nomes.map(botaoFalso) };
  const input = {
    id: "busca",
    parentElement: { querySelectorAll: () => tela.botoes },
    setAttribute() {},
    removeAttribute() {},
    addEventListener: (tipo, fn) => (ouvintes[tipo] = fn),
    removeEventListener() {},
  };
  globalThis.MouseEvent ??= class {};
  sugestoesTeclado(input, { aberta: true });
  tela.teclar = (key) => ouvintes.keydown({ key, preventDefault() {} });
  tela.destacado = () =>
    tela.botoes.find((b) => b.getAttribute("aria-selected") === "true")?.nome ?? null;
  return tela;
}

test("Enter escolhe a opção destacada mesmo se a lista mudou de ordem", () => {
  const tela = montar(["Ana", "Bruna", "Carla"]);
  tela.teclar("ArrowDown");
  tela.teclar("ArrowDown");
  assert.equal(tela.destacado(), "Bruna");

  const [ana, bruna, carla] = tela.botoes;
  tela.botoes = [botaoFalso("Maria"), carla, bruna];
  tela.teclar("Enter");
  assert.equal(bruna.cliques, 1);
  assert.equal(carla.cliques, 0);
  assert.equal(ana.cliques, 0);
});

test("Enter não escolhe ninguém se a opção destacada saiu da lista", () => {
  const tela = montar(["Ana", "Bruna", "Carla"]);
  tela.teclar("ArrowDown");
  tela.teclar("ArrowDown");

  tela.botoes = [botaoFalso("Maria"), botaoFalso("Marta"), botaoFalso("Mirna")];
  tela.teclar("Enter");
  assert.deepEqual(tela.botoes.map((b) => b.cliques), [0, 0, 0]);
});

test("Seta continua a partir da opção destacada depois de a lista mudar", () => {
  const tela = montar(["Ana", "Bruna", "Carla"]);
  tela.teclar("ArrowDown");
  assert.equal(tela.destacado(), "Ana");

  const [ana, bruna, carla] = tela.botoes;
  tela.botoes = [bruna, carla, ana];
  tela.teclar("ArrowUp");
  assert.equal(tela.destacado(), "Carla");
});

test("Enter sem opção destacada não escolhe ninguém", () => {
  const tela = montar(["Ana"]);
  tela.teclar("Enter");
  assert.equal(tela.botoes[0].cliques, 0);
});
