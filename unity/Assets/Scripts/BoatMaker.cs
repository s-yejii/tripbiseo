// 한강버스(쌍동선 여객선) 모양과 선착장. 색칠한 상자를 쌓아 만듦 (PropMaker)
// 배 모양은 배의 가운데·물 높이를 (0, 0, 0)으로, 뱃머리가 +x 쪽. 움직이는 건 RoutePlayer가 함
using UnityEngine;

public static class BoatMaker
{
    public const float LENGTH_M = 35f;    // 배 길이 (한강버스 약 35m, 폭 약 9.6m)
    public const float DECK_EYE_M = 4.9f; // 배 안에서 창밖을 보는 눈높이 (물에서). 안에서는 벽이 안 그려져서 창밖처럼 보임
    private const float PIER_GAP_M = 11f; // 배 가운데에서 선착장 판 가운데까지 (배 폭 절반 4.8 + 판 폭 절반 5.5 + 틈)
    private const float HALF_WIDTH_M = 4.8f;

    // 색: 서울시가 공개한 한강버스 디자인 그림을 보고 잡음 (뒤는 짙은 파랑, 앞으로 갈수록 흰색, 위는 흰·은색, 긴 검은 창 띠, 주황 구명뗏목)
    private static readonly Color BLUE = new Color(0.11f, 0.20f, 0.86f);
    private static readonly Color PALE_BLUE = new Color(0.62f, 0.68f, 0.95f);
    private static readonly Color WHITE = new Color(0.94f, 0.95f, 0.97f);
    private static readonly Color SILVER = new Color(0.80f, 0.82f, 0.85f);
    private static readonly Color WINDOW = new Color(0.08f, 0.10f, 0.14f);
    private static readonly Color ORANGE = new Color(0.96f, 0.42f, 0.10f);
    private static readonly Color RAIL = new Color(0.70f, 0.72f, 0.74f);

    // 배 한 척 (물체 하나로 만들어 위치·방향만 바꿔 가며 움직임)
    //   옆에서 본 모양을 옆으로 밀어 층층이 쌓음: 선체 두 개 → 몸통 → 객실(창 띠) → 지붕 → 조타실
    //   뱃머리는 위로 갈수록 앞으로 나온 모양(비스듬)
    public static Mesh BuildBoat()
    {
        PropMaker boat = new PropMaker();
        for (int side = -1; side <= 1; side += 2) // 양쪽 선체 (쌍동선). 물에 잠긴 아래쪽
        {
            float z = side * 3.6f;
            boat.AddSideProfile(Points(-17.3f, -0.8f, 14.5f, -0.8f, 17.2f, 1.5f, -17.3f, 1.5f), z - 1.2f, z + 1.2f, BLUE, PALE_BLUE);
        }
        boat.AddSideProfile(Points(-17.5f, 1.4f, 15.2f, 1.4f, 17.8f, 3.5f, -17.5f, 3.5f), -HALF_WIDTH_M, HALF_WIDTH_M, BLUE, WHITE); // 몸통
        boat.AddSideProfile(Points(-14.5f, 3.5f, 11.5f, 3.5f, 14f, 5.7f, -14.5f, 5.7f), -4.5f, 4.5f, WHITE, WHITE);                 // 객실
        boat.AddSideProfile(Points(-13.5f, 4.0f, 11f, 4.0f, 12.8f, 5.3f, -13.5f, 5.3f), -4.56f, 4.56f, WINDOW, WINDOW);           // 긴 창 띠
        boat.AddSideProfile(Points(-17f, 5.7f, 13.5f, 5.7f, 15.5f, 6.1f, -17f, 6.1f), -HALF_WIDTH_M, HALF_WIDTH_M, SILVER, WHITE); // 지붕 (뒤로 길게)
        boat.AddSideProfile(Points(1.5f, 6.1f, 10.8f, 6.1f, 9.2f, 7.7f, 1.5f, 7.7f), -3f, 3f, WHITE, WHITE);                        // 조타실 (앞 유리가 뒤로 기움)
        boat.AddSideProfile(Points(2.5f, 6.7f, 10.3f, 6.7f, 9.6f, 7.4f, 2.5f, 7.4f), -3.05f, 3.05f, WINDOW, WINDOW);               // 조타실 창
        boat.AddBox(new Vector3(7.5f, 9.0f, 0), new Vector3(0.2f, 2.6f, 0.2f), 0, RAIL);    // 돛대
        boat.AddBox(new Vector3(7.5f, 10.1f, 0.35f), new Vector3(0.1f, 0.5f, 0.6f), 0, ORANGE); // 깃발
        for (int side = -1; side <= 1; side += 2)
        {
            float z = side * 3.4f;
            boat.AddBox(new Vector3(-13.5f, 6.5f, z), new Vector3(1.8f, 0.7f, 1.1f), 0, ORANGE);       // 지붕 위 구명뗏목
            boat.AddBox(new Vector3(-11.3f, 6.5f, z), new Vector3(1.8f, 0.7f, 1.1f), 0, ORANGE);
            boat.AddBox(new Vector3(-16f, 4.1f, side * 4.6f), new Vector3(2.8f, 0.07f, 0.07f), 0, RAIL); // 뒤 갑판 난간
            boat.AddBox(new Vector3(-16f, 3.8f, side * 4.6f), new Vector3(2.8f, 0.07f, 0.07f), 0, RAIL);
            boat.AddBox(new Vector3(0, 3.0f, side * 4.82f), new Vector3(30, 0.12f, 0.05f), 0, WHITE);  // 옆면 흰 줄
        }
        return boat.BuildMesh();
    }

