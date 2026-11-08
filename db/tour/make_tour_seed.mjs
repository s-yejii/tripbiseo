// make_tour_seed.mjs — 한국관광공사 TourAPI(국문 관광정보 KorService2)에서 서울 관광지·문화시설·쇼핑을 받아
//   db/06_retrip_seed_tour_seoul.sql 을 만들어요. (11/03, 팀은 이 SQL만 실행: 키·API 호출 필요 없음)
//
// 실행 (PC에서 한 번, Node 18 이상 · 프로젝트 폴더에서)
//   set TOUR_API_KEY=공공데이터포털 인증키        (PowerShell: $env:TOUR_API_KEY="키")
//   node db/tour/make_tour_seed.mjs              목록 + 상세(영업시간·휴무·소개)까지
//   node db/tour/make_tour_seed.mjs --list-only  목록만 받아 바로 SQL (상세는 나중에 다시 실행해서 채움)
//
// 순서: ① areaBasedList2 목록(이름·주소·좌표·사진) → ② detailIntro2 영업시간·휴무 → ③ detailCommon2 소개글
//   받은 것은 db/tour/tour_cache.json 에 계속 저장 → 하루 호출 한도에 걸려 멈춰도 다음 날 다시 실행하면 이어서 받아요
//   SQL은 ON CONFLICT (source, source_id) DO UPDATE: 다시 만들어 실행하면 비어 있던 영업시간·소개가 채워져요
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const BASE_URL = 'https://apis.data.go.kr/B551011/KorService2'
const SEOUL_AREA_CODE = '1' // TourAPI 지역 코드: 서울
const SEOUL_LDONG_CODE = '11' // 지역 코드로 0건이면 법정동 시도 코드(서울 11)로 다시
// 받을 종류 (우리 DB content_type_id와 같은 번호)
const CONTENT_TYPES = { 12: '관광지', 14: '문화시설', 38: '쇼핑' }
// 종류마다 영업시간·휴무 칸 이름이 달라요 (detailIntro2)
const HOURS_FIELDS = {
  12: { use: 'usetime', rest: 'restdate' },
  14: { use: 'usetimeculture', rest: 'restdateculture' },
  38: { use: 'opentime', rest: 'restdateshopping' },
}
// 여행 갈 곳이 아니라서 받지 않는 곳 (동상·기념비·어린이공원·~터 등, 11/06). db/08_retrip_cleanup.sql과 같은 목록
const EXCLUDE_IDS = new Set(['1829744', '1836300', '2733981', '2773287', '2591833', '127308', '1837037', '2751339', '126827',
  '2753103', '1835206', '1836261', '1829573', '1829586', '605961', '605497', '126494', '2993372',
  '2946242', '2946527', '3432447', '2591885', // 가게·방탈출·끝난 행사·장난감 대여점 (11/06)
  '2848108', '3446417', '398373', '1998333', '2789182', '2864267', '2706047']) // 네이버 가게·다른 관광공사 항목과 겹치는 곳 (11/07)
const ROWS_PER_PAGE = 100
const WAIT_MS = 80 // 호출 사이 쉬는 시간 (서버에 무리 안 주게)
const SAVE_EVERY = 20 // 상세 몇 개마다 캐시 저장
const SQL_CHUNK = 300 // INSERT 한 문장에 넣을 줄 수

const here = path.dirname(fileURLToPath(import.meta.url))
const CACHE_FILE = path.join(here, 'tour_cache.json')
const SQL_FILE = path.join(here, '..', '06_retrip_seed_tour_seoul.sql')
const isListOnly = process.argv.includes('--list-only')

// 인증키: 포털에서 복사한 키가 %로 인코딩돼 있으면 풀어서 씀 (URLSearchParams가 다시 인코딩함)
let serviceKey = process.env.TOUR_API_KEY || ''
if (!serviceKey) {
  console.error('TOUR_API_KEY 환경변수가 없어요. 예) set TOUR_API_KEY=키값')
  process.exit(1)
}
if (serviceKey.includes('%')) serviceKey = decodeURIComponent(serviceKey)

