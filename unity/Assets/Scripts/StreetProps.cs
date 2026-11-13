// 길가 소품: 주차된 차 · 신호등 · 볼라드 (색칠한 상자로 만듦. RoadMaker가 부름)
using UnityEngine;

public static class StreetProps
{
    private static readonly Color[] CAR_COLORS =
    {
        new Color(0.92f, 0.92f, 0.92f), new Color(0.72f, 0.73f, 0.75f), new Color(0.12f, 0.12f, 0.13f), // 흰색 · 은색 · 검정
        new Color(0.42f, 0.43f, 0.45f), new Color(0.16f, 0.22f, 0.36f), new Color(0.48f, 0.12f, 0.12f), // 회색 · 남색 · 자주
    };
    private static readonly Color CAR_GLASS = new Color(0.14f, 0.17f, 0.21f);
    private static readonly Color TIRE = new Color(0.08f, 0.08f, 0.08f);
    private static readonly Color LAMP_FRONT = new Color(0.95f, 0.95f, 0.88f);
    private static readonly Color LAMP_BACK = new Color(0.75f, 0.08f, 0.08f);
    private static readonly Color POLE = new Color(0.36f, 0.37f, 0.38f);
    private static readonly Color SIGNAL_BODY = new Color(0.10f, 0.10f, 0.11f);
    private static readonly Color[] SIGNAL_LIGHTS = { new Color(0.95f, 0.15f, 0.12f), new Color(0.98f, 0.72f, 0.10f), new Color(0.10f, 0.85f, 0.35f) }; // 빨강 · 노랑 · 초록

    // 방향(x, z) → PropMaker.AddBox에 넣을 각도(도). 상자의 가로가 이 방향을 향하게 됨
    public static float YawOf(Vector2 direction)
    {
        return Mathf.Atan2(-direction.y, direction.x) * Mathf.Rad2Deg;
    }

    // 주차된 차 한 대. foot: 차 바닥 가운데(땅 높이), direction: 차가 향한 방향, seed: 색을 고를 번호
    public static void AddCar(PropMaker props, Vector3 foot, Vector2 direction, int seed)
    {
        AddCar(props, foot, direction, seed, CAR_COLORS[Mathf.Abs(seed) % CAR_COLORS.Length]);
    }

    // 차 한 대 (몸통 색을 직접 정함. 택시 = 주황)
    public static void AddCar(PropMaker props, Vector3 foot, Vector2 direction, int seed, Color body)
    {
        Vector2 front = direction.normalized;
        Vector3 along = new Vector3(front.x, 0, front.y);   // 차 앞쪽
        Vector3 side = new Vector3(-front.y, 0, front.x);   // 차 옆쪽
        float yaw = YawOf(front);

        bool isSuv = Mathf.Abs(seed) % 3 == 0; // 셋 중 하나는 SUV (지붕이 높고 뒤가 각짐)
        // 옆모습(앞이 +x, 위가 +y): 몸통은 범퍼 → 보닛 → 트렁크, 유리는 앞 유리가 뒤로 누운 사다리꼴
        Vector2[] bodyShape = isSuv ? Points(-2.3f, 0.32f, 2.3f, 0.32f, 2.35f, 0.85f, 1.3f, 1.1f, -2.3f, 1.15f, -2.38f, 0.8f)
                                    : Points(-2.2f, 0.3f, 2.2f, 0.3f, 2.27f, 0.72f, 1.0f, 0.98f, -2.0f, 1.0f, -2.25f, 0.72f);
        Vector2[] glassShape = isSuv ? Points(1.3f, 1.1f, -2.25f, 1.15f, -2.2f, 1.75f, 0.6f, 1.75f)
                                     : Points(1.0f, 0.98f, -1.75f, 1.0f, -1.35f, 1.45f, 0.15f, 1.45f);
        props.AddSideProfile(bodyShape, -0.9f, 0.9f, body, foot, yaw);        // 몸통
        props.AddSideProfile(glassShape, -0.78f, 0.78f, CAR_GLASS, foot, yaw); // 유리 (차 안이 어둡게 보임)
        props.AddBox(foot + Vector3.up * (isSuv ? 1.78f : 1.47f) - along * (isSuv ? 0.8f : 0.6f), new Vector3(isSuv ? 2.8f : 1.5f, 0.06f, 1.5f), yaw, body); // 지붕
        props.AddBox(foot + Vector3.up * 0.62f + along * 2.25f, new Vector3(0.1f, 0.14f, 1.5f), yaw, LAMP_FRONT); // 전조등 줄
        props.AddBox(foot + Vector3.up * 0.82f - along * 2.27f, new Vector3(0.1f, 0.12f, 1.5f), yaw, LAMP_BACK);  // 후미등 줄
        for (int i = 0; i < 4; i++) // 바퀴 4개: 앞뒤(±1.4m) × 양옆(±0.82m)
        {
            float forward = i < 2 ? 1.4f : -1.4f;
            float aside = i % 2 == 0 ? 0.82f : -0.82f;
            props.AddBox(foot + Vector3.up * 0.33f + along * forward + side * aside, new Vector3(0.66f, 0.66f, 0.26f), yaw, TIRE);
        }
    }

