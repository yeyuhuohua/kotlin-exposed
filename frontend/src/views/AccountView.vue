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
const pageCount = computed(() => auth.user?.permissions?.filter((code) => code.startsWith('page:')).length || 0)
const apiCount = computed(() => auth.user?.permissions?.filter((code) => code.startsWith('api:')).length || 0)
</script>

<template>
  <section>
    <PageHeader
      title="我的账号"
      description="当前登录账号与角色的有效权限摘要"
      category="个人账户"
    />
    <div class="account-layout">
      <DataPanel class="profile-panel">
        <div class="profile-summary">
          <span class="avatar large">{{ initials(auth.user?.username || '') }}</span>
          <div class="profile-copy">
            <h2>{{ auth.user?.username }}</h2>
            <p>账号 #{{ auth.user?.id }}</p>
            <div class="profile-tags">
              <span :class="['badge', auth.isAdmin ? 'admin' : 'neutral']">{{ auth.user?.roleCode }}</span>
              <el-tag :type="auth.user?.enabled ? 'success' : 'info'" size="small">
                {{ auth.user?.enabled ? '已启用' : '已停用' }}
              </el-tag>
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
      <DataPanel title="账号详情" subtitle="登录身份与授权范围">
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
  grid-template-columns: 310px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

.profile-summary {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30px 24px 26px;
  text-align: center;
}

.profile-summary > .avatar.large {
  width: 68px;
  height: 68px;
  border-radius: 17px;
}

.profile-copy {
  min-width: 0;
  margin-top: 16px;
}

.profile-copy h2 {
  overflow-wrap: anywhere;
  color: var(--text-strong);
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.profile-copy > p {
  margin-top: 4px;
  color: var(--text-faint);
  font-size: 12px;
}

.profile-tags {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.profile-permissions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-top: 1px solid var(--border);
  background: var(--surface-subtle);
}

.profile-permissions > div {
  padding: 18px 12px;
  text-align: center;
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
  font-size: 22px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.profile-permissions span {
  margin-top: 4px;
  color: var(--text-faint);
  font-size: 11px;
}

.account-details {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 22px 24px;
  padding: 22px;
  border-top: 0;
}

@media (max-width: 1000px) {
  .account-layout {
    grid-template-columns: 280px minmax(0, 1fr);
    gap: 18px;
  }
}

@media (max-width: 800px) {
  .account-layout {
    grid-template-columns: 1fr;
  }

  .profile-summary {
    flex-direction: row;
    gap: 18px;
    padding: 22px;
    text-align: left;
  }

  .profile-copy {
    margin-top: 0;
  }

  .profile-tags {
    justify-content: flex-start;
  }
}

@media (max-width: 680px) {
  .account-details {
    grid-template-columns: 1fr;
    padding: 18px;
  }

  .profile-summary {
    gap: 14px;
    padding: 18px;
  }

  .profile-summary > .avatar.large {
    width: 56px;
    height: 56px;
    border-radius: 14px;
  }

  .profile-copy h2 {
    font-size: 18px;
  }
}
</style>
