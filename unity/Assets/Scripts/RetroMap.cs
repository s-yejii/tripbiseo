// 3D 회고 지도의 시작점: 지도 자료(JSON)를 받아 땅·길·건물·나무·걸은 경로를 화면에 세움
// 웹에서는 페이지가 LoadJson을 불러 자료를 넘기고, 유니티 편집기에서는 시험용 파일을 읽음
// 재질(색·질감)은 Palette.Get("이름")으로 가져옴 (Palette.cs)
using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using UnityEngine;
using UnityEngine.Networking;
using UnityEngine.Rendering;
using UnityEngine.Rendering.PostProcessing;
#if UNITY_EDITOR
using UnityEditor;
#endif

public class RetroMap : MonoBehaviour
{
    private const int KIND_COUNT = 5;             // 건물 종류 수 (MapData.cs의 kind)
    private const int VARIANT_COUNT = 3;          // 종류마다 벽 색 가짓수
    private const float VILLA_MAX_M = 16f;        // 공동주택이라도 이 높이(5층쯤) 이하면 아파트가 아니라 빌라(다세대)로 그림
    private const float SHADOW_DISTANCE_M = 2500; // 이 거리까지 그림자를 그림 (멀리서 내려다봐도 그림자가 보이게)
    private const float FAR_GROUND_M = 30000;     // 지도 바깥에 까는 넓은 바닥 한 변
    private const float ROAD_LIFT_M = 0.4f;       // 길을 땅에서 띄우는 높이
    private const float ROUTE_LIFT_M = 0.75f;     // 걸은 경로 선을 땅에서 띄우는 높이 (길·인도보다 조금 위)
    private const float DECK_CHECK_STEP_M = 8f;   // 경로가 다리 위인지 이 간격마다 확인
    private const float VEHICLE_LINE_UP_M = 0.05f; // 버스·택시 띠는 걷기 띠보다 이만큼 위 (같은 높이면 겹친 곳이 번갈아 보여 들쭉날쭉)
    private const float VEHICLE_LINE_WIDTH_M = 2.4f; // 버스·택시 구간 띠 폭
    private const float ROUTE_WIDTH_M = 1.6f;     // 걸은 경로 선 폭
    private const float PIN_HEIGHT_M = 16f;       // 다녀온 곳 핀 높이
    private const float BOAT_LINE_LIFT_M = 0.3f;  // 배가 지나간 물길 선을 물에서 띄우는 높이
    private const float BOAT_LINE_WIDTH_M = 1.2f; // 물길 선 폭
    private const float RIDE_LINE_WIDTH_M = 3f;   // 지하철·택시 구간 선 폭
    private const float RIDE_ARC_RATIO = 0.08f;   // 탈것 구간 선을 하늘로 휘게 하는 높이 = 구간 길이 × 이 값 (예: 4km → 320m … 아래 최대값까지)
    private const float RIDE_ARC_MAX_M = 160f;
    private const float RIPPLE_FLOW = 0.015f;     // 잔물결이 1초에 흘러가는 양 (그림 한 장 = 1)
    private const float UPPER_LIT_CHANCE = 0.55f; // 밤에 2층 위 창이 켜져 있을 확률
    private const float SHOP_LIT_CHANCE = 0.85f;  // 밤에 1층 가게가 켜져 있을 확률
    private const float GRAIN_M = 6f;             // 땅에 덧입히는 잔무늬 한 장의 크기
    private const float ASPHALT_TILE_M = 4f;      // 아스팔트 그림 한 장이 덮는 바닥 크기(m)
    private const float BLOCK_TILE_M = 1.2f;      // 보도블록·산책로 그림 한 장 (30cm 블록 4개)
    private const float ASPHALT_PHOTO_M = 3f;     // 아스팔트 사진 한 장이 덮는 바닥(m)
    private const float PAVING_PHOTO_M = 2f;      // 보도블록 사진 한 장
    private const float WINDOW_PARALLAX = 0.02f;  // 창이 벽 안으로 들어가 보이는 정도 (그림 한 장 = 1. 0.02면 약 25cm)
    private const float SURFACE_PER_CELL = 3f;    // 벽 표면 사진을 창 한 칸에 몇 번 되풀이 (한 장 ≈ 1m)
    private const float DETAIL_NEAR_M = 60f;      // 걸은 길에서 이 안의 건물만 발코니·간판·실외기 같은 디테일을 붙임
    private const float TREE_NEAR_M = 150f;       // 걸은 길에서 이 안의 나무는 풍성한 모양으로 (멀리 있는 나무는 가볍게)
    private const float TREE_CLEAR_RATIO = 0.25f; // 나무는 건물 벽에서 "키 × 0.25" 이상 떨어져야 세움 (예: 8m 나무 → 2m). 잎이 벽을 뚫지 않게
    private static readonly string[] ROOF_NAMES = { "RoofGray", "RoofGreen", "RoofDark" }; // 옥상 색 (초록 = 방수 페인트)

    // 편집기에서 ▶ 눌렀을 때 띄울 시험 자료 이름 (StreamingAssets 폴더의 파일 이름, .json 빼고)
    //  sample-namsan: 남산 단풍 하루(걸은 경로 있음) · sample-day1: 시연 1일차 익선동~명동~남산~을지로 · sample-day2: 시연 2일차 강남 스시도 주변
    //  Hierarchy 창에서 RetroMap을 고르면 Inspector 창에서 바꿀 수 있음
    public string sampleName = "sample-trip"; // 1박 2일 전체 (sample-day1·sample-day2·sample-namsan도 고를 수 있음)
    public PostProcessResources postResources; // 화면 마무리 효과용 셰이더 모음 (편집기에서 자동으로 채움, PostEffects.cs)
    private const string POST_RESOURCES_PATH = "Packages/com.unity.postprocessing/PostProcessing/PostProcessResources.asset";

    public Ground Ground { get; private set; } // 다른 스크립트가 땅 높이를 물어볼 때 씀
    private Material groundMaterial;           // 땅 재질 (위성 사진을 여기에 입힘)
    private readonly List<Material> nightMaterials = new List<Material>(); // 밤에 창문·간판 불을 켤 벽 재질들 (SkyClock이 켬)
    private Material lampMaterial; // 가로등·다리 조명 (지도마다 새로)
    private BridgeMaker bridges;   // 물 위 다리 (경로가 다리를 건너면 다리 높이를 물어봄)
    private readonly List<Material> streetMaterials = new List<Material>(); // 밤에 가로등 빛처럼 밝아질 길 재질들

