"""
⑤ GIS건물통합정보(서울, AL_D010_11) SHP → 유니티 3D 회고용 건물 파일 (frontend/public/geo/buildings/{칸}.json)  (11/12 박석진)

- 건물마다: 높이(m), 종류(0 단독주택 · 1 아파트 등 공동주택 · 2 상가 · 3 업무·숙박 · 4 그 밖), 바닥 윤곽
- 높이: 자료의 높이(A16)가 있으면 그 값, 없으면 지상 층수(A26) × 3m
- 좌표: 자료는 EPSG:5186(Korea 2000 중부원점 TM) → 경위도(WGS84)로 바꿈 (make_forest_seoul.py와 같은 식, 상수만 다름)
- 파일 나누기: 경도·위도 0.01도(약 1km) 칸마다 한 파일. 이름은 "경도칸_위도칸" (예: 12698_3755.json = 경도 126.98~, 위도 37.55~)
  → 웹은 여행 범위에 걸친 칸만 받으면 됨
- 크기 줄이기: 좌표는 칸의 남서쪽 모서리에서 떨어진 정도를 백만분의 1도(약 0.1m) 정수로 적음
  한 건물 = [높이, 종류, x, y, x, y, …]

실행: python db/geo/make_buildings_seoul.py <건물 .shp 경로>
"""
import json
import math
import os
import struct
import sys

OUT_DIR = os.path.join(os.path.dirname(__file__), '..', '..', 'frontend', 'public', 'geo', 'buildings')
CELL_DEG = 0.01              # 한 칸 크기(도)
UNIT = 1000000               # 좌표 단위: 백만분의 1도
FLOOR_M = 3.0                # 한 층 높이
MIN_AREA_M2 = 12             # 이보다 작은 건물(창고·초소)은 뺌
KIND_OF = {'01': 0, '02': 1, '03': 2, '04': 2, '07': 2, '14': 3, '15': 3}  # 주용도 코드 앞 두 자리 → 종류

# ---------- EPSG:5186 (TM) → 경위도
A = 6378137.0                # GRS80 장반경
F = 1 / 298.257222101
E2 = F * (2 - F)             # 이심률 제곱
K0 = 1.0
LON0 = math.radians(127.0)
LAT0 = math.radians(38.0)
FE, FN = 200000.0, 600000.0


