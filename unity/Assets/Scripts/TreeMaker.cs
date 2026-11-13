// 나무 만들기: 나무마다 줄기 + 잎 덩어리를 세움
// 잎 덩어리는 "물레로 빚듯이" 만듦: 높이마다 굵기(반지름)를 정해 두고, 그 굵기의 육각형 고리를 층층이 쌓아 이음
// 막대사탕처럼 보이지 않게: 고리의 꼭짓점마다 굵기를 조금씩 다르게(울퉁불퉁) 하고, 활엽수·소나무는 작은 덩어리를 옆에 하나 더 붙임
// 나무마다 색이 달라서(단풍) 재질을 나누지 않고, 꼭짓점마다 색을 적어 둠 (Shaders/VertexColor.shader가 그 색으로 칠함)
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;

public class TreeMaker
{
    private const int SIDES = 6;            // 고리 하나의 꼭짓점 수 (육각형)
    private const float BUMPY = 0.35f;      // 울퉁불퉁한 정도 (0.35 = 굵기가 ±17%쯤 달라짐)
    private const float SIDE_BLOB = 0.65f;  // 옆에 붙이는 작은 덩어리 크기 (큰 덩어리의 65%)
    private const int CLUSTER_COUNT = 4;    // 가까운 나무의 둘레 작은 덩어리 수
    private static readonly Color TRUNK_COLOR = new Color(0.36f, 0.27f, 0.20f);
    private const float LEAF_BRIGHT = 1.1f;  // 잎 색을 이만큼 밝게 (웹에서 정한 색이 유니티 햇빛 아래에서는 어둡게 보여서)
    private const float LEAF_MIN_GRAY = 0.38f; // 잎 밝기(회색으로 치면)가 이보다 어두우면 이만큼까지 밝힘 (짙은 소나무색이 그늘에서 새까맣게 보여서)
    private const float LEAF_MUTE = 0.22f;   // 잎 색을 회색 쪽으로 이만큼 섞음 (형광처럼 쨍하지 않게, 실제 숲은 색이 차분함)

    // 모양마다 잎 덩어리의 옆모습: { 높이 비율, 굵기 비율 } 을 아래에서 위로. 둘 다 나무 키에 곱함
    // 예) { 0.68f, 0.30f } = 키의 68% 높이에서 반지름이 키의 30%
    private static readonly float[][,] CROWNS =
    {
        new float[,] { { 0.28f, 0f }, { 0.40f, 0.24f }, { 0.66f, 0.30f }, { 0.88f, 0.19f }, { 1f, 0f } },                                  // 0 둥근 활엽수
        new float[,] { { 0.16f, 0f }, { 0.20f, 0.20f }, { 0.44f, 0.09f }, { 0.46f, 0.15f }, { 0.70f, 0.05f }, { 0.72f, 0.10f }, { 1f, 0f } }, // 1 침엽수 (세 층으로 층진 모양)
        new float[,] { { 0.56f, 0f }, { 0.66f, 0.26f }, { 0.84f, 0.22f }, { 1f, 0f } },                                                    // 2 소나무 (줄기가 길고 위가 넓적)
        new float[,] { { 0.20f, 0f }, { 0.32f, 0.13f }, { 0.72f, 0.15f }, { 1f, 0f } },                                                    // 3 길쭉한 나무 (은행나무 등)
    };

    private readonly Ground ground;
    private readonly List<Vector3> vertices = new List<Vector3>();
    private readonly List<Color32> colors = new List<Color32>(); // Color32 = 색 하나를 4바이트로 (Color는 16바이트라 나무 3만 그루면 메모리를 많이 씀)
    private readonly List<int> triangles = new List<int>();

    public TreeMaker(Ground ground)
    {
        this.ground = ground;
    }

    // 나무 한 그루를 더함. isNear: 걸은 길 가까운 나무면 잎 덩어리를 여러 개 뭉쳐 더 풍성하게 (먼 나무는 가볍게)
    public void Add(TreeData tree, bool isNear)
    {
        Vector3 foot = new Vector3(tree.x, ground.HeightAt(tree.x, tree.z), tree.z); // 땅 높이에 세움
        float turn = tree.x * 0.7f + tree.z * 1.3f; // 나무마다 조금씩 돌려 세움 (다 같은 방향이면 어색해서)
        float[,] crown = CROWNS[Mathf.Clamp(tree.shape, 0, CROWNS.Length - 1)];

        // 줄기: 땅에서 잎 덩어리가 시작되는 높이보다 조금 위까지
        float trunkTop = tree.height * (crown[0, 0] + 0.12f);
        float trunkRadius = tree.height * 0.022f;
        Connect(AddRing(foot, 0, trunkRadius * 1.5f, turn, TRUNK_COLOR, 0), AddRing(foot, trunkTop, trunkRadius, turn, TRUNK_COLOR, 0));

        // 잎 덩어리. 색 글자("#C0392B")를 유니티 색으로 바꿔서 칠함
        ColorUtility.TryParseHtmlString(tree.color, out Color leaf); // out = 결과를 leaf에 담아 달라는 뜻
        float gray = leaf.grayscale; // 같은 밝기의 회색
        leaf = Color.Lerp(leaf, new Color(gray, gray, gray), LEAF_MUTE) * LEAF_BRIGHT;
        if (gray > 0 && gray < LEAF_MIN_GRAY) leaf *= LEAF_MIN_GRAY / gray; // 색깔(초록)은 그대로, 밝기만 올림
        AddBlob(foot, tree.height, crown, turn, leaf);

        bool isBroadleaf = tree.shape == 0 || tree.shape == 3;
        if (isNear && isBroadleaf)
        {
            AddCluster(foot, tree.height, crown, turn, leaf);
            return;
        }
        bool hasSideBlob = tree.shape == 0 || tree.shape == 2; // 활엽수·소나무만 옆 덩어리
        if (hasSideBlob)
        {
            // 옆으로 키의 14%만큼, 위로는 꼭대기가 큰 덩어리보다 조금 낮게
            Vector3 aside = new Vector3(Mathf.Cos(turn), 0, Mathf.Sin(turn)) * tree.height * 0.14f;
            Vector3 raise = Vector3.up * tree.height * (1 - SIDE_BLOB) * 0.8f;
            AddBlob(foot + aside + raise, tree.height * SIDE_BLOB, crown, turn + 1, leaf * 0.9f);
        }
    }

