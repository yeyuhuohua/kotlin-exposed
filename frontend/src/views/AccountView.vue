<script setup lang="ts">
/** 显示当前账号及其角色的有效权限摘要，不提供账号级授权编辑。 */
import { ShieldCheck, UserRound } from '@lucide/vue'
import { rolesPaths } from '../api/paths'
import { useAuth } from '../stores/auth'
import { initials } from '../lib/format'
const auth = useAuth()
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <p class="eyebrow">MY ACCOUNT</p>
        <h1>我的账号</h1>
        <p class="page-subtitle">当前登录账号与角色的有效权限摘要</p>
      </div>
    </header>
    <div class="account-layout">
      <section class="profile-card">
        <span class="avatar large">{{ initials(auth.user?.username || '') }}</span>
        <div class="profile-copy">
          <h2>{{ auth.user?.username }}</h2>
          <div class="profile-tags">
            <span :class="['badge', auth.isAdmin ? 'admin' : 'neutral']">{{ auth.user?.roleCode }}</span>
            <el-tag :type="auth.user?.enabled ? 'success' : 'info'" size="small">
              {{ auth.user?.enabled ? '已启用' : '已停用' }}
            </el-tag>
          </div>
        </div>
      </section>
      <section class="info-card">
        <header class="section-heading">
          <div>
            <h2>账号详情</h2>
            <span>ACCOUNT DETAILS</span>
          </div>
        </header>
        <dl class="detail-grid">
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
              {{ auth.user?.permissions?.filter((code) => code.startsWith('page:')).length || 0 }} 个页面 ·
              {{ auth.user?.permissions?.filter((code) => code.startsWith('api:')).length || 0 }} 个接口
            </dd>
          </div>
          <div>
            <dt>账号管理</dt>
            <dd>
              <ShieldCheck :size="16" />
              {{ auth.canApi('PUT', rolesPaths.permissions) ? '可管理角色权限' : '无权限修改权' }}
            </dd>
          </div>
        </dl>
        <div v-if="auth.canPage('users')" class="card-foot">
          <RouterLink v-if="auth.canPage('users')" to="/users" class="button secondary">管理用户</RouterLink>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.account-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}

.profile-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--card-radius);
  padding: 34px 26px 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.profile-copy {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 18px;
  min-width: 0;
}

.profile-card h2 {
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--text-strong);
  overflow-wrap: anywhere;
}

.profile-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 12px;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  font-size: 11px;
  font-weight: 550;
  border-radius: 8px;
  line-height: 1.35;
  white-space: nowrap;
}

.badge.neutral {
  background: var(--surface-neutral);
  color: var(--text-muted);
}

.badge.admin {
  background: var(--info-soft);
  color: var(--info-deep);
}

.info-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--card-radius);
  padding: 24px 26px 26px;
  min-width: 0;
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

.card-foot {
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--border);
  padding-top: 20px;
  margin-top: 22px;
}

@media (max-width: 1200px) {
  .account-layout {
    grid-template-columns: 280px minmax(0, 1fr);
    gap: 18px;
  }
  .profile-card,
  .info-card {
    padding: 20px;
  }
  .profile-copy {
    margin-top: 14px;
  }
}

@media (max-width: 900px) {
  .account-layout {
    grid-template-columns: 1fr;
  }
  .profile-card {
    flex-direction: row;
    align-items: center;
    text-align: left;
    gap: 18px;
    padding: 22px;
  }
  .profile-copy {
    align-items: flex-start;
    margin-top: 0;
  }
  .profile-tags {
    justify-content: flex-start;
  }
}

@media (max-width: 680px) {
  .info-card .detail-grid {
    grid-template-columns: 1fr;
  }
  .profile-card {
    padding: 18px;
    gap: 14px;
  }
  .profile-card h2 {
    font-size: 17px;
  }
}
</style>
