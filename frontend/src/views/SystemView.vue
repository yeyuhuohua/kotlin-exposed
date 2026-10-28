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
    <PageHeader title="系统状态" description="数据库与缓存服务的实时连通状态">
      <el-button :icon="RefreshCw" :loading="loading" @click="refresh">刷新状态</el-button>
    </PageHeader>
    <StateBlock :loading="loading" :error="error" @retry="refresh" />
    <template v-if="data && !loading">
      <section :class="['status-summary', { unhealthy: data.status !== 'UP' }]">
        <span class="status-icon"><Activity :size="24" /></span>
        <div class="status-copy">
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
            <span class="service-icon"><component :is="service.icon" :size="21" /></span>
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
    <DataPanel title="开发资源" subtitle="接口文档与调试入口">
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
          <ArrowUpRight :size="15" class="doc-arrow" />
        </a>
      </div>
    </DataPanel>
  </section>
</template>

<style scoped>
.status-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding: 21px 22px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--status-up);
  border-radius: var(--card-radius);
  background: var(--surface);
}

.status-summary.unhealthy {
  border-left-color: var(--warn);
}

.status-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  color: var(--status-ok-text);
}

.status-summary.unhealthy .status-icon {
  color: var(--warn);
}

.status-copy {
  flex: 1;
  min-width: 0;
}

.status-summary h2 {
  color: var(--text-strong);
  font-size: 16px;
  font-weight: 600;
}

.status-summary p {
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
  gap: 20px;
  margin-bottom: 24px;
}

.service-content {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 22px;
}

.service-icon,
.doc-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  color: var(--text-muted);
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
  color: var(--text-muted);
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
  margin-right: 5px;
  border-radius: 50%;
  background: currentColor;
}

.docs-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.doc-link {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 22px;
  transition: background 0.15s;
}

.doc-link + .doc-link {
  border-left: 1px solid var(--border);
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
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 11px;
}

.doc-arrow {
  margin-left: auto;
  flex-shrink: 0;
  color: var(--text-muted);
}

.doc-link:hover {
  background: var(--surface-subtle);
}

.doc-link:hover .doc-arrow {
  color: var(--accent-text);
}

@media (max-width: 800px) {
  .service-grid,
  .docs-grid {
    grid-template-columns: 1fr;
  }

  .service-grid {
    gap: 16px;
  }

  .doc-link + .doc-link {
    border-top: 1px solid var(--border);
    border-left: 0;
  }
}

@media (max-width: 680px) {
  .status-summary {
    gap: 12px;
    padding: 18px 16px;
  }

  .status-summary h2 {
    font-size: 15px;
  }

  .status-tag {
    display: none;
  }

  .service-content,
  .doc-link {
    padding: 18px;
  }
}
</style>
