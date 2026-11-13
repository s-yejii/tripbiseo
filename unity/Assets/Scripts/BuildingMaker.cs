// 건물 만들기: 바닥 윤곽을 위로 세워서 벽과 지붕을 만듦
// 묻히지 않게 하는 규칙 (이 파일의 핵심)
//   1층 바닥 = 건물 자리에서 가장 높은 땅 → 건물이 언덕에 파묻히지 않음
//   그 아래   = 가장 낮은 땅보다 1m 아래까지 콘크리트 기초벽 → 비탈에서도 건물이 떠 보이지 않음
// 벽 한 면은 아래에서 위로 네 토막: 기초(콘크리트) → 1층(가게·주차장 그림) → 2층부터(창문 그림) → 옥상 난간(콘크리트)
// 지붕은 두 가지: 작은 단독주택은 뾰족한 기와지붕, 나머지는 평평한 옥상 (넓으면 옥탑 상자를 하나 올림)
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;

public class BuildingMaker
{
    private const float WALL_SINK_M = 1f;       // 기초벽을 땅속으로 이만큼 더 내림 (틈이 안 보이게)
    public const float GROUND_FLOOR_M = 3.4f;   // 1층 높이
    public const float FLOOR_M = 3f;            // 2층부터 한 층 높이
    private const float BAY_M = 3.2f;           // 창문 한 칸 가로 길이
    private const float PARAPET_M = 0.7f;       // 옥상 난간 높이
    public const float HOUSE_MAX_M = 7.5f;      // 이 높이 이하인 단독주택은 기와지붕
    public const int HOUSE_MAX_CORNERS = 6;     // 기와지붕은 모서리가 이 수 이하인 단순한 윤곽만
    private const float PEAK_M = 1.8f;          // 기와지붕 꼭대기가 처마보다 높은 정도
    private const float HUT_MIN_AREA_M2 = 90f;  // 옥상이 이보다 넓으면 옥탑 상자를 올림
    private const float HUT_HALF_M = 1.6f;      // 옥탑 상자 한 변의 절반
    private const float HUT_HEIGHT_M = 2.6f;    // 옥탑 상자 높이
    private const float SHOP_INSET_M = 1.2f;    // 상가 1층 가게 앞을 벽보다 안으로 들인 깊이 (위층이 처마처럼 덮음)
    private const float SHOP_COLUMN_M = 0.7f;   // 들인 1층 양 끝에 남기는 기둥 폭
    private const float CROWN_MIN_M = 60f;      // 이보다 높은 업무 빌딩은 꼭대기 장식(크라운)을 올림
    private const float CROWN_M = 4f;           // 꼭대기 장식 띠 높이
    private const float HANOK_EAVES_M = 0.8f;   // 한옥 처마가 벽 밖으로 나온 길이
    private const float HANOK_PITCH = 0.75f;    // 한옥 지붕 기울기 (짧은 쪽 절반 1m마다 0.75m 올라감, 약 37도)
    private const int EAVE_STEPS = 6;           // 처마 한 변을 몇 칸으로 나눠 곡선을 만들지
    private const float EAVE_CURVE_M = 0.45f;   // 처마 귀퉁이가 가운데보다 올라간 높이

    // 재질 번호 (Mesh 안에서 삼각형을 재질별로 나눠 담음)
    private const int UPPER = 0, GROUND_FLOOR = 1, CONCRETE = 2, ROOF = 3, TILE_ROOF = 4;

    private readonly Ground ground;
    private readonly List<Vector3> vertices = new List<Vector3>();
    private readonly List<Vector2> uv = new List<Vector2>(); // 꼭짓점마다 그림의 어느 자리를 붙일지
    private readonly List<int>[] triangles = { new List<int>(), new List<int>(), new List<int>(), new List<int>(), new List<int>() };

    public BuildingMaker(Ground ground)
    {
        this.ground = ground;
    }

