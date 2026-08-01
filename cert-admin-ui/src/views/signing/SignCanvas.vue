<template>
  <div>
    <a-card title="PDF 签章">
      <a-form layout="inline" style="margin-bottom:16px">
        <a-form-item label="signerId" required>
          <a-input v-model:value="form.signerId" placeholder="证书ID" style="width:220px" />
        </a-form-item>
        <a-form-item label="PDF URL" required>
          <a-input v-model:value="form.pdfUrl" placeholder="PDF文件URL" style="width:400px" />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="loadPdf">加载PDF</a-button>
            <a-button @click="addSeal">插入印章</a-button>
            <a-button @click="clearSeals">清除印章</a-button>
            <a-button type="primary" danger :loading="signing" @click="doSign">确认签章</a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <div v-if="pdfUrl" style="position:relative; display:inline-block">
        <div v-for="(seal, i) in seals" :key="i" class="seal-overlay"
             :style="{ left: seal.x + 'px', top: seal.y + 'px', width: seal.width + 'px', height: seal.height + 'px' }"
             @mousedown="startDrag($event, i)">
          <img v-if="seal.sealUrl" :src="seal.sealUrl" style="width:100%;height:100%;object-fit:contain" />
          <a-button size="small" danger class="seal-close" @click="removeSeal(i)">X</a-button>
        </div>
        <canvas ref="pdfCanvas" style="border:1px solid #d9d9d9" />
        <div style="margin-top:8px">
          <a-button @click="prevPage" :disabled="pageNum <= 1">上一页</a-button>
          <span style="margin:0 12px">第 {{ pageNum }} / {{ totalPages }} 页</span>
          <a-button @click="nextPage" :disabled="pageNum >= totalPages">下一页</a-button>
          <a-slider v-model:value="scale" :min="0.5" :max="2" :step="0.1" style="width:200px;margin-left:16px;display:inline-block" @change="renderPage" />
        </div>
      </div>

      <a-modal v-model:open="sealModalVisible" title="插入印章" @ok="confirmSeal">
        <a-form>
          <a-form-item label="印章URL" required>
            <a-input v-model:value="sealForm.sealUrl" placeholder="印章图片URL" />
          </a-form-item>
          <a-form-item label="宽度">
            <a-input-number v-model:value="sealForm.width" :min="50" :max="400" />
          </a-form-item>
          <a-form-item label="高度">
            <a-input-number v-model:value="sealForm.height" :min="50" :max="400" />
          </a-form-item>
          <a-form-item label="签章原因">
            <a-input v-model:value="sealForm.reason" />
          </a-form-item>
        </a-form>
      </a-modal>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import * as pdfjsLib from 'pdfjs-dist'
import api from '../../api'

pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/4.0.379/pdf.worker.min.mjs'

const form = reactive({ signerId: '', pdfUrl: '' })
const pdfCanvas = ref<HTMLCanvasElement>()
const pdfUrl = ref('')
const pageNum = ref(1)
const totalPages = ref(0)
const scale = ref(1)
const signing = ref(false)
const seals = ref<any[]>([])

const sealModalVisible = ref(false)
const sealForm = reactive({ sealUrl: '', width: 120, height: 120, reason: '' })
let pdfDoc: any = null

async function loadPdf() {
  if (!form.signerId || !form.pdfUrl) { message.warning('请填写 signerId 和 PDF URL'); return }
  try {
    pdfDoc = await pdfjsLib.getDocument({ url: form.pdfUrl }).promise
    totalPages.value = pdfDoc.numPages
    pageNum.value = 1
    seals.value = []
    pdfUrl.value = form.pdfUrl
    renderPage()
  } catch {
    message.error('PDF加载失败')
  }
}

async function renderPage() {
  if (!pdfDoc || !pdfCanvas.value) return
  const page = await pdfDoc.getPage(pageNum.value)
  const viewport = page.getViewport({ scale: scale.value })
  const canvas = pdfCanvas.value
  canvas.width = viewport.width
  canvas.height = viewport.height
  const ctx = canvas.getContext('2d')!
  await page.render({ canvasContext: ctx, viewport }).promise
}

function prevPage() { if (pageNum.value > 1) { pageNum.value--; renderPage() } }
function nextPage() { if (pageNum.value < totalPages.value) { pageNum.value++; renderPage() } }

function addSeal() {
  sealForm.sealUrl = ''
  sealForm.width = 120
  sealForm.height = 120
  sealForm.reason = ''
  sealModalVisible.value = true
}

function confirmSeal() {
  if (!sealForm.sealUrl) { message.warning('请输入印章URL'); return }
  seals.value.push({
    pageIndex: pageNum.value - 1,
    sealUrl: sealForm.sealUrl,
    x: Math.random() * 100 + 50,
    y: Math.random() * 100 + 50,
    width: sealForm.width,
    height: sealForm.height,
    reason: sealForm.reason
  })
  sealModalVisible.value = false
}

function clearSeals() { seals.value = [] }
function removeSeal(i: number) { seals.value.splice(i, 1) }

function startDrag(e: MouseEvent, i: number) {
  const seal = seals.value[i]
  const startX = e.clientX - seal.x
  const startY = e.clientY - seal.y
  function onMove(ev: MouseEvent) {
    seal.x = ev.clientX - startX
    seal.y = ev.clientY - startY
  }
  function onUp() {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

async function doSign() {
  if (!form.signerId || seals.value.length === 0) { message.warning('请先加载PDF并添加印章'); return }
  signing.value = true
  try {
    const res = await api.post('/pdf/sign', {
      signerId: form.signerId,
      pdfUrl: form.pdfUrl,
      signatures: seals.value
    })
    message.success(`签署成功！文件URL: ${res.data.signedPdfUrl}`)
  } finally {
    signing.value = false
  }
}
</script>

<style scoped>
.seal-overlay {
  position: absolute;
  border: 2px dashed #1890ff;
  cursor: move;
  z-index: 10;
}
.seal-close {
  position: absolute;
  top: -10px;
  right: -10px;
  font-size: 10px;
  padding: 0 4px;
  height: 20px;
}
</style>
