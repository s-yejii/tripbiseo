// 길 만들기: 도로 가운데 선을 따라 땅 위에 띠를 깖
// 위성 사진은 가까이 가면 흐려서, 길만은 또렷한 띠로 한 번 더 그림
// 묻히지 않게 하는 규칙: 선을 8m마다 잘게 나눠 점마다 땅 높이를 물어보고, 그보다 조금 위에 놓음
//   찻길 높이는 어느 길이든 같은 규칙(가운데 + 동서남북 3m, 다섯 점 중 가장 높은 땅)으로 잼 → 교차로에서 두 길 높이가 같음
//   교차로에서 두 찻길이 겹치면 넓은 길만 그림 (좁은 길 바닥은 비움: 겹친 두 면이 번갈아 보이며 깜빡이지 않게)
// 큰 찻길: 아스팔트 + 노란 중앙선 두 줄 + 흰 점선 차선 + 교차로 횡단보도 + 가로등 + 연석 위로 올라간 보도블록 인도
// 골목: 아스팔트 + 가장자리 콘크리트 턱 + 전봇대와 전선
// 다른 찻길 위에는 인도·턱·차선·기둥을 안 그림 (큰길을 가로지르는 골목의 인도가 큰길 위에 띠로 깔려 "길이 뭉쳐" 보여서)
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;

public class RoadMaker
{
    private const float STEP_M = 8f;             // 이 길이마다 땅 높이를 다시 물어봄 (언덕을 따라 휘게)
    private const float BIG_ROAD_M = 9f;         // 폭이 이 이상이면 "큰 찻길": 인도·중앙선·횡단보도를 그림
    private const float LANE_ROAD_M = 16f;       // 폭이 이 이상이면 흰 점선 차선도 그림
    private const float WIDTH_LIFT = 0.004f;     // 넓은 길일수록 폭 × 이만큼 더 띄움 (길끼리 겹친 곳에서 큰 길이 위로 보이게. 16m 길이면 6cm)
    private const float SIDEWALK_M = 2.5f;       // 인도 폭
    private const float CURB_M = 0.15f;          // 인도가 찻길보다 높은 정도 (연석 높이)
    private const float JUNCTION_CLEAR_M = 10f;  // 교차로에서 이만큼은 인도를 깔지 않음 (가로지르는 찻길이 지나가는 자리)
    private const float PAINT_LIFT_M = 0.03f;    // 차선·횡단보도를 길 위로 띄우는 정도
    private const float LINE_WIDTH_M = 0.3f;     // 차선 폭
    private const float CROSSWALK_BACK_M = 7f;   // 횡단보도를 교차로에서 이만큼 물려 그림
    private const float CROSSWALK_LONG_M = 4f;   // 횡단보도 줄 하나의 길이 (찻길 방향)
    private const float STRIPE_M = 0.5f;         // 횡단보도 줄 폭 (줄 사이 간격도 같음)
    private const float JUNCTION_CELL_M = 4f;    // 길 끝점이 이 칸 안에 모이면 같은 교차로로 봄
    private const float ALLEY_M = 5f;            // 폭이 이 이상인 골목에는 가장자리 턱과 전봇대를 그림
    private const float EDGE_M = 0.4f;           // 골목 가장자리 콘크리트 턱 폭
    private const float CENTER_GAP_M = 0.25f;    // 노란 중앙선 두 줄이 가운데에서 떨어진 거리
    private const float LAMP_GAP_M = 40f;        // 가로등 간격
    private const float LAMP_HEIGHT_M = 9f;      // 가로등 높이
    private const float LAMP_ARM_M = 2.2f;       // 가로등 팔 길이
    private const float LAMP_HEAD_M = 0.35f;     // 가로등 등 높이
    private const float WIRE_POLE_GAP_M = 35f;   // 전봇대 간격
    private const float WIRE_POLE_HEIGHT_M = 9f;
    private const float WIRE_POLE_ARM_M = 1f;
    private const float POLE_THICK_M = 0.28f;    // 기둥 굵기
    private const float WIRE_THICK_M = 0.05f;    // 전선 굵기
    private const float WIRE_MAX_GAPS = 1.8f;    // 전봇대 사이가 간격의 이 배수보다 멀면 전선을 안 이음
    private const int WIRE_COUNT = 3;            // 전봇대 사이 전선 수
    private const float SKIRT_M = 4f;            // 길 옆 벽(축대)을 아래로 내리는 길이
    private const float PROP_NEAR_M = 80f;       // 걸은 길에서 이 안에만 신호등·볼라드를 둠
    private const float ONE_WAY_LANES_M = 7f;    // 한쪽 방향 길이 이 폭 이상이면 가운데 흰 점선 (차로 2개)
    private const float ONE_WAY_BIG_M = 6f;      // 한쪽 방향 길이 이 폭 이상이어야 큰길 한쪽으로 봄 (좁으면 진입로·일방통행 골목이라 인도·분리대 없음)
    private const float SURFACE_SAMPLE_M = 3f;   // 찻길 높이를 잴 때 가운데에서 동서남북으로 이만큼 떨어진 점도 봄 (넓으면 남산 같은 비탈에서 길이 떠 보임)
    private const float HOLE_STEP_M = 2f;        // 다른 찻길 위인지 이 간격마다 확인 (찻길 폭보다 촘촘해야 놓치지 않음)
    private const float MEDIAN_M = 1.2f;         // 한쪽 방향 길의 왼쪽에 붙이는 중앙분리대 폭
    private const float PIECE_CELL_M = 20f;      // 찻길 조각을 이 크기 칸에 나눠 담음 (가까운 찻길만 빨리 찾으려고)
    private const float ON_ROAD_MARGIN_M = 0.5f; // 남의 찻길 가장자리에서 이만큼 안쪽부터 "찻길 위"로 봄
    private const int NO_ROAD = -1;              // 길 번호 없음 (= 다른 찻길 위인지 안 따짐)
    private const int FOOTWAY = -2;              // 걷는 길 (어느 찻길 위든 비움: 찻길과 겹쳐 깜빡이지 않게)