    // 건물 하나를 꼭짓점·삼각형 목록에 더함
    public void Add(BuildingData building)
    {
        List<Vector2> ring = ToRing(building.points);
        if (ring.Count < 3) return;

        float low = float.MaxValue;
        float high = float.MinValue;
        foreach (Vector2 point in ring)
        {
            float height = ground.HeightAt(point.x, point.y);
            low = Mathf.Min(low, height);
            high = Mathf.Max(high, height);
        }
        float roof = high + building.height;
        bool isHouse = building.kind == 0 && building.height <= HOUSE_MAX_M && ring.Count <= HOUSE_MAX_CORNERS;

        bool hasShopFront = building.kind == 2 && building.height > GROUND_FLOOR_M + 3; // 위층이 있는 상가
        for (int i = 0; i < ring.Count; i++) AddWall(ring[i], ring[(i + 1) % ring.Count], low - WALL_SINK_M, high, roof, !isHouse, hasShopFront);
        if (building.kind == 3 && building.height >= CROWN_MIN_M) AddCrown(ring, roof);
        if (isHouse)
        {
            AddPeakRoof(ring, roof);
            return;
        }
        AddFlatRoof(ring, roof, ROOF);
        if (Mathf.Abs(SignedArea(ring)) >= HUT_MIN_AREA_M2) AddRoofHut(ring, roof);
    }

    // 한옥 한 채: 흰 벽·나무 기둥 그림의 낮은 벽 + 처마가 튀어나온 기와 팔작지붕 (재질 4번 = 기와)
    public void AddHanok(BuildingData building)
    {
        List<Vector2> ring = ToRing(building.points);
        if (ring.Count < 3) return;
        float low = float.MaxValue;
        float high = float.MinValue;
        foreach (Vector2 point in ring)
        {
            float height = ground.HeightAt(point.x, point.y);
            low = Mathf.Min(low, height);
            high = Mathf.Max(high, height);
        }
        float wallTop = high + building.height;
        for (int i = 0; i < ring.Count; i++) AddWall(ring[i], ring[(i + 1) % ring.Count], low - WALL_SINK_M, high, wallTop, false);
        AddHipRoof(ring, wallTop);
    }

    // 기와지붕(우진각): 윤곽을 감싸는 네모(가장 긴 벽 방향으로 돌린 것)를 처마만큼 넓히고, 가운데 용마루로 모이는 네 면
    //   긴 쪽 두 면은 사다리꼴, 짧은 쪽 두 면은 삼각형. 예) 10m × 6m 한옥 → 처마 포함 11.6m × 7.6m, 용마루 길이 4m
    private void AddHipRoof(List<Vector2> ring, float eaves)
    {
        int longest = 0;
        for (int i = 1; i < ring.Count; i++)
        {
            if (Vector2.Distance(ring[i], ring[(i + 1) % ring.Count]) > Vector2.Distance(ring[longest], ring[(longest + 1) % ring.Count])) longest = i;
        }
        Vector2 axis = (ring[(longest + 1) % ring.Count] - ring[longest]).normalized; // 긴 쪽 방향
        Vector2 across = new Vector2(-axis.y, axis.x);                                  // 짧은 쪽 방향

        // 모든 점을 두 방향으로 재서 가장 작은·큰 값 → 감싸는 네모
        float minA = float.MaxValue, maxA = float.MinValue, minC = float.MaxValue, maxC = float.MinValue;
        foreach (Vector2 point in ring)
        {
            minA = Mathf.Min(minA, Vector2.Dot(point, axis)); maxA = Mathf.Max(maxA, Vector2.Dot(point, axis));
            minC = Mathf.Min(minC, Vector2.Dot(point, across)); maxC = Mathf.Max(maxC, Vector2.Dot(point, across));
        }
        Vector2 center = axis * ((minA + maxA) / 2) + across * ((minC + maxC) / 2);
        float halfLong = (maxA - minA) / 2 + HANOK_EAVES_M;
        float halfShort = (maxC - minC) / 2 + HANOK_EAVES_M;
        if (halfShort > halfLong) // 감싸는 네모가 반대 방향으로 더 길면 방향을 바꿈
        {
            Vector2 swap = axis; axis = across; across = -swap;
            float size = halfLong; halfLong = halfShort; halfShort = size;
        }
        float ridgeHalf = Mathf.Max(0, halfLong - halfShort); // 용마루 길이의 절반
        float top = eaves + halfShort * HANOK_PITCH;          // 용마루 높이
        float edge = eaves - 0.15f;                           // 처마 가운데는 벽 위보다 조금 낮게 (처마가 내려앉은 느낌)

        Vector2 c0 = center - axis * halfLong - across * halfShort; // 처마 네 귀퉁이
        Vector2 c1 = center + axis * halfLong - across * halfShort;
        Vector2 c2 = center + axis * halfLong + across * halfShort;
        Vector2 c3 = center - axis * halfLong + across * halfShort;
        Vector3 r0 = Flat(center - axis * ridgeHalf, top);         // 용마루 양 끝
        Vector3 r1 = Flat(center + axis * ridgeHalf, top);
        AddRoofSide(c0, c1, r0, r1, edge, axis);   // 앞 (사다리꼴)
        AddRoofSide(c2, c3, r1, r0, edge, axis);   // 뒤 (사다리꼴)
        AddRoofSide(c1, c2, r1, r1, edge, across); // 옆 (용마루 끝 한 점으로 모이는 삼각형)
        AddRoofSide(c3, c0, r0, r0, edge, across); // 옆
    }

