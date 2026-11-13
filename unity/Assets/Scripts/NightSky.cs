// 밤하늘: 별 + 달. 해가 지면 SkyClock이 밝기를 올림 (낮에는 안 보임)
//   별 = 아주 먼 둥근 천장 위에 작은 네모 수백 개 (한 덩어리), 달 = 동그란 그림 한 장
//   카메라가 움직여도 늘 같은 자리에 보이게, 매 프레임 카메라 위치로 따라감 (하늘은 무한히 멀어서)
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;

public class NightSky : MonoBehaviour
{
    private const int STAR_COUNT = 700;
    private const float SKY_M = 15000f;        // 별을 붙일 천장 반지름 (카메라 먼 끝 20km 안쪽)
    private const float STAR_M = 18f;          // 별 하나 크기 (15km 밖이라 점처럼 보임)
    private const float MOON_M = 520f;         // 달 크기
    private static readonly Vector2 MOON = new Vector2(35, 160); // 달 { 높이, 방향 } (SkyClock의 달빛과 같은 곳)

    private Transform sky;
    private Material starMaterial;
    private Material moonMaterial;

    public void Show()
    {
        if (sky != null) return;
        sky = new GameObject("NightSky").transform;
        sky.SetParent(transform, false);
        starMaterial = new Material(Shader.Find("Sprites/Default")); // 빛 계산 없이 색 그대로 + 투명
        moonMaterial = new Material(Shader.Find("Sprites/Default"));
        moonMaterial.mainTexture = MoonPicture();
        AddPart("Stars", StarMesh(), starMaterial);
        AddPart("Moon", MoonMesh(), moonMaterial);
        SetNight(0);
    }

    // 밤 정도 0(낮) ~ 1(밤). 별·달 밝기
    public void SetNight(float night)
    {
        if (starMaterial == null) return;
        starMaterial.color = new Color(1, 1, 1, night * 0.9f);
        moonMaterial.color = new Color(1.6f, 1.55f, 1.4f, night); // 1보다 밝게 → 빛 번짐(Bloom)으로 은은하게 빛남
    }

    private void LateUpdate()
    {
        if (sky != null && Camera.main != null) sky.position = Camera.main.transform.position;
    }

    private void AddPart(string partName, Mesh mesh, Material material)
    {
        GameObject part = new GameObject(partName);
        part.transform.SetParent(sky, false);
        part.AddComponent<MeshFilter>().sharedMesh = mesh;
        MeshRenderer renderer = part.AddComponent<MeshRenderer>();
        renderer.sharedMaterial = material;
        renderer.shadowCastingMode = ShadowCastingMode.Off;
    }

    // 별: 하늘 위쪽 반구에 흩뿌린 작은 네모들. 밝기를 조금씩 달리함
    private static Mesh StarMesh()
    {
        List<Vector3> vertices = new List<Vector3>();
        List<Color32> colors = new List<Color32>();
        List<int> triangles = new List<int>();
        Random.State saved = Random.state;
        Random.InitState(7); // 늘 같은 별자리가 나오게
        for (int i = 0; i < STAR_COUNT; i++)
        {
            float height = Mathf.Lerp(8, 85, Mathf.Sqrt(Random.value)); // 지평선 가까이는 적게
            float around = Random.value * 360;
            byte bright = (byte)Random.Range(120, 256);
            AddFacingQuad(vertices, colors, triangles, Direction(height, around) * SKY_M, STAR_M * Random.Range(0.6f, 1.3f), new Color32(bright, bright, 255, 255));
        }
        Random.state = saved;
        return ToMesh(vertices, colors, triangles);
    }

    private static Mesh MoonMesh()
    {
        List<Vector3> vertices = new List<Vector3>();
        List<Color32> colors = new List<Color32>();
        List<int> triangles = new List<int>();
        AddFacingQuad(vertices, colors, triangles, Direction(MOON.x, MOON.y) * (SKY_M * 0.9f), MOON_M, new Color32(255, 255, 255, 255));
        return ToMesh(vertices, colors, triangles);
    }