    // 재질 번호 (RetroMap이 이 순서대로 재질을 넘김)
    public const int CAR_ROAD = 0, PATH = 1, YELLOW = 2, WHITE = 3, SIDEWALK = 4, POLE = 5, CURB = 6, WIRE = 7, LAMP = 8;
    public const int MATERIAL_COUNT = 9;

    private readonly Ground ground;
    private readonly float lift; // 땅에서 띄우는 높이 (땅과 겹쳐 깜빡이지 않게)
    private readonly PropMaker props; // 주차된 차·신호등을 담을 곳 (없으면 null → 소품 안 그림)
    private readonly RouteArea area;  // 걸은 길 둘레 (가까운 곳에만 소품)
    private readonly List<Vector3> vertices = new List<Vector3>();
    private readonly List<Vector2> uv = new List<Vector2>(); // 꼭짓점마다 그림 좌표. 길은 미터 단위 (가로 = 길 폭 방향, 세로 = 길 따라 간 거리)
    private readonly List<int>[] triangles = new List<int>[MATERIAL_COUNT];

    // 교차로 찾기용: 찻길 끝점이 칸마다 몇 개 모였는지, 큰 찻길 목록 (인도·횡단보도는 다 모은 뒤 BuildMesh에서 그림)
    private readonly Dictionary<Vector2Int, int> endCounts = new Dictionary<Vector2Int, int>(); // Dictionary = Java의 HashMap
    private readonly List<List<Vector2>> bigLines = new List<List<Vector2>>();
    private readonly List<float> bigWidths = new List<float>();
    private readonly List<float> bigTops = new List<float>();
    private readonly List<bool> bigOneWays = new List<bool>(); // 한쪽 방향 길이면 오른쪽 인도만
    private readonly List<int> bigIds = new List<int>();       // 큰 찻길의 길 번호

    // 찻길 조각 하나 (struct: 값 몇 개를 묶은 작은 상자. class와 비슷하지만 가벼움)
    private struct RoadPiece
    {
        public Vector2 from;
        public Vector2 to;
        public float halfWidth;
        public int id; // 어느 길의 조각인지
    }
    private readonly Dictionary<Vector2Int, List<RoadPiece>> pieces = new Dictionary<Vector2Int, List<RoadPiece>>(); // 칸 → 그 칸에 걸친 찻길 조각들
    private readonly Dictionary<RoadData, int> roadIds = new Dictionary<RoadData, int>(); // 길 → 길 번호
    private bool[] holes; // 방금 AddStrip이 비운 칸들 (바로 뒤 AddSkirt가 같은 칸의 벽도 비우려고 씀)

    // props·area는 안 줘도 됨 (= null). 걸은 경로 선을 그릴 때처럼 소품이 필요 없을 때
    public RoadMaker(Ground ground, float lift, PropMaker props = null, RouteArea area = null)
    {
        this.ground = ground;
        this.lift = lift;
        this.props = props;
        this.area = area;
        for (int i = 0; i < MATERIAL_COUNT; i++) triangles[i] = new List<int>();
    }