    // 지붕 한 면: 처마 변(from→to)을 EAVE_STEPS 칸으로 나눠 용마루(ridgeFrom→ridgeTo)와 이음
    // 처마는 가운데가 낮고 귀퉁이로 갈수록 올라감 (한옥 처마 곡선). tileAxis: 기와 골이 늘어선 방향
    private void AddRoofSide(Vector2 from, Vector2 to, Vector3 ridgeFrom, Vector3 ridgeTo, float edge, Vector2 tileAxis)
    {
        for (int i = 0; i < EAVE_STEPS; i++)
        {
            float t0 = i / (float)EAVE_STEPS;
            float t1 = (i + 1) / (float)EAVE_STEPS;
            Vector3 eave0 = EavePoint(from, to, t0, edge);
            Vector3 eave1 = EavePoint(from, to, t1, edge);
            Vector3 ridge0 = Vector3.Lerp(ridgeFrom, ridgeTo, t0);
            Vector3 ridge1 = Vector3.Lerp(ridgeFrom, ridgeTo, t1);
            AddRoofFace(eave0, eave1, ridge1, tileAxis);
            if (ridge0 != ridge1) AddRoofFace(eave0, ridge1, ridge0, tileAxis);
        }
    }

    // 처마 변 위 t(0~1) 자리의 점. 높이 = 가운데(t=0.5)는 edge, 양 끝(t=0, 1)은 EAVE_CURVE_M만큼 위
    //   예) t=0 → +0.45m, t=0.25 → +0.11m, t=0.5 → 0
    private static Vector3 EavePoint(Vector2 from, Vector2 to, float t, float edge)
    {
        float lift = EAVE_CURVE_M * (2 * t - 1) * (2 * t - 1);
        return Flat(Vector2.Lerp(from, to, t), edge + lift);
    }

    private static Vector3 Flat(Vector2 point, float height)
    {
        return new Vector3(point.x, height, point.y);
    }

    // 지붕 삼각형 하나: 위를 향하도록(바깥에서 보이게) 꼭짓점 순서를 맞춤
    // 그림 좌표: 가로 = tileAxis 방향으로 잰 거리(m, 기와 골이 이 방향으로 늘어섬), 세로 = 높이 × 1.6 (비탈을 따라 잰 거리와 비슷)
    private void AddRoofFace(Vector3 a, Vector3 b, Vector3 c, Vector2 tileAxis)
    {
        int first = vertices.Count;
        vertices.Add(a); vertices.Add(b); vertices.Add(c);
        uv.Add(TileUv(a, tileAxis)); uv.Add(TileUv(b, tileAxis)); uv.Add(TileUv(c, tileAxis));
        bool isUp = Vector3.Cross(b - a, c - a).y > 0;
        List<int> list = triangles[TILE_ROOF];
        if (isUp) { list.Add(first); list.Add(first + 1); list.Add(first + 2); }
        else { list.Add(first); list.Add(first + 2); list.Add(first + 1); }
    }

    private static Vector2 TileUv(Vector3 point, Vector2 tileAxis)
    {
        return new Vector2(Vector2.Dot(new Vector2(point.x, point.z), tileAxis), point.y * 1.6f);
    }

