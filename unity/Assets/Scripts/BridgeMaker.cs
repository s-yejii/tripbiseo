// 한강 다리: 지도 자료의 "다리" 길 중 물 위를 지나는 것을 물 위로 띄워 그림
//   물 위 = 물 높이 + DECK_M, 땅 위 = 땅 높이 → 그 사이는 경사로처럼 천천히 오르내림
//   상판 + 양쪽 난간 + 물속까지 내려가는 교각 (색칠한 상자, PropMaker)
//   다리 끝이 땅 위면 그 끝은 땅 높이로 내려와서 땅 위 길과 이어짐
//   밤 조명: 난간 아래 조명 줄 + 가로등 (lights에 따로 모아 빛나는 재질로 그림)
//   다리 위 높이도 기억해 둠 → 다리를 건넌 경로 선·걷는 사람·탈것을 다리 위에 놓음 (땅에 두면 다리 밑에 숨어서)
using System.Collections.Generic;
using UnityEngine;

public class BridgeMaker
{
    private const float DECK_M = 12f;          // 물 위 상판 높이 (한강 다리는 물에서 10~15m)
    private const float STEP_M = 15f;          // 다리 선을 이 간격으로 잘게 나눔 (높이를 점마다 정하려고)
    private const float RAMP_SLOPE = 0.05f;    // 경사로 기울기 (1m 갈 때 5cm 오르내림)
    private const float PILLAR_GAP_M = 60f;    // 교각 사이 거리
    private const float DECK_THICK_M = 1.6f;   // 상판 두께
    private const float RAIL_M = 1.1f;         // 난간 높이
    private const float END_SLOPE = 0.08f;     // 땅 위 다리 끝에서 오르는 기울기 (1m에 8cm)
    private const float WATER_CLEAR_M = 2f;    // 상판은 물보다 적어도 이만큼 위
    private const float LAMP_GAP_M = 32f;      // 다리 가로등 간격
    private const float LAMP_POLE_M = 6f;      // 다리 가로등 높이
    private const float PILLAR_RADIUS_M = 1.6f; // 교각 기둥 반지름
    private const float LIGHT_LINE_M = 0.45f;  // 난간 아래 조명 줄 굵기 (멀리서도 보이게)
    private const float DECK_CELL_M = 20f;     // 다리 조각을 이 크기 칸에 나눠 담음 (가까운 다리만 빨리 찾으려고)
    private const float SAME_WAY = 0.8f;       // 가는 방향과 다리 방향이 이만큼(cos, 약 37도 안) 나란해야 "다리 위를 건넘" (다리 밑을 지나는 길과 구별)
    private const float SURFACE_UP_M = 0.05f;  // 찻길 면은 상판 선보다 이만큼 위
    private const float GPS_REACH_M = 4f;      // GPS는 몇 m씩 어긋나서, 다리 가장자리에서 이만큼 밖까지도 다리 위로 봄
    private static readonly Color DECK = new Color(0.40f, 0.45f, 0.50f); // 짙은 청회색 상판 (동호대교 로드뷰)
    private static readonly Color ASPHALT = new Color(0.68f, 0.69f, 0.71f); // 찻길 면: 화면 색 보정(ACES)을 거치면 꽤 어두워져서 밝게 (0.3이면 새까맣게 보임)
    private static readonly Color PILLAR = new Color(0.66f, 0.65f, 0.62f);
    private static readonly Color RAIL = new Color(0.80f, 0.80f, 0.78f);

    private readonly MapData data;
    private readonly Ground ground;
    private readonly PropMaker props;
    private readonly PropMaker lights; // 밤에 빛나는 부분 (조명 줄·가로등 등)

    // 다리 상판 한 조각: 가운데 선 from → to, 그 두 점의 높이, 폭 절반
    private struct DeckPiece
    {
        public Vector2 from;
        public Vector2 to;
        public float fromHeight;
        public float toHeight;
        public float halfWidth;
    }
    private readonly Dictionary<Vector2Int, List<DeckPiece>> decks = new Dictionary<Vector2Int, List<DeckPiece>>(); // 칸 → 그 칸에 걸친 다리 조각들

