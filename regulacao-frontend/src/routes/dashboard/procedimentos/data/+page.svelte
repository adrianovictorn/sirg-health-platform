<script lang="ts">
    import { goto } from "$app/navigation";
    import { getApi } from "$lib/api";
    import Content from "$lib/Content.svelte";
    import { onMount } from "svelte";

    type formPacientePorGrupo = {
        grupo: string
        data: string
    }

    interface GrupoViewDTO {
        id: number
        codigo: string
        nome: string
        total: number
        direcionadoHospital: boolean
    }

    let form = $state<formPacientePorGrupo[]>([])
    let grupos = $state<GrupoViewDTO[]>([])
    let totalPorGrupo = $state<Record<string, number>>({})
    let dataSelecionanda = $state(new Date().toISOString().slice(0,10))

    let carregando = $state(false)

    const dataPorExtenso = $derived(() => {
        const [ano, mes, dia] = dataSelecionanda.split("-").map(Number);

        const d = new Date(Date.UTC(ano, mes - 1, dia));

        return new Intl.DateTimeFormat("pt-br", {
            weekday: "long",
            day: "numeric",
            month: "long",
            year: "numeric",
            timeZone: "UTC"
        }).format(d)
    })
    
    async function listarGrupos() {
        try {
            const res = await getApi("grupo-relatorio/listar")
           if(!res.ok){
            throw new Error("Erro ao receber dados do Grupo")
           }
           const data = await res.json();
           const lista = Array.isArray(data) ? data : (data?.content ?? []);
           return lista.filter((grupo) => grupo.direcionadoHospital);
        } catch (error) {
            throw new Error("Erro ao se conectar ao servidor !")
        }
    }


    async function contarPacientesPorGrupo() {
        grupos = await listarGrupos()
        const novo: Record<string, number> = {};
        
        try{
           for (const gr of grupos) {
               const params = new URLSearchParams()
               params.append("grupo",gr.codigo)
               params.append("data", dataSelecionanda)

               const res = await getApi(`especialidades/contar/pacientes/por/grupo?${params.toString()}`)
                if (!res.ok) throw new Error(`Erro ao contar grupo ${gr.codigo}`);

               novo[gr.codigo] = await res.json()
           }

           totalPorGrupo = novo
           
       }catch(error){
        throw new Error ("Erro ao conectar ao servidor !")
       }
    }
    

    onMount(() => {
        listarGrupos()
        contarPacientesPorGrupo()
        
    })

    $effect(() => {
        if(dataSelecionanda){
            contarPacientesPorGrupo()
        }
    })

    const COLORS = [
        {class: "bg-emerald-600", hover:"hover:bg-green-800" }, {class:"bg-sky-600", hover:"hover:bg-red-800"}, {class: "bg-amber-500", hover:"hover:bg-red-800"}, {class:"bg-rose-600", hover:"hover:bg-fuchsia-600"}, {class: "bg-violet-500", hover:"hover:bg-orange-600"}, {class: "bg-gray-300", hover: "hover:bg-gray-600"}, {class: 'bg-cyan-600', hover: 'bg-cyan-700'}]

    const ICONS = [
        `<path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 18.5A2.493 2.493 0 0 1 7.51 20H7.5a2.468 2.468 0 0 1-2.4-3.154 2.98 2.98 0 0 1-.85-5.274 2.468 2.468 0 0 1 .92-3.182 2.477 2.477 0 0 1 1.876-3.344 2.5 2.5 0 0 1 3.41-1.856A2.5 2.5 0 0 1 12 5.5m0 13v-13m0 13a2.493 2.493 0 0 0 4.49 1.5h.01a2.468 2.468 0 0 0 2.403-3.154 2.98 2.98 0 0 0 .847-5.274 2.468 2.468 0 0 0-.921-3.182 2.477 2.477 0 0 0-1.875-3.344A2.5 2.5 0 0 0 14.5 3 2.5 2.5 0 0 0 12 5.5m-8 5a2.5 2.5 0 0 1 3.48-2.3m-.28 8.551a3 3 0 0 1-2.953-5.185M20 10.5a2.5 2.5 0 0 0-3.481-2.3m.28 8.551a3 3 0 0 0 2.954-5.185"/>`,
        `<path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12.01 6.001C6.5 1 1 8 5.782 13.001L12.011 20l6.23-7C23 8 17.5 1 12.01 6.002Z"/>`,
        `<path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18 6H6m12 4H6m12 4H6m12 4H6"/>`,
        `<path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 4h12M6 4v16M6 4H5m13 0v16m0-16h1m-1 16H6m12 0h1M6 20H5M9 7h1v1H9V7Zm5 0h1v1h-1V7Zm-5 4h1v1H9v-1Zm5 0h1v1h-1v-1Zm-3 4h2a1 1 0 0 1 1 1v4h-4v-4a1 1 0 0 1 1-1Z"/>
`
    ]

    const cards = $derived(
        grupos.map((grupo, index) => ({
        label: grupo.nome,
        grupo: grupo.codigo,
        class: COLORS[index % COLORS.length].class,
        hover: COLORS[index % COLORS.length].hover,
        icon: ICONS[index % ICONS.length],
        total: totalPorGrupo[grupo.codigo] ?? 0
        }))
    );

    function abrirDetalhe(grupo: string) {
        const qs = new URLSearchParams()
        qs.append("data", dataSelecionanda)

        goto(`/agendas/${grupo}?${qs.toString()}`)
    }
    
