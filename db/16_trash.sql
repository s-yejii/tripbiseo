-- 11/04 ③ 김주형 — 휴지통. 이상재(DB 총괄)·이찬기(DELETE /api/trips 담당) 확인 필요. 각자 \i 'db/16_trash.sql' 실행
-- deleted_at: NULL이면 보통 일정, 값이 있으면 휴지통에 있는 일정이고 그 값이 지운 시각
-- status·started_at·finished_at은 그대로 둬서, 복원하면 원래 상태(예정·진행·지난·보관)로 돌아가요
ALTER TABLE trip ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
