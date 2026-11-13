// (편집기 도구) 장면을 그림 파일로 찍음. 화면을 열지 않고도 결과를 확인하려고 씀. 게임에는 들어가지 않는 코드
// 명령줄: Unity -batchmode -executeMethod Capture.Run -shot "파일.png" -eye "x,땅에서 높이,z" -look "x,땅에서 높이,z"
//   여러 장: -shots "목록.txt" (한 줄에 "파일.png;eye;look") → 지도를 한 번만 만들고 줄마다 찍음
using System;
using System.IO;
using UnityEditor.SceneManagement;
using UnityEngine;

public static class Capture
{
    public static void Run()
    {
        EditorSceneManager.OpenScene("Assets/Scenes/RetroMap.unity");
        RetroMap map = UnityEngine.Object.FindAnyObjectByType<RetroMap>();
        string sample = OptionalArgument("-sample"); // 안 주면 장면에 저장된 이름 그대로
        if (sample != null) map.sampleName = sample;
        map.LoadSample();
        string minute = OptionalArgument("-minute"); // 주면 경로 재생을 그 시각으로 (해 위치·밤 불빛·배 자리 확인용)
        RoutePlayer player = map.GetComponent<RoutePlayer>();
        if (minute != null && player != null) player.Seek(float.Parse(minute));
        if (minute != null) map.GetComponent<SkyClock>().SetMinute(float.Parse(minute)); // 하늘은 경로 끝을 넘은 시각도 (밤 확인용)

        string hide = OptionalArgument("-hide"); // 원인 찾기용: "이름,이름" 물체를 숨기고 찍음
        if (hide != null)
        {
            foreach (string name in hide.Split(','))
            {
                foreach (Transform part in map.transform)
                {
                    if (part.name.StartsWith(name)) part.gameObject.SetActive(false); // 이름이 이걸로 시작하는 물체 전부
                }
            }
        }
        Camera camera = Camera.main;
        string effect = OptionalArgument("-noeffect"); // 원인 찾기용: 화면 효과 하나 끄기 (ao · bloom · all)
        if (effect != null) TurnOffEffect(effect);
        string list = OptionalArgument("-shots");
        if (list == null)
        {
            Shoot(map, camera, Argument("-shot"), Argument("-eye"), Argument("-look"));
            return;
        }
        foreach (string line in File.ReadAllLines(list))
        {
            string[] parts = line.Split(';');
            if (parts.Length == 3) Shoot(map, camera, parts[0], parts[1], parts[2]);
        }
    }

    // 한 장 찍기: eye에서 look을 보고 file로 저장
    private static void Shoot(RetroMap map, Camera camera, string file, string eye, string look)
    {
        camera.transform.position = OnGround(map, ToVector(eye));
        camera.transform.LookAt(OnGround(map, ToVector(look)));
        WaterReflection reflection = map.GetComponent<WaterReflection>();
        if (reflection != null) reflection.RenderNow(); // 카메라 자리에서 강물 반사를 찍어 둠

        RenderTexture target = new RenderTexture(1600, 900, 24);
        camera.targetTexture = target;
        camera.Render();
        RenderTexture.active = target;
        Texture2D picture = new Texture2D(1600, 900, TextureFormat.RGB24, false);
        picture.ReadPixels(new Rect(0, 0, 1600, 900), 0, 0);
        File.WriteAllBytes(file, picture.EncodeToPNG());
        camera.targetTexture = null;
    }

    private static void TurnOffEffect(string effect)
    {
        UnityEngine.Rendering.PostProcessing.PostProcessVolume volume = UnityEngine.Object.FindAnyObjectByType<UnityEngine.Rendering.PostProcessing.PostProcessVolume>();
        if (volume == null) return;
        foreach (UnityEngine.Rendering.PostProcessing.PostProcessEffectSettings settings in volume.profile.settings)
        {
            string name = settings.GetType().Name.ToLower();
            if (effect == "all" || name.Contains(effect)) settings.active = false;
        }
    }

    // 높이(y)를 "땅에서 몇 m 위"로 보고 실제 높이로 바꿈 (카메라가 땅속에 들어가지 않게)
    private static Vector3 OnGround(RetroMap map, Vector3 point)
    {
        point.y += map.Ground.HeightAt(point.x, point.z);
        return point;
    }

    // 명령줄에서 "-이름 값"의 값. 없으면 null
    private static string OptionalArgument(string name)
    {
        string[] args = Environment.GetCommandLineArgs();
        for (int i = 0; i + 1 < args.Length; i++)
        {
            if (args[i] == name) return args[i + 1];
        }
        return null;
    }

    // 명령줄에서 "-이름 값"의 값을 찾음
    private static string Argument(string name)
    {
        string[] args = Environment.GetCommandLineArgs();
        for (int i = 0; i + 1 < args.Length; i++)
        {
            if (args[i] == name) return args[i + 1];
        }
        throw new ArgumentException(name + " 값이 없어요");
    }

    private static Vector3 ToVector(string text)
    {
        string[] parts = text.Split(',');
        return new Vector3(float.Parse(parts[0]), float.Parse(parts[1]), float.Parse(parts[2]));
    }
}