def meridian_arc(lat):
    """적도에서 위도 lat까지 자오선 길이 (m)"""
    e4, e6 = E2 * E2, E2 * E2 * E2
    return A * ((1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * lat
                - (3 * E2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * math.sin(2 * lat)
                + (15 * e4 / 256 + 45 * e6 / 1024) * math.sin(4 * lat)
                - (35 * e6 / 3072) * math.sin(6 * lat))


M0 = meridian_arc(LAT0)


def to_lnglat(x, y):
    """TM 좌표(m) → (경도, 위도) 도. Snyder 'Map Projections' 역변환 식"""
    ep2 = E2 / (1 - E2)
    m = M0 + (y - FN) / K0
    mu = m / (A * (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 ** 3 / 256))
    e1 = (1 - math.sqrt(1 - E2)) / (1 + math.sqrt(1 - E2))
    lat1 = (mu + (3 * e1 / 2 - 27 * e1 ** 3 / 32) * math.sin(2 * mu) + (21 * e1 ** 2 / 16 - 55 * e1 ** 4 / 32) * math.sin(4 * mu)
            + (151 * e1 ** 3 / 96) * math.sin(6 * mu) + (1097 * e1 ** 4 / 512) * math.sin(8 * mu))
    c1 = ep2 * math.cos(lat1) ** 2
    t1 = math.tan(lat1) ** 2
    n1 = A / math.sqrt(1 - E2 * math.sin(lat1) ** 2)
    r1 = A * (1 - E2) / (1 - E2 * math.sin(lat1) ** 2) ** 1.5
    d = (x - FE) / (n1 * K0)
    lat = lat1 - (n1 * math.tan(lat1) / r1) * (d * d / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * ep2) * d ** 4 / 24
                                              + (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * ep2 - 3 * c1 * c1) * d ** 6 / 720)
    lng = LON0 + (d - (1 + 2 * t1 + c1) * d ** 3 / 6 + (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * ep2 + 24 * t1 * t1) * d ** 5 / 120) / math.cos(lat1)
    return math.degrees(lng), math.degrees(lat)


# ---------- SHP·DBF 읽기 (파일이 커서 한 건물씩 읽음)
def open_dbf(f):
    """DBF 머리말을 읽고 (건물 수, 한 줄 길이, {칸 이름: (시작 위치, 길이)})를 돌려줌"""
    head = f.read(32)
    count, head_len, rec_len = struct.unpack('<IHH', head[4:12])
    fields, pos = {}, 1
    while True:
        b = f.read(32)
        if b[0] == 0x0D:
            break
        fields[b[:11].split(b'\0')[0].decode('ascii')] = (pos, b[16])
        pos += b[16]
    f.seek(head_len)
    return count, rec_len, fields


def text_of(record, field):
    return record[field[0]:field[0] + field[1]].decode('cp949', 'replace').strip()


def read_outer_ring(f):
    """SHP에서 다음 도형의 바깥 고리 [(x, y), …]. 도형이 없으면 []"""
    head = f.read(8)
    body = f.read(struct.unpack('>i', head[4:8])[0] * 2)
    if struct.unpack('<i', body[:4])[0] != 5:  # 5 = 면(Polygon)
        return []
    num_parts, num_points = struct.unpack('<2i', body[36:44])
    start = 44 + 4 * num_parts
    end = struct.unpack('<i', body[48:52])[0] if num_parts > 1 else num_points  # 첫 고리(바깥)만
    return [struct.unpack('<2d', body[start + 16 * i:start + 16 * i + 16]) for i in range(end)]


def area_of(ring):
    total = 0
    for i in range(len(ring) - 1):
        total += ring[i][0] * ring[i + 1][1] - ring[i + 1][0] * ring[i][1]
    return abs(total) / 2


# ---------- 건물 한 채 → [높이, 종류, x, y, …] 와 들어갈 칸
def to_number(text):
    try:
        return float(text)
    except ValueError:
        return 0


def height_of(height_text, floors_text):
    height = to_number(height_text)
    if height > 2:
        return round(height, 1)
    return max(1, to_number(floors_text)) * FLOOR_M


def to_item(ring, height, kind):
    points = [to_lnglat(x, y) for x, y in ring]
    cell_x = math.floor(points[0][0] / CELL_DEG)
    cell_y = math.floor(points[0][1] / CELL_DEG)
    item = [height, kind]
    for lng, lat in points:
        item.append(round((lng - cell_x * CELL_DEG) * UNIT))
        item.append(round((lat - cell_y * CELL_DEG) * UNIT))
    return '%d_%d' % (cell_x, cell_y), item


def main(shp_path):
    cells = {}
    kept = 0
    with open(shp_path, 'rb') as shp, open(shp_path[:-4] + '.dbf', 'rb') as dbf:
        shp.seek(100)
        count, rec_len, fields = open_dbf(dbf)
        for i in range(count):
            record = dbf.read(rec_len)
            ring = read_outer_ring(shp)
            if len(ring) < 4 or area_of(ring) < MIN_AREA_M2:
                continue
            height = height_of(text_of(record, fields['A16']), text_of(record, fields['A26']))
            kind = KIND_OF.get(text_of(record, fields['A8'])[:2], 4)
            name, item = to_item(ring, height, kind)
            cells.setdefault(name, []).append(item)
            kept += 1
            if i % 100000 == 0:
                print(i, '/', count)
    os.makedirs(OUT_DIR, exist_ok=True)
    total = 0
    for name, items in cells.items():
        path = os.path.join(OUT_DIR, name + '.json')
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(items, f, separators=(',', ':'))
        total += os.path.getsize(path)
    print('건물', kept, '채 /', count, '· 파일', len(cells), '개 ·', round(total / 1e6, 1), 'MB')


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main(sys.argv[1])
