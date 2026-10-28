<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Moon, Sun } from '@lucide/vue'
import type { FormInstance, FormRules } from 'element-plus'
import AppLogo from '../components/AppLogo.vue'
import { useAuth } from '../stores/auth'
import { useTheme } from '../stores/theme'
import { clearCredentials, readCredentials, saveCredentials } from '../lib/credentials'

/** 登录页只接收账号凭据，Token 保存和失效处理统一交给认证状态模块。 */
const auth = useAuth()
const theme = useTheme()
const router = useRouter()
const route = useRoute()
const form = reactive({ username: '', password: '', remember: false })
const remembered = readCredentials()
if (remembered) {
  // 存有凭据说明上次明确勾选过，勾选状态一并恢复；主动取消勾选会立即清除存储。
  form.username = remembered.username
  form.password = remembered.password
  form.remember = true
}
watch(
  () => form.remember,
  (remember) => {
    if (!remember) clearCredentials()
  },
)
const formRef = ref<FormInstance>()
const busy = ref(false)
const error = ref('')
const rules: FormRules = {
  username: [{ required: true, whitespace: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
function hasUnsafePathCharacters(value: string) {
  return [...value].some((character) => {
    const code = character.charCodeAt(0)
    return code <= 31 || code === 127 || code === 92
  })
}
async function submit() {
  if (busy.value || !(await formRef.value?.validate().catch(() => false))) return
  busy.value = true
  error.value = ''
  try {
    await auth.login(form.username.trim(), form.password)
    if (form.remember) saveCredentials(form.username.trim(), form.password)
    else clearCredentials()
    form.password = ''
    const target = typeof route.query.redirect === 'string' ? route.query.redirect : auth.home
    // 不跳转到外部地址，也不返回已失去页面权限的旧地址。
    const local = target.startsWith('/') && !target.startsWith('//') && !hasUnsafePathCharacters(target)
    const path = local ? router.resolve(target).path : ''
    // 登录后的动态路由尚未注册，按已加载的菜单清单把路径映射回权限 key。
    const page = auth.pages.find((page) => page.path === path)
    const allowed = page
      ? (!page.adminOnly || auth.isAdmin) && auth.canPage(page.key)
      : ['/account', '/forbidden'].includes(path) && auth.canPage(path)
    await router.replace(local && allowed ? target : auth.home)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <header class="login-header">
      <AppLogo />
      <el-tooltip :content="theme.isDark ? '切换到浅色模式' : '切换到深色模式'">
        <el-button
          text
          class="icon-button"
          :icon="theme.isDark ? Sun : Moon"
          :aria-label="theme.isDark ? '切换到浅色模式' : '切换到深色模式'"
          @click="theme.toggle()"
        />
      </el-tooltip>
    </header>
    <main class="login-main">
      <section class="login-panel" aria-label="账号登录">
        <div class="login-heading">
          <h1>登录工作台</h1>
          <p>使用你的组织账号继续</p>
        </div>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="login-form"
          @submit.prevent="submit"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              name="username"
              aria-label="用户名"
              autocomplete="username"
              placeholder="请输入用户名"
              maxlength="64"
              :disabled="busy"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              name="password"
              aria-label="密码"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="请输入密码"
              maxlength="128"
              :disabled="busy"
            />
          </el-form-item>
          <div class="login-remember">
            <el-checkbox v-model="form.remember" :disabled="busy">记住密码</el-checkbox>
            <p v-if="form.remember" class="remember-hint">
              密码仅做 Base64 编码保存在此浏览器，公共设备请勿使用
            </p>
          </div>
          <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
          <el-button native-type="submit" type="primary" class="login-submit" :loading="busy">
            登录
            <ArrowRight v-if="!busy" :size="16" />
          </el-button>
        </el-form>
      </section>
    </main>
    <footer class="login-footer">
      <span>人事与组织管理</span>
      <span>内部工作系统</span>
    </footer>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  background: var(--bg-page);
}

.login-header,
.login-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 36px;
  padding-left: 36px;
}

.login-header {
  min-height: 76px;
  border-bottom: 1px solid var(--border);
}

.login-main {
  display: grid;
  flex: 1;
  place-items: center;
  padding: 48px 24px;
}

.login-panel {
  width: min(400px, 100%);
  margin-top: -32px;
}

.login-heading {
  margin-bottom: 32px;
  padding-left: 18px;
  border-left: 2px solid var(--accent);
}

.login-heading h1 {
  color: var(--text-strong);
  font-size: 29px;
  font-weight: 650;
}

.login-heading p {
  margin-top: 9px;
  color: var(--text-muted);
  font-size: 14px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 21px;
}

.login-form :deep(.el-form-item__label) {
  color: var(--text-body);
  font-size: 13px;
}

.login-panel :deep(.el-input__wrapper) {
  min-height: 46px;
  background: var(--surface);
}

.login-form :deep(input.el-input__inner) {
  height: 42px;
  background: transparent;
}

.login-remember {
  margin: -3px 0 20px;
}

.login-remember :deep(.el-checkbox) {
  height: auto;
}

.login-remember :deep(.el-checkbox__label) {
  color: var(--text-muted);
  font-size: 12px;
}

.remember-hint {
  margin: 8px 0 0 24px;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.6;
}

.login-form :deep(.el-alert) {
  margin: 0 0 16px;
}

.login-submit {
  width: 100%;
  min-height: 44px;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.login-submit.el-button > span {
  justify-content: center;
  gap: 8px;
}

.login-footer {
  padding-top: 20px;
  padding-bottom: 24px;
  color: var(--text-muted);
  font-size: 11px;
}

@media (max-width: 520px) {
  .login-header {
    min-height: 68px;
    padding-right: 20px;
    padding-left: 20px;
  }

  .login-main {
    align-items: start;
    padding: 56px 22px 40px;
  }

  .login-panel {
    margin-top: 0;
  }

  .login-heading {
    margin-bottom: 28px;
  }

  .login-heading h1 {
    font-size: 26px;
  }

  .login-footer {
    padding: 18px 22px;
  }

  .login-footer > span:last-child {
    display: none;
  }
}
</style>
