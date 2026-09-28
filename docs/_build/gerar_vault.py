# -*- coding: utf-8 -*-
"""
Gera as notas derivadas do vault Obsidian em docs/ a partir dos documentos-fonte
da raiz do repositorio (DOCUMENTACAO_TECNICA.md, CHANGELOG.md, INFRA.md, README.md).

Uso:  python docs/_build/gerar_vault.py

As pastas docs/tecnica/ e docs/referencia/ sao REESCRITAS a cada execucao.
Notas escritas a mao (Inicio.md, mapas/, diagrama-*.md) nunca sao tocadas.
"""

from __future__ import annotations

import re
import shutil
import unicodedata
from datetime import date
from pathlib import Path

DOCS = Path(__file__).resolve().parent.parent
RAIZ = DOCS.parent
TECNICA = DOCS / "tecnica"
REFERENCIA = DOCS / "referencia"

AVISO = (
    "> [!info] Nota gerada automaticamente\n"
    "> Fonte: [`{fonte}`]({rel}) — secao `{secao}`.\n"
    "> Nao edite aqui: altere o arquivo-fonte e rode `python docs/_build/gerar_vault.py`.\n"
)

# Diagramas do vault e as palavras que indicam afinidade com uma secao
DIAGRAMAS = {
    "diagrama-usuarios": ("usuário", "usuario", "autentic", "jwt", "role", "segurança", "seguranca", "login"),
    "diagrama-solicitacoes": ("solicita", "agendamento", "especialidade", "paciente", "cota"),
    "diagrama-transporte": ("transporte", "veículo", "veiculo", "motorista"),
    "diagrama-federacao": ("federa", "pacto", "rabbit", "município", "municipio", "fhir"),
}


def slug(texto: str) -> str:
    """Slug estilo GitHub: minusculas, sem acento, espacos -> hifen."""
    t = texto.strip().lower()
    t = unicodedata.normalize("NFKD", t)
    t = "".join(c for c in t if not unicodedata.combining(c))
    t = re.sub(r"[^\w\s-]", "", t)
    t = re.sub(r"[\s_]+", "-", t)
    return t.strip("-")


def anchor(texto: str) -> str:
    """Anchor estilo GitHub: mantem acentos, remove pontuacao."""
    t = texto.strip().lower()
    t = re.sub(r"[^\w\s-]", "", t, flags=re.UNICODE)
    return re.sub(r"[\s_]+", "-", t).strip("-")


class Secao:
    def __init__(self, numero: int, titulo: str, corpo: str):
        self.numero = numero
        self.titulo = titulo
        self.corpo = corpo.strip()
        self.nota = f"{numero:02d} - {titulo}"

    @property
    def arquivo(self) -> Path:
        return TECNICA / f"{self.nota}.md"


def ler_secoes(texto: str) -> list[Secao]:
    """Quebra o documento tecnico nos titulos '## N. Titulo'."""
    padrao = re.compile(r"^## (\d+)\.\s+(.+?)\s*$", re.MULTILINE)
    marcas = list(padrao.finditer(texto))
    secoes: list[Secao] = []
    for i, m in enumerate(marcas):
        fim = marcas[i + 1].start() if i + 1 < len(marcas) else len(texto)
        corpo = texto[m.end():fim]
        corpo = re.sub(r"\n---\s*\n\s*$", "\n", corpo)  # tira o separador final
        secoes.append(Secao(int(m.group(1)), m.group(2), corpo))
    return secoes


def mapa_ancoras(secoes: list[Secao], texto: str) -> dict[str, str]:
    """Todo anchor do documento (inclusive de subsecoes) -> nota da secao que o contem."""
    mapa: dict[str, str] = {}
    por_numero = {s.numero: s for s in secoes}
    for m in re.finditer(r"^#{2,4}\s+(.+?)\s*$", texto, re.MULTILINE):
        titulo = m.group(1)
        num = re.match(r"(\d+)", titulo)
        if not num:
            continue
        secao = por_numero.get(int(num.group(1)))
        if secao:
            mapa[anchor(titulo)] = secao.nota
    return mapa


def converter_links(corpo: str, mapa: dict[str, str], atual: str) -> str:
    """[Texto](#anchor) -> [[Nota|Texto]] (ou so o texto, se aponta pra propria nota)."""
    def troca(m: re.Match) -> str:
        texto, alvo = m.group(1), m.group(2)
        nota = mapa.get(alvo)
        if not nota:
            num = re.match(r"(\d+)", alvo)
            if num:
                nota = next((v for k, v in mapa.items() if k.startswith(num.group(1) + "-")), None)
        if not nota:
            return "`" + texto + "`"
        if nota == atual:
            return "**" + texto + "**"
        return "[[" + nota + "|" + texto + "]]"

    return re.sub(r"\[([^\]]+)\]\(#([^)]+)\)", troca, corpo)


