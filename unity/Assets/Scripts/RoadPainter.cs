// 길 바닥 그림 그리기: 아스팔트 · 보도블록 · 산책로 벽돌을 코드로 그림 (사진 파일 없음)
// 그림 한 장이 바닥 몇 m인지는 RetroMap이 정함 (아스팔트 4m, 보도블록 1.2m, 산책로 1.2m)
using UnityEngine;

public static class RoadPainter
{
    private const int SIZE = 128; // 그림 한 변(픽셀)

    // 아스팔트: 짙은 회색에 자잘한 알갱이 + 덧칠한 자국(조금 더 어두운 얼룩)
    public static Texture2D Asphalt()
    {
        Color32[] pixels = new Color32[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                float grain = Noise(x, y) * 26 - 13;                                  // 알갱이 ±13
                float patch = Mathf.PerlinNoise(x * 0.045f + 3, y * 0.045f + 7) * 18; // 큰 얼룩 (0~18)
                int gray = Mathf.Clamp((int)(92 + grain - patch), 0, 255);
                pixels[y * SIZE + x] = new Color32((byte)gray, (byte)gray, (byte)(gray + 3), 255);
            }
        }
        return ToTexture(pixels);
    }

    // 보도블록: 30cm 정사각 블록 4×4 (1.2m), 블록마다 밝기를 조금씩 다르게, 사이에 어두운 줄눈
    public static Texture2D Paving()
    {
        return Blocks(4, 4, new Color32(186, 182, 174, 255), 14);
    }

    // 산책로: 벽돌색 블록 6×3, 줄마다 엇갈림
    // 공원·한강변 산책로: 붉은 갈색 포장 (잠실한강공원 로드뷰의 자전거길·산책로 색). 블록 줄눈 없이 잔 얼룩만
    public static Texture2D Path()
    {
        Color32 baseColor = new Color32(150, 92, 74, 255);
        Color32[] pixels = new Color32[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                float shade = Noise(x, y) * 14 - 7 + (Noise(x / 8, y / 8) * 10 - 5); // 작은 알갱이 + 조금 큰 얼룩
                pixels[y * SIZE + x] = new Color32(Clamp(baseColor.r + shade), Clamp(baseColor.g + shade), Clamp(baseColor.b + shade), 255);
            }
        }
        return ToTexture(pixels);
    }

    // 블록 무늬: columns × rows 칸, base 색, tone = 칸마다 밝기 차이(±)
    private static Texture2D Blocks(int columns, int rows, Color32 baseColor, int tone)
    {
        int blockWidth = SIZE / columns;
        int blockHeight = SIZE / rows;
        Color32[] pixels = new Color32[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++)
        {
            int row = y / blockHeight;
            int shift = (row % 2) * blockWidth / 2; // 한 줄 건너 반 칸 엇갈림
            for (int x = 0; x < SIZE; x++)
            {
                int column = ((x + shift) % SIZE) / blockWidth;
                bool isJoint = (x + shift) % blockWidth < 2 || y % blockHeight < 2; // 줄눈 (2픽셀)
                float shade = isJoint ? -46 : (Noise(column * 17, row * 31) * 2 - 1) * tone + (Noise(x, y) * 8 - 4);
                pixels[y * SIZE + x] = new Color32(Clamp(baseColor.r + shade), Clamp(baseColor.g + shade), Clamp(baseColor.b + shade), 255);
            }
        }
        return ToTexture(pixels);
    }

    // 0~1 사이 "정해진 난수": 같은 (x, y)면 늘 같은 값
    private static float Noise(int x, int y)
    {
        float value = Mathf.Sin(x * 127.1f + y * 311.7f) * 43758.5453f;
        return value - Mathf.Floor(value);
    }

    private static byte Clamp(float value)
    {
        return (byte)Mathf.Clamp(value, 0, 255);
    }

    private static Texture2D ToTexture(Color32[] pixels)
    {
        Texture2D texture = new Texture2D(SIZE, SIZE);
        texture.SetPixels32(pixels);
        texture.anisoLevel = 8;                // 비스듬히 봐도 덜 뭉개지게 (길은 거의 눕혀서 보니까 중요)
        texture.wrapMode = TextureWrapMode.Repeat;
        texture.Apply();
        return texture;
    }
}
