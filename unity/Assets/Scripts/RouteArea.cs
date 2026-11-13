// 걸은 경로 둘레: 어떤 자리가 걸은 길(또는 다녀온 곳)에서 몇 m 안인지 알려 줌
// 왜: 건물 디테일·주차된 차·신호등은 걸은 길 가까운 곳에만 그림 (전부 그리면 무거워서)
// 경로가 아직 없는 여행(시연 준비 중)도 다녀온 곳(핀) 둘레는 자세하게 그리도록, 핀 자리도 같이 봄
using System.Collections.Generic;
using UnityEngine;

public class RouteArea
{
    private const float STOP_EXTRA_M = 100f; // 다녀온 곳은 걸은 길보다 이만큼 더 넓게 봄 (가게 앞 골목까지)

    private readonly List<Vector2> points = new List<Vector2>(); // 걸은 경로 점 (x, z)
    private readonly List<Vector2> stops = new List<Vector2>();  // 다녀온 곳 (x, z)

    public RouteArea(RouteData route, StopData[] stopList)
    {
        if (route != null && route.points != null)
        {
            for (int i = 0; i + 1 < route.points.Length; i += 2) points.Add(new Vector2(route.points[i], route.points[i + 1]));
        }
        if (stopList != null)
        {
            foreach (StopData stop in stopList) stops.Add(new Vector2(stop.x, stop.z));
        }
    }

    // (x, z)가 걸은 길에서 meters 안이거나, 다녀온 곳에서 meters + 100m 안인지
    public bool IsNear(float x, float z, float meters)
    {
        Vector2 point = new Vector2(x, z);
        for (int i = 0; i + 1 < points.Count; i++)
        {
            if (DistanceToSegment(point, points[i], points[i + 1]) <= meters) return true;
        }
        foreach (Vector2 stop in stops)
        {
            if (Vector2.Distance(point, stop) <= meters + STOP_EXTRA_M) return true;
        }
        return false;
    }

    // 점에서 선분 a-b까지 가장 가까운 거리: 선분 위에서 점과 가장 가까운 자리를 찾아 거리를 잼
    private static float DistanceToSegment(Vector2 point, Vector2 a, Vector2 b)
    {
        Vector2 ab = b - a;
        float lengthSquared = ab.sqrMagnitude;
        float t = lengthSquared == 0 ? 0 : Mathf.Clamp01(Vector2.Dot(point - a, ab) / lengthSquared); // 0 = a 쪽 끝, 1 = b 쪽 끝
        return Vector2.Distance(point, a + ab * t);
    }
}