    // 길 하나를 더함
    public void Add(RoadData road)
    {
        List<Vector2> line = Subdivide(road.points);
        if (line.Count < 2) return;

        if (road.kind == 1) // 걷는 길
        {
            AddStrip(line, road.width, 0, lift, PATH, false, 0, FOOTWAY);
            return;
        }
        float top = lift + road.width * WIDTH_LIFT; // 이 길 바닥 높이 (땅에서)
        int id = roadIds.TryGetValue(road, out int found) ? found : NO_ROAD; // out: 찾은 값을 found에 담아 줌
        AddSkirt(AddStrip(line, road.width, 0, top, CAR_ROAD, false, road.width, id, road.width / 2), line.Count);
        CountEnd(line[0]);
        CountEnd(line[line.Count - 1]);

        if (road.isOneWay && road.width >= ONE_WAY_BIG_M) // 큰길의 한쪽 방향: 차선 + 오른쪽(바깥쪽)만 인도·가로등. 왼쪽은 가운데 분리대 쪽이라 비움
        {
            if (road.width >= ONE_WAY_LANES_M) AddStrip(line, LINE_WIDTH_M / 2, 0, top + PAINT_LIFT_M, WHITE, true, road.width, id);
            AddPoles(line, -(road.width / 2 + SIDEWALK_M - 0.4f), LAMP_GAP_M, LAMP_HEIGHT_M, LAMP_ARM_M, false, id);
            AddBigLine(line, road.width, top, true, id);
            return;
        }
        if (road.width < BIG_ROAD_M)
        {
            if (road.width < ALLEY_M) return; // 아주 좁은 길은 띠만
            float edgeAt = road.width / 2 - EDGE_M / 2;
            AddStrip(line, EDGE_M, edgeAt, top + PAINT_LIFT_M, CURB, false, road.width, id);
            AddStrip(line, EDGE_M, -edgeAt, top + PAINT_LIFT_M, CURB, false, road.width, id);
            AddPoles(line, road.width / 2 + 0.3f, WIRE_POLE_GAP_M, WIRE_POLE_HEIGHT_M, WIRE_POLE_ARM_M, true, id);
            return;
        }

        // 차선은 찻길과 같은 자리(찻길 양쪽 가장자리)에서 땅 높이를 재야 찻길에 묻히지 않음 → 마지막 값 road.width
        AddStrip(line, LINE_WIDTH_M / 2, CENTER_GAP_M, top + PAINT_LIFT_M, YELLOW, false, road.width, id);
        AddStrip(line, LINE_WIDTH_M / 2, -CENTER_GAP_M, top + PAINT_LIFT_M, YELLOW, false, road.width, id);
        if (road.width >= LANE_ROAD_M)
        {
            AddStrip(line, LINE_WIDTH_M / 2, road.width / 4, top + PAINT_LIFT_M, WHITE, true, road.width, id);
            AddStrip(line, LINE_WIDTH_M / 2, -road.width / 4, top + PAINT_LIFT_M, WHITE, true, road.width, id);
        }
        float lampAt = road.width / 2 + SIDEWALK_M - 0.4f; // 인도 바깥쪽 가장자리
        AddPoles(line, lampAt, LAMP_GAP_M, LAMP_HEIGHT_M, LAMP_ARM_M, false, id);
        AddPoles(line, -lampAt, LAMP_GAP_M, LAMP_HEIGHT_M, LAMP_ARM_M, false, id);
        AddBigLine(line, road.width, top, false, id);
    }

    // 인도·횡단보도를 나중에(BuildMesh에서) 그리려고 큰 찻길을 모아 둠
    private void AddBigLine(List<Vector2> line, float width, float top, bool isOneWay, int id)
    {
        bigIds.Add(id);
        bigLines.Add(line);
        bigWidths.Add(width);
        bigTops.Add(top);
        bigOneWays.Add(isOneWay);
    }

    // 꼭짓점 하나 넣기 (자리 + 그림 좌표)
    private void AddVertex(Vector3 position, Vector2 texture)
    {
        vertices.Add(position);
        uv.Add(texture);
    }

