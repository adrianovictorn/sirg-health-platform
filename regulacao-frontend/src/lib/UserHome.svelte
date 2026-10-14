<script>
  import { user } from '$lib/stores/auth.js';
</script>

<main class="flex-1 p-6 overflow-auto flex items-center justify-center">
  <div class="text-center">
    <h1 class="text-4xl font-bold text-emerald-800 mb-4">Bem-vindo(a), {$user?.nome}!</h1>
    <p class="text-lg text-gray-600">Utilize o menu lateral para navegar pelo sistema.</p>
    
    <div class="mt-8 flex justify-center gap-4">
        <a href={$user?.role === 'USER' ? '/atendimentos' : '/dashboard/procedimentos'} class="bg-emerald-600 text-white px-6 py-3 rounded-lg font-semibold shadow hover:bg-emerald-700 transition">
            Agendamentos
        </a>
        
    </div>
  </div>
</main>