# Proveniência das skills externas

Tudo nesta pasta veio de terceiros e passa a instruir um agente que edita código de um
sistema de saúde em produção. Por isso cada skill é uma **cópia fixada**: foi clonada num
commit específico, lida por inteiro e copiada à mão. Nada aqui se atualiza sozinho.

Data da consulta e da leitura: **2026-10-12**.

| Skill (pasta) | Origem | Caminho na origem | Commit | Licença |
|---|---|---|---|---|
| `redesign-existing-projects` | https://github.com/Leonxlnx/taste-skill | `skills/redesign-skill/SKILL.md` | `d237dd399d57524945c0c81327da5da19f7a468d` | MIT, © 2026 Leonxlnx |
| `design-taste-frontend` | https://github.com/Leonxlnx/taste-skill | `skills/taste-skill/SKILL.md` | `d237dd399d57524945c0c81327da5da19f7a468d` | MIT, © 2026 Leonxlnx |
| `image-to-code` | https://github.com/Leonxlnx/taste-skill | `skills/image-to-code-skill/SKILL.md` | `d237dd399d57524945c0c81327da5da19f7a468d` | MIT, © 2026 Leonxlnx |
| `web-design-guidelines` (`SKILL.md`) | https://github.com/vercel-labs/agent-skills | `skills/web-design-guidelines/SKILL.md` | `063bee94c3f4df8453406c830b0a7df0f2860278` | MIT, declarada no `README.md` da origem (o repositório não tem arquivo `LICENSE`) |
| `web-design-guidelines` (`command.md`) | https://github.com/vercel-labs/web-interface-guidelines | `command.md` | `434b7f91364665f2f733b310ec54809bf8f37937` | MIT, © 2025 Vercel Labs |
| `motion` | https://github.com/motiondivision/ai-kit | `plugins/motion/skills/motion/` | `d1c5c26f424adfd47c112d894e9d424b57338c7e` | MIT, declarada em `packages/motion-ai/package.json` e em `plugins/motion/.cursor-plugin/plugin.json` (o repositório não tem arquivo `LICENSE`, por isso a pasta local também não tem) |
| `playwright-cli` | https://github.com/microsoft/playwright-cli | `skills/playwright-cli/` | `b85c7a736bb473bf55b584e54a09ffa698d6d871` (pacote npm `@playwright/cli@0.1.22`) | Apache-2.0, Microsoft |

O arquivo `LICENSE` de cada pasta é a cópia do `LICENSE` da origem no mesmo commit. A
origem do Playwright CLI não tem arquivo `NOTICE`.

## Como cada uma foi instalada

- `redesign-existing-projects`, `design-taste-frontend`, `image-to-code`,
  `web-design-guidelines`: `git clone` fora
  do projeto e cópia manual. Os instaladores documentados (`npx skills add`, plugin do
  Claude Code) não foram usados porque instalam o estado da `main` no momento.
- `motion`: é a skill oficial do Motion AI Kit. Copiada inteira por `git clone`, sem o
  instalador `npx motion-ai`, sem o servidor MCP do Motion (`plugins/motion/mcp.json`), sem
  o agente `motion-reviewer` e sem as regras `motion.mdc` do mesmo repositório.
- `playwright-cli`: gerada pelo instalador oficial, `playwright-cli install --skills`, com
  o pacote `@playwright/cli@0.1.22` instalado globalmente. O resultado foi comparado com o
  commit acima e era idêntico antes da modificação local descrita abaixo.

## Modificações locais

