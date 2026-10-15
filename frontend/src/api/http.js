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
