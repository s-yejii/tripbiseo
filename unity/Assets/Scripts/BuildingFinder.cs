// "이 자리가 건물 안이거나 건물 벽에 너무 가까운가"를 빨리 알려 줌 (나무가 건물을 뚫지 않게 쓰는 곳)
// "이 둘레에서 가장 높은 건물은 몇 m인가"도 알려 줌 (위에서 보기 카메라가 건물 속으로 들어가지 않게)
// 건물이 1만 채가 넘어서 나무마다 전부 보면 느림 → 땅을 30m 칸으로 나눠 두고, 나무가 선 칸의 건물만 봄
using System.Collections.Generic;
using UnityEngine;

public class BuildingFinder
{
    private const float CELL_M = 30f;      // 칸 한 변 길이
    public const float MAX_CLEAR_M = 8f;   // 물어볼 수 있는 가장 큰 거리 (건물을 칸에 넣을 때 이만큼 넓혀서 넣음)

    // 칸 번호 → 그 칸에 걸친 건물 윤곽들
    private readonly Dictionary<long, List<List<Vector2>>> cells = new Dictionary<long, List<List<Vector2>>>();
    // 칸 번호 → 그 칸에 걸친 건물 중 가장 높은 높이(m)
    private readonly Dictionary<long, float> tallest = new Dictionary<long, float>();

    public BuildingFinder(BuildingData[] buildings)
    {
        foreach (BuildingData building in buildings) AddBuilding(BuildingMaker.ToRing(building.points), building.height);
    }

    // 이 자리(x, z)가 건물 안이거나, 건물 벽에서 clearM 미터 안인지
    public bool IsNearBuilding(float x, float z, float clearM)
    {
        clearM = Mathf.Min(clearM, MAX_CLEAR_M);
        Vector2 point = new Vector2(x, z);
        if (!cells.TryGetValue(KeyOf(CellOf(x), CellOf(z)), out List<List<Vector2>> rings)) return false; // 이 칸엔 건물이 없음
        foreach (List<Vector2> ring in rings)
        {
            if (BuildingMaker.IsInside(ring, point)) return true;
            if (DistanceToWall(ring, point) < clearM) return true;
        }
        return false;
    }

    // 이 자리 칸과 바로 옆 칸들(가로세로 3칸, 약 90m 네모)에서 가장 높은 건물 높이(m). 건물이 없으면 0
    public float TallestNear(float x, float z)
    {
        float highest = 0;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                if (tallest.TryGetValue(KeyOf(CellOf(x) + dx, CellOf(z) + dz), out float height)) highest = Mathf.Max(highest, height);
            }
        }
        return highest;
    }

    // 건물 하나를, 윤곽을 감싸는 네모(+MAX_CLEAR_M)가 걸치는 칸마다 넣음
    private void AddBuilding(List<Vector2> ring, float height)
    {
        if (ring.Count < 3) return;
        Vector2 low = ring[0];
        Vector2 high = ring[0];
        foreach (Vector2 point in ring)
        {
            low = Vector2.Min(low, point);
            high = Vector2.Max(high, point);
        }
        for (int cellX = CellOf(low.x - MAX_CLEAR_M); cellX <= CellOf(high.x + MAX_CLEAR_M); cellX++)
        {
            for (int cellZ = CellOf(low.y - MAX_CLEAR_M); cellZ <= CellOf(high.y + MAX_CLEAR_M); cellZ++)
            {
                long key = KeyOf(cellX, cellZ);
                if (!cells.ContainsKey(key)) cells[key] = new List<List<Vector2>>();
                cells[key].Add(ring);
                if (!tallest.ContainsKey(key) || tallest[key] < height) tallest[key] = height;
            }
        }
    }

    // 점에서 가장 가까운 벽까지의 거리
    private static float DistanceToWall(List<Vector2> ring, Vector2 point)
    {
        float nearest = float.MaxValue;
        for (int i = 0; i < ring.Count; i++)
        {
            Vector2 a = ring[i];
            Vector2 b = ring[(i + 1) % ring.Count];
            Vector2 wall = b - a;
            float along = Mathf.Clamp01(Vector2.Dot(point - a, wall) / Mathf.Max(wall.sqrMagnitude, 0.0001f)); // 벽 위에서 점과 가장 가까운 곳 (0 = a, 1 = b)
            nearest = Mathf.Min(nearest, Vector2.Distance(point, a + wall * along));
        }
        return nearest;
    }

    private static int CellOf(float meters)
    {
        return Mathf.FloorToInt(meters / CELL_M);
    }

    // 칸 두 번호(x, z)를 숫자 하나로 합침 (Dictionary 열쇠로 쓰려고)
    private static long KeyOf(int cellX, int cellZ)
    {
        return (long)cellX * 100000 + cellZ;
    }
}
