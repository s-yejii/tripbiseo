// 걸은 길 가까운 건물에 입체 디테일 붙이기 (LOD3 흉내)
//   층마다 튀어나온 띠 · 빌라·아파트 발코니(난간) · 상가 간판판과 돌출 간판
//   (실외기·옥상 물탱크는 11/13에 뺌: 작은 흰·파란 상자가 화면에 점처럼 어지럽게 보여서)
// 건물 본체(벽·지붕)는 BuildingMaker가 만들고, 여기서는 그 바깥에 붙는 것만 상자(PropMaker)로 더함
using System.Collections.Generic;
using UnityEngine;

public class BuildingDetails
{
    private const float MIN_WALL_M = 2.5f;      // 이보다 짧은 벽에는 아무것도 안 붙임
    private const float LEDGE_OUT_M = 0.14f;    // 층 띠가 벽에서 튀어나온 정도
    private const float LEDGE_HEIGHT_M = 0.16f; // 층 띠 두께
    private const float BALCONY_DEPTH_M = 0.9f; // 발코니 깊이
    private const float RAIL_HEIGHT_M = 1.0f;   // 발코니 난간 높이
    private const float SIGN_HEIGHT_M = 0.8f;   // 1층 간판판 높이

    private static readonly Color LEDGE = new Color(0.84f, 0.83f, 0.80f);
    private static readonly Color SLAB = new Color(0.76f, 0.75f, 0.72f);
    private static readonly Color METAL_RAIL = new Color(0.22f, 0.24f, 0.27f);
    private static readonly Color GLASS_RAIL = new Color(0.58f, 0.68f, 0.74f);
    private static readonly Color LETTERS = new Color(0.97f, 0.96f, 0.92f); // 간판 글자 자리 (밝은 띠)
    private static readonly Color[] SIGNS =
    {
        new Color(0.78f, 0.20f, 0.17f), new Color(0.16f, 0.36f, 0.68f), new Color(0.95f, 0.72f, 0.16f),
        new Color(0.12f, 0.50f, 0.36f), new Color(0.30f, 0.20f, 0.15f), new Color(0.93f, 0.93f, 0.90f),
    };

    private readonly Ground ground;
    private readonly PropMaker props;

    public BuildingDetails(Ground ground, PropMaker props)
    {
        this.ground = ground;
        this.props = props;
    }

    // 건물 하나에 디테일을 붙임. kind: 그릴 때 쓴 종류 (0 주택·빌라 · 1 아파트 · 2 상가 · 3 업무 · 4 그 밖)
    public void Add(BuildingData building, int kind)
    {
        List<Vector2> ring = BuildingMaker.ToRing(building.points);
        if (ring.Count < 3) return;
        bool isHouse = kind == 0 && building.height <= BuildingMaker.HOUSE_MAX_M && ring.Count <= BuildingMaker.HOUSE_MAX_CORNERS;
        if (isHouse) return; // 기와지붕 단독주택은 그대로

        float high = float.MinValue; // 건물 자리에서 가장 높은 땅 = 1층 바닥 (BuildingMaker와 같은 규칙)
        foreach (Vector2 point in ring) high = Mathf.Max(high, ground.HeightAt(point.x, point.y));
        float roof = high + building.height;
        float upperStart = high + BuildingMaker.GROUND_FLOOR_M; // 2층 바닥
        int seed = (int)Mathf.Abs(building.points[0] * 13 + building.points[1] * 7);
        int longest = LongestWall(ring);

        for (int i = 0; i < ring.Count; i++)
        {
            Vector2 a = ring[i];
            Vector2 b = ring[(i + 1) % ring.Count];
            if (Vector2.Distance(a, b) < MIN_WALL_M) continue;
            if (kind != 3) AddLedges(a, b, upperStart, roof);                    // 유리 빌딩은 띠 없음
            if (kind == 2 && roof - high > BuildingMaker.GROUND_FLOOR_M) AddShopSign(a, b, upperStart, seed + i);
            if ((kind == 0 || kind == 1) && i == longest) AddBalconies(a, b, upperStart, roof, kind == 1);
        }
    }

    // ---------- 벽 하나를 다루는 도구 (ring은 시계 반대 방향이라, 벽 a→b의 오른쪽이 건물 바깥)

    private static Vector2 Outward(Vector2 a, Vector2 b)
    {
        Vector2 direction = (b - a).normalized;
        return new Vector2(direction.y, -direction.x);
    }

    private static Vector3 At(Vector2 point, float height)
    {
        return new Vector3(point.x, height, point.y);
    }

    // 0~1 "정해진 난수": 같은 (seed, salt)면 늘 같은 값 (다시 그려도 같은 자리에 같은 소품)
    private static float Random01(int seed, int salt)
    {
        float value = Mathf.Sin(seed * 12.9898f + salt * 78.233f) * 43758.5453f;
        return value - Mathf.Floor(value);
    }