    private void Start()
    {
        // 카메라에 이동 조작이 안 붙어 있으면 붙임 (처음 준비를 다시 안 눌러도 돌아다닐 수 있게)
        if (Camera.main != null && Camera.main.GetComponent<CameraMove>() == null) Camera.main.gameObject.AddComponent<CameraMove>();
#if UNITY_EDITOR // 편집기에서 ▶ 눌렀을 때: 시험용 자료 파일을 바로 읽어 그림
        LoadSample();
#elif UNITY_WEBGL // 웹으로 내보냈을 때만 (WebGLInput은 웹 빌드에만 있음)
        gameObject.AddComponent<RenderSaver>(); // 카메라가 가만히 있을 때만 덜 자주 그림 (움직이면 바로 원래대로, 화질은 그대로)
        WebGLInput.captureAllKeyboardInput = false; // 웹 페이지의 다른 입력칸 글자를 유니티가 가로채지 않게 (3D 칸을 눌렀을 때만 키를 받음)
        // 웹(WebGL)에서는 파일을 바로 못 읽어서, 같이 올린 시험용 자료를 주소로 받아 옴 (웹 페이지가 LoadTrip으로 다른 여행을 넘길 수도 있음)
        // 유니티 단독 페이지(/unity3d/index.html)에서만 시연 자료를 바로 그림. 웹 회고 화면 안에서는 웹이 그 여행 자료를 만들어 LoadJson으로 넘김
        if (Application.absoluteURL.Contains("/unity3d/")) StartCoroutine(LoadFromUrl(Application.streamingAssetsPath + "/" + sampleName));
#endif
    }

    // 웹 페이지가 부름: 여행 지도 자료가 있는 주소(확장자 빼고, 예: ".../trips/1722")를 주면 .json과 .jpg를 받아 그림
    //   웹 쪽에서는 unityInstance.SendMessage("RetroMap", "LoadTrip", 주소)
    public void LoadTrip(string baseUrl)
    {
        StartCoroutine(LoadFromUrl(baseUrl));
    }

    // 주소에서 지도 자료(.json)와 바닥 사진(.jpg)을 받아 그림. IEnumerator·yield: 받는 동안 화면이 멈추지 않게 나눠서 실행
    private IEnumerator LoadFromUrl(string baseUrl)
    {
        UnityWebRequest json = UnityWebRequest.Get(baseUrl + ".json");
        yield return json.SendWebRequest(); // 다 받을 때까지 기다림
        if (json.result != UnityWebRequest.Result.Success)
        {
            Debug.LogError("지도 자료를 못 받음: " + json.error);
            yield break;
        }
        LoadJson(json.downloadHandler.text);
        UnityWebRequest photo = UnityWebRequest.Get(baseUrl + ".jpg");
        yield return photo.SendWebRequest();
        if (photo.result == UnityWebRequest.Result.Success) LoadGroundPhoto(photo.downloadHandler.data);
    }

    // 시험용 자료(StreamingAssets/{sampleName}.json + .jpg) 읽어서 그리기
    public void LoadSample()
    {
        string folder = Application.streamingAssetsPath;
        LoadJson(File.ReadAllText(Path.Combine(folder, sampleName + ".json")));
        LoadGroundPhoto(File.ReadAllBytes(Path.Combine(folder, sampleName + ".jpg")));
    }

    // 지도 자료(JSON 글자)를 받아 땅·길·건물을 만듦
    public void LoadJson(string json)
    {
        MapData data = JsonUtility.FromJson<MapData>(json);
        nightMaterials.Clear();
        streetMaterials.Clear();
        lampMaterial = null;
        QualitySettings.shadowDistance = SHADOW_DISTANCE_M;
        SetSkyLight();

        Ground = new Ground(data);
        RouteArea area = new RouteArea(data.route, data.stops); // 걸은 길·다녀온 곳 둘레 (가까운 곳만 자세하게)
        PropMaker props = new PropMaker();          // 간판·발코니·차·신호등·사람 같은 소품을 모으는 곳
        PropMaker bridgeLights = new PropMaker();   // 다리 조명 (밤에 빛남)
        ShowGround(data);
        ShowRoads(data, area, props, bridgeLights);
        ShowBuildings(data.buildings, area, props);
        ShowWater(data);
        AddPiers(data, props);
        BuildingFinder finder = new BuildingFinder(data.buildings); // "여기가 건물 안인가" (나무 자리 고르기) · 둘레 가장 높은 건물 (카메라 높이)
        Show("Props", props.BuildMesh(), new Material[] { Palette.Get("Props") });
        Show("BridgeLights", bridgeLights.BuildMesh(), new Material[] { LampMaterial() });
        ShowTrees(data, area, finder);
        ShowLandmarks(data.landmarks);
        LoadPostResources();
        PostEffects.Apply(Camera.main, postResources);
        ShowSky(data); // 재질이 다 만들어진 뒤에 (밤 불빛 재질 목록을 하늘 시계에 넘겨야 해서)
        bool hasRoute = data.route != null && data.route.minutes != null && data.route.minutes.Length >= 2;
        if (hasRoute) ShowRoute(data, finder);
    }

    // 하늘: 별·달, 구름, 물·유리 반사, 하늘 시계(해 위치에 맞춰 모두 바꿈)
    private void ShowSky(MapData data)
    {
        NightSky nightSky = GetComponent<NightSky>();
        if (nightSky == null) nightSky = gameObject.AddComponent<NightSky>();
        nightSky.Show();
        CloudLayer clouds = GetComponent<CloudLayer>();
        if (clouds == null) clouds = gameObject.AddComponent<CloudLayer>();
        clouds.Show();
        bool hasWater = data.water != null && data.water.cells != null && data.water.cells.Length > 0;
        WaterReflection reflection = GetComponent<WaterReflection>();
        if (reflection == null) reflection = gameObject.AddComponent<WaterReflection>();
        reflection.Show(hasWater ? data.water.level : Ground.LowestHeight(), data.sizeM);
        SkyClock clock = GetComponent<SkyClock>();
        if (clock == null) clock = gameObject.AddComponent<SkyClock>();
        clock.Show(data.sun, nightMaterials, streetMaterials);
        clock.SetMinute(0);
    }

