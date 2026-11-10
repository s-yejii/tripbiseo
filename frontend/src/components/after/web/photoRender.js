// ⑤ 사진 편집 그리기 (웹 여행 메모, 11/10). 화면 없이 계산만 해요
// 순서: 원본 → 돌리기·뒤집기·수평 맞추기(oriented) → 자르기 → 색 조정·필터 → 덮개(색온도·바랜 느낌·입자·주변 어둡게) → 선명하게 → 그림 → 글자
// 좌표는 모두 0~1 비율로 저장해서, 미리보기(작게)와 저장(크게)에 같은 편집이 그대로 적용돼요

const MAX_STRAIGHTEN = 15             // 수평 맞추기 최대 각도(도)
const TEXT_FONT = "'Pretendard', 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif"
const STAMP_FONT = "'Courier New', 'Consolas', monospace"
const NOISE_SIZE = 200                // 입자(그레인) 무늬 한 장 크기(px)

/** 필터 미리 정해 둔 것: css = 캔버스 filter 글자, 나머지는 덮개 세기(0~100) */
export const FILTER_PRESETS = [
  { id: 'none', name: '원본', css: '', warm: 0, fade: 0, grain: 0, vignette: 0 },
  { id: 'vivid', name: '선명하게', css: 'saturate(1.45) contrast(1.12)', warm: 0, fade: 0, grain: 0, vignette: 10 },
  { id: 'warm', name: '따뜻하게', css: 'sepia(0.2) saturate(1.15)', warm: 35, fade: 0, grain: 0, vignette: 10 },
  { id: 'cool', name: '시원하게', css: 'saturate(1.05) brightness(1.04)', warm: -35, fade: 0, grain: 0, vignette: 0 },
  { id: 'sunset', name: '노을', css: 'sepia(0.15) saturate(1.3) hue-rotate(-10deg)', warm: 50, fade: 10, grain: 0, vignette: 25 },
  { id: 'vintage', name: '빈티지', css: 'sepia(0.45) contrast(0.9) brightness(1.05)', warm: 10, fade: 35, grain: 20, vignette: 30 },
  { id: 'film', name: '필름', css: 'contrast(1.1) saturate(0.85)', warm: 5, fade: 15, grain: 45, vignette: 15 },
  { id: 'dream', name: '몽환', css: 'brightness(1.1) saturate(1.2)', warm: 10, fade: 40, grain: 0, vignette: 0 },
  { id: 'mono', name: '흑백', css: 'grayscale(1) contrast(1.1)', warm: 0, fade: 0, grain: 10, vignette: 15 },
  { id: 'noir', name: '느와르', css: 'grayscale(1) contrast(1.55) brightness(0.9)', warm: 0, fade: 0, grain: 25, vignette: 45 },
]

/** 아무것도 안 고친 편집 상태 */
export function emptyEdit() {
  return {
    rotate: 0,            // 0 · 90 · 180 · 270
    flipX: false,         // 좌우 뒤집기
    flipY: false,         // 위아래 뒤집기
    straighten: 0,        // 수평 맞추기 −15 ~ 15도
    crop: { x: 0, y: 0, w: 1, h: 1 }, // 돌린 사진 기준 비율
    aspect: 'free',       // 자르기 비율
    filter: 'none',
    adjust: { brightness: 0, contrast: 0, saturation: 0, warmth: 0, tint: 0, fade: 0, vignette: 0, grain: 0, blur: 0, sharpen: 0 },
    texts: [],            // 글자·도장·스티커 { id, text, x, y, size, color, bold, shadow, box, font }
    strokes: [],          // 그림 { color, size, alpha, points: [[x, y], …] }
  }
}

function presetOf(edit) {
  for (const preset of FILTER_PRESETS) {
    if (preset.id === edit.filter) return preset
  }
  return FILTER_PRESETS[0]
}

// ============================== 1) 돌리기·뒤집기·수평 맞추기
// 수평을 맞추느라 기울이면 모서리가 비어서, 빈 곳이 안 보이게 이만큼 키움
function straightenScale(width, height, degrees) {
  const angle = Math.abs(degrees) * Math.PI / 180
  return Math.max((width * Math.cos(angle) + height * Math.sin(angle)) / width, (width * Math.sin(angle) + height * Math.cos(angle)) / height)
}

/** 돌리고 뒤집은 사진을 새 캔버스에 (긴 변이 maxSide를 넘지 않게 줄임) */
export function buildOriented(image, edit, maxSide) {
  const isSideways = edit.rotate === 90 || edit.rotate === 270
  const sourceW = image.naturalWidth || image.width
  const sourceH = image.naturalHeight || image.height
  const scale = Math.min(1, maxSide / Math.max(sourceW, sourceH))
  const drawW = Math.round(sourceW * scale)
  const drawH = Math.round(sourceH * scale)
  const canvas = document.createElement('canvas')
  canvas.width = isSideways ? drawH : drawW
  canvas.height = isSideways ? drawW : drawH
  const ctx = canvas.getContext('2d')
  const fill = straightenScale(canvas.width, canvas.height, edit.straighten)
  ctx.translate(canvas.width / 2, canvas.height / 2)
  ctx.rotate(((edit.rotate + edit.straighten) * Math.PI) / 180)
  ctx.scale((edit.flipX ? -1 : 1) * fill, (edit.flipY ? -1 : 1) * fill)
  ctx.drawImage(image, -drawW / 2, -drawH / 2, drawW, drawH)
  return canvas
}

