<script setup lang="ts">
/** 显示当前账号及其角色的有效权限摘要，不提供账号级授权编辑。 */
import { computed } from 'vue'
import { ArrowRight, ShieldCheck, UserRound } from '@lucide/vue'
import DataPanel from '../components/DataPanel.vue'
import PageHeader from '../components/PageHeader.vue'
import { rolesPaths } from '../api/paths'
import { useAuth } from '../stores/auth'
import { pagePath } from '../lib/pages'
import { initials } from '../lib/format'
const auth = useAuth()
const usersTo = computed(() => pagePath(auth.pages, 'users'))
const pageCount = computed(
  () => auth.user?.permissions?.filter((code) => code.startsWith('page:')).length || 0,
)
const apiCount = computed(() => auth.user?.permissions?.filter((code) => code.startsWith('api:')).length || 0)
</script>

<template>
  <section>
    <PageHeader title="我的账号" description="登录身份、账号状态与有效权限" />
    <div class="account-layout">
      <DataPanel class="profile-panel">
        <div class="profile-summary">
          <span class="avatar large">{{ initials(auth.user?.username || '') }}</span>
          <div class="profile-copy">
            <h2>{{ auth.user?.username }}</h2>
            <p>账号 #{{ auth.user?.id }}</p>
            <div class="profile-tags">
              <span :class="['badge', auth.isAdmin ? 'admin' : 'neutral']">{{ auth.user?.roleCode }}</span>
              <span :class="['badge', auth.user?.enabled ? 'success' : 'neutral']">
                {{ auth.user?.enabled ? '已启用' : '已停用' }}
              </span>
            </div>
          </div>
        </div>
        <div class="profile-permissions">
          <div>
            <strong>{{ pageCount }}</strong>
            <span>可访问页面</span>
          </div>
          <div>
            <strong>{{ apiCount }}</strong>
            <span>可用接口</span>
          </div>
        </div>
      </DataPanel>
      <DataPanel title="账号与权限" subtitle="当前登录身份及角色的有效授权">
        <dl class="detail-grid account-details">
          <div>
            <dt>账号编号</dt>
            <dd>#{{ auth.user?.id }}</dd>
          </div>
          <div>
            <dt>当前角色</dt>
            <dd>{{ auth.user?.roleCode }}{{ auth.isAdmin ? ' · 管理员' : '' }}</dd>
          </div>
          <div>
            <dt>有效权限</dt>
            <dd>
              <UserRound :size="16" />
              {{ pageCount }} 个页面 · {{ apiCount }} 个接口
            </dd>
          </div>
          <div>
            <dt>账号管理</dt>
            <dd>
              <ShieldCheck :size="16" />
              {{ auth.canApi('PUT', rolesPaths.permissions) ? '可管理角色权限' : '不可管理角色权限' }}
            </dd>
          </div>
        </dl>
        <template v-if="auth.canPage('users') && usersTo" #footer>
          <RouterLink :to="usersTo" class="text-button">
            管理用户
            <ArrowRight :size="14" />
          </RouterLink>
        </template>
      </DataPanel>
    </div>
  </section>
</template>

<style scoped>
.account-layout {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.profile-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 26px 22px;
}

.profile-summary > .avatar.large {
  width: 56px;
  height: 56px;
}

.profile-copy {
  min-width: 0;
}

.profile-copy h2 {
  color: var(--text-strong);
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.02em;
  overflow-wrap: anywhere;
}

.profile-copy > p {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
}

.profile-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 11px;
}

.profile-permissions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-top: 1px solid var(--border);
}

.profile-permissions > div {
  padding: 20px 22px;
}

.profile-permissions > div + div {
  border-left: 1px solid var(--border);
}

.profile-permissions strong,
.profile-permissions span {
  display: block;
}

.profile-permissions strong {
  color: var(--text-strong);
  font-size: 24px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.profile-permissions span {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 11px;
}

.account-details {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 28px 32px;
  padding: 28px 24px 30px;
  border-top: 0;
}

@media (max-width: 900px) {
  .account-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .account-details {
    grid-template-columns: 1fr;
    gap: 22px;
    padding: 24px 20px;
  }

  .profile-summary {
    padding: 22px 18px;
  }

  .profile-permissions > div {
    padding: 18px;
  }
}
</style>
