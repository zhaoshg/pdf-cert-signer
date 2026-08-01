<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider v-model:collapsed="collapsed" collapsible theme="dark">
      <div class="logo">签章管理</div>
      <a-menu v-model:selectedKeys="selectedKeys" theme="dark" mode="inline" @click="onMenuClick">
        <a-menu-item key="certificate">
          <file-protect-outlined />
          <span>证书管理</span>
        </a-menu-item>
        <a-menu-item key="signing">
          <edit-outlined />
          <span>PDF 签章</span>
        </a-menu-item>
        <a-menu-item key="audit">
          <audit-outlined />
          <span>审计日志</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header style="background:#fff; padding:0 24px; display:flex; justify-content:flex-end; align-items:center">
        <span>{{ auth.username }}</span>
        <a-button type="link" @click="handleLogout">退出</a-button>
      </a-layout-header>
      <a-layout-content style="margin:16px">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { FileProtectOutlined, EditOutlined, AuditOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const collapsed = ref(false)
const selectedKeys = ref<string[]>(['certificate'])

watch(() => route.path, (path) => {
  if (path.includes('certificate')) selectedKeys.value = ['certificate']
  else if (path.includes('signing')) selectedKeys.value = ['signing']
  else if (path.includes('audit')) selectedKeys.value = ['audit']
}, { immediate: true })

function onMenuClick({ key }: { key: string }) {
  router.push(`/${key}`)
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
}
</style>