    // 가운데(center)에서 원점(카메라)을 바라보는 네모 한 장
    private static void AddFacingQuad(List<Vector3> vertices, List<Color32> colors, List<int> triangles, Vector3 center, float size, Color32 color)
    {
        Vector3 toCamera = -center.normalized;
        Vector3 right = Vector3.Cross(Vector3.up, toCamera).normalized * size / 2;
        Vector3 up = Vector3.Cross(toCamera, right).normalized * size / 2;
        int first = vertices.Count;
        vertices.Add(center - right - up); vertices.Add(center - right + up);
        vertices.Add(center + right + up); vertices.Add(center + right - up);
        for (int k = 0; k < 4; k++) colors.Add(color);
        triangles.Add(first); triangles.Add(first + 1); triangles.Add(first + 2);
        triangles.Add(first); triangles.Add(first + 2); triangles.Add(first + 3);
        triangles.Add(first); triangles.Add(first + 2); triangles.Add(first + 1); // 뒷면도 (어느 쪽이 앞인지 신경 안 쓰게)
        triangles.Add(first); triangles.Add(first + 3); triangles.Add(first + 2);
    }

    private static Mesh ToMesh(List<Vector3> vertices, List<Color32> colors, List<int> triangles)
    {
        Mesh mesh = new Mesh();
        mesh.SetVertices(vertices);
        mesh.SetColors(colors);
        mesh.SetTriangles(triangles, 0);
        List<Vector2> uv = new List<Vector2>();
        for (int i = 0; i < vertices.Count; i += 4)
        {
            uv.Add(new Vector2(0, 0)); uv.Add(new Vector2(0, 1)); uv.Add(new Vector2(1, 1)); uv.Add(new Vector2(1, 0));
        }
        mesh.SetUVs(0, uv);
        mesh.bounds = new Bounds(Vector3.zero, Vector3.one * SKY_M * 3); // 늘 화면 안으로 쳐서 안 사라지게
        return mesh;
    }

    // 달 그림: 가운데가 밝고 가장자리는 부드럽게 사라지는 원 + 얼룩 조금
    private static Texture2D MoonPicture()
    {
        const int size = 128;
        Color32[] pixels = new Color32[size * size];
        for (int y = 0; y < size; y++)
        {
            for (int x = 0; x < size; x++)
            {
                float dx = (x - size / 2f) / (size / 2f);
                float dy = (y - size / 2f) / (size / 2f);
                float distance = Mathf.Sqrt(dx * dx + dy * dy);
                float disc = Mathf.Clamp01((0.62f - distance) / 0.04f);  // 달 원판 (반지름 0.62)
                float halo = Mathf.Clamp01(1 - distance) * 0.25f;         // 둘레 은은한 빛
                float spot = 0.85f + 0.15f * Mathf.PerlinNoise(x * 0.08f, y * 0.08f); // 달 얼룩
                byte shade = (byte)(255 * Mathf.Lerp(1, spot, disc));
                pixels[y * size + x] = new Color32(shade, shade, (byte)(shade * 0.95f), (byte)(255 * Mathf.Max(disc, halo)));
            }
        }
        Texture2D texture = new Texture2D(size, size);
        texture.SetPixels32(pixels);
        texture.wrapMode = TextureWrapMode.Clamp;
        texture.Apply();
        return texture;
    }

    // 높이·방향(도) → 그쪽을 가리키는 방향 (북 = +z, 동 = +x)
    private static Vector3 Direction(float altitude, float azimuth)
    {
        float up = altitude * Mathf.Deg2Rad;
        float around = azimuth * Mathf.Deg2Rad;
        return new Vector3(Mathf.Sin(around) * Mathf.Cos(up), Mathf.Sin(up), Mathf.Cos(around) * Mathf.Cos(up));
    }
}