/** 자른 결과 크기를 상자(maxW × maxH)에 맞춘 크기 */
export function fitSize(oriented, crop, maxW, maxH) {
  const cropW = oriented.width * crop.w
  const cropH = oriented.height * crop.h
  const scale = Math.min(maxW / cropW, maxH / cropH)
  return { width: Math.max(1, Math.round(cropW * scale)), height: Math.max(1, Math.round(cropH * scale)) }
}

/** 이 비율(가로/세로)로 가장 크게 가운데 자르기. ratio가 null이면 전체 */
export function centerCrop(ratio, orientedW, orientedH) {
  if (!ratio) return { x: 0, y: 0, w: 1, h: 1 }
  let w = 1
  let h = (orientedW / orientedH) / ratio // 가로를 꽉 채웠을 때 필요한 세로 비율
  if (h > 1) {
    h = 1
    w = (orientedH / orientedW) * ratio
  }
  return { x: (1 - w) / 2, y: (1 - h) / 2, w: w, h: h }
}

export { MAX_STRAIGHTEN }

// ============================== 2) 색 조정·필터
// 조정 값(−100 ~ 100) → 캔버스 filter 글자. 흐림은 결과 너비에 비례해서 미리보기와 저장이 같게
function filterText(edit, width) {
  const adjust = edit.adjust
  const parts = [
    'brightness(' + (1 + adjust.brightness / 160) + ')',
    'contrast(' + (1 + adjust.contrast / 160) + ')',
    'saturate(' + (1 + adjust.saturation / 100) + ')',
  ]
  if (adjust.blur > 0) parts.push('blur(' + ((adjust.blur / 1000) * width).toFixed(2) + 'px)')
  const preset = presetOf(edit)
  if (preset.css) parts.push(preset.css)
  return parts.join(' ')
}

// 색 한 겹 덮기 (composite = 섞는 방법: soft-light는 은은하게 물들임, screen은 밝게)
function fillLayer(ctx, width, height, color, alpha, composite) {
  if (alpha <= 0) return
  ctx.save()
  ctx.globalCompositeOperation = composite
  ctx.globalAlpha = Math.min(1, alpha)
  ctx.fillStyle = color
  ctx.fillRect(0, 0, width, height)
  ctx.restore()
}

let noiseCanvas = null
// 입자 무늬 한 장 (한 번만 만들고 계속 씀. 매번 만들면 화면이 지글거림)
function noisePattern(ctx) {
  if (!noiseCanvas) {
    noiseCanvas = document.createElement('canvas')
    noiseCanvas.width = NOISE_SIZE
    noiseCanvas.height = NOISE_SIZE
    const noiseCtx = noiseCanvas.getContext('2d')
    const data = noiseCtx.createImageData(NOISE_SIZE, NOISE_SIZE)
    for (let i = 0; i < data.data.length; i += 4) {
      const gray = Math.floor(Math.random() * 256)
      data.data[i] = gray
      data.data[i + 1] = gray
      data.data[i + 2] = gray
      data.data[i + 3] = 255
    }
    noiseCtx.putImageData(data, 0, 0)
  }
  return ctx.createPattern(noiseCanvas, 'repeat')
}

// 주변 어둡게 (가운데는 그대로, 바깥으로 갈수록 검게)
function drawVignette(ctx, width, height, amount) {
  if (amount <= 0) return
  const radius = Math.hypot(width, height) / 2
  const gradient = ctx.createRadialGradient(width / 2, height / 2, radius * 0.45, width / 2, height / 2, radius)
  gradient.addColorStop(0, 'rgba(0, 0, 0, 0)')
  gradient.addColorStop(1, 'rgba(0, 0, 0, ' + Math.min(0.85, amount / 100 * 0.85) + ')')
  ctx.save()
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, width, height)
  ctx.restore()
}

// 덮개 네 가지: 색온도(따뜻·차갑게), 색조(초록·자주), 바랜 느낌, 입자, 주변 어둡게
function drawOverlays(ctx, width, height, edit) {
  const adjust = edit.adjust
  const preset = presetOf(edit)
  const warmth = Math.max(-100, Math.min(100, adjust.warmth + preset.warm))
  fillLayer(ctx, width, height, warmth > 0 ? '#FF9A3C' : '#3C8CFF', (Math.abs(warmth) / 100) * 0.4, 'soft-light')
  fillLayer(ctx, width, height, adjust.tint > 0 ? '#E040C0' : '#40D080', (Math.abs(adjust.tint) / 100) * 0.35, 'soft-light')
  fillLayer(ctx, width, height, '#6E6E6E', (Math.min(100, adjust.fade + preset.fade) / 100) * 0.55, 'screen')
  const grain = Math.min(100, adjust.grain + preset.grain)
  if (grain > 0) fillLayer(ctx, width, height, noisePattern(ctx), (grain / 100) * 0.35, 'overlay')
  drawVignette(ctx, width, height, Math.min(100, adjust.vignette + preset.vignette))
}