    public BridgeMaker(MapData data, Ground ground, PropMaker props, PropMaker lights)
    {
        this.data = data;
        this.ground = ground;
        this.props = props;
        this.lights = lights;
    }

    // 이 길이 물 위를 지나는 다리인지 (아니면 보통 길로 그림)
    public bool IsOverWater(RoadData road)
    {
        if (!road.isBridge) return false;
        foreach (Vector2 point in Densify(road.points)) // 양 끝만 강가에 있는 다리도 있어서, 잘게 나눈 점으로 봄
        {
            if (WaterMaker.IsWaterAt(data, point.x, point.y)) return true;
        }
        return false;
    }

    // 다리 하나
    public void Add(RoadData road)
    {
        List<Vector2> line = Densify(road.points);
        float[] heights = DeckHeights(line);
        float sinceLastPillar = PILLAR_GAP_M / 2;
        float sinceLastLamp = LAMP_GAP_M / 2;
        for (int i = 0; i + 1 < line.Count; i++)
        {
            Vector3 from = new Vector3(line[i].x, heights[i], line[i].y);
            Vector3 to = new Vector3(line[i + 1].x, heights[i + 1], line[i + 1].y);
            bool isSpanOverWater = WaterMaker.IsWaterAt(data, line[i].x, line[i].y) || WaterMaker.IsWaterAt(data, line[i + 1].x, line[i + 1].y);
            if (isSpanOverWater) RememberDeck(line[i], line[i + 1], heights[i], heights[i + 1], road.width / 2); // 물 위 부분만 (땅 위 진입로 밑을 걸어간 길이 다리 위로 올라가지 않게)
            props.AddBeam(from - Vector3.up * DECK_THICK_M / 2, to - Vector3.up * DECK_THICK_M / 2, road.width + 1, DECK_THICK_M, DECK); // 상판
            props.AddBeam(from + Vector3.up * 0.02f, to + Vector3.up * 0.02f, road.width, 0.05f, ASPHALT);                            // 찻길 면
            AddRails(from, to, road.width);
            sinceLastLamp += Vector2.Distance(line[i], line[i + 1]);
            if (sinceLastLamp >= LAMP_GAP_M)
            {
                AddLamps(from, to, road.width);
                sinceLastLamp = 0;
            }

            sinceLastPillar += Vector2.Distance(line[i], line[i + 1]);
            bool isOverWater = WaterMaker.IsWaterAt(data, line[i].x, line[i].y);
            if (isOverWater && sinceLastPillar >= PILLAR_GAP_M)
            {
                AddPillar(from, to - from, road.width);
                sinceLastPillar = 0;
            }
        }
    }

    // 이 자리를 heading 쪽으로 건너는 다리가 있으면 true와 그 찻길 면 높이 (out: 결과를 height에 담아 줌)
    //   heading이 다리와 나란하지 않으면(다리 밑을 가로지름) false
    public bool TryDeckHeight(Vector2 point, Vector2 heading, out float height)
    {
        height = 0;
        if (heading.magnitude < 0.1f) return false; // 가만히 있으면 방향을 몰라서 땅으로 봄
        if (!decks.TryGetValue(CellOf(point), out List<DeckPiece> list)) return false;
        foreach (DeckPiece piece in list)
        {
            Vector2 along = piece.to - piece.from;
            if (Mathf.Abs(Vector2.Dot(along.normalized, heading.normalized)) < SAME_WAY) continue;
            float rate = Mathf.Clamp01(Vector2.Dot(point - piece.from, along) / Mathf.Max(along.sqrMagnitude, 0.0001f)); // 조각 위 가장 가까운 곳 (0 = from, 1 = to)
            if (Vector2.Distance(point, piece.from + along * rate) > piece.halfWidth + GPS_REACH_M) continue;
            height = Mathf.Lerp(piece.fromHeight, piece.toHeight, rate) + SURFACE_UP_M;
            return true;
        }
        return false;
    }

