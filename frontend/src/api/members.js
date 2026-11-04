// ① 회원 API (주소·요청·응답은 docs/API.md 그대로)
import { get, post, put } from '@/api/http'

export const getMe = () => get('/members/me')
export const updateMe = (nickname) => put('/members/me', { nickname })
export const changePassword = (currentPassword, newPassword) => put('/members/me/password', { currentPassword, newPassword })

// 이메일 인증번호 · 계정 찾기 (로그인 전, purpose: signup / find)
export const sendEmailCode = (email, purpose, username = null) => post('/auth/email-code', { email, purpose, username })
export const verifyEmailCode = (email, code, purpose) => post('/auth/email-code/verify', { email, code, purpose })
export const resetPassword = (email, newPassword) => post('/auth/reset-password', { email, newPassword })
