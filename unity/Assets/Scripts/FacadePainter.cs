// 건물 벽 그림 그리기: 창문 한 칸(가로 3.2m × 한 층) 크기의 작은 그림을 코드로 그림
// 이 그림을 벽에 바둑판처럼 되풀이해 붙이면 창문이 줄지어 있는 벽이 됨
// 건물 종류(kind): 0 단독·다세대주택 · 1 아파트 · 2 상가 · 3 업무(유리 빌딩) · 4 그 밖
// 색 번호(variant): 같은 종류 안에서 벽 색을 3가지로 바꿔 건물이 다 똑같아 보이지 않게
// 색과 무늬는 남산 주변 로드뷰(회현동·후암동 골목, 퇴계로)를 보고 잡음: 주택은 아이보리 타일·주황 벽돌·회색 미장에 짙은 창틀
using UnityEngine;

public static class FacadePainter
{
    private const int SIZE = 128; // 그림 한 변(픽셀)

    // 벽 색 [종류][색 번호]
    private static readonly Color32[][] WALL_COLORS =
    {
        new Color32[] { C(206, 202, 190), C(190, 132, 92), C(166, 166, 162) },  // 주택: 아이보리 타일 · 주황 벽돌 · 회색 미장
        new Color32[] { C(208, 204, 194), C(192, 196, 200), C(204, 192, 174) }, // 아파트: 아이보리 · 연회색 · 살구
        new Color32[] { C(190, 190, 186), C(208, 198, 180), C(120, 122, 126) }, // 상가: 회색 타일 · 베이지 · 진회색 패널
        new Color32[] { C(70, 96, 122), C(62, 104, 98), C(118, 100, 78) },      // 업무: 푸른 유리 · 초록 유리 · 갈색 유리
        new Color32[] { C(98, 100, 104), C(188, 180, 166), C(212, 210, 206) },  // 그 밖: 진회색 패널 · 베이지 · 밝은 콘크리트
    };
    private static readonly Color32[] SIGN_COLORS = { C(196, 64, 54), C(52, 98, 168), C(232, 176, 52) }; // 상가 간판
    private static readonly Color32 WHITE_FRAME = C(240, 240, 236); // 흰 창틀 (아파트)
    private static readonly Color32 DARK_FRAME = C(58, 62, 68);     // 짙은 창틀 (주택·상가)
    private static readonly Color32 GLASS = C(60, 92, 100);         // 유리 (어두운 쪽, 청록빛)
    private static readonly Color32 DARK = C(38, 40, 44);           // 주차장·문 안쪽

    // 2층부터 위쪽 벽 한 칸
    public static Texture2D Upper(int kind, int variant)
    {
        Color32 wall = WALL_COLORS[kind][variant];
        Color32[] pixels = NewCanvas(wall);
        if (kind == 0)
        {
            WallPattern(pixels, wall, variant);
            Window(pixels, 34, 38, 60, 54, DARK_FRAME);
            Fill(pixels, 30, 30, 68, 4, Darker(wall, 0.7f));       // 창 아래 턱
        }
        else if (kind == 1)
        {
            Fill(pixels, 0, 18, SIZE, 10, Darker(wall, 0.88f));    // 층 사이 띠 (베란다 바닥)
            Window(pixels, 10, 36, 108, 64, WHITE_FRAME);
            Fill(pixels, 45, 36, 3, 64, WHITE_FRAME);              // 큰 창을 세 칸으로 나누는 창살
            Fill(pixels, 81, 36, 3, 64, WHITE_FRAME);
        }
        else if (kind == 2)
        {
            Fill(pixels, 4, 6, 120, 26, SIGN_COLORS[variant]);     // 층마다 붙은 간판
            Fill(pixels, 16, 15, 60, 8, WHITE_FRAME);              // 간판 글자 자리
            Window(pixels, 8, 42, 112, 66, DARK_FRAME);
        }
        else if (kind == 3)
        {
            Glass(pixels, 0, 0, SIZE, SIZE, wall);                 // 벽 전체가 유리
            Fill(pixels, 0, 0, SIZE, 20, Darker(wall, 0.6f));      // 층 사이 띠
            Fill(pixels, 0, 0, 4, SIZE, Darker(wall, 0.45f));      // 세로 창살
        }
        else
        {
            Fill(pixels, 0, 0, 2, SIZE, Darker(wall, 0.8f));       // 패널 이음매
            Fill(pixels, 0, 0, SIZE, 2, Darker(wall, 0.8f));
            Window(pixels, 14, 42, 40, 54, DARK_FRAME);
            Window(pixels, 74, 42, 40, 54, DARK_FRAME);
        }
        return ToTexture(pixels);
    }

