<script setup lang="ts">
/** 根据接口真实统计结果绘制图表，并在数据变化或组件销毁时释放 Chart 实例。 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Chart, ArcElement, DoughnutController, Tooltip } from 'chart.js'
Chart.register(ArcElement, DoughnutController, Tooltip)
const props = defineProps<{ entries: { label: string; count: number }[]; total: number }>()
const canvas = ref<HTMLCanvasElement>()
let chart: Chart | undefined
const colors = ['#177653', '#83bd97', '#487ab1', '#d5ab54', '#8b84ac', '#ced4d7']
function draw() {
  chart?.destroy()
  if (!canvas.value) return
  chart = new Chart(canvas.value, {
    type: 'doughnut',
    data: {
      labels: props.entries.map((item) => item.label),
      datasets: [
        {
          data: props.entries.map((item) => item.count),
          backgroundColor: colors,
          borderColor: '#fff',
          borderWidth: 4,
          hoverOffset: 4,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '78%',
      animation: { duration: 500 },
      plugins: { tooltip: { callbacks: { label: (context) => `${context.label}: ${context.raw} 人` } } },
    },
  })
}
onMounted(draw)
watch(() => props.entries, draw)
onBeforeUnmount(() => chart?.destroy())
</script>
<template>
  <div class="distribution">
    <div class="donut-wrap">
      <canvas ref="canvas" role="img" aria-label="部门人员占比分布"></canvas>
      <div class="donut-center">
        <strong>{{ total }}</strong>
        <span>员工总数</span>
      </div>
    </div>
    <ul class="chart-legend">
      <li v-for="(entry, index) in entries" :key="entry.label">
        <i :style="{ backgroundColor: colors[index % colors.length] }"></i>
        <span>{{ entry.label }}</span>
        <strong>{{ entry.count }}</strong>
      </li>
    </ul>
  </div>
</template>
