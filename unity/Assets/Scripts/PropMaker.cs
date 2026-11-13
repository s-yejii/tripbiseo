// 소품 만들기: 색칠한 상자를 모아 한 덩어리로 만듦 (간판·발코니·실외기·물탱크·신호등·주차된 차 …)
// 소품마다 색이 달라서 재질을 나누지 않고, 꼭짓점마다 색을 적어 둠 (나무와 같은 VertexColor 셰이더로 칠함)
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;

public class PropMaker
{
    private readonly List<Vector3> vertices = new List<Vector3>();
    private readonly List<Color32> colors = new List<Color32>(); // Color32 = 색 하나를 4바이트로 (메모리 아끼려고)
    private readonly List<int> triangles = new List<int>();

    // 상자 하나. center: 가운데, size: (가로 x, 높이 y, 깊이 z), yaw: 위에서 봤을 때 돌린 각도(도)
    // 돌리면 상자의 가로(x)가 그 방향을 향함 (방향 → 각도는 StreetProps.YawOf)
    public void AddBox(Vector3 center, Vector3 size, float yaw, Color color)
    {
        AddBox(center, size, Quaternion.Euler(0, yaw, 0), color); // Quaternion = 회전 하나를 담는 값. Euler(0, 각도, 0) = 위아래 축으로 돌리기
    }

    // 막대(보) 하나: from에서 to까지 이어진 상자. 비스듬해도 됨 (다리 상판·경사로)
    public void AddBeam(Vector3 from, Vector3 to, float width, float thickness, Color color)
    {
        if (Vector3.Distance(from, to) < 0.01f) return;
        Quaternion turn = Quaternion.LookRotation(to - from); // 상자의 깊이(z) 방향이 from → to를 향하게
        AddBox((from + to) / 2, new Vector3(width, thickness, Vector3.Distance(from, to)), turn, color);
    }

    // 상자 하나 (돌리는 값을 직접 줌)
    public void AddBox(Vector3 center, Vector3 size, Quaternion turn, Color color)
    {
        Vector3 x = new Vector3(size.x / 2, 0, 0);
        Vector3 y = new Vector3(0, size.y / 2, 0);
        Vector3 z = new Vector3(0, 0, size.z / 2);
        Color32 tint = color.linear; // .linear: 유니티 빛 계산 방식에 맞춘 색 (안 하면 허옇게 뜸)

        // 면 6개: 면의 가운데(가운데에서 얼마나 나왔나)와, 그 면 위의 두 방향
        AddFace(center, turn, y, x, z, tint);  // 위
        AddFace(center, turn, -y, x, z, tint); // 아래
        AddFace(center, turn, x, y, z, tint);  // 오른쪽
        AddFace(center, turn, -x, y, z, tint); // 왼쪽
        AddFace(center, turn, z, x, y, tint);  // 앞
        AddFace(center, turn, -z, x, y, tint); // 뒤
    }

    // 바닥 모양을 위로 세운 기둥: outline = 위에서 본 볼록한 모양 (x, z). bottom ~ top 높이
    //   상자로는 안 되는 모양(뾰족한 뱃머리 등)에 씀. 예) 점 5개짜리 오각형 → 윗면·아랫면 + 옆면 5개
    public void AddPrism(Vector2[] outline, float bottom, float top, Color color)
    {
        AddPrism(outline, bottom, top, color, color);
    }

    // 같은 기둥인데 색이 뒤(x가 작은 쪽) back → 앞(x가 큰 쪽) front로 점점 바뀜 (배 옆면의 파랑 → 흰색 같은 것)
    public void AddPrism(Vector2[] outline, float bottom, float top, Color back, Color front)
    {
        Vector3[] lower = new Vector3[outline.Length];
        Vector3[] upper = new Vector3[outline.Length];
        for (int i = 0; i < outline.Length; i++)
        {
            lower[i] = new Vector3(outline[i].x, bottom, outline[i].y);
            upper[i] = new Vector3(outline[i].x, top, outline[i].y);
        }
        AddExtrusion(lower, upper, back, front);
    }

    // 옆에서 본 모양을 옆으로 민 덩어리: profile = 옆에서 본 볼록한 모양 (x 앞뒤, y 높이), zFrom ~ zTo 폭
    //   비스듬한 뱃머리·기운 앞 유리처럼 위아래로 기운 모양에 씀
    public void AddSideProfile(Vector2[] profile, float zFrom, float zTo, Color back, Color front)
    {
        Vector3[] near = new Vector3[profile.Length];
        Vector3[] far = new Vector3[profile.Length];
        for (int i = 0; i < profile.Length; i++)
        {
            near[i] = new Vector3(profile[i].x, profile[i].y, zFrom);
            far[i] = new Vector3(profile[i].x, profile[i].y, zTo);
        }
        AddExtrusion(near, far, back, front);
    }

