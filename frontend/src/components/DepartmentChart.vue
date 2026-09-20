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

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.distribution {
  display: flex;
  align-items: center;
  gap: 24px;
  min-height: 223px;
}

.donut-wrap {
  position: relative;
  width: 180px;
  height: 180px;
  flex-shrink: 0;
}

.donut-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.donut-center strong {
  font-size: 30px;
  font-weight: 600;
  color: var(--text);
}

.donut-center span {
  font-size: 9px;
  color: var(--text-faint);
  margin-top: 4px;
}

.chart-legend {
  list-style: none;
  padding: 0;
  margin: 0;
  min-width: 0;
  flex: 1;
}

.chart-legend li {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 15px 0;
  font-size: 10px;
  color: var(--text-muted);
  min-width: 0;
}

.chart-legend i {
  width: 6px;
  height: 6px;
  border-radius: 1px;
  flex-shrink: 0;
}

.chart-legend li > span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.chart-legend strong {
  margin-left: auto;
  color: var(--text-soft);
  font-size: 10px;
  font-weight: 500;
}

@media (min-width: 1500px) {
  .distribution {
    gap: 35px;
  }
}

@media (min-width: 1500px) {
  .donut-wrap {
    width: 210px;
    height: 210px;
  }
}

@media (max-width: 1200px) {
  .distribution {
    gap: 10px;
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 1200px) {
  .donut-wrap {
    width: 155px;
    height: 155px;
    align-self: center;
  }
}

@media (max-width: 1200px) {
  .chart-legend {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0 14px;
  }
}

@media (max-width: 1200px) {
  .chart-legend li {
    margin: 6px 0;
  }
}

@media (max-width: 900px) {
  .distribution {
    flex-direction: row;
    gap: 35px;
    padding: 5px 15px;
    min-height: 180px;
  }
}

@media (max-width: 900px) {
  .chart-legend {
    display: block;
  }
}

@media (max-width: 900px) {
  .chart-legend li {
    margin: 13px 0;
  }
}

@media (max-width: 680px) {
  .distribution {
    gap: 21px;
    padding: 4px 0;
  }
}

@media (max-width: 680px) {
  .donut-wrap {
    width: 156px;
    height: 156px;
  }
}

@media (max-width: 680px) {
  .chart-legend li {
    font-size: 9px;
  }
}
</style>