    // 상판 한 조각을 기억해 둠: 조각을 감싸는 네모(+폭 절반)가 걸치는 칸마다
    private void RememberDeck(Vector2 from, Vector2 to, float fromHeight, float toHeight, float halfWidth)
    {
        DeckPiece piece = new DeckPiece { from = from, to = to, fromHeight = fromHeight, toHeight = toHeight, halfWidth = halfWidth };
        Vector2Int low = CellOf(Vector2.Min(from, to) - Vector2.one * halfWidth);
        Vector2Int high = CellOf(Vector2.Max(from, to) + Vector2.one * halfWidth);
        for (int x = low.x; x <= high.x; x++)
        {
            for (int z = low.y; z <= high.y; z++)
            {
                Vector2Int cell = new Vector2Int(x, z);
                if (!decks.ContainsKey(cell)) decks[cell] = new List<DeckPiece>();
                decks[cell].Add(piece);
            }
        }
    }

    private static Vector2Int CellOf(Vector2 point)
    {
        return new Vector2Int(Mathf.FloorToInt(point.x / DECK_CELL_M), Mathf.FloorToInt(point.y / DECK_CELL_M));
    }

    // 점 사이가 STEP_M보다 멀면 사이에 점을 넣음 (지도 자료의 다리는 양 끝 두 점뿐인 경우가 많아서)
    private static List<Vector2> Densify(float[] points)
    {
        List<Vector2> line = new List<Vector2>();
        for (int i = 0; i + 3 < points.Length; i += 2)
        {
            Vector2 a = new Vector2(points[i], points[i + 1]);
            Vector2 b = new Vector2(points[i + 2], points[i + 3]);
            int pieces = Mathf.Max(1, Mathf.CeilToInt(Vector2.Distance(a, b) / STEP_M));
            for (int k = 0; k < pieces; k++) line.Add(Vector2.Lerp(a, b, (float)k / pieces));
        }
        line.Add(new Vector2(points[points.Length - 2], points[points.Length - 1]));
        return line;
    }

    // 점마다 상판 높이: 물 위는 물 높이 + DECK_M, 땅 위는 땅 높이. 그다음 앞뒤로 훑으며 경사로를 만듦
    //   (높은 점에서 RAMP_SLOPE보다 가파르게 내려가지 않게 → 다리 끝에서 땅까지 천천히 내려옴)
    private float[] DeckHeights(List<Vector2> line)
    {
        float[] heights = new float[line.Count];
        for (int i = 0; i < line.Count; i++)
        {
            bool isOverWater = WaterMaker.IsWaterAt(data, line[i].x, line[i].y);
            heights[i] = isOverWater ? data.water.level + DECK_M : ground.HeightAt(line[i].x, line[i].y) + 0.5f;
        }
        for (int i = 1; i < line.Count; i++) heights[i] = Mathf.Max(heights[i], heights[i - 1] - Vector2.Distance(line[i], line[i - 1]) * RAMP_SLOPE);
        for (int i = line.Count - 2; i >= 0; i--) heights[i] = Mathf.Max(heights[i], heights[i + 1] - Vector2.Distance(line[i], line[i + 1]) * RAMP_SLOPE);
        LowerLandEnds(line, heights);
        return heights;
    }

    // 다리 끝이 땅 위면 그 끝은 땅 높이로 내리고, 끝에서 END_SLOPE로 오르게 함 (공중에서 끊기지 않고 땅 위 길과 이어지게)
    //   예) 땅 끝에서 50m 떨어진 점은 많아야 "끝 높이 + 4m"
    private void LowerLandEnds(List<Vector2> line, float[] heights)
    {
        int last = line.Count - 1;
        bool isFirstOnLand = !WaterMaker.IsWaterAt(data, line[0].x, line[0].y);
        bool isLastOnLand = !WaterMaker.IsWaterAt(data, line[last].x, line[last].y);
        float firstHeight = ground.HeightAt(line[0].x, line[0].y) + 0.5f;
        float lastHeight = ground.HeightAt(line[last].x, line[last].y) + 0.5f;
        float fromFirst = 0;
        float[] fromLast = new float[line.Count]; // 끝에서 거꾸로 잰 거리
        for (int i = last - 1; i >= 0; i--) fromLast[i] = fromLast[i + 1] + Vector2.Distance(line[i], line[i + 1]);
        for (int i = 0; i <= last; i++)
        {
            if (i > 0) fromFirst += Vector2.Distance(line[i], line[i - 1]);
            if (isFirstOnLand) heights[i] = Mathf.Min(heights[i], firstHeight + fromFirst * END_SLOPE);
            if (isLastOnLand) heights[i] = Mathf.Min(heights[i], lastHeight + fromLast[i] * END_SLOPE);
            heights[i] = Mathf.Max(heights[i], ground.HeightAt(line[i].x, line[i].y) + 0.5f); // 땅 밑으로는 안 내려감
            if (WaterMaker.IsWaterAt(data, line[i].x, line[i].y)) heights[i] = Mathf.Max(heights[i], data.water.level + WATER_CLEAR_M);
        }
    }

