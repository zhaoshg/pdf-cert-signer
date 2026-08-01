export const CERT_TYPE_ENTERPRISE = 1
export const CERT_TYPE_INDIVIDUAL = 2

export interface SealImage {
  dataUrl: string
  width: number
  height: number
}

export function generateSealImage(name: string, certType: number): SealImage {
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')!

  let cx: number, cy: number, bottomY: number

  if (certType === CERT_TYPE_ENTERPRISE) {
    canvas.width = 200
    canvas.height = 230
    cx = canvas.width / 2
    cy = 100

    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.strokeStyle = '#FF0000'
    ctx.fillStyle = '#FF0000'
    ctx.lineWidth = 4

    const radius = 86
    bottomY = cy + radius

    ctx.beginPath()
    ctx.arc(cx, cy, radius, 0, 2 * Math.PI)
    ctx.stroke()

    ctx.lineWidth = 1.5
    ctx.beginPath()
    ctx.arc(cx, cy, radius - 5, 0, 2 * Math.PI)
    ctx.stroke()
    ctx.lineWidth = 4

    drawStar(ctx, cx, cy + 8, 28)

    ctx.font = 'bold 18px "SimSun", "宋体", serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'

    const textRadius = radius - 18
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
    canvas.width = 220
    canvas.height = 90
    cx = canvas.width / 2
    cy = 36

    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.fillStyle = '#000000'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'

    if (name.length <= 2) {
      ctx.font = 'bold 44px "STKaiti", "华文楷体", "KaiTi", "楷体", serif'
    } else if (name.length === 3) {
      ctx.font = 'bold 40px "STKaiti", "华文楷体", "KaiTi", "楷体", serif'
    } else {
      ctx.font = 'bold 34px "STKaiti", "华文楷体", "KaiTi", "楷体", serif'
    }

    ctx.fillText(name, cx, cy)
    bottomY = cy + 20
  }

  const today = new Date()
  const yyyy = today.getFullYear()
  const mm = String(today.getMonth() + 1).padStart(2, '0')
  const dd = String(today.getDate()).padStart(2, '0')
  const dateStr = `${yyyy}-${mm}-${dd}`

  ctx.fillStyle = '#0000FF'
  ctx.font = 'bold 16px monospace'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'top'
  ctx.fillText(dateStr, cx, bottomY + 8)

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
