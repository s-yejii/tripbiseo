// 서버 호출 공통. 실패하면 서버가 보낸 message로 Error를 던집니다.
export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

export async function request(method, path, body) {
  let res
  try {
    res = await fetch('/api' + path, {
      method,
      credentials: 'include',
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, '서버에 연결할 수 없어요. 스프링 서버(8080)가 켜져 있는지 확인하세요.')
  }
  const data = res.status === 204 ? null : await res.json().catch(() => null)
  if (!res.ok) throw new ApiError(res.status, data?.message || `요청에 실패했어요 (${res.status})`)
  return data
}

export const get = (path) => request('GET', path)
export const post = (path, body = {}) => request('POST', path, body)
export const put = (path, body = {}) => request('PUT', path, body)
export const del = (path) => request('DELETE', path)

// 파일 올리기 (FormData: 파일을 담는 상자. Content-Type은 브라우저가 알아서 붙여요)
export async function upload(path, formData) {
  let res
  try {
    res = await fetch('/api' + path, { method: 'POST', credentials: 'include', body: formData })
  } catch {
    throw new ApiError(0, '서버에 연결할 수 없어요. 스프링 서버(8080)가 켜져 있는지 확인하세요.')
  }
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new ApiError(res.status, data?.message || `요청에 실패했어요 (${res.status})`)
  return data
}

// 진행 알림 받기 (11/04). SSE: 서버가 응답 하나로 알림을 여러 번 나눠 보내요 ("event: 이름" + "data: JSON" + 빈 줄)
// 알림마다 onEvent(이름, 값)을 부르고, done이 오면 그 값을 돌려주고, fail이 오면 ApiError를 던져요.
// res.body.getReader(): 응답을 다 기다리지 않고 도착한 만큼씩 읽는 도구
export async function postStream(path, body, onEvent) {
  let res
  try {
    res = await fetch('/api' + path, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, '서버에 연결할 수 없어요. 스프링 서버(8080)가 켜져 있는지 확인하세요.')
  }
  if (!res.ok) {
    const data = await res.json().catch(() => null)
    throw new ApiError(res.status, data?.message || `요청에 실패했어요 (${res.status})`)
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '')
    let end = buffer.indexOf('\n\n') // 알림 하나는 빈 줄로 끝나요
    while (end >= 0) {
      const event = parseEvent(buffer.slice(0, end))
      buffer = buffer.slice(end + 2)
      if (event.name === 'done') return event.data
      if (event.name === 'fail') throw new ApiError(event.data.status, event.data.message)
      onEvent(event.name, event.data)
      end = buffer.indexOf('\n\n')
    }
  }
  throw new ApiError(0, '일정을 만드는 중에 연결이 끊겼어요. 다시 시도해 주세요.')
}

// 알림 한 덩어리 → { name, data }
function parseEvent(block) {
  let name = 'message'
  const lines = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) name = line.slice(6).trim()
    else if (line.startsWith('data:')) lines.push(line.slice(5).replace(/^ /, ''))
  }
  return { name, data: lines.length ? JSON.parse(lines.join('\n')) : null }
}