    // 땅: 위성 사진 바닥 + 가까이 가도 덜 뭉개지게 잔무늬 + 지도 바깥 넓은 바닥
    private void ShowGround(MapData data)
    {
        groundMaterial = new Material(Palette.Get("Ground")); // 복사본을 만들어 씀 (원본 파일은 그대로)
        // 잔무늬: 유니티는 겹친 그림의 회색 128을 "그대로", 더 밝으면 밝게 곱함 → 평균이 128인 무늬여야 땅 밝기가 안 바뀜
        //   (콘크리트 사진을 그대로 겹쳤더니 사진이 밝아서 땅이 눈 온 것처럼 하얘졌음)
        groundMaterial.SetTexture("_DetailAlbedoMap", GrainTexture());
        groundMaterial.SetTextureScale("_DetailAlbedoMap", Vector2.one * (data.sizeM / GRAIN_M));
        Show("Ground", Ground.BuildMesh(), new Material[] { groundMaterial });
        ShowFarGround();
    }

    // 길: 보통 길은 RoadMaker, 물 위를 지나는 다리는 BridgeMaker
    private void ShowRoads(MapData data, RouteArea area, PropMaker props, PropMaker bridgeLights)
    {
        RoadMaker roads = new RoadMaker(Ground, ROAD_LIFT_M, props, area);
        bridges = new BridgeMaker(data, Ground, props, bridgeLights);
        List<RoadData> landRoads = new List<RoadData>(); // 땅 위에 그릴 길 (물 위 다리 빼고)
        foreach (RoadData road in data.roads)
        {
            if (bridges.IsOverWater(road)) bridges.Add(road);
            else landRoads.Add(road);
        }
        roads.MarkCarRoads(landRoads); // 찻길 자리를 먼저 다 알려 둠 → 인도·차선이 다른 찻길 위로 지나가지 않게
        foreach (RoadData road in landRoads) roads.Add(road);
        // 재질 순서는 RoadMaker의 재질 번호와 같아야 함 (찻길 · 걷는 길 · 노란 선 · 흰 선 · 인도 · 기둥 · 연석 · 전선 · 가로등)
        Material[] roadMaterials =
        {
            PhotoMaterial("Road", "Asphalt", ASPHALT_PHOTO_M, RoadPainter.Asphalt(), ASPHALT_TILE_M),
            PaintedMaterial("Path", RoadPainter.Path(), BLOCK_TILE_M),
            Palette.Get("RoadLine"), Palette.Get("RoadMark"),
            PhotoMaterial("Sidewalk", "Paving", PAVING_PHOTO_M, RoadPainter.Paving(), BLOCK_TILE_M),
            Palette.Get("Pole"), Palette.Get("Curb"), Palette.Get("Wire"), LampMaterial(),
        };
        Show("Roads", roads.BuildMesh(), roadMaterials);
    }

    // 나무: 건물 안·벽 바로 옆 나무는 빼고, 걸은 길 가까운 나무는 풍성하게
    private void ShowTrees(MapData data, RouteArea area, BuildingFinder finder)
    {
        TreeMaker trees = new TreeMaker(Ground);
        int skipped = 0;
        foreach (TreeData tree in data.trees)
        {
            if (finder.IsNearBuilding(tree.x, tree.z, tree.height * TREE_CLEAR_RATIO))
            {
                skipped++;
                continue;
            }
            trees.Add(tree, area.IsNear(tree.x, tree.z, TREE_NEAR_M));
        }
        Debug.Log("건물에 박혀서 뺀 나무: " + skipped + " / " + data.trees.Length);
        Show("Trees", trees.BuildMesh(), new Material[] { Palette.Get("Tree") });
    }

    // 눈에 띄는 건물: 지금은 전망 타워 하나 (기둥 → 전망대 두 단 → 안테나를 유니티 기본 기둥으로 쌓음)
    private void ShowLandmarks(LandmarkData[] landmarks)
    {
        Transform old = transform.Find("Landmarks");
        if (old != null) DestroyImmediate(old.gameObject);
        GameObject parent = new GameObject("Landmarks");
        parent.transform.SetParent(transform, false);
        if (landmarks == null) return;

        foreach (LandmarkData landmark in landmarks)
        {
            Vector3 foot = new Vector3(landmark.x, Ground.HeightAt(landmark.x, landmark.z), landmark.z);
            if (landmark.kind == 1)
            {
                AddLotteTower(parent.transform, foot);
                continue;
            }
            // { 아래쪽 높이, 키, 반지름 } 순서. N서울타워를 본뜬 치수 (전체 약 236m)
            Material concrete = Palette.Get("Concrete");
            Material deck = GlowMaterial("Glass", new Color(1f, 0.85f, 0.6f));    // 전망대 유리 (밤에 불 켜짐)
            Material antenna = GlowMaterial("RoadMark", new Color(0.7f, 0.8f, 1f)); // 안테나 (밤에 조명)
            AddCylinder(parent.transform, foot, 0, 112, 5.5f, concrete);   // 기둥
            AddCylinder(parent.transform, foot, 100, 9, 13f, deck);        // 전망대 아랫단
            AddCylinder(parent.transform, foot, 109, 8, 10.5f, deck);      // 전망대 윗단
            AddCylinder(parent.transform, foot, 117, 12, 4f, concrete);    // 전망대 위 받침
            AddCylinder(parent.transform, foot, 129, 105, 1.4f, antenna);  // 안테나
        }
    }

    // 롯데월드타워 (약 555m): 아래가 넓고 위로 갈수록 좁아지는 유리 탑 + 꼭대기의 갈라진 왕관
    //   팔각형 단 24개를 위로 갈수록 조금씩 좁게 쌓음 (한 번에 기울어진 면을 못 만들어서 계단처럼 나눔)
    private void AddLotteTower(Transform parent, Vector3 foot)
    {
        const int STEPS = 24;
        const float BODY_M = 500f;  // 유리 몸통 높이 (그 위 55m는 왕관)
        const float BASE_M = 36f;   // 바닥 쪽 반지름 (폭 약 72m)
        const float TOP_M = 12f;    // 몸통 꼭대기 반지름
        PropMaker tower = new PropMaker();
        for (int i = 0; i < STEPS; i++)
        {
            float rate = Mathf.Pow((i + 0.5f) / STEPS, 1.4f); // 아래는 천천히, 위로 갈수록 빨리 좁아짐 (붓 끝 같은 모양)
            tower.AddPrism(Octagon(Mathf.Lerp(BASE_M, TOP_M, rate)), BODY_M * i / STEPS, BODY_M * (i + 1) / STEPS, Color.white);
        }
        for (int k = 0; k < 4; k++) // 왕관: 네 모서리에서 솟은 가는 날
        {
            float angle = (45 + k * 90) * Mathf.Deg2Rad;
            Vector3 at = new Vector3(Mathf.Cos(angle), 0, Mathf.Sin(angle)) * (TOP_M - 2);
            tower.AddBox(at + Vector3.up * (BODY_M + 27), new Vector3(3, 55, 3), 45 + k * 90, Color.white);
        }
        GameObject item = new GameObject("Lotte World Tower");
        item.transform.SetParent(parent, false);
        item.transform.position = foot;
        item.AddComponent<MeshFilter>().sharedMesh = tower.BuildMesh();
        item.AddComponent<MeshRenderer>().sharedMaterial = GlowMaterial("Glass", new Color(0.75f, 0.85f, 1f)); // 밤에 탑 전체가 은은하게 빛남
    }

