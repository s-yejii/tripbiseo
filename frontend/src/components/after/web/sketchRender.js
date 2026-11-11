// ⑤ 사진 꾸미기 그리기 (웹 사진 편집, 11/11). 화면 없이 계산만 해요
// 순서: 사진 → (따로 만든 그림 판에) 펜·형광펜·지우개·글자·스티커·도형·메모지를 넣은 순서대로 → 그림 판을 사진 위에 얹기
// 지우개는 그림 판에서만 지워서 사진은 안 지워져요 (destination-out = 그린 자리를 투명하게)
// 좌표는 0~1 비율, 크기는 사진 너비 기준이라 미리보기(작게)와 저장(크게)에 똑같이 그려져요

export const HAND_FONT = "'Gaegu', 'Nanum Pen Script', 'Malgun Gothic', cursive" // 손글씨 글꼴 (못 받으면 맑은 고딕)
const SANS_FONT = "'Pretendard', 'Malgun Gothic', sans-serif"
const PT_TO_WIDTH = 1 / 400       // 굵기 1pt = 사진 너비의 1/400 (너비 1000px이면 2.5px)
const MARKER_SCALE = 3            // 형광펜은 펜보다 이만큼 굵게
const MARKER_ALPHA = 0.35         // 형광펜 진하기
const ERASER_SCALE = 3            // 지우개는 펜보다 이만큼 굵게
const NOTE_PAPER = '#FFF8DC'
const NOTE_TAPE = 'rgba(255, 170, 190, 0.85)'
const NOTE_ANGLE = -0.07          // 메모지 기울기(라디안, 약 −4도)

/** 아무것도 안 그린 상태 */
export function emptySketch() {
  return { items: [] } // 넣은 순서대로 그림. { id, type: 'stroke' | 'text' | 'sticker' | 'shape' | 'note', … }
}

function lineWidthOf(pt, width) {
  return pt * PT_TO_WIDTH * width
}

// ============================== 선 (펜·형광펜·지우개)
function drawStroke(ctx, width, height, item) {
  if (item.points.length === 0) return
  ctx.save()
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.strokeStyle = item.color
  ctx.lineWidth = lineWidthOf(item.size, width)
  if (item.mode === 'marker') {
    ctx.globalAlpha = MARKER_ALPHA
    ctx.lineWidth *= MARKER_SCALE
  } else if (item.mode === 'eraser') {
    ctx.globalCompositeOperation = 'destination-out' // 그린 자리를 투명하게
    ctx.strokeStyle = '#000'
    ctx.lineWidth *= ERASER_SCALE
  }
  ctx.beginPath()
  ctx.moveTo(item.points[0][0] * width, item.points[0][1] * height)
  for (const point of item.points) ctx.lineTo(point[0] * width, point[1] * height)
  if (item.points.length === 1) ctx.lineTo(item.points[0][0] * width + 0.1, item.points[0][1] * height) // 점 하나도 보이게
  ctx.stroke()
  ctx.restore()
}

// ============================== 글자·스티커
// 여러 줄 글자를 (cx, cy) 가운데에 그리고, 차지한 너비·높이(px)를 돌려줌
function drawLines(ctx, lines, cx, cy, fontSize) {
  const lineHeight = fontSize * 1.2
  let maxWidth = 0
  for (let i = 0; i < lines.length; i++) {
    ctx.fillText(lines[i], cx, cy + (i - (lines.length - 1) / 2) * lineHeight)
    maxWidth = Math.max(maxWidth, ctx.measureText(lines[i]).width)
  }
  return { w: maxWidth, h: lineHeight * lines.length }
}

function drawText(ctx, width, height, item) {
  const fontSize = (item.size / 1000) * width
  ctx.save()
  ctx.font = '700 ' + fontSize + 'px ' + (item.font === 'hand' ? HAND_FONT : SANS_FONT)
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillStyle = item.color
  ctx.shadowColor = item.color === '#FFFFFF' ? 'rgba(0, 0, 0, 0.35)' : 'rgba(255, 255, 255, 0.5)' // 사진 위에서도 읽히게 살짝 그림자
  ctx.shadowBlur = fontSize * 0.12
  const size = drawLines(ctx, item.text.split('\n'), item.x * width, item.y * height, fontSize)
  ctx.restore()
  return boxAround(item.x, item.y, size.w / width, size.h / height, 0.01)
}

function drawSticker(ctx, width, height, item) {
  const fontSize = (item.size / 1000) * width
  ctx.save()
  ctx.font = fontSize + 'px "Segoe UI Emoji", "Apple Color Emoji", "Noto Color Emoji", sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(item.emoji, item.x * width, item.y * height)
  ctx.restore()
  return boxAround(item.x, item.y, (fontSize * 1.1) / width, (fontSize * 1.1) / height, 0)
}

// 가운데 (x, y), 너비·높이(0~1)로 차지한 자리 + 여유
function boxAround(x, y, w, h, margin) {
  return { x0: x - w / 2 - margin, y0: y - h / 2 - margin, x1: x + w / 2 + margin, y1: y + h / 2 + margin }
}