</script>
<Content titleH1="Quantitativos por Data" page="/dashboard/procedimentos" >

    <section class="max-w-screen flex justify-start m-5 ">
        <div class="bg-emerald-800 w-[0.5%] ">

        </div>
        <div class="bg-emerald-200 w-[98%] p-5  rounded-r-lg">
            <h2 class="font-semibold text-3xl text-gray-800">Agendamentos do Dia</h2>
            <p class="text-gray-700 mt-2">{dataPorExtenso()}</p>
            <form action="" class="mt-3 w-full">
                <h3 class="font-medium text-gray-800 ">Buscar por outra Data:</h3>
                <input type="date" class="mt-2 border-gray-400 rounded-lg" bind:value={dataSelecionanda}>
            </form>
        </div>
    </section>
    

    <div class="grid grid-cols-1 md:grid-cols-5 gap-4 p-10 bg-white ">
    {#each cards as card }
            <div role="button" tabindex="0" onclick={() => abrirDetalhe(card.grupo)} onkeydown={(e) => {
                if(e.key === 'Enter' || e.key === ' '){
                    e.preventDefault();
                    abrirDetalhe(card.grupo)
                }
            }} class={`shadow-xl rounded-xl border p-4 mt-2 m-2 w-full border-gray-300 hover:text-emerald-600 transform transition-transform duration-200 ease-out hover:scale-[1.03] hover:-translate-y-1 `}>
                <div class="flex gap-5 border-gray-800 text-center w-full ">
                    <div class={`${card.class} ${card.hover} flex gap-4 rounded-2xl p-3 border border-gray-300`}>
                        <svg viewBox="0 0 24 24" class="w-6 h-6 text-white  " fill="none">
                            {@html card.icon}
                        </svg>
                    </div>
                    <div class="flex flex-1 align-middle items-center justify-end">
                        <p class="font-bold text-gray-700 text-4xl ">
                            {card.total}
                        </p>
                    </div>
                </div>
                <div class="flex flex-1 justify-end">
                    <p class=" font-semibold text-[0.8rem] mt-2 text-gray-600 ">Total Agendado</p>
                </div>
                <div class="flex p-5 justify-center">
                    
                </div>
                
                <p  class="text-start font-semibold text-[1rem] "> 
                    {card.label}
                </p>
                
            </div>
            {/each}
    </div>
</Content>
