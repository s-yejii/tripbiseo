// 웹이 넘겨주는 지도 자료의 모양 (JSON → 이 클래스들로 바뀜)
// 웹 쪽에서 만드는 곳: frontend/src/components/after/web/landscape/unityExport.js
// 좌표는 전부 미터. 지도 가운데가 (0, 0), 동쪽이 x, 북쪽이 z, 위쪽이 y(높이)
using System;

[Serializable] // JSON에서 읽을 수 있는 클래스라는 표시
public class MapData
{
    public float sizeM;              // 지도 한 변 길이(m)
    public int gridCount;            // 지형 격자가 한 줄에 몇 점인지
    public float[] heights;          // 격자 점마다 땅 높이(m). 남쪽 줄부터, 한 줄 안에서는 서쪽부터
    public BuildingData[] buildings; // 건물 목록
    public RoadData[] roads;         // 길 목록
    public TreeData[] trees;         // 나무 목록
    public RouteData route;          // 걸은 경로
    public StopData[] stops;         // 다녀온 곳
    public LandmarkData[] landmarks; // 모양을 따로 만드는 눈에 띄는 건물
    public WaterData water;          // 강·호수 (없으면 cells가 비어 있음)
    public SunData sun;              // 여행 시각마다 해 위치 (없으면 minutes가 비어 있음 → 늘 낮)
    public int startClock;           // 경로 0분이 첫날 몇 시 몇 분인지 (0시부터 분. 예: 11시 30분 = 690)
}

[Serializable]
public class SunData
{
    public float[] minutes;   // 여행 몇 분째
    public float[] altitudes; // 그때 해 높이(도). 0 아래면 해가 진 것
    public float[] azimuths;  // 해 방향(도). 북 0 · 동 90 · 남 180 · 서 270
}

[Serializable]
public class WaterData
{
    public float level; // 물 높이(m)
    public int[] cells; // 지형 격자 점마다 1 물 · 0 땅 (heights와 같은 순서)
}

[Serializable]
public class RoadData
{
    public float[] points; // 길 가운데 선: x, z, x, z … 순서
    public float width;    // 길 폭(m)
    public int kind;       // 0 찻길 · 1 걷는 길
    public bool isOneWay;  // 큰길이 가는 쪽·오는 쪽 두 선으로 나뉜 것 중 한쪽 (폭이 좁아도 골목이 아님)
    public bool isBridge;  // 다리 (물 위를 지나면 BridgeMaker가 물 위로 띄워 그림)
}

[Serializable]
public class RouteData
{
    public float[] points;  // 걸은 경로: x, z, x, z … 순서
    public float[] minutes; // 점마다 여행 시작부터 몇 분째인지 (points의 절반 개수)
    public int[] modes;     // 점마다 다음 점까지 어떻게 갔는지: 0 걷기 · 1 택시 · 2 배 · 3 지하철 · 4 버스 (없으면 다 걷기)

    public const int WALK = 0;
    public const int RIDE = 1;
    public const int BOAT = 2;
    public const int SUBWAY = 3;
    public const int BUS = 4;

    // 차·지하철처럼 "타고 간" 이동인지 (걷기·배가 아닌 것)
    public static bool IsRide(int mode)
    {
        return mode == RIDE || mode == SUBWAY || mode == BUS;
    }

    // i번째 점에서 다음 점까지 어떻게 갔는지 (자료가 없으면 걷기)
    public int ModeAt(int i)
    {
        if (modes == null || i < 0 || i >= modes.Length) return WALK;
        return modes[i];
    }

    // i번째 점의 자리 (x, z)
    public UnityEngine.Vector2 PointAt(int i)
    {
        return new UnityEngine.Vector2(points[i * 2], points[i * 2 + 1]);
    }
}

[Serializable]
public class LandmarkData
{
    public float x;
    public float z;
    public int kind; // 0 N서울타워 · 1 롯데월드타워
}

[Serializable]
public class StopData
{
    public float x; // 다녀온 곳의 자리
    public float z;
}

[Serializable]
public class TreeData
{
    public float x;      // 서 있는 자리
    public float z;
    public float height; // 나무 키(m)
    public int shape;    // 0 둥근 활엽수 · 1 뾰족한 침엽수 · 2 소나무 · 3 길쭉한 나무
    public string color; // 잎 색 "#RRGGBB" (계절·수종으로 웹이 정함)
}

[Serializable]
public class LookData
{
    public string wall;  // 벽 색 "#RRGGBB"
    public string sign;  // 간판·차양 색
    public int pattern;  // 벽 무늬: 0 작은 타일 · 1 벽돌 · 2 미장 · 3 돌판·패널 · 4 유리벽
    public int ground;   // 1층: 0 필로티 주차장 · 1 가게 · 2 벽 + 문 · 3 유리 로비
}

[Serializable]
public class BuildingData
{
    public float[] points; // 건물 바닥 윤곽: x, z, x, z … 순서
    public float height;   // 건물 높이(m)
    public int kind;       // 0 단독주택 · 1 아파트 등 공동주택 · 2 상가 · 3 업무·숙박 · 4 그 밖
    public LookData look;  // 로드뷰를 보고 따로 정한 모습 (없으면 wall이 비어 있음 → 규칙대로 그림)
    public int style;      // 0 보통 · 1 한옥 (익선동·남산골한옥마을의 낮은 건물) · 2 을지로 인쇄 골목 상가
}
