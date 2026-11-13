// 경로 재생: 여행 시각에 맞춰 "걷는 사람" 표시(배 구간에서는 배)를 경로 위로 움직이고, 원하면 카메라가 따라감
//   Space : 재생 / 멈춤
//   F : 따라가기 켜기 / 끄기 (끄면 CameraMove로 자유롭게 움직임)
//   V : 따라가기 중 보는 방법 바꾸기 — 뒤에서 내려다보기 ↔ 내비게이션(사람 눈높이, 배 위에서는 갑판 눈높이)
// 웹에서는 페이지가 Play · Pause · Seek을 불러 조종할 예정
using UnityEngine;

public class RoutePlayer : MonoBehaviour
{
    private const float MINUTES_PER_SECOND = 4f; // 재생 빠르기: 1초에 여행 시간 4분 (8시간 여행 = 2분)
    private const float WALKER_HEIGHT_M = 1.2f;  // 걷는 사람 표시의 가운데 높이
    private const float EYE_M = 1.6f;            // 내비게이션에서 사람 눈높이
    private const float FOLLOW_SMOOTH = 3f;      // 카메라가 따라붙는 빠르기 (클수록 바로 붙음)
    private const float TURN_SMOOTH = 2f;        // 내비게이션에서 고개를 돌리는 빠르기
    private const float STAY_MOVE_M = 30f;       // 두 점 사이를 이보다 적게 움직였으면 "머문 것"
    private const float STAY_LONG_MINUTES = 20f; // 이보다 오래 머문 구간은 빨리 감기
    private const float STAY_NIGHT_MINUTES = 180f; // 이보다 오래(밤잠 등)면 훨씬 빨리
    private const float FAST = 12f;              // 오래 머문 구간 빨리 감기 배수 (점심 80분 → 약 1.7초)
    private const float FASTER = 80f;            // 밤잠 빨리 감기 배수 (14시간 → 약 2.6초)
    private const float BUS_EYE_M = 2.6f;        // 내비게이션에서 버스 안 눈높이
    private const float TOP_TURN_DEGREES = 20f;  // 위에서 보기: 카메라가 1초에 돌 수 있는 최대 각도 (GPS가 흔들려도 빙빙 돌지 않게)
    private const float NAV_TURN_DEGREES = 35f;  // 내비: 1초에 돌 수 있는 최대 각도
    private const float ZOOM_STEP = 1.2f;        // 가까이·멀리 한 번에 거리를 이만큼 곱하거나 나눔
    private const float ZOOM_MIN = 0.3f;         // 가장 가까이 (기본 거리의 30%)
    private const float ZOOM_MAX = 3f;           // 가장 멀리 (기본 거리의 3배)
    private const float ROOF_CLEAR_M = 25f;      // 위에서 보기: 카메라를 둘레 가장 높은 지붕보다 이만큼 위에 (지붕 장식 높이 포함)
    // 따라갈 때 카메라 자리 { 뒤로 물러난 거리, 높이 }: 걷기 · 택시 · 배 · 지하철(땅속이라 높이 올라가 건너뜀) · 버스
    //   "위에서"는 동네가 한눈에 보이게 높이·멀리 (내비 = 사람 눈높이와 확실히 다르게)
    private static readonly Vector2[] FOLLOW_BACK_UP = { new Vector2(90, 75), new Vector2(110, 85), new Vector2(140, 80), new Vector2(220, 170), new Vector2(110, 85) };