    // 가까운 활엽수: 큰 덩어리 둘레에 작은 덩어리 CLUSTER_COUNT개를 높이·크기를 조금씩 다르게 붙임 (뭉게뭉게한 나무)
    //   덩어리마다 밝기를 조금씩 달리해서, 햇빛 받는 쪽·그늘진 쪽이 섞여 보이게
    private void AddCluster(Vector3 foot, float height, float[,] crown, float turn, Color leaf)
    {
        AddBlob(foot + Vector3.up * height * 0.12f, height * 0.88f, crown, turn, leaf); // 가운데 큰 덩어리
        for (int k = 0; k < CLUSTER_COUNT; k++)
        {
            float angle = turn + k * Mathf.PI * 2 / CLUSTER_COUNT;
            float wobble = Mathf.PerlinNoise(foot.x * 0.13f + k, foot.z * 0.13f); // 0~1, 덩어리마다 다른 값
            Vector3 aside = new Vector3(Mathf.Cos(angle), 0, Mathf.Sin(angle)) * height * (0.13f + 0.06f * wobble);
            Vector3 raise = Vector3.up * height * (0.18f + 0.16f * wobble);
            float size = height * (0.55f + 0.12f * wobble);
            AddBlob(foot + aside + raise, size, crown, turn + k, leaf * (0.82f + 0.25f * wobble));
        }
    }

    // 잎 덩어리 하나: 고리를 아래에서 위로 쌓으며 이음
    private void AddBlob(Vector3 foot, float height, float[,] crown, float turn, Color leaf)
    {
        int count = crown.GetLength(0); // 옆모습의 점 수
        int before = -1;
        for (int i = 0; i < count; i++)
        {
            float shade = 0.72f + 0.28f * i / (count - 1); // 아래쪽 잎은 조금 어둡게 (그늘진 느낌)
            int ring = AddRing(foot, height * crown[i, 0], height * crown[i, 1], turn, leaf * shade, BUMPY);
            if (before >= 0) Connect(before, ring);
            before = ring;
        }
    }

    // 고리 하나(육각형)의 꼭짓점을 더하고, 첫 꼭짓점 번호를 돌려줌. bumpy: 꼭짓점마다 굵기를 다르게 하는 정도
    private int AddRing(Vector3 foot, float height, float radius, float turn, Color color, float bumpy)
    {
        int first = vertices.Count;
        for (int side = 0; side < SIDES; side++)
        {
            float angle = turn + side * Mathf.PI * 2 / SIDES;
            // PerlinNoise: 자리에 따라 부드럽게 변하는 0~1 값. 같은 자리면 늘 같은 값이라 다시 그려도 모양이 같음
            float bump = 1 + bumpy * (Mathf.PerlinNoise(foot.x * 0.31f + side * 1.7f, foot.z * 0.31f + height * 0.9f) - 0.5f);
            vertices.Add(foot + new Vector3(Mathf.Cos(angle) * radius * bump, height, Mathf.Sin(angle) * radius * bump));
            colors.Add((Color32)color.linear); // .linear: 유니티가 빛 계산에 쓰는 색 방식으로 바꿈 (안 바꾸면 색이 허옇게 뜸)
        }
        return first;
    }

    // 아래 고리와 위 고리 사이를 네모(삼각형 두 개)들로 이음
    private void Connect(int lower, int upper)
    {
        for (int side = 0; side < SIDES; side++)
        {
            int next = (side + 1) % SIDES;
            triangles.Add(lower + side); triangles.Add(upper + side); triangles.Add(upper + next);
            triangles.Add(lower + side); triangles.Add(upper + next); triangles.Add(lower + next);
        }
    }

    // 지금까지 더한 나무 전부를 Mesh 하나로
    public Mesh BuildMesh()
    {
        Mesh mesh = new Mesh();
        mesh.indexFormat = IndexFormat.UInt32;
        mesh.SetVertices(vertices);
        mesh.SetColors(colors);
        mesh.SetTriangles(triangles, 0);
        mesh.RecalculateNormals();
        return mesh;
    }
}