def diagramas_relacionados(secao: Secao) -> list[str]:
    amostra = (secao.titulo + " " + secao.corpo[:4000]).lower()
    return [n for n, chaves in DIAGRAMAS.items() if sum(amostra.count(c) for c in chaves) >= 3]


def navegacao(secoes: list[Secao], i: int) -> str:
    partes = ["[[Índice Técnico]]"]
    if i > 0:
        partes.insert(0, "← [[" + secoes[i - 1].nota + "]]")
    if i + 1 < len(secoes):
        partes.append("[[" + secoes[i + 1].nota + "]] →")
    return " · ".join(partes)


def limpar(pasta: Path) -> None:
    if pasta.exists():
        shutil.rmtree(pasta)
    pasta.mkdir(parents=True)


def gerar_tecnica() -> list[Secao]:
    fonte = RAIZ / "DOCUMENTACAO_TECNICA.md"
    texto = fonte.read_text(encoding="utf-8")
    secoes = ler_secoes(texto)
    mapa = mapa_ancoras(secoes, texto)
    limpar(TECNICA)

    for i, s in enumerate(secoes):
        corpo = converter_links(s.corpo, mapa, s.nota)
        rel_diag = diagramas_relacionados(s)
        bloco_diag = ""
        if rel_diag:
            bloco_diag = "\n## Diagramas relacionados\n\n" + "\n".join("- [[" + d + "]]" for d in rel_diag) + "\n"
        s.arquivo.write_text(
            "---\n"
            "titulo: " + s.titulo + "\n"
            "secao: " + str(s.numero) + "\n"
            "tags:\n  - sirg/tecnica\n"
            "fonte: DOCUMENTACAO_TECNICA.md\n"
            "gerado: auto\n"
            "atualizado: " + date.today().isoformat() + "\n"
            "---\n\n"
            + AVISO.format(
                fonte="DOCUMENTACAO_TECNICA.md",
                rel="../../DOCUMENTACAO_TECNICA.md",
                secao=str(s.numero) + ". " + s.titulo,
            )
            + "\n# " + str(s.numero) + ". " + s.titulo + "\n\n"
            + corpo.strip()
            + "\n"
            + bloco_diag
            + "\n---\n\n"
            + navegacao(secoes, i)
            + "\n",
            encoding="utf-8",
        )

    linhas = "\n".join(str(s.numero) + ". [[" + s.nota + "]]" for s in secoes)
    (TECNICA / "Índice Técnico.md").write_text(
        "---\ntitulo: Índice Técnico\ntags:\n  - sirg/mapa\ngerado: auto\n"
        "atualizado: " + date.today().isoformat() + "\n---\n\n"
        "# Índice Técnico\n\n"
        "As 12 seções da documentação técnica, uma nota cada.\n\n"
        + linhas
        + "\n\n---\n\nVolta para [[Início]].\n",
        encoding="utf-8",
    )
    return secoes


def gerar_referencia() -> None:
    limpar(REFERENCIA)
    mapeamento = [
        ("CHANGELOG.md", "Changelog", "sirg/historico",
         "Histórico de versões. Cada `## [x.y]` é uma entrega."),
        ("INFRA.md", "Infraestrutura", "sirg/operacao",
         "Como o sistema roda em produção: duas VPS, uma por município."),
        ("README.md", "Visão Institucional", "sirg/visao",
         "O texto de apresentação do projeto, sem detalhe de implementação."),
    ]
    for arquivo, nota, tag, resumo in mapeamento:
        origem = RAIZ / arquivo
        if not origem.exists():
            continue
        corpo = origem.read_text(encoding="utf-8").strip()
        corpo = re.sub(r"^#\s+.*\n", "", corpo, count=1).strip()  # titulo vira o da nota
        (REFERENCIA / (nota + ".md")).write_text(
            "---\n"
            "titulo: " + nota + "\n"
            "tags:\n  - " + tag + "\n"
            "fonte: " + arquivo + "\n"
            "gerado: auto\n"
            "atualizado: " + date.today().isoformat() + "\n"
            "---\n\n"
            + AVISO.format(fonte=arquivo, rel="../../" + arquivo, secao="documento inteiro")
            + "\n# " + nota + "\n\n" + resumo + "\n\n---\n\n" + corpo + "\n\n---\n\nVolta para [[Início]].\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    secoes = gerar_tecnica()
    gerar_referencia()
    print("tecnica/: " + str(len(secoes)) + " secoes + indice")
    print("referencia/: Changelog, Infraestrutura, Visao Institucional")