    // 한옥 벽 한 칸: 아래 돌 기단 + 흰 회벽 + 양옆 나무 기둥 + 위 나무 보 + 가운데 창살 문
    public static Texture2D Hanok()
    {
        // 익선동 골목 로드뷰(카카오, 2013년)를 보고 잡음: 골목 쪽 벽은 붉은 벽돌, 위에 짙은 나무 보,
        //   벽 높은 곳에 나무틀 작은 창, 아래는 회색 돌 기단 (흰 회벽은 마당 안쪽이라 골목에서는 잘 안 보임)
        Color32 brick = C(128, 66, 50);
        Color32 mortar = C(92, 52, 42);    // 벽돌 사이 줄눈 (어두운 쪽)
        Color32 wood = C(70, 46, 32);
        Color32 glass = C(52, 58, 62);
        Color32[] pixels = NewCanvas(brick);
        for (int row = 0; row * 7 < SIZE; row++) // 벽돌 줄: 7픽셀마다 가로 줄눈, 줄마다 반 칸씩 엇갈린 세로 줄눈
        {
            Fill(pixels, 0, row * 7, SIZE, 1, mortar);
            for (int x = (row % 2) * 8; x < SIZE; x += 16) Fill(pixels, x, row * 7, 1, 7, mortar);
        }
        Fill(pixels, 0, 0, SIZE, 16, C(140, 136, 128));  // 돌 기단
        Fill(pixels, 0, SIZE - 14, SIZE, 14, wood);      // 위 나무 보
        Fill(pixels, 40, 66, 48, 34, wood);              // 높은 곳 작은 창 (나무틀)
        Fill(pixels, 44, 70, 40, 26, glass);             // 창 유리
        Fill(pixels, 63, 70, 2, 26, wood);               // 창 가운데 살
        return ToTexture(pixels);
    }