    // 벽 한 면. bottom: 기초 아래, floor: 1층 바닥, roof: 지붕 높이
    private void AddWall(Vector2 a, Vector2 b, float bottom, float floor, float roof, bool hasParapet, bool hasShopFront = false)
    {
        float bays = Mathf.Max(1, Mathf.Round(Vector2.Distance(a, b) / BAY_M)); // 이 벽에 창문 칸이 몇 개 들어가는지
        AddQuad(a, b, bottom, floor, 0, 0, 0, CONCRETE);
        if (hasParapet) AddQuad(a, b, roof, roof + PARAPET_M, 0, 0, 0, CONCRETE);

        if (roof - floor < GROUND_FLOOR_M + 1) // 낮은 건물은 1층 그림만
        {
            AddQuad(a, b, floor, roof, bays, 0, 1, GROUND_FLOOR);
            return;
        }
        float upperStart = floor + GROUND_FLOOR_M;
        float floors = Mathf.Max(1, Mathf.Round((roof - upperStart) / FLOOR_M)); // 2층부터 몇 층인지
        bool isLongEnough = Vector2.Distance(a, b) > SHOP_COLUMN_M * 2 + 2;
        if (hasShopFront && isLongEnough) AddShopFront(a, b, floor, upperStart, bays);
        else AddQuad(a, b, floor, upperStart, bays, 0, 1, GROUND_FLOOR);
        AddQuad(a, b, upperStart, roof, bays, 0, floors, UPPER);
    }

    // 상가 1층: 가게 앞을 SHOP_INSET_M만큼 안으로 들이고, 양 끝은 기둥으로 남기고, 위는 처마 밑면으로 덮음
    //   위에서 본 모양 (벽 a→b, 건물 안쪽이 아래):
    //   a ─ a1          b1 ─ b      ← 원래 벽 줄 (a~a1, b1~b는 기둥)
    //        │          │           ← 기둥 옆면
    //        a2 ─────── b2          ← 들인 가게 앞 (진열창 그림)
    private void AddShopFront(Vector2 a, Vector2 b, float floor, float top, float bays)
    {
        Vector2 along = (b - a).normalized;
        Vector2 inward = new Vector2(-along.y, along.x); // 윤곽이 시계 반대 방향이라 왼쪽이 건물 안쪽
        Vector2 a1 = a + along * SHOP_COLUMN_M;
        Vector2 b1 = b - along * SHOP_COLUMN_M;
        Vector2 a2 = a1 + inward * SHOP_INSET_M;
        Vector2 b2 = b1 + inward * SHOP_INSET_M;
        AddQuad(a, a1, floor, top, 0, 0, 0, CONCRETE);          // 왼쪽 기둥 앞면
        AddQuad(a1, a2, floor, top, 0, 0, 0, CONCRETE);         // 왼쪽 기둥 옆면
        AddQuad(a2, b2, floor, top, bays, 0, 1, GROUND_FLOOR);  // 가게 앞 (진열창)
        AddQuad(b2, b1, floor, top, 0, 0, 0, CONCRETE);         // 오른쪽 기둥 옆면
        AddQuad(b1, b, floor, top, 0, 0, 0, CONCRETE);          // 오른쪽 기둥 앞면
        AddCeiling(a1, b1, b2, a2, top);                        // 처마 밑면 (아래에서 보임)
    }

    // 아래를 향한 평평한 네모 (처마 밑면)
    private void AddCeiling(Vector2 p0, Vector2 p1, Vector2 p2, Vector2 p3, float height)
    {
        int first = vertices.Count;
        Vector2[] corners = { p0, p1, p2, p3 };
        foreach (Vector2 corner in corners)
        {
            vertices.Add(new Vector3(corner.x, height, corner.y));
            uv.Add(Vector2.zero);
        }
        Vector3 cross = Vector3.Cross(vertices[first + 1] - vertices[first], vertices[first + 2] - vertices[first]);
        bool isDown = cross.y < 0; // 꼭짓점 순서로 정해지는 앞면 방향이 아래인지
        List<int> list = triangles[CONCRETE];
        if (isDown) { list.Add(first); list.Add(first + 1); list.Add(first + 2); list.Add(first); list.Add(first + 2); list.Add(first + 3); }
        else { list.Add(first); list.Add(first + 2); list.Add(first + 1); list.Add(first); list.Add(first + 3); list.Add(first + 2); }
    }

