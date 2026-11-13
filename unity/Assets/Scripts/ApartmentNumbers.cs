// 아파트 동 번호: 아파트 옆벽(짧은 벽) 꼭대기 근처에 "101" 같은 큰 숫자를 붙임 (서울 아파트다운 모습)
//   숫자 그림 한 장(아틀라스)에 101 ~ 120을 칸칸이 그려 두고, 번호마다 그 칸을 잘라 붙임
//   숫자는 5 × 7 점으로 된 글꼴을 코드로 그림
using System.Collections.Generic;
using UnityEngine;

public class ApartmentNumbers
{
    private const int COUNT = 20;          // 번호 가짓수 (101 ~ 120)
    private const int COLUMNS = 4;         // 아틀라스 가로 칸 수
    private const int CELL_W = 256;        // 아틀라스 한 칸 크기(픽셀)
    private const int CELL_H = 96;
    private const float SIGN_W_M = 10f;    // 벽에 붙는 번호 크기(m)
    private const float SIGN_H_M = 3.75f;
    private const float FROM_TOP_M = 3.5f; // 지붕에서 이만큼 아래에
    private const float MIN_WALL_M = 8f;   // 이보다 짧은 벽에는 안 붙임

    // 숫자 0 ~ 9의 5 × 7 점 글꼴 (1 = 칠함, 위 줄부터)
    private static readonly string[] DIGITS =
    {
        "01110100011001110101110011000101110", "00100011000010000100001000010001110", "01110100010000100010001000100011111",
        "11110000010000101110000010000111110", "00010001100101010010111110001000010", "11111100001111000001000011000101110",
        "00110010001000011110100011000101110", "11111000010001000100010000100001000", "01110100011000101110100011000101110",
        "01110100011000101111000010001001100",
    };

    private readonly Ground ground;
    private readonly List<Vector3> vertices = new List<Vector3>();
    private readonly List<Vector2> uv = new List<Vector2>();
    private readonly List<int> triangles = new List<int>();
    private int count; // 지금까지 붙인 동 수 (번호를 차례로 매김)

    public ApartmentNumbers(Ground ground)
    {
        this.ground = ground;
    }

    // 아파트 한 동: 가장 긴 벽의 절반보다 짧은 벽(옆벽)마다 번호를 붙임
    public void Add(BuildingData building)
    {
        List<Vector2> ring = BuildingMaker.ToRing(building.points);
        if (ring.Count < 3) return;
        float high = float.MinValue;
        float longest = 0;
        for (int i = 0; i < ring.Count; i++)
        {
            high = Mathf.Max(high, ground.HeightAt(ring[i].x, ring[i].y));
            longest = Mathf.Max(longest, Vector2.Distance(ring[i], ring[(i + 1) % ring.Count]));
        }
        int number = count % COUNT;
        count++;
        float middle = high + building.height - FROM_TOP_M - SIGN_H_M / 2;
        for (int i = 0; i < ring.Count; i++)
        {
            Vector2 a = ring[i];
            Vector2 b = ring[(i + 1) % ring.Count];
            float length = Vector2.Distance(a, b);
            if (length < MIN_WALL_M || length > longest * 0.5f) continue;
            AddSign(a, b, middle, number);
        }
    }

    // 벽 a→b 가운데, middle 높이에 번호판 한 장 (벽 밖으로 5cm 띄움)
    private void AddSign(Vector2 a, Vector2 b, float middle, int number)
    {
        Vector2 along = (b - a).normalized;
        Vector2 outward = new Vector2(along.y, -along.x); // 윤곽이 시계 반대 방향이라 오른쪽이 바깥
        Vector2 center = (a + b) / 2 + outward * 0.05f;
        Vector3 half = new Vector3(along.x, 0, along.y) * SIGN_W_M / 2;
        Vector3 c = new Vector3(center.x, middle, center.y);
        Vector3 up = Vector3.up * SIGN_H_M / 2;
        int first = vertices.Count;
        vertices.Add(c - half - up); vertices.Add(c - half + up); vertices.Add(c + half + up); vertices.Add(c + half - up);
        float u0 = (number % COLUMNS) / (float)COLUMNS;
        float v0 = (number / COLUMNS) / (float)(COUNT / COLUMNS);
        float du = 1f / COLUMNS;
        float dv = 1f / (COUNT / COLUMNS);
        uv.Add(new Vector2(u0, v0)); uv.Add(new Vector2(u0, v0 + dv)); uv.Add(new Vector2(u0 + du, v0 + dv)); uv.Add(new Vector2(u0 + du, v0));
        triangles.Add(first); triangles.Add(first + 1); triangles.Add(first + 2);
        triangles.Add(first); triangles.Add(first + 2); triangles.Add(first + 3);
    }

    public Mesh BuildMesh()
    {
        Mesh mesh = new Mesh();
        mesh.indexFormat = UnityEngine.Rendering.IndexFormat.UInt32;
        mesh.SetVertices(vertices);
        mesh.SetUVs(0, uv);
        mesh.SetTriangles(triangles, 0);
        mesh.RecalculateNormals();
        return mesh;
    }

    // 번호판 재질: 숫자 칸만 보이고 나머지는 뚫린(투명) 그림, 빛을 받아 낮·밤 밝기가 바뀜
    public static Material MakeMaterial()
    {
        Material material = new Material(Shader.Find("Legacy Shaders/Transparent/Cutout/Diffuse")); // 투명한 곳은 아예 안 그리는 재질
        material.mainTexture = Atlas();
        material.color = new Color(0.30f, 0.30f, 0.33f); // 짙은 회색 페인트 글씨
        material.SetFloat("_Cutoff", 0.5f);
        return material;
    }

    // 101 ~ 120을 칸칸이 그린 그림
    private static Texture2D Atlas()
    {
        int rows = COUNT / COLUMNS;
        Color32[] pixels = new Color32[CELL_W * COLUMNS * CELL_H * rows]; // 처음엔 모두 투명
        for (int n = 0; n < COUNT; n++)
        {
            string text = (101 + n).ToString();
            int cellX = (n % COLUMNS) * CELL_W;
            int cellY = (n / COLUMNS) * CELL_H;
            const int dot = 12; // 글꼴 점 하나 = 12픽셀
            int startX = cellX + (CELL_W - text.Length * 6 * dot + dot) / 2;
            for (int k = 0; k < text.Length; k++) DrawDigit(pixels, CELL_W * COLUMNS, startX + k * 6 * dot, cellY + (CELL_H - 7 * dot) / 2, text[k] - '0', dot);
        }
        Texture2D texture = new Texture2D(CELL_W * COLUMNS, CELL_H * rows);
        texture.SetPixels32(pixels);
        texture.wrapMode = TextureWrapMode.Clamp;
        texture.Apply();
        return texture;
    }

    // 숫자 하나를 dot 크기 점으로 그림 (글꼴은 위 줄부터라 아래에서 위로 뒤집어 그림)
    private static void DrawDigit(Color32[] pixels, int width, int left, int bottom, int digit, int dot)
    {
        string pattern = DIGITS[digit];
        for (int row = 0; row < 7; row++)
        {
            for (int column = 0; column < 5; column++)
            {
                if (pattern[row * 5 + column] != '1') continue;
                for (int y = 0; y < dot; y++)
                {
                    for (int x = 0; x < dot; x++) pixels[(bottom + (6 - row) * dot + y) * width + left + column * dot + x] = new Color32(255, 255, 255, 255);
                }
            }
        }
    }
}
