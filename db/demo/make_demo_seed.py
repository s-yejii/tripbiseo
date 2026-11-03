# 가상 사용자 데이터 만들기 (시연용) → db/95_demo_seed.sql
#   실행: python db/demo/make_demo_seed.py   (db/04_retrip_seed_naver_places.sql의 실제 장소를 읽어서 만들어요)
#   같은 시드(SEED)면 누가 돌려도 같은 결과예요. 날짜는 SQL을 실행한 날(CURRENT_DATE) 기준이라
#   발표 직전에 지우고 다시 실행하면 "최근 30일" 기록이 다시 살아나요.
#   지우기: DELETE FROM member WHERE username LIKE 'demo\_%';  (여행·일정 칸도 같이 지워짐)
import math
import random
import re
from datetime import date, timedelta
from pathlib import Path

SEED = 20261103
REFERENCE_DAY = date(2026, 11, 3)  # 주말 비율을 정할 때 기준으로 삼는 날 (SQL에서는 CURRENT_DATE 기준)
MEMBER_COUNT = 300
PASSWORD_HASH = '$2a$10$cyetXWlD1wnXfv4KKNbuae9obVtciVO.VBsXlP8fyHzu0l4Iyfoq2'  # demo1234!의 BCrypt 암호문 (고정: 다시 만들어도 파일이 안 바뀌게)
DEMO_PASSWORD = 'demo1234!'
GU_LIST = ['마포구', '용산구', '종로구', '중구', '성동구', '서초구', '강남구', '송파구']
GU_SHORT = {'마포구': '마포', '용산구': '용산', '종로구': '종로', '중구': '명동·을지로', '성동구': '성수', '서초구': '서초', '강남구': '강남', '송파구': '잠실'}
CAFE = re.compile('카페|커피|디저트|베이커리|케이크|도넛|와플|아이스크림|베이글|크레페|^차$')
BAR = re.compile('이자카야|주점|맥주|호프|포장마차|바\\(BAR\\)|와인')
DAY_NAMES = '월화수목금토일'

# 취향 유형: 고르는 종류 비율 (food 식당 / cafe 카페 / bar 술집 / tour 관광·문화)
PERSONAS = {
    '카페': {'share': 0.30, 'kinds': {'cafe': 0.50, 'food': 0.40, 'bar': 0.05, 'tour': 0.05}, 'title': ['카페 투어', '카페 하루', '디저트 산책']},
    '맛집': {'share': 0.35, 'kinds': {'food': 0.65, 'cafe': 0.25, 'bar': 0.10, 'tour': 0.00}, 'title': ['맛집 하루', '먹방 투어', '맛집 탐방']},
    '노포': {'share': 0.15, 'kinds': {'food': 0.45, 'bar': 0.35, 'cafe': 0.20, 'tour': 0.00}, 'title': ['노포 한 바퀴', '골목 술집 투어', '노포 하루']},
    '관광': {'share': 0.20, 'kinds': {'tour': 0.40, 'cafe': 0.30, 'food': 0.30, 'bar': 0.00}, 'title': ['산책 하루', '나들이', '하루 여행']},
}
FOOD_GAP_MIN = 150      # 식당 다음 식당은 2시간 반 뒤부터
FOOD_MAX_PER_DAY = 3
STAY_MIN = {'cafe': 60, 'food': 70, 'bar': 90, 'tour': 90}
MEAL_SLOTS = [(11 * 60 + 30, 13 * 60 + 30), (17 * 60 + 30, 19 * 60 + 30)]
NEAR_KM = 1.5
TREND_PER_GU = 3        # 구마다 "요즘 뜨는 곳" 수
TREND_DAYS = 14         # 최근 며칠 여행에서 더 자주 뽑을지
TREND_WEIGHT = 8
BAR_POOL_PER_GU = 20    # 술집은 리뷰 수가 적어서 따로 이만큼
POOL_PER_GU = 80        # 구마다 리뷰 많은 순 이만큼만 후보 (실제로도 사람들은 알려진 곳에 몰려요)
WEIGHT_POWER = 0.8      # 뽑힐 비율 = 리뷰 수^0.8 (리뷰 많은 곳이 더 자주, 그래도 한 곳에 다 몰리지는 않게)
NICK_FIRST = ['느긋한', '배고픈', '걷는', '주말', '산책하는', '카페가는', '여행하는', '동네', '골목', '빵순이', '커피', '한강']
NICK_SECOND = ['곰', '여우', '토끼', '고양이', '펭귄', '다람쥐', '수달', '부엉이', '민지', '준호', '하늘', '지수', '서연', '도윤']