    // 한옥 기와: 둥근 수키와가 비탈을 따라 줄지어 있는 모습 (그림 한 장 = 1.2m, 골 4줄)
    //   가로: 골마다 가운데가 밝고 가장자리가 어두운 띠 (둥근 기와에 빛이 닿는 모습), 세로: 기와가 겹친 자리마다 어두운 줄
    public static Texture2D GiwaTiles()
    {
        Color32[] pixels = new Color32[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                float round = Mathf.Sin((x % 32) / 32f * Mathf.PI); // 골 하나 안에서 0 → 1 → 0
                float shade = 28 + round * 26 - (y % 32 < 3 ? 10 : 0);  // 겹친 줄은 더 어둡게
                byte gray = (byte)Mathf.Clamp(shade, 0, 255);
                pixels[y * SIZE + x] = new Color32(gray, (byte)(gray + 2), (byte)(gray + 6), 255);
            }
        }
        return ToTexture(pixels);
    }

    // ---------- 을지로 인쇄 골목 (카카오 로드뷰 을지로3가, 2017년을 보고 잡음)
    private static readonly Color32 EULJIRO_TILE = C(236, 234, 228); // 흰 작은 타일

    // 위층: 흰 작은 타일 벽 + 알루미늄 틀 창 + 창 아래 실외기 + 왼쪽 끝 배수관
    public static Texture2D EuljiroUpper()
    {
        Color32[] pixels = NewCanvas(EULJIRO_TILE);
        for (int at = 0; at < SIZE; at += 6) // 작은 타일 줄눈 (6픽셀 = 약 15cm)
        {
            Fill(pixels, 0, at, SIZE, 1, C(214, 212, 206));
            Fill(pixels, at, 0, 1, SIZE, C(214, 212, 206));
        }
        Window(pixels, 22, 46, 84, 52, C(150, 154, 158));       // 알루미늄 틀 창
        Fill(pixels, 84, 28, 26, 14, C(206, 208, 206));          // 실외기 몸통
        Fill(pixels, 88, 31, 10, 8, C(120, 124, 128));           // 실외기 바람구멍
        Fill(pixels, 4, 0, 4, SIZE, C(170, 172, 170));           // 배수관
        return ToTexture(pixels);
    }

    // 1층: 위에 넓은 흰 간판판(글자 자리 + 파랑·빨강 사선 띠), 아래는 셔터를 올린 어두운 창고형 입구
    public static Texture2D EuljiroGround()
    {
        Color32[] pixels = NewCanvas(EULJIRO_TILE);
        Fill(pixels, 0, 88, SIZE, 34, C(244, 244, 240));          // 간판판
        Fill(pixels, 14, 98, 66, 14, C(70, 66, 64));             // 간판 글자 자리
        for (int k = 0; k < 18; k++) // 오른쪽 사선 띠 (파랑 · 빨강)
        {
            Fill(pixels, 92 + k, 92 + k, 6, 1, C(44, 70, 160));
            Fill(pixels, 100 + k, 92 + k, 4, 1, C(196, 52, 48));
        }
        Fill(pixels, 12, 0, 104, 84, C(40, 46, 58));              // 열린 입구 안쪽 (어두움, 밤에는 불 켜짐)
        Fill(pixels, 12, 78, 104, 6, C(150, 150, 146));           // 말아 올린 셔터
        Fill(pixels, 12, 0, 104, 6, C(96, 94, 90));               // 입구 바닥 턱
        return ToTexture(pixels);
    }

    // 1층 벽 한 칸 (주택은 필로티 주차장이나 대문, 상가는 가게 앞, 업무는 유리 로비)
    public static Texture2D GroundFloor(int kind, int variant)
    {
        Color32 wall = WALL_COLORS[kind][variant];
        Color32[] pixels = NewCanvas(wall);
        if (kind == 0 && variant == 2) // 회색 미장 주택: 돌 붙인 아랫단 + 철 대문 + 작은 창
        {
            Fill(pixels, 0, 0, SIZE, 34, Darker(wall, 0.72f));
            Fill(pixels, 16, 0, 36, 84, DARK);
            Window(pixels, 74, 54, 38, 34, DARK_FRAME);
        }
        else if (kind == 0)            // 그 밖의 주택: 기둥 사이 주차장 (필로티)
        {
            WallPattern(pixels, wall, variant);
            Fill(pixels, 12, 0, 104, 88, DARK);
            Fill(pixels, 0, 88, SIZE, 8, Darker(wall, 0.8f));
        }
        else if (kind == 1)
        {
            Fill(pixels, 0, 0, SIZE, 30, Darker(wall, 0.75f));     // 아래쪽 돌 붙임
            Window(pixels, 30, 48, 68, 50, WHITE_FRAME);
        }
        else if (kind == 2)
        {
            Fill(pixels, 4, 92, 120, 30, SIGN_COLORS[(variant + 1) % 3]); // 가게 간판
            Fill(pixels, 16, 102, 70, 10, WHITE_FRAME);
            Glass(pixels, 6, 6, 116, 76, GLASS);                   // 진열창
            Fill(pixels, 62, 6, 4, 76, DARK_FRAME);                // 문 가운데 틀
            if (variant == 1) Awning(pixels, 82);                  // 줄무늬 차양
        }
        else if (kind == 3)
        {
            Glass(pixels, 4, 0, 120, 104, Darker(wall, 0.7f));     // 유리 로비
            Fill(pixels, 0, 104, SIZE, 10, Darker(wall, 0.45f));   // 차양
        }
        else
        {
            Fill(pixels, 50, 0, 30, 78, C(96, 72, 56));            // 문
            Window(pixels, 90, 50, 30, 40, DARK_FRAME);
        }
        return ToTexture(pixels);
    }

    // ---------- 로드뷰를 보고 따로 정한 건물 (MapData.cs의 LookData)

    // 2층부터 위쪽 벽 한 칸: 정해 준 벽 색·무늬로
    public static Texture2D CustomUpper(LookData look)
    {
        Color32 wall = Parse(look.wall);
        Color32[] pixels = NewCanvas(wall);
        if (look.pattern == 4)
        {
            Glass(pixels, 0, 0, SIZE, SIZE, wall);
            Fill(pixels, 0, 0, SIZE, 20, Darker(wall, 0.6f));
            Fill(pixels, 0, 0, 4, SIZE, Darker(wall, 0.45f));
        }
        else
        {
            WallPattern(pixels, wall, look.pattern);
            Window(pixels, 30, 38, 68, 56, DARK_FRAME);
        }
        return ToTexture(pixels);
    }

    // 1층 벽 한 칸: 정해 준 1층 모습으로
    public static Texture2D CustomGround(LookData look)
    {
        Color32 wall = Parse(look.wall);
        Color32[] pixels = NewCanvas(wall);
        if (look.ground == 0)       // 필로티 주차장
        {
            Fill(pixels, 12, 0, 104, 88, DARK);
            Fill(pixels, 0, 88, SIZE, 8, Darker(wall, 0.8f));
        }
        else if (look.ground == 1)  // 가게: 간판 + 진열창 + 차양
        {
            Color32 sign = Parse(look.sign);
            Fill(pixels, 4, 94, 120, 28, sign);
            Fill(pixels, 16, 103, 70, 10, sign.r + sign.g + sign.b > 450 ? DARK_FRAME : WHITE_FRAME); // 밝은 간판엔 짙은 글자, 어두운 간판엔 흰 글자
            Glass(pixels, 6, 6, 116, 72, GLASS);
            Fill(pixels, 62, 6, 4, 72, DARK_FRAME);
            Fill(pixels, 0, 80, SIZE, 10, Darker(sign, 0.85f));  // 차양
        }
        else if (look.ground == 2)  // 벽 + 문
        {
            WallPattern(pixels, wall, look.pattern);
            Fill(pixels, 0, 0, SIZE, 26, Darker(wall, 0.75f));
            Fill(pixels, 20, 0, 34, 82, DARK);
            Window(pixels, 76, 52, 36, 36, DARK_FRAME);
        }
        else                        // 유리 로비
        {
            Glass(pixels, 4, 0, 120, 104, GLASS);
            Fill(pixels, 0, 104, SIZE, 10, Darker(wall, 0.6f));
            Fill(pixels, 62, 0, 4, 104, DARK_FRAME);
        }
        return ToTexture(pixels);
    }

    // 색 글자("#C0392B") → 색
    private static Color32 Parse(string text)
    {
        ColorUtility.TryParseHtmlString(text, out Color color); // out = 결과를 color에 담아 달라는 뜻. 못 읽으면 검정
        return color;
    }

    // ---------- 그리기 도구

    private static Color32 C(int r, int g, int b)
    {
        return new Color32((byte)r, (byte)g, (byte)b, 255);
    }

    // 색을 어둡게 (amount 0.8 = 원래의 80% 밝기)
    private static Color32 Darker(Color32 color, float amount)
    {
        return C((int)(color.r * amount), (int)(color.g * amount), (int)(color.b * amount));
    }

    // 한 가지 색으로 칠한 빈 그림
    private static Color32[] NewCanvas(Color32 color)
    {
        Color32[] pixels = new Color32[SIZE * SIZE];
        for (int i = 0; i < pixels.Length; i++) pixels[i] = color;
        return pixels;
    }

    // 네모 칠하기. (x, y)는 왼쪽 아래 모서리 (y = 0이 그림 맨 아래)
    private static void Fill(Color32[] pixels, int x, int y, int width, int height, Color32 color)
    {
        for (int row = Mathf.Max(0, y); row < Mathf.Min(SIZE, y + height); row++)
        {
            for (int column = Mathf.Max(0, x); column < Mathf.Min(SIZE, x + width); column++) pixels[row * SIZE + column] = color;
        }
    }

    // 유리: 아래는 어둡고 위로 갈수록 밝게 (하늘이 비친 느낌)
    private static void Glass(Color32[] pixels, int x, int y, int width, int height, Color32 color)
    {
        for (int row = 0; row < height; row++)
        {
            float bright = 1f + 0.5f * row / height;
            Fill(pixels, x, y + row, width, 1, C(Mathf.Min(255, (int)(color.r * bright)), Mathf.Min(255, (int)(color.g * bright)), Mathf.Min(255, (int)(color.b * bright))));
        }
    }

    // 창문 하나: 창틀 + 유리 + 가운데 창살 + 벽 안으로 들어간 것처럼 보이게 하는 그림자
    //   위·왼쪽 안쪽에 어두운 띠(해가 위에서 비춰 창 윗면이 그늘), 아래에 밝은 창턱(빛을 받는 돌)
    private static void Window(Color32[] pixels, int x, int y, int width, int height, Color32 frame)
    {
        Fill(pixels, x - 2, y - 5, width + 4, 4, C(222, 220, 214));   // 창턱 (밝은 돌)
        Fill(pixels, x - 2, y - 7, width + 4, 2, C(110, 110, 110));   // 창턱 아래 그림자
        Fill(pixels, x, y, width, height, frame);
        Glass(pixels, x + 3, y + 3, width - 6, height - 6, GLASS);
        Fill(pixels, x + 3, y + height - 9, width - 6, 6, C(30, 40, 48));  // 윗면 그늘
        Fill(pixels, x + 3, y + 3, 4, height - 6, C(40, 52, 60));          // 왼쪽 옆면 그늘
        Fill(pixels, x + width / 2 - 1, y, 3, height, frame);
    }

    // 벽 무늬: 0 작은 타일(바둑판 줄눈) · 1 벽돌(엇갈린 줄눈) · 2 미장(무늬 없음) · 3 돌판·패널(큰 이음매)
    private static void WallPattern(Color32[] pixels, Color32 wall, int variant)
    {
        Color32 line = Darker(wall, 0.86f);
        if (variant == 0)
        {
            for (int at = 0; at < SIZE; at += 8)
            {
                Fill(pixels, 0, at, SIZE, 1, line);
                Fill(pixels, at, 0, 1, SIZE, line);
            }
        }
        else if (variant == 1)
        {
            for (int row = 0; row * 8 < SIZE; row++)
            {
                Fill(pixels, 0, row * 8, SIZE, 1, line);
                for (int x = (row % 2) * 8; x < SIZE; x += 16) Fill(pixels, x, row * 8, 1, 8, line);
            }
        }
        else if (variant == 3)
        {
            for (int at = 0; at < SIZE; at += 32)
            {
                Fill(pixels, 0, at, SIZE, 2, line);
                Fill(pixels, at, 0, 2, SIZE, line);
            }
        }
    }

    // 가게 앞 줄무늬 차양 (빨강·흰색 번갈아)
    private static void Awning(Color32[] pixels, int y)
    {
        for (int x = 0; x < SIZE; x += 16)
        {
            Fill(pixels, x, y, 8, 12, C(200, 60, 52));
            Fill(pixels, x + 8, y, 8, 12, WHITE_FRAME);
        }
    }

    // ---------- 밤 불빛
    public const int NIGHT_REPEAT = 4; // 칸마다 불이 켜졌는지 다르게 하려고, 벽 그림 한 장에 칸을 4 × 4개 넣음
    private static readonly Color32 WARM_LIGHT = C(255, 206, 140); // 집 불빛 (전구색)
    private static readonly Color32 COOL_LIGHT = C(214, 228, 255); // 사무실 불빛 (주광색)
    private static readonly Color32 NO_LIGHT = C(0, 0, 0);
    private const byte WINDOW_DEPTH = 60;        // 깊이 그림에서 창 자리 높이 (0 가장 깊음 ~ 255 벽 면)
    private const float GLASS_WALL_RATIO = 0.6f; // 칸의 60% 넘게 창이면 유리 빌딩으로 봄
    private const float OFFICE_LIGHT = 0.16f;    // 유리 빌딩 불빛 세기 (보통 창의 16%. 유리벽 전체가 빛나서 이 정도면 충분)

    // 벽 그림 한 칸을 4 × 4로 되풀이한 큰 그림 (낮 그림. NightLights와 칸 배치가 같아야 함)
    public static Texture2D Repeat(Texture2D cell)
    {
        Color32[] source = cell.GetPixels32();
        int big = SIZE * NIGHT_REPEAT;
        Color32[] pixels = new Color32[big * big];
        for (int y = 0; y < big; y++)
        {
            for (int x = 0; x < big; x++) pixels[y * big + x] = source[(y % SIZE) * SIZE + x % SIZE];
        }
        return ToBigTexture(pixels, big);
    }

    // 밤 불빛 그림: Repeat와 같은 4 × 4 칸에서, 칸마다 불이 켜졌는지 정해 창 자리만 불빛 색으로 (나머지는 검정 = 빛 없음)
    //   창 자리 = 유리처럼 푸르스름한 점, 간판 = 색이 진한 점(간판은 늘 켬)
    //   litChance: 칸마다 불이 켜질 확률 (예: 0.55 = 열에 다섯 칸 반)
    public static Texture2D NightLights(Texture2D cell, int seed, float litChance)
    {
        Color32[] source = cell.GetPixels32();
        bool isGlassWall = CountWindowPixels(source) > source.Length * GLASS_WALL_RATIO; // 유리 빌딩 (벽 전체가 창)
        int big = SIZE * NIGHT_REPEAT;
        Color32[] pixels = new Color32[big * big];
        for (int cellY = 0; cellY < NIGHT_REPEAT; cellY++)
        {
            for (int cellX = 0; cellX < NIGHT_REPEAT; cellX++)
            {
                int column = isGlassWall ? 0 : cellX; // 유리 빌딩은 층(가로줄)마다 통째로 켜짐 (사무실은 층 단위로 불을 켜서)
                float dice = Mathf.Abs(Mathf.Sin(seed * 12.9898f + column * 78.233f + cellY * 37.719f) * 43758.5453f) % 1; // 칸마다 0~1 사이 값 (늘 같은 값이 나오는 주사위)
                bool isLit = dice < litChance;
                Color32 light = dice < litChance * 0.3f ? COOL_LIGHT : WARM_LIGHT;
                if (isGlassWall) light = Color32.Lerp(NO_LIGHT, COOL_LIGHT, OFFICE_LIGHT); // 유리벽 전체가 빛나면 너무 세서 약하게
                PaintCellLights(source, pixels, big, cellX, cellY, isLit, light);
            }
        }
        return ToBigTexture(pixels, big);
    }

    // 벽 깊이 그림: Repeat와 같은 4 × 4 칸 배치로, 창 자리는 낮게(어둡게) 벽은 높게(밝게)
    //   재질이 이걸 보고 비스듬히 볼 때 창 그림을 조금 밀어 그려서, 창이 벽 안으로 들어가 보이게 함 (패럴랙스)
    public static Texture2D Depth(Texture2D cell)
    {
        Color32[] source = cell.GetPixels32();
        Color32[] small = new Color32[source.Length];
        for (int i = 0; i < source.Length; i++)
        {
            byte height = IsWindow(source[i]) ? WINDOW_DEPTH : (byte)255;
            small[i] = new Color32(height, height, height, 255);
        }
        int big = SIZE * NIGHT_REPEAT;
        Color32[] pixels = new Color32[big * big];
        for (int y = 0; y < big; y++)
        {
            for (int x = 0; x < big; x++) pixels[y * big + x] = small[(y % SIZE) * SIZE + x % SIZE];
        }
        return ToBigTexture(pixels, big);
    }

    // 창 자리(푸르스름한 점) 개수
    private static int CountWindowPixels(Color32[] source)
    {
        int count = 0;
        foreach (Color32 color in source)
        {
            if (IsWindow(color)) count++;
        }
        return count;
    }

    // 큰 그림의 칸 하나를 칠함
    private static void PaintCellLights(Color32[] source, Color32[] pixels, int big, int cellX, int cellY, bool isLit, Color32 light)
    {
        for (int y = 0; y < SIZE; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                Color32 color = source[y * SIZE + x];
                Color32 result = NO_LIGHT;
                if (IsSign(color)) result = color;
                else if (isLit && IsWindow(color)) result = light;
                pixels[(cellY * SIZE + y) * big + cellX * SIZE + x] = result;
            }
        }
    }

    // 유리처럼 푸르스름한 점 (창 유리, 유리벽)
    private static bool IsWindow(Color32 color)
    {
        return color.b > color.r + 15 && color.b < 215;
    }

    // 색이 진한 점 (간판: 가장 밝은 색과 가장 어두운 색 차이가 큼)
    private static bool IsSign(Color32 color)
    {
        int high = Mathf.Max(color.r, Mathf.Max(color.g, color.b));
        int low = Mathf.Min(color.r, Mathf.Min(color.g, color.b));
        return high - low > 110;
    }

    // 큰 그림은 압축해서 메모리를 줄임 (512 × 512: 1MB → 약 0.2MB)
    private static Texture2D ToBigTexture(Color32[] pixels, int size)
    {
        Texture2D texture = new Texture2D(size, size);
        texture.SetPixels32(pixels);
        texture.anisoLevel = 8;
        texture.Apply();
        texture.Compress(true);
        return texture;
    }

    private static Texture2D ToTexture(Color32[] pixels)
    {
        Texture2D texture = new Texture2D(SIZE, SIZE);
        texture.SetPixels32(pixels);
        texture.anisoLevel = 8; // 비스듬히 봐도 덜 뭉개지게
        texture.Apply();        // 칠한 내용을 실제 그림에 반영 (멀리서 볼 작은 그림도 같이 만들어짐)
        return texture;
    }
}
