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
      </div>
    </header>
    <section class="account-profile">
      <div class="profile-heading">
        <span class="avatar large">{{ initials(auth.user?.username || '') }}</span>
        <div>
          <h2>{{ auth.user?.username }}</h2>
          <el-tag :type="auth.user?.enabled ? 'success' : 'info'" size="small">
            {{ auth.user?.enabled ? '已启用' : '已停用' }}
          </el-tag>
        </div>
      </div>
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
      <RouterLink v-if="auth.canPage('users')" to="/users" class="button secondary">管理用户</RouterLink>
    </section>
  </section>
</template>

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.account-profile {
  max-width: 750px;
  padding: 28px 0;
}

.account-profile .detail-grid {
  margin-bottom: 28px;
}

@media (max-width: 680px) {
  .account-profile .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
