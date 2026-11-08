-- biz-group-매핑.sql — 업종(biz_type)을 큰 묶음(biz_group)으로 (2026-11-07, ① 이상재 요청, 노트북 DB 시험용)
--  biz_type 원래 값은 그대로 둠. 이유: 코드가 biz_type 글자를 직접 씀
--    PlaceMapper.xml kindCase(카페·술집·맛집 나누기)·검색(ILIKE), 화면 분류 글자(Place.category),
--    LiveServiceImpl·PaymentServiceImpl의 category.contains("카페") 등 → 값을 바꾸면 분류·검색·화면이 달라짐
--  새 칸 biz_group만 추가 (학원 PC 07의 place_group과 이름 안 겹치게). 코드는 아직 이 칸을 안 씀
--  다시 실행해도 같은 결과. 새 업종이 들어오면(내일 영등포 크롤링) ① 정확한 이름 목록 → ② 글자 규칙 순서로 묶이고,
--  둘 다 안 맞으면 NULL(미분류)로 남음 → 맨 아래 확인 쿼리로 보고 목록에 추가
--  repo에 넣을 때는 docs/DB.md에 먼저 등록 (DB 총괄 이상재)

ALTER TABLE retrip_place ADD COLUMN IF NOT EXISTS biz_group VARCHAR(20);

UPDATE retrip_place SET biz_group = CASE
  -- 관광공사·시연 장소(음식점 아님)는 분류 번호로
  WHEN content_type_id IN ('12','15','28') THEN '관광'
  WHEN content_type_id = '14' THEN '문화'
  WHEN content_type_id = '38' THEN '쇼핑'
  WHEN content_type_id = '32' THEN '숙박'
  WHEN content_type_id <> '39' OR content_type_id IS NULL THEN NULL

  -- 애매: 여행 식당으로 둘지 상재가 정함 (묶지 않음)
  WHEN biz_type IN ('고양이카페','애견카페','테마카페','가공식품','수산물','호두과자') THEN NULL

  -- ① 정확한 이름 목록 (11/07 노트북 DB의 네이버 업종 전부)
  WHEN biz_type IN ('한식','국밥','감자탕','한정식','국수','냉면','곰탕,설렁탕','찌개,전골','백숙,삼계탕','닭볶음탕','해장국',
                    '두부요리','보리밥','막국수','칼국수,만두','순대,순댓국','백반,가정식','전,빈대떡','갈비탕','추어탕','쌈밥','죽',
                    '향토음식','찜닭','닭요리','샤브샤브','닭발') THEN '한식'
  WHEN biz_type IN ('육류,고기요리','돼지고기구이','소고기구이','곱창,막창,양','족발,보쌈','정육식당','양갈비','닭갈비','오리요리') THEN '고기·구이'
  WHEN biz_type IN ('생선회','해물,생선요리','조개요리','장어,먹장어요리','주꾸미요리','게요리','아귀찜,해물찜','생선구이','낙지요리',
                    '복어요리','대게요리','오징어요리','킹크랩요리','바닷가재요리','매운탕,해물탕') THEN '해산물'
  WHEN biz_type IN ('일식당','초밥,롤','돈가스','일본식라면','우동,소바','일식튀김,꼬치','오니기리','카레','덮밥') THEN '일식'
  WHEN biz_type IN ('중식당','마라탕','딤섬,중식만두','양꼬치') THEN '중식'
  WHEN biz_type IN ('양식','이탈리아음식','피자','스파게티,파스타전문','스테이크,립','패밀리레스토랑','스페인음식','프랑스음식','브런치',
                    '독일음식','그리스음식','퓨전음식','오므라이스','다이어트,샐러드') THEN '양식'
  WHEN biz_type IN ('멕시코,남미음식','베트남음식','아시아음식','태국음식','인도음식','터키음식') THEN '아시아·외국'
  WHEN biz_type IN ('햄버거','핫도그','치킨,닭강정','샌드위치','토스트') THEN '패스트푸드'
  WHEN biz_type IN ('분식','종합분식','김밥','떡볶이','만두','라면','도시락,컵밥','불닭','호떡') THEN '분식'
  WHEN biz_type IN ('뷔페','일식,초밥뷔페','해산물뷔페','한식뷔페','고기뷔페') THEN '뷔페'
  WHEN biz_type IN ('카페','카페,디저트','브런치카페','차','테이크아웃커피','떡카페','홍차전문점','아이스크림','와플','크레페',
                    '초콜릿전문점','한방카페','북카페','갤러리카페') THEN '카페·디저트'
  WHEN biz_type IN ('베이커리','케이크전문','도넛','베이글') THEN '베이커리'
  WHEN biz_type IN ('이자카야','요리주점','맥주,호프','와인','포장마차','바(BAR)','전통,민속주점','술집') THEN '술집'

  -- ② 목록에 없는 새 업종: 글자 규칙 (위에서 아래 순서로 먼저 맞는 것)
  WHEN biz_type ~ '스터디|PC방|노래방|키즈카페|애견|고양이' THEN NULL
  WHEN biz_type ~ '이자카야|주점|맥주|호프|와인|포장마차|BAR|술' THEN '술집'
  WHEN biz_type ~ '뷔페' THEN '뷔페'
  WHEN biz_type ~ '베이커리|빵|케이크|도넛|베이글' THEN '베이커리'
  WHEN biz_type ~ '카페|커피|디저트|아이스크림|^차$|차전문' THEN '카페·디저트'
  WHEN biz_type ~ '일식|초밥|스시|라멘|우동|소바|돈가스|돈까스|오마카세' THEN '일식'
  WHEN biz_type ~ '중식|중국|마라|딤섬' THEN '중식'
  WHEN biz_type ~ '햄버거|버거|치킨|핫도그|샌드위치|토스트' THEN '패스트푸드'
  WHEN biz_type ~ '분식|떡볶이|김밥' THEN '분식'
  WHEN biz_type ~ '회$|생선|해물|해산물|조개|게요리|장어|낙지|주꾸미|대게|랍스터' THEN '해산물'
  WHEN biz_type ~ '고기|구이|곱창|막창|갈비|족발|보쌈|삼겹' THEN '고기·구이'
  WHEN biz_type ~ '양식|이탈리아|피자|파스타|스테이크|프랑스|스페인|브런치' THEN '양식'
  WHEN biz_type ~ '음식$' THEN '아시아·외국'      -- 베트남음식·태국음식처럼 "○○음식"
  WHEN biz_type ~ '한식|국밥|탕$|국수|냉면|찌개|백반|정식' THEN '한식'
  ELSE NULL
END;