    // 반지름 radius인 팔각형 (위에서 본 모양)
    private static Vector2[] Octagon(float radius)
    {
        Vector2[] points = new Vector2[8];
        for (int k = 0; k < 8; k++)
        {
            float angle = (22.5f + k * 45) * Mathf.Deg2Rad;
            points[k] = new Vector2(Mathf.Cos(angle), Mathf.Sin(angle)) * radius;
        }
        return points;
    }

    // 기둥 하나: 발밑에서 from 높이부터 height만큼, 반지름 radius
    private void AddCylinder(Transform parent, Vector3 foot, float from, float height, float radius, Material material)
    {
        GameObject part = GameObject.CreatePrimitive(PrimitiveType.Cylinder); // 기본 기둥은 키 2m, 굵기 1m
        part.transform.SetParent(parent, false);
        part.transform.position = foot + Vector3.up * (from + height / 2);
        part.transform.localScale = new Vector3(radius * 2, height / 2, radius * 2);
        part.GetComponent<MeshRenderer>().sharedMaterial = material;
    }

    // 강물: 물 칸마다 평평한 네모 + 잔물결 두 겹 (하나는 크게, 하나는 작게 반대로 흘러서 무늬가 덜 되풀이돼 보임)
    private void ShowWater(MapData data)
    {
        Mesh mesh = WaterMaker.BuildMesh(data);
        Transform old = transform.Find("Water");
        if (old != null) DestroyImmediate(old.gameObject);
        if (mesh == null) return; // 이 지도에는 강이 없음

        Material water = new Material(Palette.Get("Water"));
        Texture2D ripple = WaterMaker.RippleNormalMap();
        water.SetTexture("_BumpMap", ripple);
        water.EnableKeyword("_NORMALMAP");
        water.SetFloat("_BumpScale", 0.6f);            // 큰 물결 세기
        water.SetFloat("_DetailNormalMapScale", 0.35f); // 작은 물결은 더 약하게 (세면 그물 무늬처럼 보임)
        water.SetTexture("_DetailNormalMap", ripple);
        water.SetTextureScale("_DetailAlbedoMap", Vector2.one * 3.7f); // 작은 물결 (유니티는 둘째 그림의 크기를 이 이름으로 정함)
        water.EnableKeyword("_DETAIL_MULX2");
        Show("Water", mesh, new Material[] { water });
        transform.Find("Water").gameObject.AddComponent<WaterFlow>().Set(RIPPLE_FLOW);
    }

    // 배를 탄 구간의 처음·끝마다 선착장 (배가 대는 물 위 자리 + 그 앞 강가 자리)
    private void AddPiers(MapData data, PropMaker props)
    {
        RouteData route = data.route;
        if (route == null || route.minutes == null || data.water == null) return;
        for (int i = 0; i + 1 < route.minutes.Length; i++)
        {
            bool isBoat = route.ModeAt(i) == RouteData.BOAT;
            bool wasBoat = i > 0 && route.ModeAt(i - 1) == RouteData.BOAT;
            if (isBoat && !wasBoat) BoatMaker.AddPier(props, Ground, route.PointAt(i + 1), route.PointAt(i), data.water.level); // 배 타는 곳
            if (!isBoat && wasBoat) BoatMaker.AddPier(props, Ground, route.PointAt(i - 1), route.PointAt(i), data.water.level); // 배 내리는 곳
        }
    }

    // 경로: 걷기 = 땅을 따라가는 파란 띠, 배 = 물 위 흰 선, 지하철·택시 = 하늘로 휜 주황 선 + 다녀온 곳 핀 + 재생
    private void ShowRoute(MapData data, BuildingFinder finder)
    {
        RouteData route = data.route;
        StopData[] stops = data.stops;
        Transform oldLines = transform.Find("RouteLines");
        if (oldLines != null) DestroyImmediate(oldLines.gameObject);
        GameObject lines = new GameObject("RouteLines");
        lines.transform.SetParent(transform, false);

        // 길 만드는 도구를 그대로 씀: 걸은 구간을 "걷는 길"로 넣으면 땅을 따라가는 띠가 됨
        RoadMaker walkLine = new RoadMaker(Ground, ROUTE_LIFT_M);
        RoadMaker busLine = new RoadMaker(Ground, ROUTE_LIFT_M + VEHICLE_LINE_UP_M);  // 버스: 도로를 따라 초록 띠
        RoadMaker taxiLine = new RoadMaker(Ground, ROUTE_LIFT_M + VEHICLE_LINE_UP_M); // 택시: 도로를 따라 주황 띠
        float level = data.water != null ? data.water.level : 0;
        int runStart = 0; // 같은 방법으로 움직인 구간이 시작된 점
        int count = route.minutes.Length;
        for (int i = 1; i < count; i++)
        {
            bool isRunEnd = i == count - 1 || route.ModeAt(i) != route.ModeAt(runStart);
            if (!isRunEnd) continue;
            int mode = route.ModeAt(runStart);
            if (mode == RouteData.WALK) AddRouteRun(walkLine, lines.transform, route, runStart, i, ROUTE_WIDTH_M, "Route", 0);
            if (mode == RouteData.BOAT) AddFlatLine(lines.transform, BoatLinePositions(route, runStart, i, level), "BoatLine", BOAT_LINE_WIDTH_M); // 눕힌 선 (배 위 눈높이에서 벽처럼 안 보이게)
            if (mode == RouteData.SUBWAY) AddLine(lines.transform, RideLinePositions(route, runStart, i), "SubwayLine", RIDE_LINE_WIDTH_M); // 땅속 → 하늘로 휜 선
            if (mode == RouteData.BUS) AddRouteRun(busLine, lines.transform, route, runStart, i, VEHICLE_LINE_WIDTH_M, "BusLine", VEHICLE_LINE_UP_M);
            if (mode == RouteData.RIDE) AddRouteRun(taxiLine, lines.transform, route, runStart, i, VEHICLE_LINE_WIDTH_M, "RideLine", VEHICLE_LINE_UP_M);
            runStart = i;
        }
        Material blue = Palette.Get("Route");
        Material[] blues = new Material[RoadMaker.MATERIAL_COUNT];
        for (int i = 0; i < blues.Length; i++) blues[i] = blue;
        Show("Route", walkLine.BuildMesh(), blues);
        Show("RouteBus", busLine.BuildMesh(), SameMaterial("BusLine"));
        Show("RouteTaxi", taxiLine.BuildMesh(), SameMaterial("RideLine"));

        Transform old = transform.Find("Pins");
        if (old != null) DestroyImmediate(old.gameObject);
        GameObject pins = new GameObject("Pins");
        pins.transform.SetParent(transform, false);
        foreach (StopData stop in stops) AddPin(pins.transform, stop);

        RoutePlayer player = GetComponent<RoutePlayer>();
        if (player == null) player = gameObject.AddComponent<RoutePlayer>();
        player.Show(Ground, route, level, data.startClock, finder, bridges);
    }