    private Ground ground;
    private BuildingFinder buildings; // 카메라 둘레 가장 높은 건물을 물어봄
    private BridgeMaker bridges;      // 다리를 건널 때 다리 위 높이를 물어봄 (없을 수 있음)
    private RouteData route;
    private float waterLevel;
    private Transform walker;   // 걷는 사람 표시
    private Transform boat;     // 배 (배 구간이 없으면 null)
    private Transform bus;      // 버스 (버스 구간이 없으면 null)
    private Transform taxi;     // 택시 (택시 구간이 없으면 null)
    private SkyClock skyClock;  // 시각에 맞춰 하늘·밤 불빛을 바꿈 (없을 수 있음)
    private int firstBoatPoint = -1; // 배를 처음 탄 점 번호
    private int lastBoatPoint = -1;  // 배에서 내린 점 번호
    private float minute;       // 지금 재생 중인 시각 (여행 시작부터 몇 분)
    private int mode;           // 지금 어떻게 움직이는 중인지 (RouteData.WALK · RIDE(택시) · BOAT · SUBWAY · BUS)
    private bool isPlaying;
    private bool isFollowing;
    private bool isNavigation;  // 따라가기 중 사람 눈높이로 보기
    private int startClock;     // 경로 0분이 첫날 몇 시 몇 분인지 (화면 시계용)
    private int segment;        // 지금 몇 번째 점과 다음 점 사이인지
    private float cameraYaw;    // 카메라가 바라보는 방향(도). 가는 쪽으로 천천히 따라 돎
    private float zoom = 1;     // 위에서 보기 거리 배수 (1 = 기본)
    private bool isWebDriven;   // 웹 회고 화면이 재생을 조종하는지 (그러면 화면 시계는 웹이 보여 주니 숨김)

    // 경로를 받아 걷는 사람 표시(와 배)를 출발점에 세움 (RetroMap이 부름)
    public void Show(Ground ground, RouteData route, float waterLevel, int startClock, BuildingFinder buildings, BridgeMaker bridges)
    {
        this.buildings = buildings;
        this.bridges = bridges;
        this.startClock = startClock;
        this.ground = ground;
        this.route = route;
        this.waterLevel = waterLevel;
        skyClock = GetComponent<SkyClock>();
        if (walker != null) DestroyImmediate(walker.gameObject);
        if (boat != null) DestroyImmediate(boat.gameObject);
        if (bus != null) DestroyImmediate(bus.gameObject);
        if (taxi != null) DestroyImmediate(taxi.gameObject);

        GameObject body = GameObject.CreatePrimitive(PrimitiveType.Capsule); // 유니티 기본 알약 모양 (키 2m)
        body.name = "Walker";
        body.transform.SetParent(transform, false);
        body.transform.localScale = new Vector3(1.2f, WALKER_HEIGHT_M, 1.2f);
        body.GetComponent<MeshRenderer>().sharedMaterial = Palette.Get("Pin");
        walker = body.transform;
        MakeBoat();
        bus = MakeVehicle("Bus", RouteData.BUS, StreetProps.BuildBus());
        taxi = MakeVehicle("Taxi", RouteData.RIDE, StreetProps.BuildTaxi());
        Seek(0);
        LookAtStart();
        cameraYaw = walker.eulerAngles.y;
        ShowSubwayLines(!isNavigation); // 새 지도도 지금 시점(내비면 숨김)에 맞춤
    }

    // 처음 열었을 때 카메라가 출발점을 비스듬히 내려다보게 (여행이 어디서 시작하는지 바로 보이게)
    private void LookAtStart()
    {
        if (Camera.main == null) return;
        Camera.main.transform.position = walker.position + new Vector3(-220, 180, -260);
        Camera.main.transform.LookAt(walker.position);
    }

    // 배를 탄 구간이 있으면 배 한 척을 만듦
    private void MakeBoat()
    {
        firstBoatPoint = -1;
        for (int i = 0; i < route.minutes.Length - 1; i++)
        {
            if (route.ModeAt(i) != RouteData.BOAT) continue;
            if (firstBoatPoint < 0) firstBoatPoint = i;
            lastBoatPoint = i + 1;
        }
        if (firstBoatPoint < 0) return;

        GameObject item = new GameObject("Boat");
        item.transform.SetParent(transform, false);
        item.AddComponent<MeshFilter>().sharedMesh = BoatMaker.BuildBoat();
        item.AddComponent<MeshRenderer>().sharedMaterial = Palette.Get("Props");
        boat = item.transform;
    }

    // 경로에 그 이동 방법(mode) 구간이 있으면 탈것 하나를 만듦 (없으면 null)
    private Transform MakeVehicle(string vehicleName, int vehicleMode, Mesh mesh)
    {
        bool hasMode = false;
        for (int i = 0; i < route.minutes.Length - 1; i++)
        {
            if (route.ModeAt(i) == vehicleMode) hasMode = true;
        }
        if (!hasMode) return null;
        GameObject item = new GameObject(vehicleName);
        item.transform.SetParent(transform, false);
        item.AddComponent<MeshFilter>().sharedMesh = mesh;
        item.AddComponent<MeshRenderer>().sharedMaterial = Palette.Get("Props");
        return item.transform;
    }