    // 숫자 짝(x, y …)을 점 목록으로 (옆모습 점을 짧게 적으려고)
    private static Vector2[] Points(params float[] numbers) // params: 숫자를 쉼표로 여러 개 줄 수 있게 함
    {
        Vector2[] points = new Vector2[numbers.Length / 2];
        for (int i = 0; i < points.Length; i++) points[i] = new Vector2(numbers[i * 2], numbers[i * 2 + 1]);
        return points;
    }

    // 선착장 하나 (물에 뜬 넓은 판 + 지붕 + 땅으로 가는 다리). waterPoint: 배가 대는 물 위 자리, landPoint: 강가 쪽 자리
    public static void AddPier(PropMaker props, Ground ground, Vector2 waterPoint, Vector2 landPoint, float level)
    {
        Vector2 toLand = landPoint - waterPoint;
        if (toLand.magnitude < 1) toLand = Vector2.up;
        Vector2 along = new Vector2(-toLand.y, toLand.x).normalized; // 강가를 따라가는 방향 (배가 나란히 댐)
        float yaw = StreetProps.YawOf(along);
        Vector3 deck = new Vector3(waterPoint.x, level + 0.5f, waterPoint.y) + new Vector3(toLand.x, 0, toLand.y).normalized * PIER_GAP_M;
        props.AddBox(deck, new Vector3(40, 1.2f, 11), yaw, new Color(0.80f, 0.80f, 0.78f));                   // 물에 뜬 판
        props.AddBox(deck + Vector3.up * 3.6f, new Vector3(30, 0.3f, 8), yaw, new Color(0.18f, 0.42f, 0.70f)); // 대합실 지붕
        props.AddBox(deck + Vector3.up * 2.0f, new Vector3(26, 2.8f, 6), yaw, new Color(0.55f, 0.70f, 0.78f)); // 유리 대합실

        // 땅으로 가는 다리: 판 가운데에서 강가 쪽 땅까지 비스듬히
        Vector3 bridgeFrom = deck + Vector3.up * 0.6f;
        Vector3 bridgeTo = new Vector3(landPoint.x, ground.HeightAt(landPoint.x, landPoint.y) + 0.3f, landPoint.y);
        Vector3 middle = (bridgeFrom + bridgeTo) / 2;
        Vector2 flat = new Vector2(bridgeTo.x - bridgeFrom.x, bridgeTo.z - bridgeFrom.z);
        props.AddBox(middle, new Vector3(flat.magnitude, 0.4f, 3), StreetProps.YawOf(flat), new Color(0.66f, 0.66f, 0.64f));
    }
}
