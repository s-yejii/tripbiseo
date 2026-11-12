// 위치·알림 권한 화면(S03)을 본 아이디 기억 (11/12 ①): 로그인할 때마다 뜨지 않고 처음 한 번만 뜨게
// 이 브라우저에만 저장해요 (localStorage). 다른 PC·브라우저에서는 처음 한 번 다시 떠요
import { get } from '@/api/http'

const KEY_PREFIX = 'permissionSeen_'

// 이 아이디가 권한 화면을 본 적 있는지 (저장이 막힌 브라우저면 false → 화면을 보여 줌)
export function hasSeenPermission(username) {
  try {
    return localStorage.getItem(KEY_PREFIX + username) === '1'
  } catch (e) {
    return false
  }
}

// 이 아이디가 권한 화면을 봤다고 기억
export function markPermissionSeen(username) {
  try {
    localStorage.setItem(KEY_PREFIX + username, '1')
  } catch (e) {
    // 저장이 막힌 브라우저면 다음 로그인 때 다시 보여 줘요
  }
}

// 지금 로그인한 아이디로 "봤음" 기억 (권한 화면에서 버튼을 누를 때)
export async function markPermissionSeenForMe() {
  try {
    const user = await get('/auth/me')
    markPermissionSeen(user.username)
  } catch (e) {
    // 로그인 안 한 상태면 기억할 아이디가 없어서 넘어가요
  }
}

// 로그인·가입 뒤 갈 곳: 권한 화면을 봤으면 홈, 처음이면 권한 화면
export function getPathAfterLogin(username) {
  if (hasSeenPermission(username)) {
    return '/'
  }
  return '/permission'
}