    public void Play() { isPlaying = true; }

    // ---------- 웹 회고 화면이 부름 (재생은 웹이 하고, 3D는 웹이 정한 시각·시점을 그대로 보여 줌)

    // 웹의 재생 시각으로 옮김. 첫날 0시부터 몇 분째인지 (예: 1일차 11:30 = 690, 2일차 12:10 = 1440 + 730)
    public void SeekClock(float minutesFromFirstMidnight)
    {
        isPlaying = false; // 3D 혼자 재생하지 않음 (속도는 웹의 배속 버튼이 정함)
        isWebDriven = true;
        Seek(minutesFromFirstMidnight - startClock);
    }

    // 웹의 [위에서 / 내비] 버튼: "top" = 뒤쪽 위에서 따라감, "street" = 사람 눈높이 내비게이션
    public void SetView(string view)
    {
        isNavigation = view == "street";
        SetFollowing(true);
        ShowSubwayLines(!isNavigation);
    }

    // 지하철 무지개 선 보이기·숨기기: 내비(사람 눈높이)에서는 머리 위로 지나가는 큰 선이 거슬려서 숨김
    private void ShowSubwayLines(bool isOn)
    {
        Transform lines = transform.Find("RouteLines");
        if (lines == null) return;
        foreach (Transform line in lines)
        {
            if (line.name == "SubwayLine") line.gameObject.SetActive(isOn);
        }
    }

    // 웹의 [＋ / －] 버튼·마우스 휠: 1 = 가까이, -1 = 멀리 (위에서 보기 거리만 바뀜)
    public void Zoom(float direction)
    {
        zoom = Mathf.Clamp(zoom * Mathf.Pow(ZOOM_STEP, -direction), ZOOM_MIN, ZOOM_MAX); // Pow(1.2, -1) = 1 / 1.2 → 가까이
    }

    // 웹의 [따라가기] 체크: 1 = 켬, 0 = 끔 (끄면 마우스·키보드로 자유롭게 둘러봄)
    //   웹(SendMessage)이 숫자를 넘기면 float로 오므로 float로 받음
    public void SetFollow(float isOn)
    {
        SetFollowing(isOn > 0.5f);
    }

    public void Pause() { isPlaying = false; }

    // 재생 시각을 옮기고 걷는 사람(과 배)을 그 자리에 놓음
    public void Seek(float toMinute)
    {
        minute = Mathf.Clamp(toMinute, 0, route.minutes[route.minutes.Length - 1]);
        PlaceWalker();
        if (skyClock != null) skyClock.SetMinute(minute);
    }

    private void Update()
    {
        if (route == null) return;
        if (Input.GetKeyDown(KeyCode.Space)) isPlaying = !isPlaying;
        if (Input.GetKeyDown(KeyCode.F)) SetFollowing(!isFollowing);
        if (Input.GetKeyDown(KeyCode.V))
        {
            isNavigation = !isNavigation;
            if (!isFollowing) SetFollowing(true);
            ShowSubwayLines(!isNavigation);
        }

        if (isPlaying)
        {
            Seek(minute + Time.deltaTime * MINUTES_PER_SECOND * SpeedHere());
            if (minute >= route.minutes[route.minutes.Length - 1]) isPlaying = false; // 끝까지 가면 멈춤
        }
        if (!isFollowing) return;
        if (!isNavigation && Input.mouseScrollDelta.y != 0) Zoom(Mathf.Sign(Input.mouseScrollDelta.y)); // 휠을 위로 = 가까이
        if (isNavigation && mode != RouteData.SUBWAY) LookLikeNavigation(); // 지하철은 땅속이라 위에서 봄
        else FollowFromBehind();
    }