| Arquivo | Modificação | Motivo |
|---|---|---|
| `redesign-existing-projects/SKILL.md` | nenhuma | — |
| `design-taste-frontend/SKILL.md` | Só o campo `description` do frontmatter: foi acrescentado no início um aviso entre colchetes restringindo a skill à landing pública (`sirg-landing/`), em SvelteKit e sem dependência nova, e o valor passou a ficar entre aspas. O corpo está intacto. | A skill declara que não serve para dashboards e painéis administrativos (seção 13), que são as telas internas do SIRG, e assume React/Next |
| `motion/SKILL.md` | Só o campo `description` do frontmatter: foi acrescentada uma primeira linha entre colchetes dizendo que, no SIRG, só `best-practices/` se aplica, que o servidor MCP não está conectado, que o Motion só está instalado em `sirg-landing/` e que `motion-plus` não deve ser importado. Os demais arquivos estão intactos. | Sem o servidor MCP, busca, geração de easing, auditoria e editor não funcionam; o aviso evita que o agente os cite |
| `playwright-cli/SKILL.md` | Removida do frontmatter a linha `allowed-tools: Bash(playwright-cli:*) Bash(npx playwright:*) Bash(npx --no-install playwright:*)`. O corpo e as dez referências estão intactos. | Com a skill ativa, essa linha pré-aprovava todos os subcomandos e o caminho `npx playwright`, anulando a lista `allow` restrita de `.claude/settings.json` (inclusive a restrição de `goto` a localhost) |
| `web-design-guidelines/command.md` | nenhuma | — |
| `web-design-guidelines/SKILL.md` | O original manda buscar o `command.md` da `main` por WebFetch a cada uso. Aqui ele manda ler a cópia local `command.md` com Read. São sete linhas alteradas, nos trechos "How It Works", "Guidelines Source" e "Usage", mais uma frase acrescentada ("Do not fetch them from the network…"). | Manter as regras fixadas num commit revisado, sem instrução remota não lida |
| `image-to-code/SKILL.md` | Só o campo `description` do frontmatter: foi acrescentado no início um aviso entre colchetes dizendo que, no SIRG, a skill só vale com imagem fornecida pelo usuário e só nas regras de análise, e o valor passou a ficar entre aspas (exigência do YAML por causa dos colchetes). O corpo está intacto. | A skill foi escrita para o Codex e exige gerar imagens; o Claude Code não gera imagem |

Para conferir: clone a origem, faça `git checkout <commit>` e rode `diff` contra o arquivo
desta pasta. Só as modificações da tabela devem aparecer.

## O que não se aplica ao SIRG

Lido nas fontes e registrado em `.claude/agents/ux-ui-designer.md`, seção "Skills externas":

- `redesign-existing-projects` recomenda contêiner com `max-width`, troca de fonte, troca
  de biblioteca de ícones e imagens de `picsum.photos`.
- `design-taste-frontend` assume React/Next e Motion, manda instalar pacotes, usar geração
  de imagem ou `picsum.photos`, exige modo escuro e bane o travessão.
- `motion` depende de servidor MCP para tudo além de `best-practices/`, e traz regras de
  React, Vue e Base UI.
- `image-to-code` é para sites de marketing e exige geração de imagem, que não existe aqui.
- `web-design-guidelines` tem regras específicas de React/Next e pede "Title Case".
- `playwright-cli` documenta preenchimento de formulário, salvar estado de autenticação em
  arquivo e anexar capturas a pull requests.

## Fontes pesquisadas que não viraram skill

- **Awesome DESIGN.md** (https://github.com/VoltAgent/awesome-design-md, commit
  `c7c1bada4eda972236d124e06ee58df129a9266b`, MIT): não é skill, é uma coleção de arquivos
  `DESIGN.md` de marcas. Dela veio o **formato** usado em `regulacao-frontend/DESIGN.md`,
  cuja especificação é do Google Stitch
  (https://stitch.withgoogle.com/docs/design-md/specification/). O conteúdo daquele arquivo
  é do SIRG.

## Referência que fica fora do git

`.claude/referencias/motion-docs/` guarda cinco páginas da documentação do Motion em
Markdown (`svelte`, `quick-start`, `animate`, `stagger`, `text-animation`), baixadas de
`https://motion.dev/docs/<página>.md` em 2026-10-12. Não encontrei licença que permita
redistribuir o texto da documentação (a licença MIT citada pelo site cobre os pacotes
`motion`, `motion/react` e `motion-v`), então a pasta está no `.gitignore` e não vai para
o repositório. Em outra máquina, baixe de novo as mesmas URLs.

O pacote `motion` (MIT) está em `sirg-landing/package.json` na versão exata `14.1.0`.

## Como atualizar uma skill

1. Clone a origem fora do projeto e leia o diff entre o commit desta tabela e o novo.
2. Leia o arquivo novo por inteiro antes de copiar.
3. Reaplique as modificações locais, se ainda fizerem sentido.
4. Atualize o commit, a data e a tabela de modificações neste arquivo, no mesmo commit.