    // 선을 따라 폭 width짜리 띠 한 줄. 돌려줌: 이 띠의 첫 꼭짓점 번호
    //  offset: 가운데 선에서 왼쪽(+)·오른쪽(−)으로 옮김, isDashed: 한 칸 건너 한 칸만 그림(점선)
    //  sampleWidth: 0이면 띠의 양쪽 끝 땅에 맞춤 (인도·걷는 길), 0보다 크면 길 가운데 기준 (찻길·차선)
    //  ownerId: 이 띠가 붙은 길의 번호. 주면 다른 찻길 위에 놓이는 칸은 비움 (안 주면 다 그림)
    //  ownHalfWidth: 찻길 바닥일 때 그 길 폭의 절반. 주면 "더 넓은 찻길" 위인 칸만 비움
    private int AddStrip(List<Vector2> line, float width, float offset, float height, int material, bool isDashed, float sampleWidth, int ownerId = NO_ROAD, float ownHalfWidth = 0)
    {
        holes = FindHoles(line, offset, ownerId, ownHalfWidth);
        int first = vertices.Count;
        float along = 0; // 길을 따라 간 거리(m)
        for (int i = 0; i < line.Count; i++)
        {
            if (i > 0) along += Vector2.Distance(line[i - 1], line[i]);
            Vector2 side = SideAt(line, i);
            Vector2 left = line[i] + side * (offset + width / 2);
            Vector2 right = line[i] + side * (offset - width / 2);
            // 찻길·차선: 길 가운데 기준 같은 규칙. 인도·걷는 길: 거기에 자기 양쪽 끝 땅도 봄 (비탈에서 한쪽이 묻히지 않게)
            float y = sampleWidth > 0
                ? SurfaceGround(line[i])
                : Mathf.Max(ground.HeightAt(left.x, left.y), ground.HeightAt(right.x, right.y)); // 인도·걷는 길은 자기 양쪽 끝 땅 중 높은 쪽
            y += height;
            AddVertex(new Vector3(left.x, y, left.y), new Vector2(0, along));
            AddVertex(new Vector3(right.x, y, right.y), new Vector2(width, along));
        }

        List<int> list = triangles[material];
        for (int i = 0; i < line.Count - 1; i++)
        {
            if (isDashed && i % 2 == 1) continue;
            if (holes != null && holes[i]) continue;
            int a = first + i * 2; // 이번 점의 왼쪽. a+1 = 이번 오른쪽, a+2 = 다음 왼쪽, a+3 = 다음 오른쪽
            list.Add(a); list.Add(a + 2); list.Add(a + 3);
            list.Add(a); list.Add(a + 3); list.Add(a + 1);
        }
        return first;
    }

    // 옆 벽: 띠의 양쪽 가장자리에서 아래로 벽을 내림 (연석·축대)
    // 왜: 비탈에서 길을 높은 쪽 땅에 맞추면 낮은 쪽은 땅에서 떠서, 얇은 판이 공중에 뜬 것처럼 보임 → 벽으로 막음
    //     인도에서는 찻길 쪽 벽이 찻길 위로 15cm 드러나서 연석이 됨
    // first: AddStrip이 방금 넣은 띠의 첫 꼭짓점 번호, count: 띠의 점 수
    private void AddSkirt(int first, int count)
    {
        int bottom = vertices.Count; // 아래쪽 꼭짓점들이 시작되는 번호
        for (int i = 0; i < count * 2; i++) AddVertex(vertices[first + i] + Vector3.down * SKIRT_M, uv[first + i]);

        List<int> list = triangles[CURB];
        for (int i = 0; i < count - 1; i++)
        {
            if (holes != null && holes[i]) continue; // 띠를 비운 칸은 벽도 비움
            int top = first + i * 2;      // 이번 점 왼쪽 위. +1 오른쪽 위, +2 다음 왼쪽 위, +3 다음 오른쪽 위
            int low = bottom + i * 2;     // 같은 순서의 아래쪽
            list.Add(top + 2); list.Add(top); list.Add(low);             // 왼쪽 벽 (왼쪽에서 봤을 때 보이는 방향)
            list.Add(top + 2); list.Add(low); list.Add(low + 2);
            list.Add(top + 1); list.Add(top + 3); list.Add(low + 3);     // 오른쪽 벽
            list.Add(top + 1); list.Add(low + 3); list.Add(low + 1);
        }
    }

    // i번째 점에서 길의 왼쪽을 가리키는 길이 1짜리 방향 (길이 향하는 방향을 왼쪽으로 90도 돌린 것)
    private Vector2 SideAt(List<Vector2> line, int i)
    {
        Vector2 direction = (line[Mathf.Min(i + 1, line.Count - 1)] - line[Mathf.Max(i - 1, 0)]).normalized;
        return new Vector2(-direction.y, direction.x);
    }