    // 지금 구간의 재생 배수: 한자리에 오래 머문 구간(식사·밤잠)은 빨리 감음
    private float SpeedHere()
    {
        float span = route.minutes[segment + 1] - route.minutes[segment];
        bool isStaying = Vector2.Distance(route.PointAt(segment), route.PointAt(segment + 1)) < STAY_MOVE_M;
        if (!isStaying || span < STAY_LONG_MINUTES) return 1;
        return span >= STAY_NIGHT_MINUTES ? FASTER : FAST;
    }

    // 화면 왼쪽 위 시계: "DAY 1  17:15" (OnGUI: 유니티가 화면에 글자를 그릴 때 부르는 함수)
    private void OnGUI()
    {
        if (route == null || isWebDriven) return;
        int clock = startClock + Mathf.FloorToInt(minute);
        int day = clock / (24 * 60) + 1;
        int hour = clock % (24 * 60) / 60;
        string text = "DAY " + day + "  " + hour.ToString("00") + ":" + (clock % 60).ToString("00"); // 영어로: 웹(WebGL) 기본 글꼴에 한글이 없어서
        GUIStyle style = new GUIStyle(GUI.skin.label) { fontSize = 28, fontStyle = FontStyle.Bold };
        style.normal.textColor = Color.white;
        GUI.Label(new Rect(24, 18, 400, 50), text, style);
    }

    // 따라가기를 켜면 자유 이동(CameraMove)을 끄고, 끄면 다시 켬
    private void SetFollowing(bool isOn)
    {
        isFollowing = isOn;
        CameraMove free = Camera.main.GetComponent<CameraMove>();
        if (free != null) free.enabled = !isOn;
    }

    // 지금 시각이 경로의 몇 번째 점과 다음 점 사이인지 찾아, 그 사이를 시간 비율만큼 간 자리에 놓음
    // 예) 10분 점과 14분 점 사이에서 지금이 11분이면 → 두 점 사이를 25% 간 자리
    private void PlaceWalker()
    {
        int i = 0;
        while (i < route.minutes.Length - 2 && route.minutes[i + 1] <= minute) i++;

        Vector2 from = route.PointAt(i);
        Vector2 to = route.PointAt(i + 1);
        float span = route.minutes[i + 1] - route.minutes[i];
        Vector2 at = Vector2.Lerp(from, to, span > 0 ? (minute - route.minutes[i]) / span : 0);
        mode = route.ModeAt(i);
        segment = i;

        Vector2 heading = to - from;
        walker.position = new Vector3(at.x, SurfaceAt(at, heading) + WALKER_HEIGHT_M, at.y);
        if (heading.magnitude > 1) walker.rotation = Quaternion.LookRotation(new Vector3(heading.x, 0, heading.y)); // 가는 쪽을 바라봄 (머무는 중이면 그대로)
        walker.gameObject.SetActive(mode == RouteData.WALK); // 배·버스·택시 안이나 땅속(지하철)에서는 사람 표시를 숨김
        PlaceBoat(i, at, heading);
        PlaceVehicle(bus, RouteData.BUS, at, heading);
        PlaceVehicle(taxi, RouteData.RIDE, at, heading);
    }

    // 배: 배 구간이면 지금 자리로, 아니면 타기 전엔 타는 곳에, 내린 뒤엔 내린 곳에 세워 둠
    private void PlaceBoat(int i, Vector2 at, Vector2 heading)
    {
        if (boat == null) return;
        if (mode != RouteData.BOAT)
        {
            bool isBefore = i < firstBoatPoint;
            int onWater = isBefore ? firstBoatPoint + 1 : lastBoatPoint - 1; // 강가 점 바로 옆의 물 위 점
            int beside = isBefore ? onWater + 1 : onWater - 1;               // 그 옆 점 (배가 향할 쪽을 정하려고)
            at = route.PointAt(onWater);
            heading = isBefore ? route.PointAt(beside) - at : at - route.PointAt(beside);
        }
        boat.position = new Vector3(at.x, waterLevel, at.y);
        if (heading.magnitude > 1) boat.rotation = Quaternion.Euler(0, StreetProps.YawOf(heading), 0); // 배 모양은 뱃머리가 +x라 이 각도로 돌림
    }