    // 경로 한 구간(from ~ to번째 점): 땅 위는 띠로, 다리를 건넌 부분은 다리 높이에 선으로 (땅에 그리면 다리 밑에 숨어서)
    //   GPS 점이 다리 위에 하나도 없을 수 있어서 DECK_CHECK_STEP_M마다 점을 더 찍어 확인함
    //   같은 상태(땅 / 다리)인 점끼리 묶어 그림. 묶음끼리는 경계 점을 같이 써서 끊기지 않게
    //   extraLift: 걷기 띠보다 더 띄울 높이 (버스·택시)
    private void AddRouteRun(RoadMaker maker, Transform lines, RouteData route, int from, int to, float width, string materialName, float extraLift)
    {
        List<Vector2> points = DensePoints(route, from, to);
        int start = 0;
        bool isOnDeck = IsOnDeck(points, 0);
        for (int k = 1; k < points.Count; k++)
        {
            bool isNextOnDeck = IsOnDeck(points, k);
            if (isNextOnDeck == isOnDeck && k < points.Count - 1) continue;
            if (isOnDeck) AddFlatLine(lines, DeckLinePositions(points, start, k, extraLift), materialName, width);
            else maker.Add(new RoadData { points = Flatten(points, start, k), width = width, kind = 1 });
            start = k;
            isOnDeck = isNextOnDeck;
        }
    }

    // from번째 점부터 to번째 점까지, 점 사이가 멀면 DECK_CHECK_STEP_M마다 점을 더 넣은 목록
    private List<Vector2> DensePoints(RouteData route, int from, int to)
    {
        List<Vector2> points = new List<Vector2> { route.PointAt(from) };
        for (int i = from + 1; i <= to; i++)
        {
            Vector2 before = route.PointAt(i - 1);
            Vector2 point = route.PointAt(i);
            int pieces = Mathf.Max(1, Mathf.CeilToInt(Vector2.Distance(before, point) / DECK_CHECK_STEP_M));
            for (int k = 1; k <= pieces; k++) points.Add(Vector2.Lerp(before, point, (float)k / pieces));
        }
        return points;
    }

    // 점 목록의 from ~ to를 x, z, x, z … 숫자 목록으로 (RoadData.points 모양)
    private static float[] Flatten(List<Vector2> points, int from, int to)
    {
        float[] numbers = new float[(to - from + 1) * 2];
        for (int i = from; i <= to; i++)
        {
            numbers[(i - from) * 2] = points[i].x;
            numbers[(i - from) * 2 + 1] = points[i].y;
        }
        return numbers;
    }

    // k번째 점에서 가는 방향 (앞뒤 점으로)
    private static Vector2 HeadingAt(List<Vector2> points, int k)
    {
        return points[Mathf.Min(k + 1, points.Count - 1)] - points[Mathf.Max(k - 1, 0)];
    }

    // k번째 점이 다리 위인지 (가는 방향이 다리와 나란할 때만)
    private bool IsOnDeck(List<Vector2> points, int k)
    {
        return bridges != null && bridges.TryDeckHeight(points[k], HeadingAt(points, k), out float height);
    }

    // 다리 위 경로 선의 점들: 다리 위면 다리 높이, 아니면 땅 높이 (+ 경로 선 띄우는 높이)
    private Vector3[] DeckLinePositions(List<Vector2> points, int from, int to, float extraLift)
    {
        Vector3[] positions = new Vector3[to - from + 1];
        for (int k = from; k <= to; k++)
        {
            Vector2 point = points[k];
            float height = Ground.HeightAt(point.x, point.y);
            if (bridges.TryDeckHeight(point, HeadingAt(points, k), out float deck)) height = deck;
            positions[k - from] = new Vector3(point.x, height + ROUTE_LIFT_M + extraLift, point.y);
        }
        return positions;
    }

    // 길 띠(RoadMaker)의 재질 칸 전부를 같은 재질로
    private static Material[] SameMaterial(string materialName)
    {
        Material[] materials = new Material[RoadMaker.MATERIAL_COUNT];
        for (int i = 0; i < materials.Length; i++) materials[i] = Palette.Get(materialName);
        return materials;
    }

    // from번째 점부터 to번째 점까지의 좌표 (x, z, x, z …)
    private float[] PointsBetween(RouteData route, int from, int to)
    {
        float[] points = new float[(to - from + 1) * 2];
        Array.Copy(route.points, from * 2, points, 0, points.Length); // Array.Copy: 목록의 한 부분을 다른 목록으로 그대로 옮김
        return points;
    }

    // 배 구간 선: 점마다 물 높이 조금 위
    private Vector3[] BoatLinePositions(RouteData route, int from, int to, float level)
    {
        Vector3[] positions = new Vector3[to - from + 1];
        for (int i = from; i <= to; i++)
        {
            Vector2 point = route.PointAt(i);
            positions[i - from] = new Vector3(point.x, level + BOAT_LINE_LIFT_M, point.y);
        }
        return positions;
    }

    // 탈것 구간 선: 땅 위를 그리지 않은 구간이라, 처음과 끝을 잇는 무지개처럼 휜 선 (가운데가 가장 높음)
    private Vector3[] RideLinePositions(RouteData route, int from, int to)
    {
        const int PIECES = 40;
        Vector2 start = route.PointAt(from);
        Vector2 end = route.PointAt(to);
        float arc = Mathf.Min(RIDE_ARC_MAX_M, Vector2.Distance(start, end) * RIDE_ARC_RATIO);
        Vector3[] positions = new Vector3[PIECES + 1];
        for (int k = 0; k <= PIECES; k++)
        {
            float t = (float)k / PIECES;
            Vector2 point = Vector2.Lerp(start, end, t);
            float height = Mathf.Lerp(Ground.HeightAt(start.x, start.y), Ground.HeightAt(end.x, end.y), t) + 2 + arc * Mathf.Sin(t * Mathf.PI);
            positions[k] = new Vector3(point.x, height, point.y);
        }
        return positions;
    }

