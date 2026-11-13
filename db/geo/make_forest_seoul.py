"""
⑤ 산림청 임상도(1:5,000, 서울) SHP → 웹 회고 '계절 숲' 파일 (frontend/public/geo/forest-seoul.json)  (11/11 박석진)

- 숲 구역마다: kind(conifer 침엽수 / broadleaf 활엽수 / mixed 혼효림), species(주요 수종), height(임분고 m)
- 무립목지·비산림, 죽림은 뺌
- 좌표: 임상도는 우리나라 좌표계(EPSG:5179, Korea 2000 통합좌표계 TM) → 지도용 경위도(WGS84)로 바꿈
  (GRS80 타원체 TM 역변환 공식, 라이브러리 없이 계산. Korea 2000과 WGS84는 cm 차이라 같은 것으로 봄)
- 크기를 줄이려고 선을 단순하게(더글러스-포이커, 3m) 하고 좌표는 소수 5자리(약 1m)
- NDVI는 make_forest_ndvi.py가 이 파일에 ndvi_YYYYMM으로 더 넣음

실행: python db/geo/make_forest_seoul.py <임상도 .shp 경로>
"""
import json
import math
import os
import struct
import sys

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'frontend', 'public', 'geo', 'forest-seoul.json')
SIMPLIFY_M = 3.0             # 이보다 작은 굴곡은 지움 (m)
KINDS = {'침엽수림': 'conifer', '활엽수림': 'broadleaf', '혼효림': 'mixed'}
DEFAULT_HEIGHT_M = 12

# ---------- EPSG:5179 (TM) → 경위도
A = 6378137.0                # GRS80 장반경
F = 1 / 298.257222101
E2 = F * (2 - F)             # 이심률 제곱
K0 = 0.9996
LON0 = math.radians(127.5)
LAT0 = math.radians(38.0)
FE, FN = 1000000.0, 2000000.0


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


# ---------- SHP·DBF 읽기
def read_dbf(path):
    rows = []
    with open(path, 'rb') as f:
        head = f.read(32)
        count, head_len, rec_len = struct.unpack('<I', head[4:8])[0], struct.unpack('<H', head[8:10])[0], struct.unpack('<H', head[10:12])[0]
        fields = []
        while True:
            b = f.read(32)
            if b[0] == 0x0D:
                break
            fields.append((b[:11].split(b'\0')[0].decode('cp949'), b[16]))
        f.seek(head_len)
        for _ in range(count):
            rec = f.read(rec_len)
            row, pos = {}, 1
            for name, length in fields:
                row[name] = rec[pos:pos + length].decode('cp949', 'replace').strip()
                pos += length
            rows.append(row)
    return rows


def read_polygons(path):
    """도형마다 고리(ring) 목록 [[(x, y), …], …]"""
    shapes = []
    with open(path, 'rb') as f:
        f.seek(100)
        while True:
            head = f.read(8)
            if len(head) < 8:
                break
            length = struct.unpack('>i', head[4:8])[0] * 2
            body = f.read(length)
            shape_type = struct.unpack('<i', body[:4])[0]
            if shape_type != 5:
                shapes.append([])
                continue
            num_parts, num_points = struct.unpack('<2i', body[36:44])
            parts = list(struct.unpack('<%di' % num_parts, body[44:44 + 4 * num_parts])) + [num_points]
            start = 44 + 4 * num_parts
            points = [struct.unpack('<2d', body[start + 16 * i:start + 16 * i + 16]) for i in range(num_points)]
            shapes.append([points[parts[i]:parts[i + 1]] for i in range(num_parts)])
    return shapes


# ---------- 단순화 · 고리 정리
def simplify(points, tolerance):
    """더글러스-포이커: 직선에서 tolerance보다 가까운 점은 지움"""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        first, last = stack.pop()
        (x1, y1), (x2, y2) = points[first], points[last]
        dx, dy = x2 - x1, y2 - y1
        length = math.hypot(dx, dy)
        far, far_index = 0, None
        for i in range(first + 1, last):
            if length < 1e-9:  # 닫힌 고리(처음 점 = 끝 점): 직선 대신 그 점까지 거리
                gap = math.hypot(points[i][0] - x1, points[i][1] - y1)
            else:
                gap = abs(dy * (points[i][0] - x1) - dx * (points[i][1] - y1)) / length
            if gap > far:
                far, far_index = gap, i
        if far_index is not None and far > tolerance:
            keep[far_index] = True
            stack.append((first, far_index))
            stack.append((far_index, last))
    return [p for p, k in zip(points, keep) if k]


def signed_area(ring):
    total = 0
    for (x1, y1), (x2, y2) in zip(ring, ring[1:]):
        total += x1 * y2 - x2 * y1
    return total / 2


def to_polygons(rings):
    """SHP 고리 → GeoJSON MultiPolygon 좌표. 시계 방향 = 바깥 고리, 반시계 = 그 앞 바깥 고리의 구멍"""
    polygons = []
    for ring in rings:
        small = simplify(ring, SIMPLIFY_M)
        if len(small) < 4:
            continue
        coords = [[round(v, 5) for v in to_lnglat(x, y)] for x, y in small]
        if signed_area(ring) < 0 or not polygons:  # 시계 방향(넓이 음수) = 바깥
            polygons.append([coords])
        else:
            polygons[-1].append(coords)
    return polygons


def main(shp_path):
    rows = read_dbf(shp_path[:-4] + '.dbf')
    shapes = read_polygons(shp_path)
    features = []
    for index, (row, rings) in enumerate(zip(rows, shapes)):
        kind = KINDS.get(row.get('FRTP_NM', ''))
        if not kind or not rings:
            continue
        polygons = to_polygons(rings)
        if not polygons:
            continue
        height = int(row['HEIGHT']) if row.get('HEIGHT', '').isdigit() else DEFAULT_HEIGHT_M
        features.append({'type': 'Feature', 'geometry': {'type': 'MultiPolygon', 'coordinates': polygons},
                         'properties': {'id': index, 'kind': kind, 'species': row.get('KOFTR_NM', ''), 'height': height}})
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump({'type': 'FeatureCollection', 'source': '산림청 임상도 1:5,000 (서울)', 'features': features}, f, ensure_ascii=False, separators=(',', ':'))
    print('숲 구역', len(features), '개 →', os.path.abspath(OUT), round(os.path.getsize(OUT) / 1e6, 1), 'MB')


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main(sys.argv[1])