    // 숫자 짝(x, y …)을 점 목록으로
    private static Vector2[] Points(params float[] numbers) // params: 숫자를 쉼표로 여러 개 줄 수 있게 함
    {
        Vector2[] points = new Vector2[numbers.Length / 2];
        for (int i = 0; i < points.Length; i++) points[i] = new Vector2(numbers[i * 2], numbers[i * 2 + 1]);
        return points;
    }

    // 재생할 때 우리가 탄 택시 (주황 세단 + 지붕 위 택시 표시등). 앞이 +x
    public static Mesh BuildTaxi()
    {
        PropMaker taxi = new PropMaker();
        AddCar(taxi, Vector3.zero, Vector2.right, 1, new Color(0.95f, 0.58f, 0.12f));
        taxi.AddBox(new Vector3(-0.6f, 1.62f, 0), new Vector3(0.6f, 0.22f, 0.3f), 0, new Color(0.98f, 0.95f, 0.85f)); // 지붕 표시등
        return taxi.BuildMesh();
    }

    // 재생할 때 우리가 탄 버스 (초록 몸통 + 흰 지붕 띠 + 긴 창 띠). 앞이 +x, 길이 11m
    public static Mesh BuildBus()
    {
        PropMaker bus = new PropMaker();
        Color green = new Color(0.16f, 0.62f, 0.34f);
        bus.AddBox(new Vector3(0, 1.75f, 0), new Vector3(11, 2.7f, 2.5f), 0, green);                                    // 몸통
        bus.AddBox(new Vector3(0, 2.25f, 0), new Vector3(10.6f, 1.0f, 2.54f), 0, CAR_GLASS);                           // 옆 창 띠
        bus.AddBox(new Vector3(5.47f, 2.0f, 0), new Vector3(0.1f, 1.6f, 2.2f), 0, CAR_GLASS);                          // 앞 유리
        bus.AddBox(new Vector3(0, 3.15f, 0), new Vector3(10.4f, 0.12f, 2.3f), 0, new Color(0.92f, 0.92f, 0.90f));      // 흰 지붕 띠
        for (int i = 0; i < 4; i++) // 바퀴: 앞뒤(±3.6m) × 양옆
        {
            float forward = i < 2 ? 3.6f : -3.6f;
            float aside = i % 2 == 0 ? 1.15f : -1.15f;
            bus.AddBox(new Vector3(forward, 0.45f, aside), new Vector3(0.95f, 0.95f, 0.3f), 0, TIRE);
        }
        return bus.BuildMesh();
    }

    // 신호등 하나: 인도 위 기둥 + 찻길 위로 뻗은 팔 + 끝에 가로로 긴 신호기
    // foot: 기둥 발밑, toRoad: 기둥에서 찻길 가운데 쪽 방향, armLength: 팔 길이
    public static void AddTrafficLight(PropMaker props, Vector3 foot, Vector2 toRoad, float armLength)
    {
        Vector2 inward = toRoad.normalized;
        Vector3 reach = new Vector3(inward.x, 0, inward.y);
        Vector3 facing = new Vector3(inward.y, 0, -inward.x); // 신호기가 바라보는 쪽 (팔과 직각 = 차가 오는 쪽)
        float yaw = YawOf(inward);

        props.AddBox(foot + Vector3.up * 3f, new Vector3(0.24f, 6f, 0.24f), 0, POLE);                                 // 기둥
        props.AddBox(foot + Vector3.up * 5.85f + reach * armLength / 2, new Vector3(armLength, 0.16f, 0.16f), yaw, POLE); // 팔
        Vector3 head = foot + Vector3.up * 5.5f + reach * armLength;
        props.AddBox(head, new Vector3(1.3f, 0.42f, 0.34f), yaw, SIGNAL_BODY);                                       // 신호기 몸통
        for (int i = 0; i < SIGNAL_LIGHTS.Length; i++) // 등 3개를 신호기 앞면에 가로로
        {
            props.AddBox(head + reach * (i - 1) * 0.4f + facing * 0.18f, new Vector3(0.28f, 0.28f, 0.04f), yaw, SIGNAL_LIGHTS[i]);
        }
    }

    // 볼라드(차가 인도로 못 들어오게 막는 짧은 기둥)
    public static void AddBollard(PropMaker props, Vector3 foot)
    {
        props.AddBox(foot + Vector3.up * 0.42f, new Vector3(0.2f, 0.84f, 0.2f), 0, POLE);
    }
}