    // 선 하나 (LineRenderer: 점들을 이은 띠를 그려 주는 유니티 기본 부품)
    private void AddLine(Transform parent, Vector3[] positions, string materialName, float width)
    {
        GameObject item = new GameObject(materialName);
        item.transform.SetParent(parent, false);
        LineRenderer line = item.AddComponent<LineRenderer>();
        line.positionCount = positions.Length;
        line.SetPositions(positions);
        line.widthMultiplier = width;
        line.sharedMaterial = Palette.Get(materialName);
        line.shadowCastingMode = ShadowCastingMode.Off;
    }

    // 바닥에 눕힌 선 (다리 위 경로): 보통 선은 늘 카메라 쪽을 보고 서서, 사람 눈높이(내비)에서 보면 벽처럼 보임
    //   alignment = TransformZ: 물체의 z 방향을 띠의 앞면으로 씀 → 물체를 x축으로 90도 돌려 z가 아래를 보게 하면 띠가 바닥에 누움
    private void AddFlatLine(Transform parent, Vector3[] positions, string materialName, float width)
    {
        AddLine(parent, positions, materialName, width);
        Transform item = parent.GetChild(parent.childCount - 1); // 방금 만든 선
        item.rotation = Quaternion.Euler(90, 0, 0);
        item.GetComponent<LineRenderer>().alignment = LineAlignment.TransformZ;
    }

    // 다녀온 곳 핀 = 가는 기둥 + 그 위의 공 (유니티 기본 도형)
    private void AddPin(Transform parent, StopData stop)
    {
        Vector3 foot = new Vector3(stop.x, Ground.HeightAt(stop.x, stop.z), stop.z);
        Material material = Palette.Get("Pin");

        GameObject pole = GameObject.CreatePrimitive(PrimitiveType.Cylinder); // 기본 기둥은 키 2m, 굵기 1m
        pole.transform.SetParent(parent, false);
        pole.transform.position = foot + Vector3.up * PIN_HEIGHT_M / 2;
        pole.transform.localScale = new Vector3(0.5f, PIN_HEIGHT_M / 2, 0.5f);
        pole.GetComponent<MeshRenderer>().sharedMaterial = material;

        GameObject ball = GameObject.CreatePrimitive(PrimitiveType.Sphere);
        ball.transform.SetParent(parent, false);
        ball.transform.position = foot + Vector3.up * PIN_HEIGHT_M;
        ball.transform.localScale = Vector3.one * 5;
        ball.GetComponent<MeshRenderer>().sharedMaterial = material;
    }

    // 땅에 겹칠 잔무늬: 점마다 밝기가 조금씩 다른 회색 그림 (모래알 같은 무늬)
    private Texture2D GrainTexture()
    {
        const int size = 256;
        Color32[] pixels = new Color32[size * size];
        for (int y = 0; y < size; y++)
        {
            for (int x = 0; x < size; x++)
            {
                // 콘크리트 얼룩(큰 무늬) + 모래알(작은 점). 둘 다 128 둘레로 오르내려서 평균은 128
                float stain = (Mathf.PerlinNoise(x * 0.03f, y * 0.03f) - 0.5f) * 30;
                float sand = ((x * 7919 + y * 104729) % 23) - 11;
                byte gray = (byte)Mathf.Clamp(128 + stain + sand, 0, 255);
                pixels[y * size + x] = new Color32(gray, gray, gray, 255);
            }
        }
        Texture2D texture = new Texture2D(size, size);
        texture.SetPixels32(pixels);
        texture.Apply();
        return texture;
    }

    // 건물을 "종류 × 벽 색" 묶음으로 나눠 그림 (건물마다 따로 그리면 느려서, 같은 모습끼리 한 덩어리로)
    // 걸은 길 가까운 건물은 디테일(BuildingDetails)도 붙여서 props에 담음
    private void ShowBuildings(BuildingData[] buildings, RouteArea area, PropMaker props)
    {
        BuildingDetails details = new BuildingDetails(Ground, props);
        BuildingMaker hanoks = new BuildingMaker(Ground); // 한옥 동네의 낮은 건물
        ApartmentNumbers numbers = new ApartmentNumbers(Ground); // 아파트 동 번호
        BuildingMaker euljiro = new BuildingMaker(Ground);       // 을지로 인쇄 골목 상가
        BuildingMaker[] makers = new BuildingMaker[KIND_COUNT * VARIANT_COUNT];
        for (int i = 0; i < makers.Length; i++) makers[i] = new BuildingMaker(Ground);
        int customCount = 0;
        foreach (BuildingData building in buildings)
        {
            int kind = Mathf.Clamp(building.kind, 0, KIND_COUNT - 1);
            if (kind == 1 && building.height <= VILLA_MAX_M) kind = 0; // 자료에는 빌라도 "공동주택"이라, 낮은 것은 주택 모습으로
            if (building.style == 1) // 한옥
            {
                hanoks.AddHanok(building);
                continue;
            }
            Vector2 center = CenterOf(building);
            if (area.IsNear(center.x, center.y, DETAIL_NEAR_M)) details.Add(building, kind);
            if (building.style == 2)
            {
                euljiro.Add(building);
                continue;
            }
            if (kind == 1) numbers.Add(building); // 빌라 높이를 넘는 공동주택 = 아파트

            bool hasLook = building.look != null && !string.IsNullOrEmpty(building.look.wall);
            if (hasLook) // 로드뷰를 보고 따로 정한 건물은 한 채씩 따로 그림
            {
                ShowCustomBuilding(building, customCount++);
                continue;
            }
            makers[kind * VARIANT_COUNT + VariantOf(building)].Add(building);
        }

        Material concrete = Palette.Get("Concrete");
        Material hanokWall = WallMaterial("Wall", FacadePainter.Hanok(), 0);
        AddSurface(hanokWall, "Bricks"); // 익선동 골목 한옥 벽은 붉은 벽돌 (로드뷰)
        Material giwa = PaintedMaterial("HanokRoof", FacadePainter.GiwaTiles(), 1.2f); // 기와 그림 한 장 = 1.2m
        Show("ApartmentNumbers", numbers.BuildMesh(), new Material[] { ApartmentNumbers.MakeMaterial() });
        Material[] euljiroMaterials =
        {
            WallMaterial("Wall", FacadePainter.EuljiroUpper(), UPPER_LIT_CHANCE, "Tiles"),
            WallMaterial("Wall", FacadePainter.EuljiroGround(), SHOP_LIT_CHANCE, "Tiles"),
            Palette.Get("Concrete"), Palette.Get(ROOF_NAMES[0]), Palette.Get("RoofTile"),
        };
        Show("Buildings Euljiro", euljiro.BuildMesh(), euljiroMaterials);
        Show("Hanoks", hanoks.BuildMesh(), new Material[] { hanokWall, hanokWall, concrete, giwa, giwa });
        for (int kind = 0; kind < KIND_COUNT; kind++)
        {
            for (int variant = 0; variant < VARIANT_COUNT; variant++)
            {
                bool hasColoredRoof = kind == 0 || kind == 2; // 주택·상가 옥상만 색이 여러 가지, 나머지는 회색
                string wallName = kind == 3 ? "Glass" : "Wall"; // 업무용 건물은 하늘이 비치는 유리 재질
                Material[] materials =
                {
                    WallMaterial(wallName, FacadePainter.Upper(kind, variant), UPPER_LIT_CHANCE, SurfaceOf(kind, variant)),
                    WallMaterial(wallName, FacadePainter.GroundFloor(kind, variant), SHOP_LIT_CHANCE, SurfaceOf(kind, variant)),
                    concrete,
                    Palette.Get(hasColoredRoof ? ROOF_NAMES[variant] : ROOF_NAMES[0]),
                    Palette.Get("RoofTile"),
                };
                Show("Buildings " + kind + "-" + variant, makers[kind * VARIANT_COUNT + variant].BuildMesh(), materials);
            }
        }
    }

