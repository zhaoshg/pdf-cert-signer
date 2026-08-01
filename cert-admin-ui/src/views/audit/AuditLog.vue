<template>
  <a-card title="审计日志">
    <a-table :columns="columns" :data-source="dataSource" :loading="loading" :pagination="pagination"
             row-key="id" @change="onTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'signTime'">
          {{ formatTime(record.signTime) }}
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import api from '../../api'

const loading = ref(false)
const dataSource = ref<any[]>([])
const pagination = reactive({ current: 1, pageSize: 20, total: 0 })

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '证书ID', dataIndex: 'signerId', key: 'signerId' },
  { title: '统一信用代码', dataIndex: 'creditCode', key: 'creditCode' },
  { title: '证书序列号', dataIndex: 'certSerialNumber', key: 'certSerialNumber' },
  { title: '签名前哈希', dataIndex: 'pdfHash', key: 'pdfHash', ellipsis: true },
  { title: '签名后哈希', dataIndex: 'signedPdfHash', key: 'signedPdfHash', ellipsis: true },
  { title: '签署时间', dataIndex: 'signTime', key: 'signTime' },
  { title: '客户端IP', dataIndex: 'clientIp', key: 'clientIp' }
]

onMounted(fetchList)

async function fetchList() {
  loading.value = true
  try {
    const res = await api.get('/audit/list', { params: {
      page: pagination.current,
      size: pagination.pageSize
    }})
    dataSource.value = res.data.content
    pagination.total = res.data.totalElements
  } finally {
    loading.value = false
  }
}

function onTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchList()
}

function formatTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 19)
}
</script>
