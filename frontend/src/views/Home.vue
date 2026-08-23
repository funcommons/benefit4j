<template>
  <div class="home-page">
    <div class="home-card">
      <img class="home-logo" :src="oem.config.logoUrl || '/logo.svg'" :alt="oem.config.companyName" />
      <h1 class="home-title">{{ oem.config.companyName || '视觉统一手脚架' }}</h1>
      <p v-if="oem.config.subtitle" class="home-subtitle">{{ oem.config.subtitle }}</p>
      <p class="home-hint">欢迎, {{ userStore.userName || '用户' }}。请通过左侧菜单进入开发者演示页面。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useOemStore } from '@/store/oem'
import { useUserStore } from '@/store/user'

defineOptions({ name: 'HomePage' })

const oem = useOemStore()
const userStore = useUserStore()
</script>

<style scoped lang="scss">
.home-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 80px);
  padding: 24px;
}

.home-card {
  width: 100%;
  max-width: 480px;
  text-align: center;
  padding: 48px 32px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.04);
}

.home-logo {
  width: 64px;
  height: 64px;
  margin: 0 auto 20px;
  display: block;
}

.home-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.home-subtitle {
  margin: 0 0 24px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.home-hint {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}
</style>