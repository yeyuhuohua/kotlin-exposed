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
      </div>
      <el-button class="button secondary" :disabled="loading" @click="refresh">
        <RefreshCw :size="16" :class="{ spin: loading }" />
        刷新状态
      </el-button>
    </header>
    <StateBlock :loading="loading" :error="error" @retry="refresh" />
    <template v-if="data && !loading">
      <div :class="['system-status', { unhealthy: data.status !== 'UP' }]">
        <Activity :size="28" />
        <div>
          <h2>{{ data.status === 'UP' ? '所有服务运行正常' : '部分服务不可用' }}</h2>
          <p>最近检查 {{ updated }}</p>
        </div>
        <el-tag :type="data.status === 'UP' ? 'success' : 'danger'">{{ data.status }}</el-tag>
      </div>
      <div class="service-list">
        <div
          v-for="service in [
            { name: 'MySQL', status: data.database, icon: Database, detail: 'atguigudb' },
            { name: 'Redis', status: data.redis, icon: Workflow, detail: '缓存服务' },
          ]"
          :key="service.name"
        >
          <component :is="service.icon" :size="24" />
          <div>
            <h3>{{ service.name }}</h3>
            <p>{{ service.detail }}</p>
          </div>
          <el-tag :type="service.status === 'UP' ? 'success' : 'danger'">
            <i class="status-dot"></i>
            {{ service.status === 'UP' ? '在线' : '离线' }}
          </el-tag>
        </div>
      </div>
    </template>
    <section class="documentation-links">
      <h2>开发资源</h2>
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
      >
        <Server :size="17" />
        {{ link.label }}
        <ArrowUpRight :size="17" />
      </a>
    </section>
  </section>
</template>

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.service-list {
  margin: 25px 0 45px;
  border-top: 1px solid var(--border);
}

.service-list > div {
  display: flex;
  gap: 19px;
  align-items: center;
  padding: 23px 12px;
  border-bottom: 1px solid var(--border);
  color: var(--text-dim);
}

.service-list h3 {
  color: var(--text-body);
  font-size: 14px;
}

.service-list p {
  font-size: 11px;
  margin-top: 5px;
  color: var(--text-faint);
}

.service-list .badge {
  margin-left: auto;
}

.documentation-links {
  max-width: 600px;
}

.documentation-links h2 {
  margin-bottom: 15px;
}

.documentation-links > a {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 16px 0;
  font-size: 12px;
  border-bottom: 1px solid var(--border);
  color: var(--text-muted);
}

.documentation-links > a svg:last-child {
  margin-left: auto;
}

@media (max-width: 680px) {
  .documentation-links {
    margin-top: 28px;
  }
}
.system-status {
  display: flex;
  align-items: center;
  gap: 17px;
  background: var(--green-tint);
  padding: 23px;
  color: var(--status-ok-text);
  border: 1px solid var(--border-green);
  border-radius: 6px;
}
.system-status p {
  font-size: 11px;
  color: var(--text-dim);
  margin-top: 4px;
}
.system-status > .badge {
  margin-left: auto;
}
.system-status.unhealthy {
  background: var(--warn-tint);
  color: var(--warn);
  border-color: var(--warn-soft);
}
@media (max-width: 680px) {
  .system-status {
    padding: 18px 14px;
    gap: 12px;
  }
}
@media (max-width: 680px) {
  .system-status h2 {
    font-size: 14px;
  }
}
@media (max-width: 680px) {
  .system-status p {
    font-size: 9px;
  }
}
@media (max-width: 680px) {
  .system-status > .badge {
    display: none;
  }
}
</style>
