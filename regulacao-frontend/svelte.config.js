// svelte.config.js
import adapter from "@sveltejs/adapter-node"; // Alterado

const config = {
  kit: {
    adapter: adapter(), // Alterado para adapter-node
  },
};

export default config;