    // 옆모습을 옆으로 민 덩어리를 원하는 자리(origin)에 원하는 방향(yaw, 도)으로 놓음 (차처럼 여기저기 놓는 소품용)
    public void AddSideProfile(Vector2[] profile, float zFrom, float zTo, Color color, Vector3 origin, float yaw)
    {
        Quaternion turn = Quaternion.Euler(0, yaw, 0);
        Vector3[] near = new Vector3[profile.Length];
        Vector3[] far = new Vector3[profile.Length];
        for (int i = 0; i < profile.Length; i++)
        {
            near[i] = origin + turn * new Vector3(profile[i].x, profile[i].y, zFrom);
            far[i] = origin + turn * new Vector3(profile[i].x, profile[i].y, zTo);
        }
        AddExtrusion(near, far, color, color);
    }

    // 같은 모양 두 장(first, second: 나란히 옮긴 것)과 그 사이 옆면들. 색은 x 위치에 따라 back → front
    private void AddExtrusion(Vector3[] first, Vector3[] second, Color back, Color front)
    {
        float minX = float.MaxValue;
        float maxX = float.MinValue;
        foreach (Vector3 point in first)
        {
            minX = Mathf.Min(minX, point.x);
            maxX = Mathf.Max(maxX, point.x);
        }
        ColorRamp paint = new ColorRamp(back, front, minX, maxX);
        Vector3 center = (Average(first) + Average(second)) / 2;
        AddFlatPolygon(first, Average(first) - center, paint);
        AddFlatPolygon(second, Average(second) - center, paint);
        for (int i = 0; i < first.Length; i++) // 옆면: 변마다 네모
        {
            int next = (i + 1) % first.Length;
            Vector3[] side = { first[i], first[next], second[next], second[i] };
            AddFlatPolygon(side, Average(side) - center, paint); // 가운데에서 이 면 쪽 = 바깥
        }
    }

    // x 위치에 따라 두 색을 섞는 규칙 (minX에서 back, maxX에서 front)
    private struct ColorRamp // struct = 값 몇 개를 묶어 다니는 작은 상자 (class보다 가벼움)
    {
        private readonly Color back, front;
        private readonly float minX, maxX;

        public ColorRamp(Color back, Color front, float minX, float maxX)
        {
            this.back = back;
            this.front = front;
            this.minX = minX;
            this.maxX = maxX;
        }

        public Color32 At(float x)
        {
            float rate = maxX > minX ? (x - minX) / (maxX - minX) : 0;
            return Color.Lerp(back, front, rate).linear; // .linear: 유니티 빛 계산 방식에 맞춘 색
        }
    }

    // 평평한 볼록 다각형 하나: 첫 점에서 부채꼴로 삼각형을 나눔. outward 쪽에서 보이게 순서를 맞춤
    private void AddFlatPolygon(Vector3[] corners, Vector3 outward, ColorRamp paint)
    {
        int first = vertices.Count;
        foreach (Vector3 corner in corners)
        {
            vertices.Add(corner);
            colors.Add(paint.At(corner.x));
        }
        bool isFlipped = Vector3.Dot(Vector3.Cross(corners[1] - corners[0], corners[2] - corners[0]), outward) < 0;
        for (int i = 1; i + 1 < corners.Length; i++)
        {
            triangles.Add(first);
            triangles.Add(first + (isFlipped ? i + 1 : i));
            triangles.Add(first + (isFlipped ? i : i + 1));
        }
    }

    private static Vector3 Average(Vector3[] points)
    {
        Vector3 sum = Vector3.zero;
        foreach (Vector3 point in points) sum += point;
        return sum / points.Length;
    }

    // 네모 면 하나 (삼각형 2개). 바깥을 향하도록 꼭짓점 순서를 맞춤
    private void AddFace(Vector3 center, Quaternion turn, Vector3 middle, Vector3 a, Vector3 b, Color32 tint)
    {
        Vector3 p0 = center + turn * (middle - a - b);
        Vector3 p1 = center + turn * (middle - a + b);
        Vector3 p2 = center + turn * (middle + a + b);
        Vector3 p3 = center + turn * (middle + a - b);

        // 유니티는 꼭짓점 순서로 앞면을 정함: Cross(p1-p0, p2-p0)가 바깥(middle 쪽)을 향해야 보임. 반대면 순서를 뒤집음
        bool isFlipped = Vector3.Dot(Vector3.Cross(p1 - p0, p2 - p0), turn * middle) < 0;
        int first = vertices.Count;
        vertices.Add(p0); vertices.Add(p1); vertices.Add(p2); vertices.Add(p3);
        for (int i = 0; i < 4; i++) colors.Add(tint);
        if (isFlipped)
        {
            triangles.Add(first); triangles.Add(first + 2); triangles.Add(first + 1);
            triangles.Add(first); triangles.Add(first + 3); triangles.Add(first + 2);
        }
        else
        {
            triangles.Add(first); triangles.Add(first + 1); triangles.Add(first + 2);
            triangles.Add(first); triangles.Add(first + 2); triangles.Add(first + 3);
        }
    }

    // 지금까지 더한 소품 전부를 Mesh 하나로
    public Mesh BuildMesh()
    {
        Mesh mesh = new Mesh();
        mesh.indexFormat = IndexFormat.UInt32;
        mesh.SetVertices(vertices);
        mesh.SetColors(colors);
        mesh.SetTriangles(triangles, 0);
        mesh.RecalculateNormals(); // 면마다 꼭짓점을 따로 둬서, 모서리가 각지게 빛을 받음
        return mesh;
    }
}
