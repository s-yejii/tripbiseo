// 일정을 휴지통으로 옮긴 뒤 토스트: 되돌리기를 누르면 복원하고 onUndo를 불러요 (화면 다시 읽기)
import { restoreTrip } from '@/api/trips'
import { showToast } from '@/utils/toast'

export function showTrashToast(tripId, onUndo) {
  showToast('여행 기록으로 옮겼어요', {
    actionText: '되돌리기',
    action: async () => {
      try {
        await restoreTrip(tripId)
        showToast('되돌렸어요')
        if (onUndo) await onUndo()
      } catch (e) {
        showToast(e.message)
      }
    },
  })
}
