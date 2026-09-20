<script setup lang="ts">
/** 展示数据库和 Redis 的实际健康状态，保留依赖故障时返回的诊断数据。 */
import { onMounted, ref } from 'vue'
import { Activity, ArrowUpRight, Database, RefreshCw, Server, Workflow } from '@lucide/vue'
import { api } from '../lib/api'
import { useResource } from '../composables/useResource'
import StateBlock from '../components/StateBlock.vue'
import type { Health } from '../types'
const updated = ref('')
const { data, loading, error, refresh } = useResource(async (signal) => {
  const result = await api<Health>('/health', { signal, acceptUnavailable: true })
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
          { label: 'Swagger API', path: '/swagger' },
          { label: 'Knife4j 文档', path: '/doc.html' },
          { label: 'OpenAPI JSON', path: '/v3/api-docs' },
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