    // 고층 업무 빌딩 꼭대기 장식: 난간보다 높은 띠(CROWN_M)를 둘러 세우고, 가운데에 기계실 상자를 올림
    private void AddCrown(List<Vector2> ring, float roof)
    {
        for (int i = 0; i < ring.Count; i++) AddQuad(ring[i], ring[(i + 1) % ring.Count], roof + PARAPET_M, roof + CROWN_M, 0, 0, 0, CONCRETE);
        Vector2 center = CenterOf(ring);
        List<Vector2> machine = new List<Vector2>();
        foreach (Vector2 point in ring) machine.Add(Vector2.Lerp(center, point, 0.55f)); // 윤곽을 가운데 쪽으로 55% 크기로 줄인 모양
        for (int i = 0; i < machine.Count; i++) AddQuad(machine[i], machine[(i + 1) % machine.Count], roof, roof + CROWN_M + 3, 0, 0, 0, CONCRETE);
        AddFlatRoof(machine, roof + CROWN_M + 3, CONCRETE);
    }

    // 네모 한 장: a→b 변을 y0에서 y1까지 세움. 그림은 가로로 bays번, 세로로 v0~v1만큼 되풀이
    private void AddQuad(Vector2 a, Vector2 b, float y0, float y1, float bays, float v0, float v1, int material)
    {
        int first = vertices.Count;
        vertices.Add(new Vector3(a.x, y0, a.y)); uv.Add(new Vector2(0, v0));    // 0: a 아래
        vertices.Add(new Vector3(a.x, y1, a.y)); uv.Add(new Vector2(0, v1));    // 1: a 위
        vertices.Add(new Vector3(b.x, y1, b.y)); uv.Add(new Vector2(bays, v1)); // 2: b 위
        vertices.Add(new Vector3(b.x, y0, b.y)); uv.Add(new Vector2(bays, v0)); // 3: b 아래
        List<int> list = triangles[material];
        list.Add(first); list.Add(first + 1); list.Add(first + 2);
        list.Add(first); list.Add(first + 2); list.Add(first + 3);
    }

    // x, z, x, z … 숫자 목록 → 점 목록. 마지막 점이 첫 점과 같으면 빼고, 시계 반대 방향으로 맞춤
    public static List<Vector2> ToRing(float[] points)
    {
        List<Vector2> ring = new List<Vector2>();
        for (int i = 0; i + 1 < points.Length; i += 2) ring.Add(new Vector2(points[i], points[i + 1]));
        if (ring.Count > 1 && ring[0] == ring[ring.Count - 1]) ring.RemoveAt(ring.Count - 1);
        if (SignedArea(ring) < 0) ring.Reverse();
        return ring;
    }

    // 넓이. 점이 시계 반대 방향으로 돌면 +, 시계 방향이면 −
    public static float SignedArea(List<Vector2> ring)
    {
        float sum = 0;
        for (int i = 0; i < ring.Count; i++)
        {
            Vector2 a = ring[i];
            Vector2 b = ring[(i + 1) % ring.Count];
            sum += a.x * b.y - b.x * a.y;
        }
        return sum / 2;
    }

    // 윤곽의 가운데 (점들의 평균)
    private Vector2 CenterOf(List<Vector2> ring)
    {
        Vector2 sum = Vector2.zero;
        foreach (Vector2 point in ring) sum += point;
        return sum / ring.Count;
    }

    // ---------- 지붕

    // 기와지붕: 윤곽의 변마다 가운데 꼭대기로 모이는 삼각형 한 장
    private void AddPeakRoof(List<Vector2> ring, float eaves)
    {
        Vector2 center = CenterOf(ring);
        for (int i = 0; i < ring.Count; i++)
        {
            Vector2 a = ring[i];
            Vector2 b = ring[(i + 1) % ring.Count];
            int first = vertices.Count;
            vertices.Add(new Vector3(a.x, eaves, a.y)); uv.Add(Vector2.zero);
            vertices.Add(new Vector3(center.x, eaves + PEAK_M, center.y)); uv.Add(Vector2.zero);
            vertices.Add(new Vector3(b.x, eaves, b.y)); uv.Add(Vector2.zero);
            List<int> list = triangles[TILE_ROOF];
            list.Add(first); list.Add(first + 1); list.Add(first + 2);
        }
    }

