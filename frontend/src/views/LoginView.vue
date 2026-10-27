<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, LockKeyhole, Moon, Sun } from '@lucide/vue'
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
    <div class="login-shell">
      <div class="login-topbar">
        <AppLogo />
        <el-tooltip :content="theme.isDark ? '切换到浅色模式' : '切换到深色模式'">
          <el-button
            text
            class="icon-button outlined"
            :icon="theme.isDark ? Sun : Moon"
            :aria-label="theme.isDark ? '切换到浅色模式' : '切换到深色模式'"
            @click="theme.toggle()"
          />
        </el-tooltip>
      </div>
      <section class="login-card" aria-label="账号登录">
        <div class="login-panel">
          <h2>欢迎回来</h2>
          <p class="login-subtitle">登录人事工作台，继续你的工作</p>
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
                placeholder="输入用户名"
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
                placeholder="输入密码"
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
              <ArrowRight v-if="!busy" :size="17" />
            </el-button>
          </el-form>
          <div class="login-security">
            <LockKeyhole :size="14" />
            <span>组织账号 · 安全访问</span>
          </div>
        </div>
      </section>
      <footer class="login-footer">
        <span>HR / 人事与组织管理</span>
        <span>HR WORKSPACE</span>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100dvh;
  display: flex;
  padding: 32px;
  background: var(--bg-page);
}

.login-shell {
  width: min(460px, 100%);
  margin: auto;
}

.login-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.login-card {
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--panel-shadow);
}

.login-panel {
  padding: 36px 38px;
}

.login-panel h2 {
  font-size: 27px;
  font-weight: 650;
  letter-spacing: -0.035em;
}

.login-subtitle {
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 14px;
}

.login-form {
  margin-top: 30px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-form :deep(.el-form-item__label) {
  font-size: 13px;
}

.login-panel :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 10px;
}

.login-form :deep(input.el-input__inner) {
  height: 42px;
  background: transparent;
}

.login-remember {
  margin: -5px 0 4px;
}

.login-remember :deep(.el-checkbox) {
  height: auto;
}

.login-remember :deep(.el-checkbox__label) {
  color: var(--text-muted);
  font-size: 13px;
}

.remember-hint {
  margin: 7px 0 0 24px;
  color: var(--text-faint);
  font-size: 11px;
  line-height: 1.6;
}

.login-form :deep(.el-alert) {
  margin-top: 4px;
  border-radius: 10px;
}

.login-submit {
  width: 100%;
  min-height: 46px;
  justify-content: center;
  margin-top: 10px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
}

.login-submit.el-button > span {
  justify-content: center;
  gap: 8px;
}

.login-security {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
  color: var(--text-faint);
  font-size: 12px;
}

.login-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 18px;
  color: var(--text-faint);
  font-size: 11px;
}

@media (max-width: 520px) {
  .login-page {
    padding: 20px 16px;
  }

  .login-topbar {
    margin-bottom: 16px;
  }

  .login-panel {
    padding: 30px 22px;
  }

  .login-panel h2 {
    font-size: 25px;
  }

  .login-form {
    margin-top: 26px;
  }

  .login-footer > span:last-child {
    display: none;
  }
}
</style>