    // 상판 양쪽 가장자리의 난간
    private void AddRails(Vector3 from, Vector3 to, float width)
    {
        Vector3 along = (to - from).normalized;
        Vector3 side = new Vector3(-along.z, 0, along.x).normalized * (width / 2 + 0.3f);
        Vector3 up = Vector3.up * RAIL_M / 2;
        props.AddBeam(from + side + up, to + side + up, 0.35f, RAIL_M, RAIL);
        props.AddBeam(from - side + up, to - side + up, 0.35f, RAIL_M, RAIL);
        Vector3 outside = side.normalized * 0.25f; // 난간 바깥 아래쪽 조명 줄 (밤에 다리 모양이 선으로 보이게)
        Vector3 below = Vector3.down * (DECK_THICK_M + 0.1f);
        lights.AddBeam(from + side + outside + below, to + side + outside + below, LIGHT_LINE_M, LIGHT_LINE_M, Color.white);
        lights.AddBeam(from - side - outside + below, to - side - outside + below, LIGHT_LINE_M, LIGHT_LINE_M, Color.white);
    }

    // 다리 양쪽 가로등 한 쌍: 기둥은 보통 소품, 등은 빛나는 소품
    private void AddLamps(Vector3 from, Vector3 to, float width)
    {
        Vector3 along = (to - from).normalized;
        Vector3 side = new Vector3(-along.z, 0, along.x).normalized * (width / 2 + 0.3f);
        for (int sign = -1; sign <= 1; sign += 2)
        {
            Vector3 foot = from + side * sign;
            Vector3 top = foot + Vector3.up * LAMP_POLE_M;
            props.AddBeam(foot, top, 0.2f, 0.2f, RAIL);
            lights.AddBox(top - side.normalized * sign * 0.8f, new Vector3(1.4f, 0.3f, 0.5f), Quaternion.LookRotation(side), Color.white);
        }
    }

    // 교각 하나: 상판 아래에서 강바닥까지. 다리 폭 쪽으로 길쭉하게 (along = 다리가 놓인 방향)
    private void AddPillar(Vector3 top, Vector3 along, float width)
    {
        float bottom = ground.HeightAt(top.x, top.z) - 1;
        float height = top.y - DECK_THICK_M - bottom;
        // 동호대교 로드뷰처럼 굵은 기둥 두 개가 짝지어 섬 (다리 폭의 양쪽 1/4 자리). 기둥 = 팔각 단면을 세운 것
        Vector3 across = new Vector3(-along.z, 0, along.x).normalized * (width / 4);
        for (int side = -1; side <= 1; side += 2)
        {
            Vector3 foot = new Vector3(top.x, 0, top.z) + across * side;
            props.AddPrism(Octagon(foot, PILLAR_RADIUS_M), bottom, bottom + height, PILLAR);
        }
        props.AddBeam(new Vector3(top.x, 0, top.z) - across * 1.4f + Vector3.up * (bottom + height - 0.8f), new Vector3(top.x, 0, top.z) + across * 1.4f + Vector3.up * (bottom + height - 0.8f), 2.2f, 1.6f, PILLAR); // 기둥 머리를 잇는 보
    }

    // 가운데 foot, 반지름 radius인 팔각형 (위에서 본 모양, x·z)
    private static Vector2[] Octagon(Vector3 foot, float radius)
    {
        Vector2[] points = new Vector2[8];
        for (int k = 0; k < 8; k++)
        {
            float angle = (22.5f + k * 45) * Mathf.Deg2Rad;
            points[k] = new Vector2(foot.x + Mathf.Cos(angle) * radius, foot.z + Mathf.Sin(angle) * radius);
        }
        return points;
    }
}