class QuotaError extends Error {} // 하루 호출 한도 초과

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// TourAPI 한 번 부르기 → body. 한도 초과·키 오류는 에러로
async function callTour(operation, params) {
  const query = new URLSearchParams({ serviceKey, MobileOS: 'ETC', MobileApp: 'tripbiseo', _type: 'json', ...params })
  const res = await fetch(`${BASE_URL}/${operation}?${query}`)
  const text = await res.text()
  await sleep(WAIT_MS)
  // 한도 초과·인증 오류는 _type=json이어도 XML로 올 때가 있어요
  if (text.trim().startsWith('<')) {
    const message = (text.match(/<returnAuthMsg>([^<]+)</) || text.match(/<resultMsg>([^<]+)</) || [])[1] || text.slice(0, 200)
    if (/LIMITED|EXCEEDS/.test(message)) throw new QuotaError(message)
    throw new Error(`${operation} 실패: ${message}`)
  }
  if (res.status === 429) throw new QuotaError('429 Too Many Requests')
  const json = JSON.parse(text)
  const header = json.response?.header
  if (header && header.resultCode !== '0000') {
    if (/LIMITED|EXCEEDS/.test(header.resultMsg)) throw new QuotaError(header.resultMsg)
    throw new Error(`${operation} 실패: ${header.resultCode} ${header.resultMsg}`)
  }
  return json.response?.body || {}
}

// 공공데이터 API는 결과가 1건이면 배열이 아니라 객체, 0건이면 빈 글자 → 항상 배열로
function toItems(body) {
  const item = body.items?.item
  if (!item) return []
  return Array.isArray(item) ? item : [item]
}

function loadCache() {
  if (!fs.existsSync(CACHE_FILE)) return { places: {} }
  return JSON.parse(fs.readFileSync(CACHE_FILE, 'utf8'))
}
function saveCache(cache) {
  fs.writeFileSync(CACHE_FILE, JSON.stringify(cache))
}

// ① 목록: 종류마다 페이지를 넘기며 전부
async function fetchList(cache) {
  for (const [typeId, typeName] of Object.entries(CONTENT_TYPES)) {
    let area = { areaCode: SEOUL_AREA_CODE }
    let first = await callTour('areaBasedList2', { ...area, contentTypeId: typeId, numOfRows: ROWS_PER_PAGE, pageNo: 1, arrange: 'A' })
    if (!Number(first.totalCount)) {
      area = { lDongRegnCd: SEOUL_LDONG_CODE }
      first = await callTour('areaBasedList2', { ...area, contentTypeId: typeId, numOfRows: ROWS_PER_PAGE, pageNo: 1, arrange: 'A' })
    }
    const total = Number(first.totalCount) || 0
    const pages = Math.ceil(total / ROWS_PER_PAGE)
    let added = 0
    for (let page = 1; page <= pages; page++) {
      const body = page === 1 ? first : await callTour('areaBasedList2', { ...area, contentTypeId: typeId, numOfRows: ROWS_PER_PAGE, pageNo: page, arrange: 'A' })
      for (const item of toItems(body)) {
        if (!Number(item.mapx) || !Number(item.mapy)) continue // 좌표 없는 곳은 지도에 못 올려서 뺌
        const id = String(item.contentid)
        if (EXCLUDE_IDS.has(id)) continue
        if (!cache.places[id]) added++
        cache.places[id] = { ...cache.places[id], list: item, typeId: String(typeId) }
      }
    }
    saveCache(cache)
    console.log(`① ${typeName}(${typeId}): 서울 ${total}곳 중 좌표 있는 곳 새로 ${added}곳`)
  }
}

// ②③ 상세: 아직 안 받은 곳만 (한도에 걸리면 저장하고 멈춤)
async function fetchDetails(cache) {
  const ids = Object.keys(cache.places).filter((id) => !cache.places[id].detailDone)
  console.log(`②③ 상세를 받을 곳 ${ids.length}곳`)
  let count = 0
  try {
    for (const id of ids) {
      const place = cache.places[id]
      const intro = toItems(await callTour('detailIntro2', { contentId: id, contentTypeId: place.typeId }))[0] || {}
      let common = toItems(await callTour('detailCommon2', { contentId: id }))[0] || {}
      if (common.overview === undefined) {
        // 예전 방식 서버면 무엇을 받을지(YN)를 붙여야 소개가 와요
        common = toItems(await callTour('detailCommon2', { contentId: id, defaultYN: 'Y', overviewYN: 'Y' }))[0] || {}
      }
      place.intro = intro
      place.overview = common.overview || null
      place.detailDone = true
      count++
      if (count % SAVE_EVERY === 0) {
        saveCache(cache)
        console.log(`   ${count}/${ids.length}`)
      }
    }
  } catch (e) {
    saveCache(cache)
    if (e instanceof QuotaError) {
      console.log(`!! 오늘 호출 한도에 걸렸어요 (${e.message}). ${count}곳까지 받았어요. 내일 다시 실행하면 이어서 받아요.`)
      return
    }
    throw e
  }
  saveCache(cache)
  console.log(`②③ 상세 ${count}곳 받음`)
}