    // 버스·택시: 그 구간을 지날 때만 지금 자리에 보이고, 아니면 숨김
    private void PlaceVehicle(Transform vehicle, int vehicleMode, Vector2 at, Vector2 heading)
    {
        if (vehicle == null) return;
        bool isRiding = mode == vehicleMode;
        vehicle.gameObject.SetActive(isRiding);
        if (!isRiding) return;
        vehicle.position = new Vector3(at.x, SurfaceAt(at, heading) + 0.3f, at.y);
        if (heading.magnitude > 1) vehicle.rotation = Quaternion.Euler(0, StreetProps.YawOf(heading), 0); // 모양은 앞이 +x
    }

    // 땅 높이. 그 자리를 같은 방향으로 건너는 다리가 있으면 다리 위 높이
    private float SurfaceAt(Vector2 at, Vector2 heading)
    {
        if (bridges != null && bridges.TryDeckHeight(at, heading, out float deck)) return deck;
        return ground.HeightAt(at.x, at.y);
    }

    // 뒤쪽 위에서 부드럽게 따라가며 내려다봄 (탈것 구간은 훨씬 높이 올라가 동네를 건너뜀)
    private void FollowFromBehind()
    {
        Transform eye = Camera.main.transform;
        Transform target = mode == RouteData.BOAT ? boat : walker;
        Vector3 forward = mode == RouteData.BOAT ? boat.right : walker.forward; // 배는 뱃머리가 +x(right) 쪽
        forward = SmoothForward(forward, TOP_TURN_DEGREES);
        Vector2 backUp = FOLLOW_BACK_UP[mode] * zoom;
        Vector3 wanted = target.position - forward * backUp.x + Vector3.up * backUp.y;
        float roof = ground.HeightAt(wanted.x, wanted.z) + buildings.TallestNear(wanted.x, wanted.z);
        wanted.y = Mathf.Max(wanted.y, roof + ROOF_CLEAR_M); // 카메라가 언덕·높은 건물 속으로 들어가지 않게
        eye.position = Vector3.Lerp(eye.position, wanted, Time.deltaTime * FOLLOW_SMOOTH);
        eye.rotation = Quaternion.Slerp(eye.rotation, Quaternion.LookRotation(target.position + Vector3.up * 2 - eye.position), Time.deltaTime * FOLLOW_SMOOTH * 2);
    }

    // 내비게이션: 걷는 중이면 사람 눈높이, 배 위면 위층 갑판 눈높이에서 가는 쪽을 봄
    private void LookLikeNavigation()
    {
        Transform eye = Camera.main.transform;
        bool isOnBoat = mode == RouteData.BOAT;
        Vector3 forward = SmoothForward(isOnBoat ? boat.right : walker.forward, NAV_TURN_DEGREES);
        Vector3 wanted = isOnBoat
            ? boat.position + Vector3.up * BoatMaker.DECK_EYE_M + forward * (BoatMaker.LENGTH_M * 0.35f) // 앞쪽 창가 (뒤에 앉으면 배 앞부분에 가려서)
            : new Vector3(walker.position.x, walker.position.y - WALKER_HEIGHT_M + (mode == RouteData.BUS ? BUS_EYE_M : EYE_M), walker.position.z); // 버스는 조금 높게
        eye.position = Vector3.Lerp(eye.position, wanted, Time.deltaTime * FOLLOW_SMOOTH * 2);
        eye.rotation = Quaternion.Slerp(eye.rotation, Quaternion.LookRotation(forward), Time.deltaTime * TURN_SMOOTH);
    }

    // 카메라 방향을 가는 쪽(heading)으로 1초에 maxTurn도까지만 돌림 → 머문 곳에서 GPS가 흔들려도 화면이 빙빙 돌지 않음
    private Vector3 SmoothForward(Vector3 heading, float maxTurn)
    {
        heading.y = 0;
        if (heading.sqrMagnitude > 0.01f)
        {
            float wanted = Mathf.Atan2(heading.x, heading.z) * Mathf.Rad2Deg; // 가는 쪽 각도 (북 = 0도)
            cameraYaw = Mathf.MoveTowardsAngle(cameraYaw, wanted, maxTurn * Time.deltaTime); // 359도→1도처럼 한 바퀴 넘어도 짧은 쪽으로
        }
        return Quaternion.Euler(0, cameraYaw, 0) * Vector3.forward;
    }
}
