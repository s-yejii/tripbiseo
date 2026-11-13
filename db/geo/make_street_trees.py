"""
⑤ 서울시 가로수 위치정보(엑셀) → 웹 회고 가로수 파일 (frontend/public/geo/trees/{자치구}.json)  (11/11 박석진)

- 자료: 서울 열린데이터광장 "서울시 가로수 위치정보" 2026 (구·관리청마다 시트, 칸: 자치구·노선·수종·주소·경도·위도)
- 구마다 파일 하나: {"species": ["은행나무", …], "trees": [[경도, 위도, 수종 번호], …]}  (좌표 소수 5자리 ≈ 1m)
- 빈 자리("공분")와 좌표가 서울 밖인 줄은 뺌. "은행나무 암나무"는 은행나무로 합침
- 엑셀(.xlsx)은 zip 안의 XML이라 라이브러리 없이 읽음

실행: python db/geo/make_street_trees.py <엑셀 경로>
"""
import json
import os
import re
import sys
import zipfile

OUT_DIR = os.path.join(os.path.dirname(__file__), '..', '..', 'frontend', 'public', 'geo', 'trees')
SKIP_SPECIES = {'공분', ''}
SAME_SPECIES = {'은행나무 암나무': '은행나무', '왕벚나무': '벚나무류'}


def read_rows(xlsx_path):
    """시트마다 줄을 {칸 글자: 값}으로 (앞 3줄은 제목)"""
    z = zipfile.ZipFile(xlsx_path)
    strings = [re.sub(r'<[^>]+>', '', s) for s in re.findall(r'<si>(.*?)</si>', z.read('xl/sharedStrings.xml').decode('utf-8'), re.S)]
    sheet_count = len(re.findall(r'<sheet ', z.read('xl/workbook.xml').decode('utf-8')))
    for number in range(1, sheet_count + 1):
        sheet = z.read('xl/worksheets/sheet%d.xml' % number).decode('utf-8')
        for row in re.findall(r'<row [^>]*>(.*?)</row>', sheet, re.S)[3:]:
            cells = {}
            for attrs, body in re.findall(r'<c ([^>]*?)(?:/>|>(.*?)</c>)', row, re.S):
                column = re.search(r'r="([A-Z]+)', attrs).group(1)
                value = re.search(r'<v>(.*?)</v>', body or '')
                value = value.group(1) if value else ''
                cells[column] = strings[int(value)] if 't="s"' in attrs and value else value
            yield cells


def main(xlsx_path):
    by_gu = {}
    for cells in read_rows(xlsx_path):
        species = SAME_SPECIES.get(cells.get('C', '').strip(), cells.get('C', '').strip())
        gu = cells.get('A', '').strip()
        try:
            lng, lat = float(cells.get('F', '')), float(cells.get('G', ''))
        except ValueError:
            continue
        if species in SKIP_SPECIES or not gu.endswith('구') or not (126.7 < lng < 127.3 and 37.4 < lat < 37.75):
            continue
        by_gu.setdefault(gu, []).append((round(lng, 5), round(lat, 5), species))
    os.makedirs(OUT_DIR, exist_ok=True)
    total = 0
    for gu, trees in sorted(by_gu.items()):
        names = sorted({tree[2] for tree in trees})
        index = {name: i for i, name in enumerate(names)}
        data = {'gu': gu, 'source': '서울시 가로수 위치정보 2026 (서울 열린데이터광장)', 'species': names, 'trees': [[lng, lat, index[name]] for lng, lat, name in trees]}
        with open(os.path.join(OUT_DIR, gu + '.json'), 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, separators=(',', ':'))
        total += len(trees)
        print(gu, len(trees), '그루')
    print('모두', total, '그루 ·', len(by_gu), '개 구 →', os.path.abspath(OUT_DIR))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main(sys.argv[1])