    // x, z, x, z … 숫자 목록 → 점 목록. 점 사이가 멀면 STEP_M마다 점을 더 넣음
    private List<Vector2> Subdivide(float[] points)
    {
        List<Vector2> line = new List<Vector2>();
        for (int i = 0; i + 1 < points.Length; i += 2)
        {
            Vector2 point = new Vector2(points[i], points[i + 1]);
            if (line.Count > 0)
            {
                Vector2 before = line[line.Count - 1];
                int pieces = Mathf.CeilToInt(Vector2.Distance(before, point) / STEP_M);
                for (int piece = 1; piece < pieces; piece++) line.Add(Vector2.Lerp(before, point, piece / (float)pieces));
            }
            line.Add(point);
        }
        return line;
    }

    // ---------- 길가 기둥: 큰 찻길은 양쪽에 가로등, 골목은 한쪽에 전봇대 (전봇대끼리는 전선으로 이음)

    // 길을 따라 가며 gap(m)마다 기둥을 세움. sideAt: 가운데 선에서 옆으로 떨어진 거리(+ 왼쪽, − 오른쪽), arm: 길 쪽으로 뻗는 팔 길이
    private void AddPoles(List<Vector2> line, float sideAt, float gap, float height, float arm, bool hasWires, int ownerId)
    {
        float walked = gap / 2; // 첫 기둥은 반 간격 지난 곳에
        bool hasBefore = false;
        Vector3 beforeTop = Vector3.zero;
        for (int i = 1; i < line.Count; i++)
        {
            walked += Vector2.Distance(line[i - 1], line[i]);
            if (walked < gap) continue;
            walked = 0;

            Vector2 side = SideAt(line, i);
            Vector2 at = line[i] + side * sideAt;
            if (IsOnOtherRoad(at, ownerId))
            {
                hasBefore = false; // 기둥을 건너뛰면 전선도 끊음
                continue;
            }
            Vector3 foot = new Vector3(at.x, ground.HeightAt(at.x, at.y), at.y);
            Vector3 top = foot + Vector3.up * height;
            Vector2 toRoad = -side * Mathf.Sign(sideAt) * arm; // 길 가운데를 향하는 방향으로 팔 길이만큼
            AddBeam(foot, top, POLE_THICK_M, POLE);
            AddBeam(top, top + new Vector3(toRoad.x, 0, toRoad.y), POLE_THICK_M * 0.6f, POLE);
            if (!hasWires) // 가로등이면 팔 끝에 등 (밤에 빛남)
            {
                Vector3 lampAt = top + new Vector3(toRoad.x, 0, toRoad.y);
                AddBeam(lampAt, lampAt + Vector3.down * LAMP_HEAD_M, LAMP_HEAD_M * 1.6f, LAMP);
            }

            bool isNearBefore = hasBefore && Vector3.Distance(beforeTop, top) < gap * WIRE_MAX_GAPS; // 전봇대가 너무 멀면 전선을 안 이음 (하늘을 가로지르는 긴 선이 생겨서)
            if (hasWires && isNearBefore)
            {
                for (int k = 0; k < WIRE_COUNT; k++)
                {
                    Vector3 drop = Vector3.down * (0.4f + k * 0.45f); // 전선마다 높이를 조금씩 다르게
                    AddBeam(beforeTop + drop, top + drop, WIRE_THICK_M, WIRE);
                }
            }
            beforeTop = top;
            hasBefore = true;
        }
    }

    // 막대 하나: from에서 to까지 굵기 thick인 네모 기둥 (옆면 네 장)
    private void AddBeam(Vector3 from, Vector3 to, float thick, int material)
    {
        Vector3 along = (to - from).normalized;
        // 막대와 직각인 두 방향을 구함. Cross = 두 방향 모두에 직각인 방향을 구하는 계산
        Vector3 u = Vector3.Cross(along, Mathf.Abs(along.y) > 0.9f ? Vector3.right : Vector3.up).normalized * (thick / 2);
        Vector3 v = Vector3.Cross(along, u).normalized * (thick / 2);
        Vector3[] corners = { u + v, v - u, -u - v, u - v }; // 막대를 끝에서 봤을 때의 네 귀퉁이

        int first = vertices.Count;
        foreach (Vector3 corner in corners)
        {
            AddVertex(from + corner, Vector2.zero);
            AddVertex(to + corner, Vector2.zero);
        }
        List<int> list = triangles[material];
        for (int i = 0; i < 4; i++)
        {
            int a = first + i * 2;             // 이 귀퉁이의 from 쪽. a+1 = to 쪽
            int b = first + ((i + 1) % 4) * 2; // 다음 귀퉁이
            list.Add(a); list.Add(a + 1); list.Add(b + 1);
            list.Add(a); list.Add(b + 1); list.Add(b);
        }
    }


