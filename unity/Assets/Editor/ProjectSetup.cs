// (편집기 도구) 처음 한 번만: 재질과 장면 파일을 만들어 둠. 게임에는 들어가지 않는 코드
// 유니티 메뉴 Tools > Retro > 처음 준비 로 실행
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;

public static class ProjectSetup
{
    private const string SCENE_PATH = "Assets/Scenes/RetroMap.unity";
    private static readonly Color SKY_HAZE = new Color(0.74f, 0.82f, 0.90f); // 먼 곳이 묻히는 안개 색 (하늘 아래쪽 색)

    [MenuItem("Tools/Retro/처음 준비 (재질·장면 만들기)")]
    public static void Run()
    {
        if (!AssetDatabase.IsValidFolder("Assets/Resources")) AssetDatabase.CreateFolder("Assets", "Resources");
        if (!AssetDatabase.IsValidFolder("Assets/Scenes")) AssetDatabase.CreateFolder("Assets", "Scenes");
        // 재질 파일 저장 (색은 Palette.cs 한 곳에서 정함)
        foreach (string name in Palette.Names()) SaveMaterial(name);

        // 기본 장면(카메라 + 해)에 지도 물체 하나를 더해 저장
        EditorSceneManager.NewScene(NewSceneSetup.DefaultGameObjects, NewSceneMode.Single);
        new GameObject("RetroMap").AddComponent<RetroMap>();

        Camera.main.transform.position = new Vector3(-900, 700, -1500);
        Camera.main.transform.LookAt(new Vector3(0, 100, 0));
        Camera.main.nearClipPlane = 1;   // 너무 가까운 것은 안 그림 (멀리 있는 길이 땅과 겹쳐 깜빡이는 것을 줄임)
        Camera.main.farClipPlane = 20000;
        Camera.main.gameObject.AddComponent<CameraMove>(); // 키보드·마우스로 돌아다니기

        Light sun = Object.FindAnyObjectByType<Light>();
        sun.transform.rotation = Quaternion.Euler(42, -40, 0); // 해 높이 42도, 남동쪽에서 비춤
        sun.color = new Color(1f, 0.96f, 0.88f);                // 살짝 따뜻한 햇빛
        sun.intensity = 1.0f;
        sun.shadows = LightShadows.Soft;                        // 건물 그림자
        sun.shadowStrength = 0.65f;

        Material sky = new Material(Shader.Find("Skybox/Procedural")); // 유니티 기본 하늘. 지평선 아래 색만 안개 색으로 바꿈 (갈색 띠가 보이지 않게)
        sky.SetColor("_GroundColor", SKY_HAZE);
        AssetDatabase.CreateAsset(sky, "Assets/Resources/Sky.mat");
        RenderSettings.skybox = sky;

        RenderSettings.fog = true;                              // 먼 곳을 뿌옇게 → 깊이감, 지도 끝이 덜 티 남
        RenderSettings.fogMode = FogMode.Linear;
        RenderSettings.fogColor = SKY_HAZE;
        RenderSettings.fogStartDistance = 1200;
        RenderSettings.fogEndDistance = 9000;

        EditorSceneManager.SaveScene(EditorSceneManager.GetActiveScene(), SCENE_PATH);
        EditorBuildSettings.scenes = new EditorBuildSettingsScene[] { new EditorBuildSettingsScene(SCENE_PATH, true) };
        AssetDatabase.SaveAssets();
    }

    private static void SaveMaterial(string name)
    {
        string path = "Assets/Resources/" + name + ".mat";
        AssetDatabase.DeleteAsset(path); // 예전 것이 있으면 지우고 새로
        AssetDatabase.CreateAsset(Palette.Make(name), path);
    }
}