    private static int LongestWall(List<Vector2> ring)
    {
        int longest = 0;
        for (int i = 1; i < ring.Count; i++)
        {
            if (Vector2.Distance(ring[i], ring[(i + 1) % ring.Count]) > Vector2.Distance(ring[longest], ring[(longest + 1) % ring.Count])) longest = i;
        }
        return longest;
    }

    // ---------- 붙이는 것들

    // 층마다 벽을 따라 튀어나온 띠 (층이 나뉘어 보이게)
    private void AddLedges(Vector2 a, Vector2 b, float upperStart, float roof)
    {
        Vector2 middle = (a + b) / 2 + Outward(a, b) * (LEDGE_OUT_M / 2);
        float yaw = StreetProps.YawOf(b - a);
        float length = Vector2.Distance(a, b);
        for (float y = upperStart; y < roof - 0.5f; y += BuildingMaker.FLOOR_M)
        {
            props.AddBox(At(middle, y), new Vector3(length, LEDGE_HEIGHT_M, LEDGE_OUT_M), yaw, LEDGE);
        }
    }

    // 상가 1층 위 간판판 (벽 길이만큼 가로로) + 긴 벽이면 한쪽 끝에 돌출 간판
    private void AddShopSign(Vector2 a, Vector2 b, float upperStart, int seed)
    {
        Vector2 outward = Outward(a, b);
        Vector2 direction = (b - a).normalized;
        float yaw = StreetProps.YawOf(b - a);
        float length = Vector2.Distance(a, b);
        Color color = SIGNS[seed % SIGNS.Length];

        float boardY = upperStart - SIGN_HEIGHT_M / 2 - 0.1f;
        Vector2 boardAt = (a + b) / 2 + outward * 0.14f;
        props.AddBox(At(boardAt, boardY), new Vector3(length * 0.92f, SIGN_HEIGHT_M, 0.22f), yaw, color);
        Color letters = color.grayscale > 0.6f ? SIGNS[4] : LETTERS; // 밝은 간판엔 짙은 글자, 어두운 간판엔 밝은 글자
        props.AddBox(At(boardAt + outward * 0.12f, boardY), new Vector3(length * 0.45f, 0.26f, 0.02f), yaw, letters); // 글자 자리 띠
        if (length > 6)
        {
            Vector2 bladeAt = a + direction * 0.8f + outward * 0.55f; // 벽에서 직각으로 튀어나온 세로 간판
            Color blade = SIGNS[(seed + 2) % SIGNS.Length];
            props.AddBox(At(bladeAt, upperStart + 1.6f), new Vector3(0.1f, 2.6f, 0.8f), yaw, blade);
            // 양면 글자 자리: 간판보다 조금 두꺼운 세로 띠라서 앞뒤 면에 밝게 드러남
            props.AddBox(At(bladeAt, upperStart + 1.6f), new Vector3(0.12f, 1.9f, 0.3f), yaw, blade.grayscale > 0.6f ? SIGNS[4] : LETTERS);
        }
    }

    // 가장 긴 벽(앞면)에 층마다 발코니: 바닥판 + 앞 난간 + 양옆 난간. 아파트는 유리 난간, 빌라는 쇠 난간
    private void AddBalconies(Vector2 a, Vector2 b, float upperStart, float roof, bool isApartment)
    {
        float length = Vector2.Distance(a, b);
        float width = isApartment ? length * 0.8f : Mathf.Min(length - 1, 3.4f);
        if (width < 1.5f) return;
        Vector2 outward = Outward(a, b);
        Vector2 direction = (b - a).normalized;
        Vector2 middle = (a + b) / 2;
        float yaw = StreetProps.YawOf(b - a);
        Color rail = isApartment ? GLASS_RAIL : METAL_RAIL;

        for (float y = upperStart; y < roof - 2; y += BuildingMaker.FLOOR_M)
        {
            props.AddBox(At(middle + outward * (BALCONY_DEPTH_M / 2), y), new Vector3(width, 0.15f, BALCONY_DEPTH_M), yaw, SLAB);
            props.AddBox(At(middle + outward * BALCONY_DEPTH_M, y + RAIL_HEIGHT_M / 2), new Vector3(width, RAIL_HEIGHT_M, 0.05f), yaw, rail);
            props.AddBox(At(middle + direction * (width / 2) + outward * (BALCONY_DEPTH_M / 2), y + RAIL_HEIGHT_M / 2), new Vector3(0.05f, RAIL_HEIGHT_M, BALCONY_DEPTH_M), yaw, rail);
            props.AddBox(At(middle - direction * (width / 2) + outward * (BALCONY_DEPTH_M / 2), y + RAIL_HEIGHT_M / 2), new Vector3(0.05f, RAIL_HEIGHT_M, BALCONY_DEPTH_M), yaw, rail);
        }
    }
}