    // i번째 점에서 찻길 바닥 높이 (AddStrip이 찻길을 놓은 높이와 같은 규칙)
    private float RoadSurfaceY(List<Vector2> line, int i, float width, float top)
    {
        return SurfaceGround(line[i]) + top;
    }

    // 이 자리 찻길 밑 땅 높이: 가운데와 동서남북 SURFACE_SAMPLE_M 떨어진 다섯 점 중 가장 높은 땅
    //   길 폭·방향과 상관없는 규칙이라, 두 길이 만나는 곳에서 높이가 같음
    private float SurfaceGround(Vector2 point)
    {
        float highest = ground.HeightAt(point.x, point.y);
        highest = Mathf.Max(highest, ground.HeightAt(point.x + SURFACE_SAMPLE_M, point.y));
        highest = Mathf.Max(highest, ground.HeightAt(point.x - SURFACE_SAMPLE_M, point.y));
        highest = Mathf.Max(highest, ground.HeightAt(point.x, point.y + SURFACE_SAMPLE_M));
        highest = Mathf.Max(highest, ground.HeightAt(point.x, point.y - SURFACE_SAMPLE_M));
        return highest;
    }

    // ---------- 교차로: 찻길 끝점이 3개 이상 모인 곳

    private Vector2Int CellOf(Vector2 point)
    {
        return new Vector2Int(Mathf.RoundToInt(point.x / JUNCTION_CELL_M), Mathf.RoundToInt(point.y / JUNCTION_CELL_M));
    }

    private void CountEnd(Vector2 point)
    {
        Vector2Int cell = CellOf(point);
        endCounts.TryGetValue(cell, out int count); // 없으면 count = 0
        endCounts[cell] = count + 1;
    }

    private bool IsJunction(Vector2 point)
    {
        return endCounts.TryGetValue(CellOf(point), out int count) && count >= 3;
    }

    // 큰 찻길마다: 양옆 인도(교차로 근처는 비움) + 교차로 쪽 끝에 횡단보도
    private void AddSidewalksAndCrosswalks()
    {
        for (int i = 0; i < bigLines.Count; i++)
        {
            List<Vector2> line = bigLines[i];
            bool isStartJunction = IsJunction(line[0]);
            bool isEndJunction = IsJunction(line[line.Count - 1]);

            List<Vector2> walk = Trim(line, isStartJunction, isEndJunction);
            float sidewalkAt = bigWidths[i] / 2 + SIDEWALK_M / 2; // 가운데에서 인도 가운데까지
            if (walk.Count >= 2)
            {
                int id = bigIds[i];
                if (!bigOneWays[i]) AddSkirt(AddStrip(walk, SIDEWALK_M, sidewalkAt, bigTops[i] + CURB_M, SIDEWALK, false, 0, id), walk.Count); // 왼쪽
                else AddSkirt(AddStrip(walk, MEDIAN_M, bigWidths[i] / 2 + MEDIAN_M / 2, bigTops[i] + CURB_M, CURB, false, 0, id), walk.Count);   // 왼쪽은 중앙분리대
                AddSkirt(AddStrip(walk, SIDEWALK_M, -sidewalkAt, bigTops[i] + CURB_M, SIDEWALK, false, 0, id), walk.Count);                     // 오른쪽
            }
            if (isStartJunction) AddCrosswalk(line, bigWidths[i], bigTops[i], true, bigIds[i]);
            if (isEndJunction) AddCrosswalk(line, bigWidths[i], bigTops[i], false, bigIds[i]);
        }
    }

    // 선의 앞쪽·뒤쪽에서 JUNCTION_CLEAR_M 안의 점을 뺀 선 (교차로 쪽 끝만)
    private List<Vector2> Trim(List<Vector2> line, bool cutStart, bool cutEnd)
    {
        float total = 0;
        for (int i = 1; i < line.Count; i++) total += Vector2.Distance(line[i - 1], line[i]);

        List<Vector2> result = new List<Vector2>();
        float along = 0;
        for (int i = 0; i < line.Count; i++)
        {
            if (i > 0) along += Vector2.Distance(line[i - 1], line[i]);
            bool isNearStart = cutStart && along < JUNCTION_CLEAR_M;
            bool isNearEnd = cutEnd && total - along < JUNCTION_CLEAR_M;
            if (!isNearStart && !isNearEnd) result.Add(line[i]);
        }
        return result;
    }

