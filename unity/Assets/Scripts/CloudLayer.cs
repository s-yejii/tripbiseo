// 구름: 하늘 높이 넓은 판 하나에 구름 그림을 붙이고 천천히 흘려 보냄
//   구름 그림은 코드로 만듦 (PerlinNoise를 크기를 바꿔 네 번 겹친 무늬 → 뭉게뭉게한 모양, 듬성한 곳은 투명)
//   색은 SkyClock이 시각에 맞춰 바꿈 (낮 흰색 · 노을 주황 · 밤 짙은 회색)
using UnityEngine;

public class CloudLayer : MonoBehaviour
{
    private const int PICTURE_PX = 512;       // 구름 그림 한 변
    private const float HEIGHT_M = 1600f;     // 구름 높이
    private const float SIZE_M = 40000f;      // 구름 판 한 변 (지도보다 훨씬 넓게, 끝은 안개에 묻힘)
    private const float REPEAT = 6f;          // 판 위에 그림을 몇 번 되풀이 (그림 한 장 ≈ 6.7km)
    private const float COVER = 0.52f;        // 이 값보다 짙은 무늬만 구름 (클수록 구름이 적음)
    private const float DRIFT = 0.0015f;      // 1초에 흘러가는 양 (그림 한 장 = 1)

    private Material material;

    // 구름 판을 만들어 이 물체에 붙임 (RetroMap이 부름)
    public void Show()
    {
        if (material != null) return;
        GameObject plane = GameObject.CreatePrimitive(PrimitiveType.Quad); // 유니티 기본 네모 판 (한 변 1m)
        plane.name = "Clouds";
        plane.transform.SetParent(transform, false);
        plane.transform.position = new Vector3(0, HEIGHT_M, 0);
        plane.transform.rotation = Quaternion.Euler(90, 0, 0); // 눕혀서 아래(땅)를 보게
        plane.transform.localScale = new Vector3(SIZE_M, SIZE_M, 1);
        DestroyImmediate(plane.GetComponent<Collider>()); // 부딪힘 판정은 필요 없음

        material = new Material(Shader.Find("Sprites/Default")); // 빛 계산 없이 그림 색 그대로 + 투명 + 양면
        material.mainTexture = MakePicture();
        material.mainTextureScale = Vector2.one * REPEAT;
        plane.GetComponent<MeshRenderer>().sharedMaterial = material;
        plane.GetComponent<MeshRenderer>().shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
    }

    // 구름 색 (SkyClock이 부름)
    public void SetColor(Color color)
    {
        if (material != null) material.color = color;
    }

    private void Update()
    {
        if (material != null) material.mainTextureOffset = new Vector2(Time.time * DRIFT, Time.time * DRIFT * 0.4f);
    }

    // 구름 그림: 점마다 짙기(0~1)를 정해 흰색 + 투명도로. 끝과 처음이 이어지게 네 귀퉁이 값을 섞음
    private static Texture2D MakePicture()
    {
        Color32[] pixels = new Color32[PICTURE_PX * PICTURE_PX];
        for (int y = 0; y < PICTURE_PX; y++)
        {
            for (int x = 0; x < PICTURE_PX; x++)
            {
                float u = (float)x / PICTURE_PX;
                float v = (float)y / PICTURE_PX;
                // 이어 붙여도 경계가 안 보이게: (u, v)와 한 장 옮긴 자리의 값을 거리 비율로 섞음
                float density = Density(u, v) * (1 - u) * (1 - v) + Density(u - 1, v) * u * (1 - v)
                              + Density(u, v - 1) * (1 - u) * v + Density(u - 1, v - 1) * u * v;
                float alpha = Mathf.Clamp01((density - COVER) / 0.18f); // 짙은 곳만 보이게, 가장자리는 부드럽게
                byte shade = (byte)(235 + 20 * alpha);                  // 짙은 곳이 더 하얗게
                pixels[y * PICTURE_PX + x] = new Color32(shade, shade, shade, (byte)(alpha * 230));
            }
        }
        Texture2D texture = new Texture2D(PICTURE_PX, PICTURE_PX);
        texture.SetPixels32(pixels);
        texture.wrapMode = TextureWrapMode.Repeat;
        texture.Apply();
        return texture;
    }

    // 짙기: 큰 무늬 + 작은 무늬를 세기를 줄여 가며 네 번 겹침 (0~1쯤)
    private static float Density(float u, float v)
    {
        float sum = 0;
        float scale = 3f;
        float weight = 0.5f;
        for (int octave = 0; octave < 4; octave++)
        {
            sum += Mathf.PerlinNoise(u * scale + 17.3f, v * scale + 5.1f) * weight;
            scale *= 2;
            weight /= 2;
        }
        return sum / 0.9375f; // 0.5 + 0.25 + 0.125 + 0.0625 = 0.9375 로 나눠 0~1로
    }
}
