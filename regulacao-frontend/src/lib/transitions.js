import { cubicOut } from "svelte/easing";

/**
 * Combina as transições de fade (opacidade) e scale (escala) em uma única função.
 * @param {HTMLElement} node - O elemento HTML a ser animado.
 * @param {object} params - Parâmetros da animação.
 * @param {number} params.duration - Duração em milissegundos.
 * @param {number} params.start - O valor inicial da escala (ex: 0.95).
 * @returns {object} Objeto de transição para o Svelte.
 */
export function fadeScale(node, { duration = 150, start = 0.95 }) {
  return {
    duration,
    css: (t) => {
      const eased = cubicOut(t);
      return `
        opacity: ${eased};
        transform: scale(${eased * (1 - start) + start});
      `;
    },
  };
}