    // 길 끝(isAtStart면 시작 쪽)에서 CROSSWALK_BACK_M만큼 들어온 자리에 흰 줄들을 그림
    private void AddCrosswalk(List<Vector2> line, float width, float top, bool isAtStart, int ownerId)
    {
        Vector2 end = isAtStart ? line[0] : line[line.Count - 1];
        int at = -1;
        for (int i = 0; i < line.Count && at < 0; i++)
        {
            int index = isAtStart ? i : line.Count - 1 - i;
            if (Vector2.Distance(line[index], end) >= CROSSWALK_BACK_M) at = index;
        }
        if (at < 0) return; // 길이 너무 짧음
        if (IsOnOtherRoad(line[at], ownerId, width / 2)) return; // 횡단보도 자리가 더 넓은 찻길 위 (교차로 안 짧은 길 조각)

        Vector2 side = SideAt(line, at);
        Vector2 along = new Vector2(side.y, -side.x) * (CROSSWALK_LONG_M / 2); // 길이 향하는 방향으로 줄 길이의 절반
        float y = SurfaceGround(line[at]) + top + PAINT_LIFT_M * 2;
        for (float offset = -width / 2 + STRIPE_M; offset < width / 2 - STRIPE_M; offset += STRIPE_M * 2)
        {
            Vector2 a = line[at] + side * offset;              // 줄의 오른쪽 가장자리 가운데
            Vector2 b = line[at] + side * (offset + STRIPE_M); // 줄의 왼쪽 가장자리 가운데
            int first = vertices.Count;
            AddVertex(new Vector3(b.x - along.x, y, b.y - along.y), Vector2.zero); // 0: 왼쪽 뒤
            AddVertex(new Vector3(a.x - along.x, y, a.y - along.y), Vector2.zero); // 1: 오른쪽 뒤
            AddVertex(new Vector3(b.x + along.x, y, b.y + along.y), Vector2.zero); // 2: 왼쪽 앞
            AddVertex(new Vector3(a.x + along.x, y, a.y + along.y), Vector2.zero); // 3: 오른쪽 앞
            List<int> list = triangles[WHITE];
            list.Add(first); list.Add(first + 2); list.Add(first + 3);
            list.Add(first); list.Add(first + 3); list.Add(first + 1);
        }
        AddCrossingProps(line, at, width, y - PAINT_LIFT_M * 2, isAtStart, ownerId);
    }

    // 횡단보도 옆 인도에 신호등 하나 + 볼라드 세 개 (걸은 길 가까운 곳만)
    // roadY: 그 자리 찻길 바닥 높이, isAtStart: 교차로가 선의 시작 쪽인지 (신호등을 교차로 쪽으로 세움)
    private void AddCrossingProps(List<Vector2> line, int at, float width, float roadY, bool isAtStart, int ownerId)
    {
        if (props == null || area == null || !area.IsNear(line[at].x, line[at].y, PROP_NEAR_M)) return;
        Vector2 side = SideAt(line, at);
        Vector2 forward = new Vector2(side.y, -side.x) * (isAtStart ? -1 : 1); // 교차로 쪽
        Vector2 polePlace = line[at] - side * (width / 2 + 0.8f) + forward * (CROSSWALK_LONG_M / 2 + 1);
        if (IsOnOtherRoad(polePlace, ownerId)) return; // 신호등 자리가 다른 찻길 한가운데면 안 세움
        float sidewalkY = roadY + CURB_M;
        StreetProps.AddTrafficLight(props, new Vector3(polePlace.x, sidewalkY, polePlace.y), side, width / 2);
        for (int k = -1; k <= 1; k++) // 횡단보도 앞 인도 가장자리에 1.5m 간격으로
        {
            Vector2 bollard = line[at] + side * (width / 2 + 0.35f) + forward * (k * 1.5f);
            StreetProps.AddBollard(props, new Vector3(bollard.x, sidewalkY, bollard.y));
        }
    }

    // ---------- 다른 찻길 위인지 알아보기

    // 그릴 찻길 전부를 먼저 알려 줌 (Add보다 먼저 부름). 길마다 번호를 매기고 조각을 칸에 나눠 담음
    public void MarkCarRoads(List<RoadData> roads)
    {
        foreach (RoadData road in roads)
        {
            if (road.kind == 1) continue; // 걷는 길은 위에 인도가 놓여도 괜찮음
            int id = roadIds.Count;
            roadIds[road] = id;
            for (int i = 0; i + 3 < road.points.Length; i += 2)
            {
                RoadPiece piece = new RoadPiece
                {
                    from = new Vector2(road.points[i], road.points[i + 1]),
                    to = new Vector2(road.points[i + 2], road.points[i + 3]),
                    halfWidth = road.width / 2,
                    id = id,
                };
                AddPiece(piece);
            }
        }
    }

