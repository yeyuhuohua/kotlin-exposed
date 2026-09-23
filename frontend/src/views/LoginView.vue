<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, LockKeyhole, Moon, Sun, Users } from '@lucide/vue'
import type { FormInstance, FormRules } from 'element-plus'
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
    await router.replace(
      target.startsWith('/') &&
        !target.startsWith('//') &&
        !target.startsWith('/login') &&
        auth.canPage(router.resolve(target).path)
        ? target
        : auth.home,
    )
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败'
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="login-page">
    <div class="login-visual">
      <img
        class="login-background"
        src="https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&amp;fit=crop&amp;w=2400&amp;q=85"
        alt="明亮的开放式办公空间"
        referrerpolicy="no-referrer"
      />
      <div class="visual-shade"></div>
      <div class="visual-brand">
        <span class="brand-icon"><Users :size="23" /></span>
        <strong>人事工作台</strong>
        <span>HR WORKSPACE</span>
      </div>
      <div class="visual-copy">
        <p class="visual-eyebrow">HR WORKSPACE</p>
        <h1>
          组织、人才与协作，
          <br />
          一处搞定。
        </h1>
        <p class="visual-sub">员工档案、组织结构、薪酬与访问控制的一体化工作台。</p>
      </div>
    </div>
    <div class="login-side">
      <el-tooltip :content="theme.isDark ? '切换到白天模式' : '切换到夜间模式'">
        <el-button
          text
          class="icon-button outlined login-theme-toggle"
          :icon="theme.isDark ? Sun : Moon"
          :aria-label="theme.isDark ? '切换到白天模式' : '切换到夜间模式'"
          @click="theme.toggle()"
        />
      </el-tooltip>
      <section class="login-panel">
        <span class="login-eyebrow">登录</span>
        <h2>欢迎回来</h2>
        <p class="login-subtitle">使用组织账号继续</p>
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
            <ArrowRight v-if="!busy" :size="18" />
          </el-button>
        </el-form>
        <div class="login-security">
          <LockKeyhole :size="14" />
          <span>组织账号 · 安全访问</span>
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
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.login-page {
  min-height: 100dvh;
  display: flex;
  background: var(--bg-page);
}

/* 左侧视觉区：整高图片 + 品牌叙事 */
.login-visual {
  flex: 1.15;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 36px 44px 44px;
  min-width: 0;
}

.login-background {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  filter: saturate(0.55);
}

.visual-shade {
  position: absolute;
  inset: 0;
  background: var(--overlay-login-shade);
}

.visual-brand {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-green-dark);
}

.visual-brand > span:last-child {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--text-medium);
  font-weight: 500;
  margin-left: 6px;
}

.visual-copy {
  position: relative;
  max-width: 520px;
}

.visual-eyebrow {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.14em;
  color: var(--green-text);
  margin-bottom: 16px;
}

.visual-copy h1 {
  font-size: 40px;
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.25;
  color: var(--text-strong);
  display: block;
}

.visual-sub {
  margin-top: 16px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-medium);
}

/* 右侧登录区：平铺暖纸面，表单无卡片边框 */
.login-side {
  flex: 1;
  min-width: 460px;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 48px 70px;
  background: var(--bg-page);
}

.login-theme-toggle {
  position: absolute;
  top: 26px;
  right: 28px;
  background: var(--surface);
}

.login-panel {
  width: 100%;
  max-width: 380px;
}

.login-eyebrow {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--green-text);
}

.login-panel h2 {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 12px 0 8px;
}

.login-subtitle {
  color: var(--text-muted);
  font-size: 14px;
}

.login-form {
  margin-top: 34px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-form :deep(.el-form-item__label) {
  font-size: 13px;
}

.login-submit {
  width: 100%;
  min-height: 46px;
  font-size: 14px;
  font-weight: 600;
  margin-top: 10px;
  justify-content: center;
  border-radius: 12px;
}

/* 文字与箭头整体居中，不用 padding 偏移的伪居中。 */
.login-submit.el-button > span {
  justify-content: center;
  gap: 8px;
}

.login-remember {
  margin: -6px 0 4px;
}

.login-remember :deep(.el-checkbox) {
  height: auto;
}

.login-remember :deep(.el-checkbox__label) {
  font-size: 13px;
  color: var(--text-muted);
}

.remember-hint {
  font-size: 11px;
  line-height: 1.6;
  color: var(--text-faint);
  margin: 7px 0 0 24px;
}

.login-security {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: var(--text-pale);
  font-size: 12px;
  margin-top: 34px;
  padding-top: 22px;
  border-top: 1px solid var(--border);
}

.login-footer {
  position: absolute;
  bottom: 24px;
  left: 48px;
  right: 48px;
  display: flex;
  justify-content: space-between;
  color: var(--text-pale);
  font-size: 11px;
  letter-spacing: 0.06em;
}

.login-panel :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 12px;
}

.login-panel :deep(.el-input__inner) {
  font-size: 14px;
}

.login-form :deep(input.el-input__inner) {
  height: 42px;
  background: transparent;
}

.login-form :deep(.el-alert) {
  margin-top: 4px;
  border-radius: 10px;
}

/* 窄屏：视觉区收为顶部横幅，登录区铺满 */
@media (max-width: 1100px) {
  .login-visual {
    flex: 1;
    padding: 30px 32px 36px;
  }
  .visual-copy h1 {
    font-size: 32px;
  }
  .login-side {
    min-width: 420px;
    padding: 70px 36px 64px;
  }
}

@media (max-width: 900px) {
  .login-page {
    flex-direction: column;
  }
  .login-visual {
    flex: none;
    min-height: 300px;
    padding: 24px 24px 28px;
  }
  .visual-copy h1 {
    font-size: 26px;
  }
  .visual-copy h1 br {
    display: none;
  }
  .visual-sub {
    font-size: 13px;
    margin-top: 10px;
  }
  .visual-eyebrow {
    margin-bottom: 10px;
  }
  .login-side {
    min-width: 0;
    flex: 1;
    justify-content: flex-start;
    padding: 40px 24px 80px;
  }
  .login-theme-toggle {
    top: 18px;
    right: 18px;
  }
  .login-footer {
    left: 24px;
    right: 24px;
  }
  .login-footer > span:last-child {
    display: none;
  }
}

@media (max-width: 680px) {
  .login-visual {
    min-height: 240px;
  }
  .visual-brand strong {
    font-size: 15px;
  }
  .visual-brand > span:last-child {
    display: none;
  }
  .login-panel h2 {
    font-size: 24px;
  }
}
</style>
