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
            <a-input v-model:value="reason" placeholder="签章原因（可空）" style="width:200px" allow-clear />
            <a-button :loading="inserting" @click="addSeal">插入印章</a-button>
            <a-button @click="clearSeals">清除印章</a-button>
            <a-button type="primary" danger :loading="signing" @click="doSign">确认签章</a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <div v-if="pdfUrl" style="position:relative; display:inline-block">
        <div v-for="(seal, i) in seals" :key="i" class="seal-overlay"
             :style="{ left: seal.x * scale + 'px', top: seal.y * scale + 'px', width: seal.width * scale + 'px', height: seal.height * scale + 'px' }"
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
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import * as pdfjsLib from 'pdfjs-dist'
import api from '../../api'
import { generateSealImage, getSealPdfSize } from '../../utils/sealUtils'

pdfjsLib.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url).toString()

const form = reactive({ signerId: '', pdfUrl: '' })
const pdfCanvas = ref<HTMLCanvasElement>()
const pdfUrl = ref('')
const pageNum = ref(1)
const totalPages = ref(0)
const scale = ref(1)
const signing = ref(false)
const inserting = ref(false)
const reason = ref('')
const seals = ref<any[]>([])

let pdfDoc: any = null

async function loadPdf() {
  if (!form.signerId || !form.pdfUrl) { message.warning('请填写 signerId 和 PDF URL'); return }
  try {
    const fetchUrl = `/api/v1/pdf/fetch?url=${encodeURIComponent(form.pdfUrl)}`
    pdfDoc = await pdfjsLib.getDocument({ url: fetchUrl }).promise
    totalPages.value = pdfDoc.numPages
    pageNum.value = 1
    seals.value = []
    pdfUrl.value = form.pdfUrl
    renderPage()
  } catch (e: any) {
    console.error('PDF加载失败', e)
    message.error('PDF加载失败: ' + (e?.message || e?.toString?.() || '未知错误'))
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
  if (!form.signerId) { message.warning('请输入 signerId'); return }
  inserting.value = true
  try {
    const res = await api.get(`/cert/info/${form.signerId}`)
    const cert = res.data
    const seal = generateSealImage(cert.name, cert.certType)
    const pdfSize = getSealPdfSize(cert.certType)
    seals.value.push({
      pageIndex: pageNum.value - 1,
      sealUrl: seal.dataUrl,
      // x/y 为 PDF 左上角原点坐标系下的 pt 坐标，渲染时乘 scale 换算成屏幕像素
      x: Math.random() * 100 + 50,
      y: Math.random() * 100 + 50,
      width: pdfSize.width,
      height: pdfSize.height,
      reason: reason.value
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
  // 记录按下时鼠标相对印章左上角的偏移（屏幕像素），并换算回 PDF pt
  const startX = e.clientX - seal.x * scale.value
  const startY = e.clientY - seal.y * scale.value
  function onMove(ev: MouseEvent) {
    // 拖拽增量除以当前缩放，换算回 PDF pt 坐标系
    seal.x = (ev.clientX - startX) / scale.value
    seal.y = (ev.clientY - startY) / scale.value
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
    // 提交前将所有印章的 reason 同步为输入框当前值（后端取第一个非空 reason）
    seals.value.forEach(s => { s.reason = reason.value })
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