// ============================== 도형
// 도형 모양 길(path)만 만들기. (0, 0) 가운데, 너비 w·높이 h(px)
function shapePath(ctx, shape, w, h) {
  ctx.beginPath()
  if (shape === 'rect') roundRect(ctx, -w / 2, -h / 2, w, h, Math.min(w, h) * 0.12)
  else if (shape === 'circle') ctx.ellipse(0, 0, w / 2, h / 2, 0, 0, Math.PI * 2)
  else if (shape === 'heart') heartPath(ctx, w, h)
  else if (shape === 'star') starPath(ctx, w, h)
  else if (shape === 'cloud') cloudPath(ctx, w, h)
  else if (shape === 'arrow') arrowPath(ctx, w, h)
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

function heartPath(ctx, w, h) {
  ctx.moveTo(0, h * 0.35)
  ctx.bezierCurveTo(-w * 0.55, -h * 0.05, -w * 0.3, -h * 0.55, 0, -h * 0.2)
  ctx.bezierCurveTo(w * 0.3, -h * 0.55, w * 0.55, -h * 0.05, 0, h * 0.35)
  ctx.closePath()
}

// 별: 바깥 꼭짓점 5개와 안쪽 꼭짓점 5개를 번갈아 이음
function starPath(ctx, w, h) {
  for (let i = 0; i < 10; i++) {
    const radius = i % 2 === 0 ? 0.5 : 0.21
    const angle = -Math.PI / 2 + (i * Math.PI) / 5
    const x = Math.cos(angle) * radius * w
    const y = Math.sin(angle) * radius * h + h * 0.05
    if (i === 0) ctx.moveTo(x, y)
    else ctx.lineTo(x, y)
  }
  ctx.closePath()
}

// 구름 말풍선: 타원 둘레의 점들을 바깥으로 부푼 곡선으로 이음
function cloudPath(ctx, w, h) {
  const bumps = 9
  for (let i = 0; i <= bumps; i++) {
    const angle = (i / bumps) * Math.PI * 2
    const x = Math.cos(angle) * w * 0.42
    const y = Math.sin(angle) * h * 0.4
    if (i === 0) {
      ctx.moveTo(x, y)
      continue
    }
    const middle = ((i - 0.5) / bumps) * Math.PI * 2
    ctx.quadraticCurveTo(Math.cos(middle) * w * 0.6, Math.sin(middle) * h * 0.58, x, y)
  }
  ctx.closePath()
}

function arrowPath(ctx, w, h) {
  ctx.moveTo(-w / 2, 0)
  ctx.lineTo(w / 2, 0)
  ctx.moveTo(w / 2 - h / 2, -h / 2)
  ctx.lineTo(w / 2, 0)
  ctx.lineTo(w / 2 - h / 2, h / 2)
}

function drawShape(ctx, width, height, item) {
  const w = item.w * width
  const h = item.h * width // 높이도 너비 기준 (사진 모양이 달라도 하트가 찌그러지지 않게)
  ctx.save()
  ctx.translate(item.x * width, item.y * height)
  shapePath(ctx, item.shape, w, h)
  ctx.lineWidth = lineWidthOf(item.size, width)
  ctx.lineJoin = 'round'
  ctx.lineCap = 'round'
  if (item.fill && item.shape !== 'arrow') {
    ctx.fillStyle = item.color
    ctx.fill()
  }
  ctx.strokeStyle = item.color
  ctx.stroke()
  ctx.restore()
  return boxAround(item.x, item.y, item.w, h / height, 0.01)
}

// ============================== 메모지 (날짜 쪽지)
function drawNote(ctx, width, height, item) {
  const fontSize = item.w * width * 0.11
  const lines = item.text.split('\n')
  ctx.font = '700 ' + fontSize + 'px ' + HAND_FONT
  let textWidth = 0
  for (const line of lines) textWidth = Math.max(textWidth, ctx.measureText(line).width)
  const w = Math.max(item.w * width, textWidth + fontSize * 1.4) // 글자가 길면 종이도 넓게
  const h = fontSize * 1.2 * lines.length + fontSize * 1.4
  ctx.save()
  ctx.translate(item.x * width, item.y * height)
  ctx.rotate(NOTE_ANGLE)
  ctx.shadowColor = 'rgba(0, 0, 0, 0.25)'
  ctx.shadowBlur = w * 0.04
  ctx.fillStyle = NOTE_PAPER
  ctx.fillRect(-w / 2, -h / 2, w, h)
  ctx.shadowColor = 'transparent'
  ctx.fillStyle = NOTE_TAPE
  ctx.fillRect(-w / 2 - w * 0.05, -h / 2 - fontSize * 0.35, w * 0.35, fontSize * 0.7) // 왼쪽 위 테이프
  ctx.font = '700 ' + fontSize + 'px ' + HAND_FONT
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillStyle = item.color
  drawLines(ctx, lines, 0, 0, fontSize)
  ctx.restore()
  return boxAround(item.x, item.y, w / width, h / height, 0.01)
}

const DRAWERS = { stroke: drawStroke, text: drawText, sticker: drawSticker, shape: drawShape, note: drawNote }

/**
 * 사진(photo: 그림이나 캔버스) 위에 꾸미기를 canvas 크기에 맞춰 그리기
 * 돌려줌: 고를 수 있는 것(선 빼고)의 자리 목록 [{ id, x0, y0, x1, y1 }] (0~1 비율)
 */
export function renderSketch(canvas, photo, sketch) {
  const width = canvas.width
  const height = canvas.height
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, width, height)
  ctx.drawImage(photo, 0, 0, width, height)
  const layer = document.createElement('canvas') // 그림 판 (지우개가 사진은 안 지우게 따로)
  layer.width = width
  layer.height = height
  const layerCtx = layer.getContext('2d')
  const boxes = []
  for (const item of sketch.items) {
    const box = DRAWERS[item.type](layerCtx, width, height, item)
    if (box) boxes.push(Object.assign({ id: item.id }, box))
  }
  ctx.drawImage(layer, 0, 0)
  return boxes
}
