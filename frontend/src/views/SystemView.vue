<script setup lang="ts">
/** 展示数据库和 Redis 的实际健康状态，保留依赖故障时返回的诊断数据。 */
import { onMounted, ref } from 'vue'
import { Activity, ArrowUpRight, Database, RefreshCw, Server, Workflow } from '@lucide/vue'
import DataPanel from '../components/DataPanel.vue'
import PageHeader from '../components/PageHeader.vue'
import StateBlock from '../components/StateBlock.vue'
import { docsHref, docsPaths, systemPaths } from '../api/paths'
import { api } from '../lib/api'
import { useResource } from '../composables/useResource'
import type { Health } from '../types'
const updated = ref('')
const { data, loading, error, refresh } = useResource(async (signal) => {
  const result = await api<Health>(systemPaths.health, { signal, acceptUnavailable: true })
  updated.value = new Date().toLocaleString('zh-CN')
  return result
})
onMounted(refresh)
const docLinks = [
  { label: 'Swagger API', detail: '交互式接口调试', path: docsPaths.swagger },
  { label: 'Knife4j 文档', detail: '分组接口文档', path: docsPaths.knife4j },
  { label: 'OpenAPI JSON', detail: '运行时接口定义', path: docsPaths.openapi },
]
</script>

<template>
  <section>
    <PageHeader
      title="系统状态"
      description="数据库与缓存服务的实时连通状态"
      category="系统管理"
    >
      <el-button :icon="RefreshCw" :loading="loading" @click="refresh">刷新状态</el-button>
    </PageHeader>
    <StateBlock :loading="loading" :error="error" @retry="refresh" />
    <template v-if="data && !loading">
      <section :class="['status-hero', { unhealthy: data.status !== 'UP' }]">
        <span class="status-icon"><Activity :size="26" /></span>
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
        <DataPanel
          v-for="service in [
            { name: 'MySQL', status: data.database, icon: Database, detail: 'atguigudb 数据库' },
            { name: 'Redis', status: data.redis, icon: Workflow, detail: '缓存与失效广播' },
          ]"
          :key="service.name"
        >
          <div class="service-content">
            <span class="service-icon"><component :is="service.icon" :size="22" /></span>
            <div class="service-copy">
              <h3>{{ service.name }}</h3>
              <p>{{ service.detail }}</p>
            </div>
            <el-tag :type="service.status === 'UP' ? 'success' : 'danger'" size="small" class="service-tag">
              <i class="status-dot"></i>
              {{ service.status === 'UP' ? '在线' : '离线' }}
            </el-tag>
          </div>
        </DataPanel>
      </div>
    </template>
    <DataPanel title="开发资源" subtitle="接口与调试入口">
      <div class="docs-grid">
        <a
          v-for="link in docLinks"
          :key="link.path"
          :href="docsHref(link.path)"
          target="_blank"
          rel="noopener"
          class="doc-link"
        >
          <span class="doc-icon"><Server :size="17" /></span>
          <span class="doc-copy">
            <strong>{{ link.label }}</strong>
            <small>{{ link.detail }}</small>
          </span>
          <ArrowUpRight :size="16" class="doc-arrow" />
        </a>
      </div>
    </DataPanel>
  </section>
</template>

<style scoped>
.status-hero {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 24px;
  padding: 22px 24px;
  border: 1px solid var(--border-success);
  border-radius: var(--card-radius);
  background: var(--status-up-soft);
}

.status-hero.unhealthy {
  border-color: var(--warn-soft);
  background: var(--warn-tint);
}

.status-icon {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border: 1px solid var(--border-success);
  border-radius: 12px;
  background: var(--surface);
  color: var(--status-ok-text);
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
  margin-bottom: 6px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
}

.status-hero h2 {
  color: var(--status-ok-text);
  font-size: 17px;
  font-weight: 650;
}

.status-hero.unhealthy h2 {
  color: var(--warn);
}

.status-hero p {
  margin-top: 5px;
  color: var(--text-muted);
  font-size: 11px;
}

.status-tag {
  flex-shrink: 0;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  margin-bottom: 24px;
}

.service-content {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 22px;
}

.service-icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--accent-tint);
  color: var(--accent-text);
}

.service-copy {
  min-width: 0;
}

.service-copy h3 {
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 600;
}

.service-copy p {
  margin-top: 4px;
  color: var(--text-faint);
  font-size: 11px;
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

.docs-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  padding: 0 22px 22px;
}

.doc-link {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface-subtle);
  transition:
    border-color 0.15s,
    background 0.15s;
}

.doc-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 9px;
  background: var(--accent-tint);
  color: var(--accent-text);
}

.doc-copy {
  min-width: 0;
}

.doc-copy strong,
.doc-copy small {
  display: block;
}

.doc-copy strong {
  color: var(--text-body);
  font-size: 13px;
  font-weight: 550;
}

.doc-copy small {
  margin-top: 3px;
  color: var(--text-faint);
  font-size: 11px;
}

.doc-arrow {
  margin-left: auto;
  flex-shrink: 0;
  color: var(--text-faint);
}

.doc-link:hover {
  border-color: var(--border-accent-strong);
  background: var(--accent-tint);
}

@media (max-width: 1000px) {
  .service-grid,
  .docs-grid {
    gap: 14px;
  }
}

@media (max-width: 800px) {
  .service-grid,
  .docs-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .status-hero {
    gap: 12px;
    padding: 18px 16px;
  }

  .status-icon {
    width: 42px;
    height: 42px;
  }

  .status-hero h2 {
    font-size: 15px;
  }

  .status-tag {
    display: none;
  }

  .service-content {
    padding: 18px;
  }

  .docs-grid {
    padding: 0 16px 18px;
  }
}
</style>
