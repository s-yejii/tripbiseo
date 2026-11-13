// (편집기 도구) 웹(WebGL)으로 내보내기. 게임에는 들어가지 않는 코드
//   메뉴: Tools > Retro > 웹으로 내보내기 (WebGL)
//   명령줄: Unity -batchmode -quit -projectPath … -executeMethod BuildWebGL.Run -out "폴더"
// 하는 일
//   1. 코드로 만드는 재질이 쓰는 셰이더 조합을 빌드에 꼭 넣음 (안 넣으면 유니티가 "안 쓰는 것"으로 보고 빼서 분홍색으로 나옴)
//   2. 장면의 RetroMap에 화면 효과 셰이더 모음을 채우고, 시연 자료(sample-trip)를 기본으로 정해 저장
//   3. 시연 자료만 담아 빌드 (다른 시험 자료는 빌드 동안 잠깐 밖으로 옮겼다가 되돌림 → 웹에 올릴 크기를 줄이려고)
using System;
using System.IO;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering.PostProcessing;

public static class BuildWebGL
{
    private const string SCENE_PATH = "Assets/Scenes/RetroMap.unity";
    private const string VARIANT_FOLDER = "Assets/Resources/ShaderVariants";
    private const string DEMO_SAMPLE = "sample-trip";
    private const string HOLD_FOLDER = "SamplesHold"; // 빌드 동안 다른 시험 자료를 잠깐 옮겨 두는 곳 (Assets 밖)

    // 코드에서 켜는 Standard 재질 기능 조합 (RetroMap.cs의 WallMaterial·PhotoMaterial·GlowMaterial·ShowWater 등)
    private static readonly string[][] STANDARD_KEYWORDS =
    {
        new string[] { },
        new string[] { "_EMISSION" },                                                  // 유리 빌딩·조명·경로 선
        new string[] { "_DETAIL_MULX2" },                                              // 땅
        new string[] { "_NORMALMAP", "_EMISSION" },                                    // 길 사진
        new string[] { "_NORMALMAP", "_DETAIL_MULX2" },                                // 강물·한옥 벽
        new string[] { "_NORMALMAP", "_DETAIL_MULX2", "_EMISSION" },
        new string[] { "_NORMALMAP", "_DETAIL_MULX2", "_EMISSION", "_PARALLAXMAP" },  // 보통 벽 (표면 굴곡 + 밤 불빛 + 창 깊이)
    };
    // 코드에서 Shader.Find로 찾는 셰이더 (재질 파일이 없어도 빌드에 들어가게)
    private static readonly string[] EXTRA_SHADERS = { "Sprites/Default", "Legacy Shaders/Transparent/Cutout/Diffuse", "Retro/VertexColor", "Skybox/Procedural" };

    [MenuItem("Tools/Retro/웹으로 내보내기 (WebGL)")]
    public static void FromMenu()
    {
        Build(Path.Combine(Directory.GetCurrentDirectory(), "Builds", "WebGL"));
    }

    // 명령줄용
    public static void Run()
    {
        string outFolder = Argument("-out");
        if (outFolder == null) outFolder = Path.Combine(Directory.GetCurrentDirectory(), "Builds", "WebGL");
        Build(outFolder);
    }

    private static void Build(string outFolder)
    {
        SaveVariantMaterials();
        PrepareScene();
        PlayerSettings.WebGL.compressionFormat = WebGLCompressionFormat.Disabled; // 압축 없이 (개발 서버가 압축 파일 헤더를 못 붙여서)
        PlayerSettings.WebGL.dataCaching = true;  // 한 번 받은 파일은 브라우저에 저장해 두고 다시 씀
        PlayerSettings.runInBackground = true;
        MoveOtherSamples(true);
        try
        {
            BuildPlayerOptions options = new BuildPlayerOptions
            {
                scenes = new[] { SCENE_PATH },
                locationPathName = outFolder,
                target = BuildTarget.WebGL,
                options = BuildOptions.None,
            };
            UnityEditor.Build.Reporting.BuildReport report = BuildPipeline.BuildPlayer(options);
            Debug.Log("WebGL 빌드 결과: " + report.summary.result + " · 크기 " + (report.summary.totalSize / 1048576) + "MB · " + outFolder);
        }
        finally
        {
            MoveOtherSamples(false); // 빌드가 실패해도 시험 자료는 꼭 되돌림
        }
    }

    // 셰이더 조합마다 재질 파일을 하나씩 Resources에 저장 (Resources 안 재질은 빌드에 늘 들어가서, 그 조합도 같이 들어감)
    private static void SaveVariantMaterials()
    {
        if (!AssetDatabase.IsValidFolder(VARIANT_FOLDER)) AssetDatabase.CreateFolder("Assets/Resources", "ShaderVariants");
        Shader standard = Shader.Find("Standard");
        for (int i = 0; i < STANDARD_KEYWORDS.Length; i++)
        {
            Material material = new Material(standard);
            foreach (string keyword in STANDARD_KEYWORDS[i]) material.EnableKeyword(keyword);
            AssetDatabase.CreateAsset(material, VARIANT_FOLDER + "/Standard" + i + ".mat");
        }
        for (int i = 0; i < EXTRA_SHADERS.Length; i++)
        {
            AssetDatabase.CreateAsset(new Material(Shader.Find(EXTRA_SHADERS[i])), VARIANT_FOLDER + "/Extra" + i + ".mat");
        }
        AssetDatabase.SaveAssets();
    }

    // 장면의 RetroMap에 화면 효과 셰이더 모음·시연 자료 이름을 넣고 저장
    private static void PrepareScene()
    {
        EditorSceneManager.OpenScene(SCENE_PATH);
        RetroMap map = UnityEngine.Object.FindAnyObjectByType<RetroMap>();
        map.postResources = AssetDatabase.LoadAssetAtPath<PostProcessResources>("Packages/com.unity.postprocessing/PostProcessing/PostProcessResources.asset");
        map.sampleName = DEMO_SAMPLE;
        EditorUtility.SetDirty(map);
        EditorSceneManager.SaveScene(EditorSceneManager.GetActiveScene());
    }

    // 시연 자료가 아닌 시험 자료를 Assets 밖으로 옮기거나(true) 되돌림(false)
    private static void MoveOtherSamples(bool isMovingOut)
    {
        string streaming = Path.Combine(Application.dataPath, "StreamingAssets");
        string hold = Path.Combine(Directory.GetCurrentDirectory(), HOLD_FOLDER);
        string from = isMovingOut ? streaming : hold;
        string to = isMovingOut ? hold : streaming;
        if (!Directory.Exists(from)) return;
        Directory.CreateDirectory(to);
        foreach (string file in Directory.GetFiles(from, "sample-*"))
        {
            string name = Path.GetFileName(file);
            if (isMovingOut && name.StartsWith(DEMO_SAMPLE + ".")) continue; // 시연 자료는 그대로 둠
            File.Move(file, Path.Combine(to, name));
        }
        AssetDatabase.Refresh();
    }

    // 명령줄에서 "-이름 값"의 값. 없으면 null
    private static string Argument(string name)
    {
        string[] args = Environment.GetCommandLineArgs();
        for (int i = 0; i + 1 < args.Length; i++)
        {
            if (args[i] == name) return args[i + 1];
        }
        return null;
    }
}
