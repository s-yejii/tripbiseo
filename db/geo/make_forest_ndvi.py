"""
⑤ 위성 NDVI(식생지수)를 숲 구역마다 계산해서 forest-seoul.json에 넣기 (11/11 박석진)

- 위성: Sentinel-2 L2A (AWS 공개 저장소 sentinel-cogs, 가입 없음). 서울은 위성 칸 52SBG·52SCG 두 칸에 걸침
- NDVI = (근적외선 B08 − 빨강 B04) ÷ (B08 + B04). 1에 가까울수록 푸름
- 파일 전체(칸마다 수백 MB)를 받지 않고, 파일 안 20m 축소본에서 서울에 걸친 1024×1024 조각만 HTTP Range로 받아서 읽음
- 숲 구역마다 안에 든 픽셀(20m) 평균 → 속성 ndvi_YYYYMM (예: ndvi_202610). 원본 위성 자료는 저장 안 함
- 라이브러리: numpy만 (GeoTIFF 조각은 직접 풀어 읽음: deflate 압축 + 가로 차이(predictor 2))

실행: python db/geo/make_forest_ndvi.py 2026-01-31 2026-04-08 2025-07-10 2026-10-28
"""
import json
import math
import os
import struct
import sys
import urllib.request
import zlib

import numpy as np

FOREST = os.path.join(os.path.dirname(__file__), '..', '..', 'frontend', 'public', 'geo', 'forest-seoul.json')
COG_URL = 'https://sentinel-cogs.s3.us-west-2.amazonaws.com/sentinel-s2-l2a-cogs/52/S/{sq}/{y}/{m}/S2{sat}_52S{sq}_{ymd}_0_L2A/{band}.tif'
SQUARES = {'BG': 199980.0, 'CG': 300000.0}   # 위성 칸 → 왼쪽 위 x (UTM 52N, m). 위쪽 y는 둘 다 4200000
TOP_Y = 4200000.0
LEVEL = 1                    # 파일 안의 축소본 단계: 0 = 원본 10m, 1 = 20m (받는 양이 1/4, 숲 구역 평균에는 충분)
PIXEL = 10.0 * 2 ** LEVEL
DN_OFFSET = 0                # 반사율 = (값 − DN_OFFSET) ÷ 10000. Earth Search는 1000 보정을 이미 빼서 저장함 (boa_offset_applied)
SEOUL = (126.76, 37.42, 127.19, 37.71)   # 경도·위도 범위
SATS = ['A', 'B', 'C']       # Sentinel-2A·2B·2C 중 그날 찍은 위성 (주소에 들어감)
total_bytes = 0


# ---------- 경위도 → UTM 52N (TM 정변환, Snyder 식)
A = 6378137.0
E2 = 0.00669437999014
K0 = 0.9996
LON0 = math.radians(129.0)