random.seed(SEED)
ROOT = Path(__file__).resolve().parents[1]


def parse_rows(text):
    """INSERT ... VALUES (...),(...) 글 전체 → 행 목록 (작은따옴표 안의 '' · 줄바꿈 처리)"""
    rows, row, i, n = [], None, 0, len(text)
    while i < n:
        c = text[i]
        if row is None:
            if c == '(' and text[i + 1] == "'":
                row = []
            i += 1
            continue
        if c == "'":
            j, buf = i + 1, []
            while True:
                if text[j] == "'" and j + 1 < n and text[j + 1] == "'":
                    buf.append("'")
                    j += 2
                elif text[j] == "'":
                    break
                else:
                    buf.append(text[j])
                    j += 1
            row.append(''.join(buf))
            i = j + 1
        elif text.startswith('NULL', i):
            row.append(None)
            i += 4
        elif c == ')':
            rows.append(row)
            row = None
            i += 1
        elif c in ', \n\r':
            i += 1
        else:
            j = i
            while text[j] not in ',)':
                j += 1
            row.append(text[i:j].strip())
            i = j
    return rows


def load_places():
    cols = ['id', 'source', 'source_id', 'name', 'category', 'content_type_id', 'address', 'tel', 'longitude', 'latitude',
            'use_time', 'rest_date', 'image_url', 'biz_type', 'rating', 'review_count']
    places = []
    text = (ROOT / '04_retrip_seed_naver_places.sql').read_text(encoding='utf-8')
    for values in parse_rows(text[text.index('VALUES'):]):
        v = dict(zip(cols, values))
        address = v['address'] or ''
        parts = address.split()
        gu = parts[1] if len(parts) > 1 and parts[0].startswith('서울') else None
        if gu not in GU_LIST:
            continue
        biz = v['biz_type'] or ''
        if v['category'] == 'PLAY':
            kind = 'tour'
        elif v['category'] == 'STAY':
            continue  # 숙박은 하루 일정에 넣지 않음
        elif CAFE.search(biz):
            kind = 'cafe'
        elif BAR.search(biz):
            kind = 'bar'
        else:
            kind = 'food'
        rating = float(v['rating']) if v['rating'] else None
        if rating is not None and rating < 3.0:
            continue
        reviews = int(v['review_count']) if v['review_count'] else 0
        places.append({
            'id': v['id'], 'gu': gu, 'kind': kind, 'lat': float(v['latitude']), 'lng': float(v['longitude']),
            'weight': reviews ** WEIGHT_POWER + 3, 'reviews': reviews, 'closed': closed_days(v['use_time'] or '', v['rest_date'] or ''),
        })
    return places


def closed_days(use_time, rest_date):
    """'월 휴무'·'정기휴무 (매주 화요일)' 같은 글에서 쉬는 요일 번호(월=0)"""
    text = use_time + ' ' + rest_date
    days = set()
    for k, name in enumerate(DAY_NAMES):
        if re.search(name + r'(요일)?\s*(정기)?휴무', text) or re.search(r'매주\s*' + name, text):
            days.add(k)
    return days


def km(a, b):
    dx = (a['lng'] - b['lng']) * 88.8
    dy = (a['lat'] - b['lat']) * 111.0
    return math.hypot(dx, dy)


def pick(candidates, weight_of):
    weights = [weight_of(p) for p in candidates]
    total = sum(weights)
    if total <= 0:
        return None
    return random.choices(candidates, weights=weights, k=1)[0]


def weighted_choice(table):
    keys = list(table)
    return random.choices(keys, weights=[table[k] for k in keys], k=1)[0]