// 선명하게: 점마다 (자기 × 크게) − (위·아래·왼·오른쪽 이웃)으로 경계를 또렷하게 (합성곱)
function sharpen(ctx, width, height, amount) {
  if (amount <= 0) return
  const strength = (amount / 100) * 0.7
  const image = ctx.getImageData(0, 0, width, height)
  const source = new Uint8ClampedArray(image.data) // 원래 값 복사본 (바꾼 값을 다시 읽지 않게)
  const out = image.data
  const row = width * 4
  for (let y = 1; y < height - 1; y++) {
    for (let x = 1; x < width - 1; x++) {
      const i = y * row + x * 4
      for (let c = 0; c < 3; c++) {
        const around = source[i - row + c] + source[i + row + c] + source[i - 4 + c] + source[i + 4 + c]
        out[i + c] = source[i + c] * (1 + 4 * strength) - around * strength
      }
    }
  }
  ctx.putImageData(image, 0, 0)
}

// ============================== 3) 그림·글자
function drawStrokes(ctx, width, height, strokes) {
  for (const stroke of strokes) {
    if (stroke.points.length === 0) continue
    ctx.save()
    ctx.strokeStyle = stroke.color
    ctx.globalAlpha = stroke.alpha
    ctx.lineWidth = (stroke.size / 1000) * width
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    ctx.beginPath()
    ctx.moveTo(stroke.points[0][0] * width, stroke.points[0][1] * height)
    for (const point of stroke.points) ctx.lineTo(point[0] * width, point[1] * height)
    if (stroke.points.length === 1) ctx.lineTo(stroke.points[0][0] * width + 0.1, stroke.points[0][1] * height) // 점 하나도 보이게
    ctx.stroke()
    ctx.restore()
  }
}

// 글자 한 개 그리기. 돌려줌: 차지한 자리(0~1 비율) — 끌어서 옮길 때 눌렀는지 확인용
function drawText(ctx, width, height, item) {
  const fontSize = (item.size / 1000) * width
  const lines = item.text.split('\n')
  ctx.save()
  ctx.font = (item.bold ? '700 ' : '400 ') + fontSize + 'px ' + (item.font === 'stamp' ? STAMP_FONT : TEXT_FONT)
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  let textWidth = 0
  for (const line of lines) textWidth = Math.max(textWidth, ctx.measureText(line).width)
  const lineHeight = fontSize * 1.25
  const boxW = textWidth + fontSize * 0.8
  const boxH = lineHeight * lines.length + fontSize * 0.4
  const centerX = item.x * width
  const centerY = item.y * height
  if (item.box) {
    ctx.fillStyle = 'rgba(0, 0, 0, 0.45)'
    ctx.fillRect(centerX - boxW / 2, centerY - boxH / 2, boxW, boxH)
  }
  if (item.shadow) {
    ctx.shadowColor = 'rgba(0, 0, 0, 0.55)'
    ctx.shadowBlur = fontSize * 0.18
    ctx.shadowOffsetY = fontSize * 0.05
  }
  ctx.fillStyle = item.color
  for (let i = 0; i < lines.length; i++) ctx.fillText(lines[i], centerX, centerY + (i - (lines.length - 1) / 2) * lineHeight)
  ctx.restore()
  return { id: item.id, x0: (centerX - boxW / 2) / width, y0: (centerY - boxH / 2) / height, x1: (centerX + boxW / 2) / width, y1: (centerY + boxH / 2) / height }
}

/**
 * 편집한 사진을 canvas 크기에 맞춰 그리기
 * options.isCropView: 자르기 화면이면 자르기 전 전체를 그리고 그림·글자는 빼요
 * 돌려줌: 글자마다 차지한 자리 목록
 */
export function renderEdit(canvas, oriented, edit, options) {
  const ctx = canvas.getContext('2d')
  const width = canvas.width
  const height = canvas.height
  const crop = options.isCropView ? { x: 0, y: 0, w: 1, h: 1 } : edit.crop
  ctx.clearRect(0, 0, width, height)
  ctx.save()
  ctx.filter = filterText(edit, width)
  ctx.drawImage(oriented, crop.x * oriented.width, crop.y * oriented.height, crop.w * oriented.width, crop.h * oriented.height, 0, 0, width, height)
  ctx.restore()
  drawOverlays(ctx, width, height, edit)
  sharpen(ctx, width, height, edit.adjust.sharpen)
  if (options.isCropView) return []
  drawStrokes(ctx, width, height, edit.strokes)
  const boxes = []
  for (const item of edit.texts) boxes.push(drawText(ctx, width, height, item))
  return boxes
}