    // 조각 하나를, 조각을 감싸는 네모(+길 폭 절반)가 걸치는 칸마다 넣음
    private void AddPiece(RoadPiece piece)
    {
        Vector2 low = Vector2.Min(piece.from, piece.to) - Vector2.one * piece.halfWidth;
        Vector2 high = Vector2.Max(piece.from, piece.to) + Vector2.one * piece.halfWidth;
        for (int x = PieceCell(low.x); x <= PieceCell(high.x); x++)
        {
            for (int z = PieceCell(low.y); z <= PieceCell(high.y); z++)
            {
                Vector2Int cell = new Vector2Int(x, z);
                if (!pieces.ContainsKey(cell)) pieces[cell] = new List<RoadPiece>();
                pieces[cell].Add(piece);
            }
        }
    }

    private static int PieceCell(float meters)
    {
        return Mathf.FloorToInt(meters / PIECE_CELL_M);
    }

    // 이 자리가 ownerId가 아닌 다른 찻길의 바닥 위인지
    //   ownHalfWidth를 주면(찻길 바닥끼리) 더 넓은 길만 셈. 폭이 같으면 번호가 작은 길이 이김 (둘 중 하나만 그리려고)
    private bool IsOnOtherRoad(Vector2 point, int ownerId, float ownHalfWidth = 0)
    {
        if (ownerId == NO_ROAD) return false;
        if (!pieces.TryGetValue(new Vector2Int(PieceCell(point.x), PieceCell(point.y)), out List<RoadPiece> list)) return false;
        foreach (RoadPiece piece in list)
        {
            if (piece.id == ownerId) continue;
            bool isWinner = piece.halfWidth > ownHalfWidth || (piece.halfWidth == ownHalfWidth && piece.id < ownerId);
            if (ownHalfWidth > 0 && !isWinner) continue;
            if (DistanceToPiece(point, piece) < piece.halfWidth - ON_ROAD_MARGIN_M) return true;
        }
        return false;
    }

    // 점에서 조각(선분)까지 가장 가까운 거리
    private static float DistanceToPiece(Vector2 point, RoadPiece piece)
    {
        Vector2 along = piece.to - piece.from;
        float rate = Mathf.Clamp01(Vector2.Dot(point - piece.from, along) / Mathf.Max(along.sqrMagnitude, 0.0001f)); // 선분 위 가장 가까운 곳 (0 = from, 1 = to)
        return Vector2.Distance(point, piece.from + along * rate);
    }

    // 띠의 칸마다 다른 찻길 위에 걸치는지 (칸을 HOLE_STEP_M마다 확인). ownerId가 없으면 null = 다 그림
    private bool[] FindHoles(List<Vector2> line, float offset, int ownerId, float ownHalfWidth)
    {
        if (ownerId == NO_ROAD) return null;
        bool[] result = new bool[line.Count];
        for (int i = 0; i + 1 < line.Count; i++)
        {
            Vector2 from = line[i] + SideAt(line, i) * offset;
            Vector2 to = line[i + 1] + SideAt(line, i + 1) * offset;
            int checks = Mathf.Max(1, Mathf.CeilToInt(Vector2.Distance(from, to) / HOLE_STEP_M));
            for (int k = 0; k <= checks && !result[i]; k++) result[i] = IsOnOtherRoad(Vector2.Lerp(from, to, (float)k / checks), ownerId, ownHalfWidth);
        }
        return result;
    }

    // 지금까지 더한 길 전부를 Mesh 하나로. 재질 순서는 위의 재질 번호 (CAR_ROAD … WIRE)
    public Mesh BuildMesh()
    {
        AddSidewalksAndCrosswalks();
        Mesh mesh = new Mesh();
        mesh.indexFormat = IndexFormat.UInt32;
        mesh.SetVertices(vertices);
        mesh.SetUVs(0, uv);
        mesh.subMeshCount = MATERIAL_COUNT;
        for (int i = 0; i < MATERIAL_COUNT; i++) mesh.SetTriangles(triangles[i], i);
        mesh.RecalculateNormals();
        mesh.RecalculateTangents(); // 아스팔트·보도블록 질감(노멀 맵)이 빛을 바르게 받으려면 필요
        return mesh;
    }
}
