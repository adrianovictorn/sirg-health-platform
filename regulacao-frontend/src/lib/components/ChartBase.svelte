<script lang="ts">
    import Chart from "chart.js/auto";
    import { onDestroy, onMount } from "svelte";


    export let type: "line" | "bar" | "pie" | "doughnut" = "bar";
    export let labels: string[] = []
    export let values: number[] = []
    export let title = ""; 
    


    let canvas: HTMLCanvasElement
    let chart: Chart | null = null
    let lastType = type;

    function build(){
        const ctx = canvas?.getContext("2d")
        if(!ctx) return;

        chart = new Chart(ctx, {
            type,
            data: {
                labels,
                datasets: [
                    {
                        label: title,
                        data:values,
                        tension: type === "line" ? 0.3 : 0,
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {display: true}
                }
            }
        });

        lastType = type
    }

    function rebuild(){
        chart?.destroy();
        chart = null
        build()
    }

    function sync(){
        if(!chart) return;

        if(type !== lastType){
            rebuild()
            return;
        }

        chart.data.labels = labels
        chart.data.datasets[0].label = title;
        chart.data.datasets[0].data = values

        chart.update();
    }

    onMount(() =>{
        build(),
        sync()
    }) 


    $: {
        void [type, labels, values, title]
        sync();
    }


    

    onDestroy(() => {
        chart?.destroy();
        chart = null
    })
</script>

<div style="height:280px">
    <canvas bind:this={canvas}></canvas>
</div>