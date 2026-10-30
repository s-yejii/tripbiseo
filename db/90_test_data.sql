-- 90_test_data.sql — 테스트 계정 + 샘플 여행 (DB 총괄). 01~15 다음에 실행. 다시 실행해도 중복 없음.
--  계정: tripuser / tripuser1234  (비밀번호는 BCrypt 암호문으로 저장)
--  샘플 여행: 실행한 날 하루짜리 "샘플 여행 (종로 하루)" + 일정 3칸 (장소는 03의 샘플 장소)

INSERT INTO member (username, password, nickname)
VALUES ('tripuser', '$2a$10$JC9CpqpDcKIkMrhzA1qqWOgIp7XQoSgkZQy4t80tBzEXJiV.umxZG', '여행자')
ON CONFLICT (username) DO NOTHING;

-- 이 계정에 샘플 여행이 없을 때만 넣음
INSERT INTO trip (member_id, title, start_date, end_date, people, transports)
SELECT m.member_id, '샘플 여행 (종로 하루)', CURRENT_DATE, CURRENT_DATE, 2, 'subway,bus,walk'
FROM member m
WHERE m.username = 'tripuser'
  AND NOT EXISTS (SELECT 1 FROM trip t WHERE t.member_id = m.member_id AND t.title = '샘플 여행 (종로 하루)');

-- 샘플 여행에 일정 칸이 없을 때만 3칸 넣음 (세종문화회관은 늦으면 안 되는 고정 일정)
INSERT INTO trip_stop (trip_id, day_no, place_id, name, category, address, lat, lng, start_time, stay_min, fixed)
SELECT t.trip_id, s.day_no, s.place_id, s.name, s.category, s.address, s.lat, s.lng, s.start_time, s.stay_min, s.fixed
FROM trip t
JOIN member m ON m.member_id = t.member_id
CROSS JOIN (VALUES
  (1, 'e70caff5-27cf-5c76-a1f3-201b7032d672', '경복궁',       '관광지', '서울 종로구 사직로 161', 37.5796, 126.977,  '10:00', 90,  FALSE),
  (1, '7cfdfd7b-ea87-5ef5-ae6a-aa07397d0929', '북촌한옥마을', '관광지', '서울 종로구 계동길 37',  37.5826, 126.985,  '12:30', 60,  FALSE),
  (1, '9b0d5521-9ff2-52ec-a1ea-36e9114f63ad', '광화문광장',   '관광지', '서울 종로구 세종대로 172', 37.5726, 126.9769, '19:00', 60,  TRUE)
) AS s(day_no, place_id, name, category, address, lat, lng, start_time, stay_min, fixed)
WHERE m.username = 'tripuser'
  AND t.title = '샘플 여행 (종로 하루)'
  AND NOT EXISTS (SELECT 1 FROM trip_stop x WHERE x.trip_id = t.trip_id);