    // 옥탑 상자: 옥상 가운데에 작은 상자 (계단실·물탱크). 네 귀퉁이가 전부 옥상 안에 들어올 때만
    private void AddRoofHut(List<Vector2> ring, float roof)
    {
        Vector2 center = CenterOf(ring);
        List<Vector2> hut = new List<Vector2>
        {
            center + new Vector2(-HUT_HALF_M, -HUT_HALF_M), center + new Vector2(HUT_HALF_M, -HUT_HALF_M),
            center + new Vector2(HUT_HALF_M, HUT_HALF_M), center + new Vector2(-HUT_HALF_M, HUT_HALF_M),
        };
        foreach (Vector2 corner in hut)
        {
            if (!IsInside(ring, corner)) return;
        }
        for (int i = 0; i < hut.Count; i++) AddQuad(hut[i], hut[(i + 1) % hut.Count], roof, roof + HUT_HEIGHT_M, 0, 0, 0, CONCRETE);
        AddFlatRoof(hut, roof + HUT_HEIGHT_M, CONCRETE);
    }

    // 점이 윤곽 안에 있는지: 점에서 오른쪽으로 선을 그어 윤곽의 변과 만나는 횟수가 홀수면 안
    public static bool IsInside(List<Vector2> ring, Vector2 point)
    {
        bool isInside = false;
        for (int i = 0; i < ring.Count; i++)
        {
            Vector2 a = ring[i];
            Vector2 b = ring[(i + 1) % ring.Count];
            bool crosses = (a.y > point.y) != (b.y > point.y) && point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x;
            if (crosses) isInside = !isInside;
        }
        return isInside;
    }

    // 평평한 지붕: 윤곽을 삼각형들로 자름 ("귀 자르기": 튀어나온 모서리를 하나씩 삼각형으로 잘라 냄)
    private void AddFlatRoof(List<Vector2> ring, float top, int material)
    {
        int first = vertices.Count;
        foreach (Vector2 point in ring)
        {
            vertices.Add(new Vector3(point.x, top, point.y));
            uv.Add(Vector2.zero);
        }

        List<int> left = new List<int>(); // 아직 안 잘린 점 번호
        for (int i = 0; i < ring.Count; i++) left.Add(i);

        List<int> list = triangles[material];
        while (left.Count > 3)
        {
            int ear = FindEar(ring, left);
            if (ear < 0) break; // 모양이 꼬인 윤곽 → 남은 부분은 포기 (지붕 일부가 비어도 벽은 있음)
            AddRoofTriangle(list, first, left[(ear + left.Count - 1) % left.Count], left[ear], left[(ear + 1) % left.Count]);
            left.RemoveAt(ear);
        }
        if (left.Count == 3) AddRoofTriangle(list, first, left[0], left[1], left[2]);
    }

    private void AddRoofTriangle(List<int> list, int first, int a, int b, int c)
    {
        list.Add(first + a); list.Add(first + c); list.Add(first + b); // 위에서 봤을 때 보이는 방향
    }

    // 잘라도 되는 모서리 찾기: 바깥으로 볼록하고, 그 삼각형 안에 다른 점이 없어야 함
    private int FindEar(List<Vector2> ring, List<int> left)
    {
        for (int i = 0; i < left.Count; i++)
        {
            Vector2 a = ring[left[(i + left.Count - 1) % left.Count]];
            Vector2 b = ring[left[i]];
            Vector2 c = ring[left[(i + 1) % left.Count]];
            if (Cross(a, b, c) <= 0) continue; // 안으로 들어간 모서리

            bool hasPointInside = false;
            foreach (int other in left)
            {
                Vector2 p = ring[other];
                if (p == a || p == b || p == c) continue;
                if (Cross(a, b, p) > 0 && Cross(b, c, p) > 0 && Cross(c, a, p) > 0) hasPointInside = true;
            }
            if (!hasPointInside) return i;
        }
        return -1;
    }

    // a→b→c가 왼쪽으로 꺾이면 +, 오른쪽이면 −
    private float Cross(Vector2 a, Vector2 b, Vector2 c)
    {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    // 지금까지 더한 건물 전부를 Mesh 하나로. 재질 순서: 0 위층 벽 · 1 1층 벽 · 2 콘크리트 · 3 옥상 · 4 기와지붕
    public Mesh BuildMesh()
    {
        Mesh mesh = new Mesh();
        mesh.indexFormat = IndexFormat.UInt32;
        mesh.SetVertices(vertices);
        mesh.SetUVs(0, uv);
        mesh.subMeshCount = triangles.Length;
        for (int i = 0; i < triangles.Length; i++) mesh.SetTriangles(triangles[i], i);
        mesh.RecalculateNormals();
        mesh.RecalculateTangents(); // 벽 표면 질감(노멀 맵)이 빛을 바르게 받으려면 필요
        return mesh;
    }
}