    // 따로 정한 모습의 건물 한 채: 그 건물만의 벽 그림을 만들어 붙임
    private void ShowCustomBuilding(BuildingData building, int number)
    {
        BuildingMaker maker = new BuildingMaker(Ground);
        maker.Add(building);
        string wallName = building.look.pattern == 4 ? "Glass" : "Wall";
        Material[] materials =
        {
            WallMaterial(wallName, FacadePainter.CustomUpper(building.look), UPPER_LIT_CHANCE, SurfaceOfPattern(building.look.pattern)),
            WallMaterial(wallName, FacadePainter.CustomGround(building.look), SHOP_LIT_CHANCE, SurfaceOfPattern(building.look.pattern)),
            Palette.Get("Concrete"),
            Palette.Get(ROOF_NAMES[0]),
            Palette.Get("RoofTile"),
        };
        Show("Building look " + number, maker.BuildMesh(), materials);
    }

    // 건물 바닥 윤곽의 가운데 (점들의 평균)
    private Vector2 CenterOf(BuildingData building)
    {
        Vector2 sum = Vector2.zero;
        int count = building.points.Length / 2;
        for (int i = 0; i + 1 < building.points.Length; i += 2) sum += new Vector2(building.points[i], building.points[i + 1]);
        return sum / count;
    }

    // 하늘·땅에서 오는 빛: 그늘진 벽이 시커멓게 보이지 않게 (위는 하늘색, 옆은 회색, 아래는 땅색으로 살짝 밝힘)
    private void SetSkyLight()
    {
        RenderSettings.ambientMode = AmbientMode.Trilight;
        RenderSettings.ambientSkyColor = new Color(0.60f, 0.67f, 0.78f);
        RenderSettings.ambientEquatorColor = new Color(0.52f, 0.53f, 0.52f);
        RenderSettings.ambientGroundColor = new Color(0.34f, 0.32f, 0.29f);
    }

    // 건물의 벽 색 번호 (0 ~ 2). 건물 위치로 정해서, 다시 그려도 같은 건물은 같은 색
    private int VariantOf(BuildingData building)
    {
        return (int)Mathf.Abs(building.points[0] * 7 + building.points[1] * 13) % VARIANT_COUNT;
    }

    // 화면 효과용 셰이더 모음 찾기 (편집기에서만. 웹으로 내보낼 때는 장면에 저장된 값을 씀)
    private void LoadPostResources()
    {
#if UNITY_EDITOR
        if (postResources == null) postResources = AssetDatabase.LoadAssetAtPath<PostProcessResources>(POST_RESOURCES_PATH);
#endif
    }

#if UNITY_EDITOR
    // 편집기에서 이 부품을 볼 때마다 불림: 효과용 셰이더 모음을 칸에 채워 둠 (장면을 저장하면 웹에서도 씀)
    private void OnValidate()
    {
        LoadPostResources();
    }
#endif

    // 질감 사진(Resources/Textures/이름.jpg + 이름_Normal.jpg)을 붙인 재질. 사진 한 장이 tileM 미터를 덮음
    //   사진이 없으면 코드로 그린 그림(fallback)으로 대신함
    private Material PhotoMaterial(string baseName, string photoName, float tileM, Texture2D fallback, float fallbackTileM)
    {
        Texture2D photo = Resources.Load<Texture2D>("Textures/" + photoName);
        if (photo == null) return PaintedMaterial(baseName, fallback, fallbackTileM);
        Material material = PaintedMaterial(baseName, photo, tileM);
        material.SetTexture("_BumpMap", Resources.Load<Texture2D>("Textures/" + photoName + "_Normal"));
        material.EnableKeyword("_NORMALMAP");
        material.SetTexture("_EmissionMap", photo); // 밤에 사진 무늬 그대로 은은하게 밝아짐 (SkyClock)
        material.EnableKeyword("_EMISSION");
        material.SetColor("_EmissionColor", Color.black);
        streetMaterials.Add(material);
        return material;
    }

    // 벽에 표면 질감(벽돌·타일·미장의 울퉁불퉁함)을 겹침. 창문 그림은 그대로 두고 빛 받는 모양만 바뀜
    //   벽 그림 좌표는 창 한 칸(가로 3.2m × 한 층) = 1이라, 표면 사진을 칸마다 SURFACE_PER_CELL번 되풀이
    private static void AddSurface(Material material, string photoName)
    {
        if (photoName == null) return;
        Texture2D normal = Resources.Load<Texture2D>("Textures/" + photoName + "_Normal");
        if (normal == null) return;
        material.EnableKeyword("_NORMALMAP");     // 표면 기울기 계산 켜기 (큰 노멀 맵은 없음 = 평평)
        material.EnableKeyword("_DETAIL_MULX2");  // 겹쳐 그리는 그림(디테일) 켜기
        material.SetTexture("_DetailNormalMap", normal);
        material.SetFloat("_DetailNormalMapScale", 0.8f);
        material.SetTextureScale("_DetailAlbedoMap", Vector2.one * SURFACE_PER_CELL); // 디테일 그림 크기는 이 이름으로 정함
    }

