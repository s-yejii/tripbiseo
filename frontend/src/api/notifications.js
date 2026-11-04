// ① 알림함 API (주소·요청·응답은 docs/API.md 그대로)
import { get, post } from '@/api/http'

export const getNotificationList = () => get('/notifications') // { unreadCount, notifications: [...] }
export const readNotification = (id) => post('/notifications/' + id + '/read')
export const readAllNotifications = () => post('/notifications/read-all')
