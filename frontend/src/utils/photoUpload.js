// ⑤ 사진·영상 올리기 공통 (앨범·회고에서 같이 씀). POST /api/photos
import { upload } from '@/api/http'

// 두 자리로: 7 → "07"
function pad(n) {
  return String(n).padStart(2, '0')
}

// 파일 수정 시각 → "2026-11-01T10:20". 서버가 파일 안의 찍은 시각(EXIF)을 못 읽을 때만 써요
function toTakenAt(file) {
  const d = new Date(file.lastModified)
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes())
}

// 영상 길이(초). 브라우저에 영상을 잠깐 불러서 길이만 읽어요. 사진이면 0
// Promise: "다 읽으면 값을 줄게"라는 약속. 부르는 쪽에서 await로 기다려요
function readDuration(file) {
  if (!file.type.startsWith('video/')) return Promise.resolve(0)
  return new Promise((resolve) => {
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.onloadedmetadata = () => {
      URL.revokeObjectURL(video.src) // 잠깐 만든 주소 정리
      resolve(Number.isFinite(video.duration) ? Math.round(video.duration) : 0)
    }
    video.onerror = () => resolve(0)
    video.src = URL.createObjectURL(file) // 내 PC 파일을 브라우저에서 잠깐 쓰는 주소
  })
}

// 고른 파일들을 한 번에 올려요. tripId가 없으면 서버가 찍은 날짜로 여행을 찾아요
// 돌려줌: { savedCount, unmatchedCount }
export async function uploadPhotos(files, tripId) {
  const form = new FormData() // 파일을 담는 상자
  for (const file of files) {
    form.append('files', file)
    form.append('takenAts', toTakenAt(file))
    form.append('durations', await readDuration(file))
  }
  if (tripId) form.append('tripId', tripId)
  return upload('/photos', form)
}

// 영상 길이 글자: 12 → "0:12", 75 → "1:15"
export function toDurationText(sec) {
  if (!sec) return ''
  return Math.floor(sec / 60) + ':' + pad(sec % 60)
}
