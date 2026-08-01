<template>
  <div>
    <a-card title="证书管理">
      <template #extra>
        <a-space>
          <a-input-search v-model:value="query.name" placeholder="姓名" style="width:150px" @search="fetchList" />
          <a-input-search v-model:value="query.creditCode" placeholder="统一信用代码" style="width:200px" @search="fetchList" />
          <a-select v-model:value="query.status" placeholder="状态" style="width:120px" allow-clear @change="fetchList">
            <a-select-option value="ACTIVE">有效</a-select-option>
            <a-select-option value="REVOKED">已吊销</a-select-option>
          </a-select>
          <a-button type="primary" @click="showIssueModal">签发证书</a-button>
        </a-space>
      </template>
      <a-table :columns="columns" :data-source="dataSource" :loading="loading" :pagination="pagination"
               row-key="id" @change="onTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'green' : 'red'">{{ record.status === 'ACTIVE' ? '有效' : '已吊销' }}</a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button size="small" @click="downloadP12(record.id)">下载P12</a-button>
              <a-popconfirm v-if="record.status === 'ACTIVE'" title="确认吊销此证书？" @confirm="revokeCert(record.id)">
                <a-button size="small" danger>吊销</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="issueVisible" title="签发证书" @ok="handleIssue" :confirm-loading="issueLoading">
      <a-form :model="issueForm" :label-col="{ span: 6 }">
        <a-form-item label="统一信用代码" required>
          <a-input v-model:value="issueForm.creditCode" />
        </a-form-item>
        <a-form-item label="姓名" required>
          <a-input v-model:value="issueForm.name" />
        </a-form-item>
        <a-form-item label="部门" required>
          <a-input v-model:value="issueForm.department" />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input v-model:value="issueForm.email" />
        </a-form-item>
        <a-form-item label="有效天数">
          <a-input-number v-model:value="issueForm.validDays" :min="1" :max="3650" style="width:100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import api from '../../api'

const loading = ref(false)
const dataSource = ref<any[]>([])
const query = reactive({ name: '', creditCode: '', status: '' })
const pagination = reactive({ current: 1, pageSize: 20, total: 0 })

const columns = [
  { title: '证书ID', dataIndex: 'signerId', key: 'signerId' },
  { title: '姓名', dataIndex: 'name', key: 'name' },
  { title: '部门', dataIndex: 'department', key: 'department' },
  { title: '统一信用代码', dataIndex: 'creditCode', key: 'creditCode' },
  { title: '证书主题', dataIndex: 'certSubject', key: 'certSubject' },
  { title: '有效期起', dataIndex: 'validFrom', key: 'validFrom' },
  { title: '有效期止', dataIndex: 'validTo', key: 'validTo' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '操作', key: 'action', width: 180 }
]

const issueVisible = ref(false)
const issueLoading = ref(false)
const issueForm = reactive({
  creditCode: '', name: '', department: '', email: '', validDays: 365
})

onMounted(() => fetchList())

async function fetchList() {
  loading.value = true
  try {
    const res = await api.get('/cert/list', { params: {
      creditCode: query.creditCode || undefined,
      name: query.name || undefined,
      status: query.status || undefined,
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

function showIssueModal() {
  issueForm.creditCode = ''
  issueForm.name = ''
  issueForm.department = ''
  issueForm.email = ''
  issueForm.validDays = 365
  issueVisible.value = true
}

async function handleIssue() {
  if (!issueForm.creditCode || !issueForm.name || !issueForm.department) {
    message.warning('请填写必填项')
    return
  }
  issueLoading.value = true
  try {
    const res = await api.post('/cert/issue', issueForm)
    message.success(`签发成功，signerId: ${res.data.signerId}`)
    issueVisible.value = false
    fetchList()
  } finally {
    issueLoading.value = false
  }
}

async function revokeCert(id: number) {
  await api.post(`/cert/revoke/${id}`)
  message.success('吊销成功')
  fetchList()
}

function downloadP12(id: number) {
  window.open(`/api/v1/cert/download/p12/${id}`)
}
</script>