// HTML 태그·엔티티를 글자로 (<br> → 줄바꿈)
function cleanText(value) {
  if (value === undefined || value === null) return null
  const text = String(value)
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .trim()
  return text || null
}

// SQL 글자 값 ('는 두 번, 없으면 NULL)
function sqlText(value) {
  const text = cleanText(value)
  return text === null ? 'NULL' : `'${text.replace(/'/g, "''")}'`
}

function toRow(id, place, now) {
  const item = place.list
  const hours = HOURS_FIELDS[place.typeId] || {}
  const intro = place.intro || {}
  const address = [cleanText(item.addr1), cleanText(item.addr2)].filter(Boolean).join(' ')
  return `(${sqlText('tour-' + id)},'tour',${sqlText(id)},${sqlText(item.title)},'PLAY',${sqlText(place.typeId)},`
    + `${sqlText(address)},${sqlText(item.tel)},${Number(item.mapx)},${Number(item.mapy)},`
    + `${sqlText(intro[hours.use])},${sqlText(intro[hours.rest])},${sqlText(item.firstimage)},${sqlText(place.overview)},'${now}','${now}')`
}

// ④ SQL 파일 쓰기
function writeSql(cache) {
  const now = new Date().toISOString()
  const ids = Object.keys(cache.places).filter((id) => !EXCLUDE_IDS.has(id)) // 캐시에 남아 있어도 SQL에는 안 씀
  const lines = [
    '-- 06_retrip_seed_tour_seoul.sql — 서울 관광지(12)·문화시설(14)·쇼핑(38) (한국관광공사 TourAPI KorService2). 04 다음에 실행',
    '--  만든 곳: db/tour/make_tour_seed.mjs (직접 고치지 말고 스크립트로 다시 만들어요)',
    `--  ${ids.length}곳 · 상세(영업시간·휴무·소개) 받은 곳 ${ids.filter((id) => cache.places[id].detailDone).length}곳 · 만든 시각 ${now}`,
    '--  다시 실행해도 중복 없음. 이미 있는 곳은 비어 있던 영업시간·휴무·사진·소개만 채워요',
    '',
  ]
  for (let i = 0; i < ids.length; i += SQL_CHUNK) {
    const rows = ids.slice(i, i + SQL_CHUNK).map((id) => toRow(id, cache.places[id], now))
    lines.push('INSERT INTO retrip_place (id,source,source_id,name,category,content_type_id,address,tel,longitude,latitude,use_time,rest_date,image_url,overview,created_at,updated_at) VALUES')
    lines.push(rows.join(',\n'))
    lines.push('ON CONFLICT (source, source_id) DO UPDATE SET')
    lines.push('  use_time = COALESCE(retrip_place.use_time, EXCLUDED.use_time), rest_date = COALESCE(retrip_place.rest_date, EXCLUDED.rest_date),')
    lines.push('  image_url = COALESCE(retrip_place.image_url, EXCLUDED.image_url), overview = COALESCE(retrip_place.overview, EXCLUDED.overview);')
    lines.push('')
  }
  fs.writeFileSync(SQL_FILE, lines.join('\n'))
  console.log(`④ ${SQL_FILE} 에 ${ids.length}곳 썼어요`)
}

// 구별 개수 (확인용)
function printGuCounts(cache) {
  const counts = {}
  for (const place of Object.values(cache.places)) {
    const gu = (String(place.list.addr1 || '').match(/서울(?:특별시)?\s+(\S+구)/) || [])[1] || '(구 모름)'
    counts[gu] = (counts[gu] || 0) + 1
  }
  console.log('구별:', Object.entries(counts).sort((a, b) => b[1] - a[1]).map(([gu, n]) => `${gu} ${n}`).join(', '))
}

const cache = loadCache()
await fetchList(cache)
printGuCounts(cache)
if (!isListOnly) await fetchDetails(cache)
writeSql(cache)
