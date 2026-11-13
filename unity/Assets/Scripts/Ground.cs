// 땅(지형): 높이 격자로 땅 모양을 만들고, 어느 자리든 땅 높이를 알려 줌
// 건물·나무·길이 전부 HeightAt으로 땅 높이를 물어보고 그 위에 올라감 → 땅에 묻히지 않음
using UnityEngine;
using UnityEngine.Rendering;

public class Ground
{
    private readonly MapData data;
    private readonly float step; // 격자 한 칸 길이(m)
    private readonly float half; // 가운데에서 가장자리까지(m)

    public Ground(MapData data)
    {
        this.data = data;
        step = data.sizeM / (data.gridCount - 1);
        half = data.sizeM / 2;
    }

    // 격자 점 하나의 높이 (가장자리를 넘으면 가장자리 값)
    private float GridHeight(int column, int row)
    {
        column = Mathf.Clamp(column, 0, data.gridCount - 1);
        row = Mathf.Clamp(row, 0, data.gridCount - 1);
        return data.heights[row * data.gridCount + column];
    }

    // (x, z) 자리의 땅 높이. 둘러싼 격자 점 네 개의 높이를 거리만큼 섞음
    // 예: 높이 10m 점과 20m 점의 딱 가운데면 15m
    public float HeightAt(float x, float z)
    {
        float gridX = (x + half) / step;
        float gridZ = (z + half) / step;
        int column = Mathf.FloorToInt(gridX);
        int row = Mathf.FloorToInt(gridZ);
        float south = Mathf.Lerp(GridHeight(column, row), GridHeight(column + 1, row), gridX - column);
        float north = Mathf.Lerp(GridHeight(column, row + 1), GridHeight(column + 1, row + 1), gridX - column);
        return Mathf.Lerp(south, north, gridZ - row);
    }

    // 지도에서 가장 낮은 땅 높이
    public float LowestHeight()
    {
        float lowest = float.MaxValue;
        foreach (float height in data.heights) lowest = Mathf.Min(lowest, height);
        return lowest;
    }

    // 땅 모양(Mesh) 만들기: 격자 점을 꼭짓점으로 놓고, 네모 칸마다 삼각형 두 개로 이음
    public Mesh BuildMesh()
    {
        int count = data.gridCount;
        Vector3[] vertices = new Vector3[count * count];
        Vector2[] uv = new Vector2[count * count]; // 나중에 위성 사진을 입힐 때 쓰는 그림 좌표 (0~1)
        for (int row = 0; row < count; row++)
        {
            for (int column = 0; column < count; column++)
            {
                int i = row * count + column;
                vertices[i] = new Vector3(column * step - half, GridHeight(column, row), row * step - half);
                uv[i] = new Vector2(column / (count - 1f), row / (count - 1f));
            }
        }

        int[] triangles = new int[(count - 1) * (count - 1) * 6];
        int t = 0;
        for (int row = 0; row < count - 1; row++)
        {
            for (int column = 0; column < count - 1; column++)
            {
                int i = row * count + column; // 칸의 남서쪽 점
                triangles[t++] = i;
                triangles[t++] = i + count;     // 북서
                triangles[t++] = i + count + 1; // 북동
                triangles[t++] = i;
                triangles[t++] = i + count + 1;
                triangles[t++] = i + 1;         // 남동
            }
        }

        Mesh mesh = new Mesh();
        mesh.indexFormat = IndexFormat.UInt32; // 꼭짓점이 65,535개를 넘어도 되게
        mesh.vertices = vertices;
        mesh.uv = uv;
        mesh.triangles = triangles;
        mesh.RecalculateNormals(); // 빛 받는 방향 계산 (언덕에 그늘이 지게)
        return mesh;
    }
}
