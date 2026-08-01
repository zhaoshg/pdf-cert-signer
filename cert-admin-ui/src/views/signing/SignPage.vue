<template>
  <div class="sign-page">
    <div class="toolbar">
      <a-button :loading="inserting" @click="addSeal">插入印章</a-button>
      <a-button @click="clearSeals">清除印章</a-button>
      <a-button type="primary" danger :loading="signing" @click="doSign">确认签章</a-button>
    </div>

    <div style="position:relative; display:inline-block; margin-top:8px">
      <div v-for="(seal, i) in seals" :key="i" class="seal-overlay"
           :style="{ left: seal.x + 'px', top: seal.y + 'px', width: seal.width + 'px', height: seal.height + 'px' }"
           @mousedown="startDrag($event, i)">
        <img v-if="seal.sealUrl" :src="seal.sealUrl" style="width:100%;height:100%;object-fit:contain" />
        <a-button size="small" danger class="seal-close" @click="removeSeal(i)">X</a-button>
      </div>
      <canvas ref="pdfCanvas" style="border:1px solid #d9d9d9; max-width:100%" />
      <div class="pager">
        <a-button @click="prevPage" :disabled="pageNum <= 1">上一页</a-button>
        <span>第 {{ pageNum }} / {{ totalPages }} 页</span>
        <a-button @click="nextPage" :disabled="pageNum >= totalPages">下一页</a-button>
        <a-slider v-model:value="scale" :min="0.5" :max="2" :step="0.1" style="width:160px;display:inline-block;margin-left:12px" @change="renderPage" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import * as pdfjsLib from 'pdfjs-dist'
import api from '../../api'
import { generateSealImage } from '../../utils/sealUtils'

pdfjsLib.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url).toString()

const route = useRoute()

const pdfCanvas = ref<HTMLCanvasElement>()
const pageNum = ref(1)
const totalPages = ref(0)
const scale = ref(1)
const signing = ref(false)
const inserting = ref(false)
const seals = ref<any[]>([])
const signerId = ref('')
const pdfUrl = ref('')

let pdfDoc: any = null

onMounted(async () => {
  const sid = route.query.signerId as string
  const url = route.query.pdfUrl as string
  if (!sid || !url) {
    message.error('缺少参数：signerId 或 pdfUrl')
    return
  }
  signerId.value = sid
  pdfUrl.value = url
  await loadPdf()
})

async function loadPdf() {
  try {
    const fetchUrl = `/api/v1/pdf/fetch?url=${encodeURIComponent(pdfUrl.value)}`
    pdfDoc = await pdfjsLib.getDocument({ url: fetchUrl }).promise
    totalPages.value = pdfDoc.numPages
    pageNum.value = 1
    seals.value = []
    renderPage()
  } catch (e: any) {
    console.error('PDF加载失败', e)
    message.error('PDF加载失败: ' + (e?.message || e?.toString?.() || ''))
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

async function addSeal() {
  inserting.value = true
  try {
    const res = await api.get(`/cert/info/${signerId.value}`)
    const cert = res.data
    const seal = generateSealImage(cert.name, cert.certType)
    seals.value.push({
      pageIndex: pageNum.value - 1,
      sealUrl: seal.dataUrl,
      x: Math.random() * 100 + 50,
      y: Math.random() * 100 + 50,
      width: seal.width,
      height: seal.height,
      reason: ''
    })
    message.success('印章已插入')
  } catch (e: any) {
    message.error(e?.response?.data?.message || e?.message || '获取证书信息失败')
  } finally {
    inserting.value = false
  }
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
  if (seals.value.length === 0) { message.warning('请先插入印章'); return }
  signing.value = true
  try {
    const res = await api.post('/pdf/sign', {
      signerId: signerId.value,
      pdfUrl: pdfUrl.value,
      signatures: seals.value
    })
    message.success(`签署成功！文件URL: ${res.data.signedPdfUrl}`)
  } catch (e: any) {
    message.error(e?.response?.data?.message || e?.message || '签章失败')
  } finally {
    signing.value = false
  }
}
</script>

<style scoped>
.sign-page {
  padding: 12px;
  min-height: 100vh;
  background: #f5f5f5;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.pager {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
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
