<script setup lang="ts">
/** 展示数据库和 Redis 的实际健康状态，保留依赖故障时返回的诊断数据。 */
import { onMounted, ref } from 'vue'
import { Activity, ArrowUpRight, Database, RefreshCw, Server, Workflow } from '@lucide/vue'
import { docsPaths, systemPaths } from '../api/paths'
import { api } from '../lib/api'
import { useResource } from '../composables/useResource'
import StateBlock from '../components/StateBlock.vue'
import type { Health } from '../types'
const updated = ref('')
const { data, loading, error, refresh } = useResource(async (signal) => {
  const result = await api<Health>(systemPaths.health, { signal, acceptUnavailable: true })
  updated.value = new Date().toLocaleString('zh-CN')
  return result
})
onMounted(refresh)
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <p class="eyebrow">SYSTEM STATUS</p>
        <h1>系统状态</h1>
        <p class="page-subtitle">数据库与缓存服务的实时连通状态</p>
      </div>
      <div class="heading-actions">
        <el-button class="button secondary" :disabled="loading" @click="refresh">
          <RefreshCw :size="16" :class="{ spin: loading }" />
          刷新状态
        </el-button>
      </div>
    </header>
    <StateBlock :loading="loading" :error="error" @retry="refresh" />
    <template v-if="data && !loading">
      <section :class="['status-hero', { unhealthy: data.status !== 'UP' }]">
        <span class="status-icon"><Activity :size="28" /></span>
        <div class="status-copy">
          <span class="status-eyebrow">整体状态</span>
          <h2>{{ data.status === 'UP' ? '所有服务运行正常' : '部分服务不可用' }}</h2>
          <p>最近检查 {{ updated }}</p>
        </div>
        <el-tag :type="data.status === 'UP' ? 'success' : 'danger'" class="status-tag">
          {{ data.status }}
        </el-tag>
      </section>
      <div class="service-grid">
        <section
          v-for="service in [
            { name: 'MySQL', status: data.database, icon: Database, detail: 'atguigudb' },
            { name: 'Redis', status: data.redis, icon: Workflow, detail: '缓存服务' },
          ]"
          :key="service.name"
          class="service-card"
        >
          <span class="service-icon"><component :is="service.icon" :size="24" /></span>
          <div class="service-copy">
            <h3>{{ service.name }}</h3>
            <p>{{ service.detail }}</p>
          </div>
          <el-tag :type="service.status === 'UP' ? 'success' : 'danger'" size="small" class="service-tag">
            <i class="status-dot"></i>
            {{ service.status === 'UP' ? '在线' : '离线' }}
          </el-tag>
        </section>
      </div>
    </template>
    <section class="docs-card">
      <header class="section-heading">
        <div>
          <h2>开发资源</h2>
          <span>DEVELOPER RESOURCES</span>
        </div>
      </header>
      <div class="docs-grid">
        <a
          v-for="link in [
            { label: 'Swagger API', path: docsPaths.swagger },
            { label: 'Knife4j 文档', path: docsPaths.knife4j },
            { label: 'OpenAPI JSON', path: docsPaths.openapi },
          ]"
          :key="link.path"
          :href="link.path"
          target="_blank"
          rel="noopener"
          class="doc-link"
        >
          <Server :size="17" />
          {{ link.label }}
          <ArrowUpRight :size="17" />
        </a>
      </div>
    </section>
  </section>
</template>

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.spin {
  animation: spin 0.8s linear infinite;
}

.status-hero {
  display: flex;
  align-items: center;
  gap: 18px;
  background: var(--green-tint);
  border: 1px solid var(--border-green);
  border-radius: var(--card-radius);
  padding: 24px 26px;
  margin-bottom: 22px;
}

.status-hero.unhealthy {
  background: var(--warn-tint);
  border-color: var(--warn-soft);
}

.status-icon {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: var(--surface);
  border: 1px solid var(--border-green);
  color: var(--status-ok-text);
  flex-shrink: 0;
}

.status-hero.unhealthy .status-icon {
  border-color: var(--warn-soft);
  color: var(--warn);
}

.status-copy {
  flex: 1;
  min-width: 0;
}

.status-eyebrow {
  display: block;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-dim);
  margin-bottom: 7px;
}

.status-hero h2 {
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--status-ok-text);
}

.status-hero.unhealthy h2 {
  color: var(--warn);
}

.status-hero p {
  font-size: 11px;
  color: var(--text-dim);
  margin-top: 5px;
}

.status-tag {
  flex-shrink: 0;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 22px;
  margin-bottom: 22px;
}

.service-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--card-radius);
  padding: 22px 24px;
  min-width: 0;
}

.service-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--surface-sunken);
  color: var(--text-dim);
  flex-shrink: 0;
}

.service-copy {
  min-width: 0;
}

.service-copy h3 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-strong);
}

.service-copy p {
  font-size: 11px;
  color: var(--text-faint);
  margin-top: 4px;
}

.service-tag {
  margin-left: auto;
  flex-shrink: 0;
}

.status-dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.docs-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--card-radius);
  padding: 24px 26px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}

.section-heading h2 {
  font-size: 15px;
}

.section-heading > div > span {
  display: block;
  color: var(--text-pale);
  font-size: 10px;
  letter-spacing: 0.1em;
  margin-top: 5px;
}

.docs-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.doc-link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-radius: 12px;
  font-size: 13px;
  font-weight: 550;
  color: var(--text-body);
  transition:
    border-color 0.15s,
    background 0.15s,
    color 0.15s;
}

.doc-link svg {
  color: var(--text-dim);
}

.doc-link svg:last-child {
  margin-left: auto;
  color: var(--text-faint);
}

.doc-link:hover {
  border-color: var(--border-green-strong);
  background: var(--green-tint);
  color: var(--green-text);
}

.doc-link:hover svg {
  color: var(--green-text);
}

@media (max-width: 1200px) {
  .status-hero,
  .service-card,
  .docs-card {
    padding: 20px;
  }
  .service-grid,
  .docs-grid {
    gap: 14px;
  }
}

@media (max-width: 900px) {
  .service-grid,
  .docs-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .status-hero {
    padding: 18px 16px;
    gap: 12px;
  }
  .status-icon {
    width: 44px;
    height: 44px;
  }
  .status-hero h2 {
    font-size: 15px;
  }
  .status-hero p {
    font-size: 10px;
  }
  .status-tag {
    display: none;
  }
  .service-card {
    padding: 18px;
  }
}
</style>
