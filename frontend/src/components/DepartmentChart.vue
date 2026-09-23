<script setup lang="ts">
/** 根据接口真实统计结果绘制图表，并在数据变化或组件销毁时释放 Chart 实例。 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Chart, ArcElement, DoughnutController, Tooltip } from 'chart.js'
import { useTheme } from '../stores/theme'
Chart.register(ArcElement, DoughnutController, Tooltip)
const props = defineProps<{ entries: { label: string; count: number }[]; total: number }>()
const theme = useTheme()
const canvas = ref<HTMLCanvasElement>()
let chart: Chart | undefined
/** canvas 读不了 CSS 变量，从语义 token 取值，保证暗色主题下颜色同步切换。 */
function cssToken(name: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}
const colors = ref<string[]>([])
function readPalette() {
  colors.value = ['--chart-1', '--chart-2', '--chart-3', '--chart-4', '--chart-5', '--chart-6'].map(cssToken)
}
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
          backgroundColor: colors.value,
          borderColor: cssToken('--surface'),
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
onMounted(() => {
  readPalette()
  draw()
})
watch(() => props.entries, draw)
watch(
  () => theme.theme,
  () => {
    readPalette()
    draw()
  },
)
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
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--text-strong);
  font-variant-numeric: tabular-nums;
}

.donut-center span {
  font-size: 11px;
  letter-spacing: 0.06em;
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
  gap: 9px;
  margin: 14px 0;
  font-size: 12px;
  color: var(--text-muted);
  min-width: 0;
}

.chart-legend i {
  width: 8px;
  height: 8px;
  border-radius: 2.5px;
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
  font-size: 12px;
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}

@media (min-width: 1500px) {
  .distribution {
    gap: 35px;
  }
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
  .donut-wrap {
    width: 155px;
    height: 155px;
    align-self: center;
  }
  .chart-legend {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0 14px;
  }
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
  .chart-legend {
    display: block;
  }
  .chart-legend li {
    margin: 13px 0;
  }
}

@media (max-width: 680px) {
  .distribution {
    gap: 21px;
    padding: 4px 0;
  }
  .donut-wrap {
    width: 156px;
    height: 156px;
  }
  .chart-legend li {
    font-size: 11px;
  }
}
</style>
