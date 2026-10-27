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
          borderWidth: 3,
          hoverOffset: 3,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '74%',
      animation: { duration: 400 },
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
.distribution {
  display: flex;
  align-items: center;
  gap: 24px;
  min-height: 220px;
}

.donut-wrap {
  position: relative;
  width: 168px;
  height: 168px;
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
  color: var(--text-strong);
  font-size: 28px;
  font-weight: 650;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}

.donut-center span {
  margin-top: 4px;
  color: var(--text-faint);
  font-size: 11px;
}

.chart-legend {
  flex: 1;
  min-width: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.chart-legend li {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  margin: 11px 0;
  color: var(--text-muted);
  font-size: 12px;
}

.chart-legend i {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border-radius: 2px;
}

.chart-legend li > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chart-legend strong {
  margin-left: auto;
  color: var(--text-soft);
  font-size: 12px;
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1200px) {
  .distribution {
    flex-direction: column;
    align-items: stretch;
    gap: 14px;
  }

  .donut-wrap {
    width: 150px;
    height: 150px;
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

@media (max-width: 1000px) {
  .distribution {
    flex-direction: row;
    gap: 32px;
    min-height: 190px;
  }

  .donut-wrap {
    width: 170px;
    height: 170px;
  }

  .chart-legend {
    display: block;
  }

  .chart-legend li {
    margin: 10px 0;
  }
}

@media (max-width: 680px) {
  .distribution {
    gap: 20px;
  }

  .donut-wrap {
    width: 148px;
    height: 148px;
  }

  .chart-legend li {
    font-size: 11px;
  }
}
</style>