def pick_offset():
    """여행 날짜: 90%는 지난 60일, 10%는 앞으로 30일. 주말은 2배"""
    if random.random() < 0.9:
        days = list(range(-60, 0))
    else:
        days = list(range(1, 31))
    weights = [2 if (REFERENCE_DAY + timedelta(d)).weekday() >= 5 else 1 for d in days]
    return random.choices(days, weights=weights, k=1)[0]


def hhmm(minutes):
    return '%02d:%02d' % (minutes // 60, minutes % 60)


def plan_day(gu, persona, places_by_gu, trend, offset):
    weekday = (REFERENCE_DAY + timedelta(offset)).weekday()
    is_recent = -TREND_DAYS <= offset < 0
    pool = [p for p in places_by_gu[gu] if weekday not in p['closed']]
    kinds = PERSONAS[persona]['kinds']
    count = random.randint(4, 6)
    if persona == '노포':
        clock = random.choice(range(14 * 60, 17 * 60 + 1, 30))  # 노포·술집형은 오후에 시작해서 저녁까지
    else:
        clock = random.choice(range(10 * 60, 13 * 60 + 1, 30))
    stops, used, meals_done = [], set(), set()
    last_food, food_count = None, 0
    prev = None
    for _ in range(count):
        ate_recently = last_food is not None and clock - last_food < FOOD_GAP_MIN
        meal = next((k for k, (s, e) in enumerate(MEAL_SLOTS) if s <= clock <= e and k not in meals_done), None)
        if meal is not None and not ate_recently:
            kind = 'food'
            meals_done.add(meal)
        else:
            kind = weighted_choice({k: w for k, w in kinds.items() if not (k == 'bar' and clock < 17 * 60)} or {'cafe': 1})
            if kind == 'food' and (ate_recently or food_count >= FOOD_MAX_PER_DAY):
                kind = 'bar' if clock >= 19 * 60 else 'cafe'  # 방금 먹었으면 카페, 저녁 늦게면 술집
        near = [p for p in pool if p['id'] not in used and (prev is None or km(p, prev) <= NEAR_KM)]
        same_kind = [p for p in near if p['kind'] == kind]
        if not same_kind and kind != 'food':
            same_kind = [p for p in near if p['kind'] in ('cafe', 'bar')]  # 식당을 피해야 할 때는 식당으로 채우지 않음
        if not same_kind:
            same_kind = [p for p in near if p['kind'] != 'tour'] or near
        if not same_kind:
            break
        place = pick(same_kind, lambda p: p['weight'] * (TREND_WEIGHT if is_recent and p['id'] in trend else 1))
        if place is None:
            break
        stay = max(20, STAY_MIN[place['kind']] + random.choice([-20, -10, 0, 0, 10, 20]))
        stops.append({'place': place, 'time': clock, 'stay': stay})
        used.add(place['id'])
        prev = place
        if place['kind'] == 'food':
            last_food = clock
            food_count += 1
        clock += stay
        if clock >= 22 * 60:
            break
        # 다음 장소까지 이동: 앞으로 고를 곳은 1.5km 안이라 걸어서 5~20분
        clock = int(math.ceil((clock + random.choice([5, 10, 15, 20])) / 5.0) * 5)
    return stops


def main():
    places = load_places()
    places_by_gu = {}
    for gu in GU_LIST:
        in_gu = [p for p in places if p['gu'] == gu]
        tours = [p for p in in_gu if p['kind'] == 'tour']  # 관광·문화(종로)는 리뷰 수가 없어서 따로 다 넣음
        others = sorted([p for p in in_gu if p['kind'] in ('food', 'cafe')], key=lambda p: -p['reviews'])[:POOL_PER_GU]
        bars = sorted([p for p in in_gu if p['kind'] == 'bar'], key=lambda p: -p['reviews'])[:BAR_POOL_PER_GU]
        places_by_gu[gu] = others + bars + tours
    trend = set()
    for gu in GU_LIST:
        ranked = sorted([p for p in places_by_gu[gu] if p['kind'] in ('food', 'cafe')], key=lambda p: -p['reviews'])
        mid = ranked[30:90]  # 아주 유명하진 않은 곳이 요즘 뜨는 모습
        for p in random.sample(mid, min(TREND_PER_GU, len(mid))):
            trend.add(p['id'])
    gu_weights = {gu: 1 for gu in GU_LIST}  # 8개 구에 고르게 (장소 수 비율로 하면 종로에 몰림)
    persona_weights = {k: v['share'] for k, v in PERSONAS.items()}

    members, trips, stops = [], [], []
    for n in range(1, MEMBER_COUNT + 1):
        username = 'demo_%03d' % n
        nickname = random.choice(NICK_FIRST) + random.choice(NICK_SECOND)
        persona = weighted_choice(persona_weights)
        home_gu = weighted_choice(gu_weights)
        members.append((username, nickname))
        titles = set()
        for _ in range(random.choices([1, 2, 3, 4, 5], weights=[15, 25, 30, 20, 10])[0]):
            gu = home_gu if random.random() < 0.7 else weighted_choice(gu_weights)
            if persona == '관광' and gu != '종로구' and random.random() < 0.3:
                gu = '종로구'  # 관광·문화 장소가 종로에만 있어서
            offset = pick_offset()
            days = 2 if random.random() < 0.15 else 1
            title = GU_SHORT[gu] + ' ' + random.choice(PERSONAS[persona]['title'])
            while (title, offset) in titles:
                title += ' 2'
            titles.add((title, offset))
            is_done = offset + days - 1 < 0
            people = random.choices([1, 2, 3, 4], weights=[20, 45, 20, 15])[0]
            transports = 'subway,bus,walk,taxi' if random.random() < 0.1 else 'subway,bus,walk'
            day_plans = [plan_day(gu, persona, places_by_gu, trend, offset + d) for d in range(days)]
            day_plans = [plan for plan in day_plans if len(plan) >= 3]  # 3곳도 못 채운 날은 빼요
            if not day_plans:
                continue
            days = len(day_plans)
            first = day_plans[0][0]['time']
            last_day = days - 1
            last = day_plans[last_day][-1] if day_plans[last_day] else day_plans[0][-1]
            trips.append((username, title, offset, days - 1, people, transports, 'done' if is_done else 'planned',
                          first - 10 if is_done else None, (last_day, last['time'] + last['stay'] + 5) if is_done else None))
            for d, plan in enumerate(day_plans):
                for s in plan:
                    state, arrived, departed = 'todo', None, None
                    if is_done:
                        if random.random() < 0.1:
                            state = 'skipped'
                        else:
                            state = 'done'
                            arrived = s['time'] + random.randint(-15, 15)
                            departed = arrived + s['stay'] + random.randint(-15, 15)
                    importance = 'must' if random.random() < 0.1 else 'maybe'
                    stops.append((username, title, offset, d + 1, s['place']['id'], hhmm(s['time']), s['stay'], state, importance,
                                  (offset + d, arrived) if arrived is not None else None, (offset + d, departed) if departed is not None else None))

    write_sql(members, trips, stops, PASSWORD_HASH)
    print('members %d, trips %d, stops %d, trend %d' % (len(members), len(trips), len(stops), len(trend)))


def sql_text(value):
    return "'" + value.replace("'", "''") + "'"


def sql_day(offset):
    return '(CURRENT_DATE + %d)' % offset


def sql_time(day_and_minute):
    if day_and_minute is None:
        return 'NULL::timestamp'
    offset, minute = day_and_minute
    return "(CURRENT_DATE + %d + TIME '00:00' + INTERVAL '%d minutes')" % (offset, minute)


def write_sql(members, trips, stops, password_hash):
    out = []
    out.append('-- 95_demo_seed.sql — 시연용 가상 사용자 데이터 (회원 %d명 · 여행 %d개 · 일정 칸 %d개). 01~15·90 다음에 실행' % (len(members), len(trips), len(stops)))
    out.append('--  만든 곳: db/demo/make_demo_seed.py (직접 고치지 말고 스크립트를 고쳐서 다시 만들어요)')
    out.append('--  가상 회원 아이디 demo_001~ · 비밀번호 %s · 장소는 서울 시범 8개 구의 실제 retrip_place' % DEMO_PASSWORD)
    out.append('--  날짜는 실행한 날(CURRENT_DATE) 기준: 지난 60일 여행(마침) + 앞으로 30일 여행(계획)')
    out.append('--  다시 실행해도 중복 없음. 지우기: DELETE FROM member WHERE username LIKE \'demo\\_%\';')
    out.append('')
    out.append('BEGIN;')
    out.append('')
    out.append('INSERT INTO member (username, password, nickname) VALUES')
    out.append(',\n'.join("  (%s, %s, %s)" % (sql_text(u), sql_text(password_hash), sql_text(nick)) for u, nick in members))
    out.append('ON CONFLICT (username) DO NOTHING;')
    out.append('')
    out.append('-- 여행: (아이디, 이름, 시작일) 이 같은 여행이 없을 때만')
    out.append('INSERT INTO trip (member_id, title, start_date, end_date, people, transports, status, started_at, finished_at)')
    out.append('SELECT m.member_id, v.title, v.start_date, v.end_date, v.people, v.transports, v.status, v.started_at, v.finished_at')
    out.append('FROM (VALUES')
    rows = []
    for u, title, offset, extra, people, transports, status, started, finished in trips:
        rows.append('  (%s, %s, %s, %s, %d, %s, %s, %s, %s)' % (
            sql_text(u), sql_text(title), sql_day(offset), sql_day(offset + extra), people, sql_text(transports), sql_text(status),
            sql_time((offset, started)) if started is not None else 'NULL::timestamp', sql_time((offset + finished[0], finished[1])) if finished else 'NULL::timestamp'))
    out.append(',\n'.join(rows))
    out.append(') AS v(username, title, start_date, end_date, people, transports, status, started_at, finished_at)')
    out.append('JOIN member m ON m.username = v.username')
    out.append('WHERE NOT EXISTS (SELECT 1 FROM trip t WHERE t.member_id = m.member_id AND t.title = v.title AND t.start_date = v.start_date);')
    out.append('')
    out.append('-- 일정 칸: 이름·분류·주소·좌표는 retrip_place에서 그대로 복사. 칸이 아직 없는 여행에만')
    out.append('INSERT INTO trip_stop (trip_id, day_no, place_id, name, category, address, lat, lng, start_time, stay_min, state, importance, arrived_at, departed_at)')
    out.append('SELECT t.trip_id, v.day_no, p.id, LEFT(p.name, 120),')
    out.append("  LEFT(COALESCE(NULLIF(p.biz_type, ''), CASE p.content_type_id WHEN '12' THEN '관광지' WHEN '14' THEN '문화시설' WHEN '15' THEN '축제·행사'")
    out.append("    WHEN '28' THEN '레포츠' WHEN '32' THEN '숙박' WHEN '38' THEN '쇼핑' WHEN '39' THEN '음식점' ELSE '관광지' END), 60),")
    out.append('  LEFT(p.address, 200), p.latitude, p.longitude, v.start_time, v.stay_min, v.state, v.importance, v.arrived_at, v.departed_at')
    out.append('FROM (VALUES')
    rows = []
    for u, title, offset, day_no, place_id, start, stay, state, importance, arrived, departed in stops:
        rows.append('  (%s, %s, %s, %d, %s, %s, %d, %s, %s, %s, %s)' % (
            sql_text(u), sql_text(title), sql_day(offset), day_no, sql_text(place_id), sql_text(start), stay, sql_text(state), sql_text(importance),
            sql_time(arrived), sql_time(departed)))
    out.append(',\n'.join(rows))
    out.append(') AS v(username, title, start_date, day_no, place_id, start_time, stay_min, state, importance, arrived_at, departed_at)')
    out.append('JOIN member m ON m.username = v.username')
    out.append('JOIN trip t ON t.member_id = m.member_id AND t.title = v.title AND t.start_date = v.start_date')
    out.append('JOIN retrip_place p ON p.id = v.place_id')
    out.append('WHERE NOT EXISTS (SELECT 1 FROM trip_stop x WHERE x.trip_id = t.trip_id);')
    out.append('')
    out.append('COMMIT;')
    (ROOT / '95_demo_seed.sql').write_text('\n'.join(out) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