def to_utm(lng, lat):
    phi, lam = math.radians(lat), math.radians(lng)
    ep2 = E2 / (1 - E2)
    n = A / math.sqrt(1 - E2 * math.sin(phi) ** 2)
    t = math.tan(phi) ** 2
    c = ep2 * math.cos(phi) ** 2
    a = math.cos(phi) * (lam - LON0)
    e4, e6 = E2 * E2, E2 ** 3
    m = A * ((1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * phi - (3 * E2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * math.sin(2 * phi)
             + (15 * e4 / 256 + 45 * e6 / 1024) * math.sin(4 * phi) - (35 * e6 / 3072) * math.sin(6 * phi))
    x = K0 * n * (a + (1 - t + c) * a ** 3 / 6 + (5 - 18 * t + t * t + 72 * c - 58 * ep2) * a ** 5 / 120) + 500000.0
    y = K0 * (m + n * math.tan(phi) * (a * a / 2 + (5 - t + 9 * c + 4 * c * c) * a ** 4 / 24 + (61 - 58 * t + t * t + 600 * c - 330 * ep2) * a ** 6 / 720))
    return x, y


# ---------- GeoTIFF(COG) 조각 읽기
def fetch(url, start, length):
    global total_bytes
    request = urllib.request.Request(url, headers={'Range': 'bytes=%d-%d' % (start, start + length - 1)})
    data = urllib.request.urlopen(request, timeout=120).read()
    total_bytes += len(data)
    return data


def read_tile_index(url):
    """LEVEL번째 IFD(0 = 원본, 1 = 첫 축소본)의 그림 너비, 조각 위치(TileOffsets)·크기(TileByteCounts)"""
    head = fetch(url, 0, 16384)
    ifd = struct.unpack('<I', head[4:8])[0]
    for _ in range(LEVEL):  # IFD 끝에 다음 IFD 위치가 있음 → 따라감
        count = struct.unpack('<H', head[ifd:ifd + 2])[0]
        ifd = struct.unpack('<I', head[ifd + 2 + 12 * count:ifd + 6 + 12 * count])[0]
    count = struct.unpack('<H', head[ifd:ifd + 2])[0]
    where = {}
    for i in range(count):
        tag, typ, cnt, val = struct.unpack('<HHII', head[ifd + 2 + 12 * i:ifd + 14 + 12 * i])
        where[tag] = (typ, cnt, val)
    width = where[256][2] & 0xFFFF if where[256][0] == 3 else where[256][2]  # SHORT 값은 앞 2바이트
    tile = where[322][2] & 0xFFFF if where[322][0] == 3 else where[322][2]    # 조각 한 변 (원본 1024, 축소본 512)
    offsets = struct.unpack('<%dI' % where[324][1], fetch(url, where[324][2], 4 * where[324][1]))
    counts = struct.unpack('<%dI' % where[325][1], fetch(url, where[325][2], 4 * where[325][1]))
    return width, tile, offsets, counts


def read_tile(url, offset, length, tile):
    """tile×tile 조각 하나: deflate 풀고 가로 차이(predictor 2)를 되돌림"""
    raw = zlib.decompress(fetch(url, offset, length))
    diff = np.frombuffer(raw, dtype='<u2').reshape(tile, tile)
    return np.cumsum(diff, axis=1, dtype=np.uint16)  # 왼쪽부터 더해서 원래 값으로 (16비트 넘침도 원래 방식대로)


def find_url(square, date, band):
    """그날 찍은 위성(A·B·C)을 차례로 시험해서 있는 파일 주소"""
    y, m, d = date.split('-')
    for sat in SATS:
        url = COG_URL.format(sq=square, y=y, m=int(m), sat=sat, ymd=y + m + d, band=band)
        try:
            urllib.request.urlopen(urllib.request.Request(url, method='HEAD'), timeout=30)
            return url
        except Exception:
            continue
    return None


# ---------- 서울 모자이크 (UTM 52N, PIXEL m 격자)
def seoul_grid():
    xs, ys = [], []
    for lng, lat in [(SEOUL[0], SEOUL[1]), (SEOUL[2], SEOUL[1]), (SEOUL[0], SEOUL[3]), (SEOUL[2], SEOUL[3])]:
        x, y = to_utm(lng, lat)
        xs.append(x)
        ys.append(y)
    left = math.floor(min(xs) / PIXEL) * PIXEL
    top = math.ceil(max(ys) / PIXEL) * PIXEL
    width = int(math.ceil((max(xs) - left) / PIXEL))
    height = int(math.ceil((top - min(ys)) / PIXEL))
    return left, top, width, height


def read_band(date, band, grid):
    left, top, width, height = grid
    mosaic = np.zeros((height, width), dtype=np.uint16)
    for square, origin_x in SQUARES.items():
        url = find_url(square, date, band)
        if not url:
            print('  없음', square, date, band)
            continue
        image_width, tile, offsets, counts = read_tile_index(url)
        tiles_across = int(math.ceil(image_width / tile))
        col0 = max(0, int((left - origin_x) // (PIXEL * tile)))
        col1 = min(tiles_across - 1, int((left + width * PIXEL - origin_x) // (PIXEL * tile)))
        row0 = max(0, int((TOP_Y - top) // (PIXEL * tile)))
        row1 = min(tiles_across - 1, int((TOP_Y - (top - height * PIXEL)) // (PIXEL * tile)))
        for row in range(row0, row1 + 1):
            for col in range(col0, col1 + 1):
                index = row * tiles_across + col
                if counts[index] == 0:
                    continue
                paste(mosaic, read_tile(url, offsets[index], counts[index], tile), origin_x + col * tile * PIXEL, TOP_Y - row * tile * PIXEL, grid)
    return mosaic


def paste(mosaic, tile, tile_x, tile_y, grid):
    """조각을 모자이크 자리에 붙임 (값이 0인 곳은 자료 없음이라 안 덮음)"""
    left, top, width, height = grid
    dx = int(round((tile_x - left) / PIXEL))
    dy = int(round((top - tile_y) / PIXEL))
    x0, y0 = max(0, dx), max(0, dy)
    x1, y1 = min(width, dx + tile.shape[1]), min(height, dy + tile.shape[0])
    if x0 >= x1 or y0 >= y1:
        return
    part = tile[y0 - dy:y1 - dy, x0 - dx:x1 - dx]
    target = mosaic[y0:y1, x0:x1]
    target[part > 0] = part[part > 0]


def ndvi_of(red, nir):
    r = red.astype(np.float32) - DN_OFFSET
    n = nir.astype(np.float32) - DN_OFFSET
    valid = (red > 0) & (nir > 0) & (r + n > 0)
    result = np.full(red.shape, np.nan, dtype=np.float32)
    result[valid] = (n[valid] - r[valid]) / (n[valid] + r[valid])
    return result


# ---------- 숲 구역 평균
def polygon_mean(ndvi, polygons, grid):
    """MultiPolygon 안에 든 픽셀(가운데 점) 평균 NDVI. 픽셀이 없으면 None"""
    left, top, width, height = grid
    total, count = 0.0, 0
    for polygon in polygons:
        rings = [[((x - left) / PIXEL, (top - y) / PIXEL) for x, y in (to_utm(lng, lat) for lng, lat in ring)] for ring in polygon]
        xs = [p[0] for p in rings[0]]
        ys = [p[1] for p in rings[0]]
        c0, c1 = max(0, int(min(xs))), min(width - 1, int(max(xs)))
        r0, r1 = max(0, int(min(ys))), min(height - 1, int(max(ys)))
        if c0 > c1 or r0 > r1:
            continue
        cx, cy = np.meshgrid(np.arange(c0, c1 + 1) + 0.5, np.arange(r0, r1 + 1) + 0.5)
        inside = np.zeros(cx.shape, dtype=bool)
        for ring in rings:  # 홀짝 판정: 고리마다 반직선과 만나는 횟수를 뒤집음 (구멍은 다시 빠짐)
            for (x1, y1), (x2, y2) in zip(ring, ring[1:]):
                crosses = ((y1 > cy) != (y2 > cy)) & (cx < (x2 - x1) * (cy - y1) / ((y2 - y1) or 1e-12) + x1)
                inside ^= crosses
        values = ndvi[r0:r1 + 1, c0:c1 + 1][inside]
        values = values[~np.isnan(values)]
        total += float(values.sum())
        count += values.size
    return round(total / count, 3) if count > 0 else None


def main(dates):
    sys.stdout.reconfigure(encoding='utf-8')
    with open(FOREST, encoding='utf-8') as f:
        forest = json.load(f)
    grid = seoul_grid()
    print('서울 격자', grid[2], '×', grid[3], '픽셀 (%dm)' % PIXEL)
    for date in dates:
        red = read_band(date, 'B04', grid)
        nir = read_band(date, 'B08', grid)
        ndvi = ndvi_of(red, nir)
        key = 'ndvi_' + date[:4] + date[5:7]
        filled = 0
        for feature in forest['features']:
            value = polygon_mean(ndvi, feature['geometry']['coordinates'], grid)
            feature['properties'][key] = value
            if value is not None:
                filled += 1
        print(date, key, '숲 구역', filled, '곳 · 중앙값', round(float(np.nanmedian([f['properties'][key] for f in forest['features'] if f['properties'][key] is not None])), 3),
              '· 지금까지 받은 양', round(total_bytes / 1e6, 1), 'MB')
    forest['ndvi_source'] = 'Sentinel-2 L2A (Copernicus, AWS sentinel-cogs) ' + ', '.join(dates)
    with open(FOREST, 'w', encoding='utf-8') as f:
        json.dump(forest, f, ensure_ascii=False, separators=(',', ':'))
    print('저장', os.path.abspath(FOREST), round(os.path.getsize(FOREST) / 1e6, 1), 'MB')


if __name__ == '__main__':
    main(sys.argv[1:])
