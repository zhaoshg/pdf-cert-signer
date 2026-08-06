export const CERT_TYPE_ENTERPRISE = 1
export const CERT_TYPE_INDIVIDUAL = 2

export interface SealImage {
  dataUrl: string
  width: number
  height: number
}

/**
 * 印章在 PDF 上的固定物理尺寸（单位 pt，1pt ≈ 0.3528mm）。
 * 与生成图片的宽高比严格一致，保证盖章时绝不变形。
 * 企业章 160x190 对应 40mm x 47.5mm；个人章 220x90 对应 40mm x 16.4mm。
 */
export const SEAL_PDF_SIZE_PT: Record<number, { width: number; height: number }> = {
  [CERT_TYPE_ENTERPRISE]: { width: 113.4, height: 134.7 },
  [CERT_TYPE_INDIVIDUAL]: { width: 113.4, height: 46.4 },
}

export function getSealPdfSize(certType: number) {
  return SEAL_PDF_SIZE_PT[certType] ?? SEAL_PDF_SIZE_PT[CERT_TYPE_INDIVIDUAL]
}

export function generateSealImage(name: string, certType: number): SealImage {
  // 超采样因子：放大生成图片的像素分辨率以提升清晰度。
  // PDF 上的物理尺寸由 SEAL_PDF_SIZE_PT 决定（盖章时固定），
  // 这里放大像素不影响盖章大小，只让印章在 PDF 渲染时更细腻。
  const S = 3

  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')!

  let cx: number, cy: number, bottomY: number

  if (certType === CERT_TYPE_ENTERPRISE) {
    // 1. 企业圆章基准画布 160 x 190，放大 S 倍
    canvas.width = 160 * S
    canvas.height = 190 * S
    cx = canvas.width / 2 // 80 * S
    cy = 80 * S           // 圆章圆心

    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.strokeStyle = '#FF0000'
    ctx.fillStyle = '#FF0000'
    ctx.lineWidth = 3 * S

    const radius = 69 * S    // 外圆半径
    bottomY = cy + radius

    // 画外圆
    ctx.beginPath()
    ctx.arc(cx, cy, radius, 0, 2 * Math.PI)
    ctx.stroke()

    // 画内圆
    ctx.lineWidth = 1.2 * S
    ctx.beginPath()
    ctx.arc(cx, cy, radius - 4 * S, 0, 2 * Math.PI)
    ctx.stroke()
    ctx.lineWidth = 3 * S

    // 画中心五角星
    drawStar(ctx, cx, cy + 6 * S, 22 * S)

    // 环形文字
    ctx.font = `bold ${14 * S}px "SimSun", "宋体", serif`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'

    const textRadius = radius - 15 * S
    const totalAngle = Math.PI * 1.1
    const startAngle = -Math.PI / 2 - totalAngle / 2

    for (let i = 0; i < name.length; i++) {
      let angle = startAngle + (i / (name.length - 1)) * totalAngle
      if (name.length === 1) angle = -Math.PI / 2
      ctx.save()
      ctx.translate(cx + Math.cos(angle) * textRadius, cy + Math.sin(angle) * textRadius)
      ctx.rotate(angle + Math.PI / 2)
      ctx.fillText(name[i], 0, 0)
      ctx.restore()
    }

  } else {
    // 个人章基准画布 220 x 90，放大 S 倍
    canvas.width = 220 * S
    canvas.height = 90 * S
    cx = canvas.width / 2
    cy = 36 * S

    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.fillStyle = '#000000'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'

    if (name.length <= 2) {
      ctx.font = `bold ${44 * S}px "STKaiti", "华文楷体", "KaiTi", "楷体", serif`
    } else if (name.length === 3) {
      ctx.font = `bold ${40 * S}px "STKaiti", "华文楷体", "KaiTi", "楷体", serif`
    } else {
      ctx.font = `bold ${34 * S}px "STKaiti", "华文楷体", "KaiTi", "楷体", serif`
    }

    ctx.fillText(name, cx, cy)
    bottomY = cy + 20 * S
  }

  // 2. 底部蓝色日期
  const today = new Date()
  const yyyy = today.getFullYear()
  const mm = String(today.getMonth() + 1).padStart(2, '0')
  const dd = String(today.getDate()).padStart(2, '0')
  const dateStr = `${yyyy}-${mm}-${dd}`

  ctx.fillStyle = '#0000FF'
  ctx.font = `bold ${16 * S}px monospace`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'top'
  ctx.fillText(dateStr, cx, bottomY + 8 * S)

  return { dataUrl: canvas.toDataURL('image/png'), width: canvas.width, height: canvas.height }
}

function drawStar(ctx: CanvasRenderingContext2D, cx: number, cy: number, outerRadius: number) {
  const innerRadius = outerRadius * 0.382
  let rot = Math.PI / 2 * 3
  const step = Math.PI / 5

  ctx.beginPath()
  ctx.moveTo(cx, cy - outerRadius)
  for (let i = 0; i < 5; i++) {
    ctx.lineTo(cx + Math.cos(rot) * outerRadius, cy + Math.sin(rot) * outerRadius)
    rot += step
    ctx.lineTo(cx + Math.cos(rot) * innerRadius, cy + Math.sin(rot) * innerRadius)
    rot += step
  }
  ctx.lineTo(cx, cy - outerRadius)
  ctx.closePath()
  ctx.fill()
}