    // 건물 종류·색 번호별 벽 표면 (FacadePainter의 벽 색과 맞춤). null = 표면 질감 없음(유리)
    private static string SurfaceOf(int kind, int variant)
    {
        if (kind == 0) return variant == 0 ? "Tiles" : variant == 1 ? "Bricks" : "Plaster"; // 주택: 타일 · 벽돌 · 미장
        if (kind == 1) return "Plaster";  // 아파트
        if (kind == 2) return "Tiles";    // 상가
        if (kind == 3) return null;       // 업무: 유리
        return "Concrete";                // 그 밖
    }

    // 로드뷰로 정한 모습의 벽 무늬 번호(0 타일 · 1 벽돌 · 2 미장 · 3 패널 · 4 유리) → 표면
    private static string SurfaceOfPattern(int pattern)
    {
        string[] surfaces = { "Tiles", "Bricks", "Plaster", "Concrete", null };
        return surfaces[Mathf.Clamp(pattern, 0, surfaces.Length - 1)];
    }

    // 바닥 그림을 붙인 재질. 길의 그림 좌표는 미터 단위라서, 그림 한 장이 tileM 미터를 덮게 줄여 붙임
    private Material PaintedMaterial(string baseName, Texture2D picture, float tileM)
    {
        Material material = new Material(Palette.Get(baseName)); // 복사본 (원본 재질 파일은 그대로)
        material.color = Color.white; // 색은 그림에 들어 있음
        material.mainTexture = picture;
        material.mainTextureScale = Vector2.one / tileM;
        return material;
    }

    // 가로등·다리 조명: 낮에는 밝은 회색 등, 밤에는 전구색으로 빛남
    private Material LampMaterial()
    {
        if (lampMaterial == null) lampMaterial = GlowMaterial("RoadMark", new Color(1f, 0.80f, 0.52f));
        return lampMaterial;
    }

    // 밤에 glow 색으로 빛나는 재질 (낮 모습은 baseName 재질 그대로). 빛 색은 1 × 1 그림으로 정하고, 세기는 SkyClock이 정함
    private Material GlowMaterial(string baseName, Color glow)
    {
        Texture2D color = new Texture2D(1, 1);
        color.SetPixel(0, 0, glow);
        color.Apply();
        Material material = new Material(Palette.Get(baseName));
        material.SetTexture("_EmissionMap", color);
        material.EnableKeyword("_EMISSION");
        material.SetColor("_EmissionColor", Color.black); // 처음엔 꺼 둠
        nightMaterials.Add(material);
        return material;
    }

    // 벽 그림을 붙인 재질. litChance > 0이면 밤 불빛 그림도 붙여 두고 nightMaterials에 넣음 (SkyClock이 밤에 켬)
    //   그림 한 장에 벽 칸을 4 × 4개 넣어서(FacadePainter.Repeat), 칸마다 불이 켜지고 꺼진 게 달라 보이게 함
    private Material WallMaterial(string baseName, Texture2D picture, float litChance, string surface = null)
    {
        Material material = new Material(Palette.Get(baseName));
        AddSurface(material, surface);
        if (litChance <= 0)
        {
            material.mainTexture = picture;
            return material;
        }
        material.mainTexture = FacadePainter.Repeat(picture);
        material.mainTextureScale = Vector2.one / FacadePainter.NIGHT_REPEAT;
        if (baseName == "Wall") // 유리벽 빌딩은 벽 전체가 창이라 깊이를 안 줌
        {
            material.SetTexture("_ParallaxMap", FacadePainter.Depth(picture));
            material.EnableKeyword("_PARALLAXMAP");
            material.SetFloat("_Parallax", WINDOW_PARALLAX);
        }
        material.SetTexture("_EmissionMap", FacadePainter.NightLights(picture, nightMaterials.Count, litChance));
        material.EnableKeyword("_EMISSION");
        material.SetColor("_EmissionColor", Color.black); // 처음엔 꺼 둠
        nightMaterials.Add(material);
        return material;
    }

    // 지도 바깥이 허공으로 보이지 않게, 가장 낮은 땅 높이에 넓은 바닥을 깖 (멀리는 안개에 묻힘)
    private void ShowFarGround()
    {
        Transform old = transform.Find("FarGround");
        if (old != null) DestroyImmediate(old.gameObject);

        GameObject far = GameObject.CreatePrimitive(PrimitiveType.Plane); // 유니티 기본 평면 (한 변 10m)
        far.name = "FarGround";
        far.transform.SetParent(transform, false);
        far.transform.position = new Vector3(0, Ground.LowestHeight() - 2, 0);
        far.transform.localScale = Vector3.one * (FAR_GROUND_M / 10);
        far.GetComponent<MeshRenderer>().sharedMaterial = Palette.Get("FarGround");
    }

    // 땅에 위성 사진 입히기. 웹은 그림을 글자(base64)로 바꿔서 넘김 → 다시 그림으로 되돌려 땅 재질에 붙임
    public void LoadGroundPhoto(string base64)
    {
        LoadGroundPhoto(Convert.FromBase64String(base64));
    }

    // 바닥 사진(JPEG 파일 내용 그대로)을 땅에 입힘
    public void LoadGroundPhoto(byte[] jpeg)
    {
        Texture2D photo = new Texture2D(2, 2); // 크기는 LoadImage가 그림에 맞게 바꿈
        photo.LoadImage(jpeg);
        photo.wrapMode = TextureWrapMode.Clamp; // 가장자리에서 반대쪽 그림이 번지지 않게
        photo.anisoLevel = 8;                   // 비스듬히 봐도 덜 뭉개지게
        photo.Compress(true);                   // 압축해서 메모리를 줄임 (8192 사진: 256MB → 약 43MB)
        photo.Apply(false, true);               // 압축 전 픽셀 사본은 버림 (true = 다시 고칠 일 없음)
        groundMaterial.mainTexture = photo;
        groundMaterial.color = Color.white;     // 사진 색 그대로
    }

    // 모양(Mesh)과 재질을 가진 물체를 이 지도 아래에 하나 만듦 (같은 이름이 있으면 지우고 새로)
    private void Show(string objectName, Mesh mesh, Material[] materials)
    {
        Transform old = transform.Find(objectName);
        if (old != null) DestroyImmediate(old.gameObject);

        GameObject item = new GameObject(objectName);
        item.transform.SetParent(transform, false);
        item.AddComponent<MeshFilter>().sharedMesh = mesh;             // 모양
        item.AddComponent<MeshRenderer>().sharedMaterials = materials; // 색·재질
    }
}
