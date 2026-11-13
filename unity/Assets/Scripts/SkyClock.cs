// 하늘 시계: 재생 시각의 해 위치(웹이 날짜·시각·위경도로 계산해 보냄)로
//   햇빛 방향·세기·색, 하늘 밝기, 주변 빛, 안개 색, 밤 창문 불빛을 바꿈
//   해가 지평선 근처면 노을빛, 해가 지면(-6도 아래) 밤: 하늘이 어두워지고 창문·간판에 불이 켜짐
using System.Collections.Generic;
using UnityEngine;

public class SkyClock : MonoBehaviour
{
    private const float DAY_ALTITUDE = 8f;     // 해가 이보다 높으면 완전히 낮
    private const float NIGHT_ALTITUDE = -6f;  // 이보다 낮으면 완전히 밤 (해 진 뒤 30분쯤)
    private const float LIGHTS_ON_ALTITUDE = 2f;   // 해가 이 높이 밑으로 가면 창문 불이 켜지기 시작
    private const float LIGHTS_FULL_ALTITUDE = -5f; // 이 높이면 불이 다 켜짐
    private const float LOWEST_LIGHT_ALTITUDE = 5f; // 햇빛이 이보다 낮은 각도로는 안 비추게 (땅 밑에서 비추면 이상해서)
    private const float WINDOW_GLOW = 1.4f;    // 밤 창문 불빛 세기
    private const float KEY_STEP_MINUTES = 15f; // [ ] 키를 한 번 누를 때 시각을 옮기는 양
    private const float HALF_DAY_MINUTES = 720f; // 밤낮을 끄면: 지금 시각 앞뒤 반나절 안에서 해가 가장 높은 때 하늘로
    private static readonly Vector2 MOON = new Vector2(35, 160); // 밤에 빛이 오는 곳 { 높이, 방향 } (달빛 느낌)

    private static readonly Color NOON_LIGHT = new Color(1f, 0.96f, 0.88f);
    private static readonly Color SUNSET_LIGHT = new Color(1f, 0.58f, 0.32f);
    private static readonly Color MOON_LIGHT = new Color(0.55f, 0.65f, 1f);
    private static readonly Color DAY_FOG = new Color(0.74f, 0.82f, 0.90f);
    private static readonly Color DUSK_FOG = new Color(0.82f, 0.60f, 0.50f);
    private static readonly Color NIGHT_FOG = new Color(0.05f, 0.07f, 0.13f);
    // 주변 빛 { 위(하늘), 옆, 아래(땅) }
    private static readonly Color[] DAY_AMBIENT = { new Color(0.70f, 0.77f, 0.88f), new Color(0.62f, 0.63f, 0.62f), new Color(0.42f, 0.40f, 0.36f) }; // 그늘이 시커멓지 않게 (위성 사진에 이미 그림자가 찍혀 있어서 겹치면 너무 어두움)
    private static readonly Color[] NIGHT_AMBIENT = { new Color(0.20f, 0.24f, 0.40f), new Color(0.14f, 0.15f, 0.22f), new Color(0.08f, 0.08f, 0.09f) }; // 도시 밤하늘은 불빛에 밝아서 아주 까맣진 않음
    private static readonly Color STREET_GLOW = new Color(1f, 0.78f, 0.50f) * 0.28f; // 밤에 가로등이 비춘 길바닥 밝기 (전구색)

    private SunData sun;
    private Light sunLight;
    private Material sky;
    private List<Material> nightMaterials;
    private List<Material> streetMaterials; // 밤에 가로등 빛을 받은 것처럼 은은하게 밝아질 길 재질들
    private float minute; // 지금 하늘의 시각 (여행 몇 분째)
    private bool isDayNightOn = true; // 시각에 맞춰 밤낮이 바뀌는지 (끄면 늘 낮)
    private float lastReflectionMinute = -9999; // 강물 반사를 마지막으로 다시 찍게 한 시각
    private const float REFLECTION_STEP_MINUTES = 10f; // 하늘 시각이 이만큼 바뀌면 강물 반사를 다시 찍음
    private static readonly Color DUSK_CLOUD = new Color(1f, 0.68f, 0.52f);
    private static readonly Color NIGHT_CLOUD = new Color(0.16f, 0.18f, 0.24f, 0.7f);

    // 해 위치 표와 밤에 불을 켤 재질들을 받음 (RetroMap이 부름)
    public void Show(SunData sun, List<Material> nightMaterials, List<Material> streetMaterials)
    {
        this.sun = sun;
        this.nightMaterials = nightMaterials;
        this.streetMaterials = streetMaterials;
        sunLight = RenderSettings.sun != null ? RenderSettings.sun : FindAnyObjectByType<Light>();
        if (sky == null) MakeSky();
    }

    // 하늘: 유니티 기본 하늘(해 위치에 따라 낮·노을·밤 색을 알아서 그림)을 복사해서 씀
    //   장면에 하늘이 빠져 있으면 카메라가 단색(짙은 파랑)만 칠해서, 여기서 늘 하늘을 그리게 맞춤
    private void MakeSky()
    {
        Material saved = RenderSettings.skybox != null ? RenderSettings.skybox : Resources.Load<Material>("Sky");
        sky = saved != null ? new Material(saved) : new Material(Shader.Find("Skybox/Procedural")); // 복사본을 바꿈 (원본 하늘 파일이 바뀌지 않게)
        sky.SetFloat("_AtmosphereThickness", 0.9f);
        RenderSettings.skybox = sky;
        if (sunLight != null) RenderSettings.sun = sunLight; // 하늘에 그릴 해 = 이 햇빛의 방향
        if (Camera.main != null) Camera.main.clearFlags = CameraClearFlags.Skybox;
    }

    // 이 지도에 해 위치 표가 있는지 (없으면 늘 낮으로 둠)
    public bool HasSun()
    {
        return sun != null && sun.minutes != null && sun.minutes.Length >= 2;
    }

    // 웹의 [밤낮] 버튼: 1 = 시각에 맞춰 밤낮이 바뀜, 0 = 늘 낮
    //   웹(SendMessage)이 숫자를 넘기면 float로 오므로 float로 받음
    public void SetDayNight(float isOn)
    {
        isDayNightOn = isOn > 0.5f;
        SetMinute(minute);
    }

    // 여행 몇 분째의 하늘로 바꿈
    public void SetMinute(float minute)
    {
        if (!HasSun()) return;
        this.minute = minute;
        float skyMinute = isDayNightOn ? minute : BrightestMinuteNear(minute); // 밤낮을 끄면 그날 한낮 하늘
        float altitude = Lookup(sun.altitudes, skyMinute, false);
        float azimuth = Lookup(sun.azimuths, skyMinute, true);
        float day = Mathf.InverseLerp(NIGHT_ALTITUDE, DAY_ALTITUDE, altitude);             // 0 밤 ~ 1 낮
        float dusk = Mathf.Clamp01(1 - Mathf.Abs(altitude - 1) / 9);                         // 해가 지평선 근처(-8 ~ 10도)일수록 1
        float lights = Mathf.InverseLerp(LIGHTS_ON_ALTITUDE, LIGHTS_FULL_ALTITUDE, altitude); // 0 꺼짐 ~ 1 다 켜짐
        SetSunLight(altitude, azimuth, day, dusk);
        SetAir(day, dusk);
        SetWindowLights(lights);
        SetClouds(day, dusk);
        if (Mathf.Abs(skyMinute - lastReflectionMinute) >= REFLECTION_STEP_MINUTES)
        {
            WaterReflection reflection = GetComponent<WaterReflection>();
            if (reflection != null) reflection.MarkDirty();
            lastReflectionMinute = skyMinute;
        }
    }

    // minute 앞뒤 반나절 안에서 해가 가장 높은 시각
    private float BrightestMinuteNear(float minute)
    {
        float best = minute;
        float bestAltitude = -90;
        for (int i = 0; i < sun.minutes.Length; i++)
        {
            if (Mathf.Abs(sun.minutes[i] - minute) > HALF_DAY_MINUTES) continue;
            if (sun.altitudes[i] <= bestAltitude) continue;
            bestAltitude = sun.altitudes[i];
            best = sun.minutes[i];
        }
        return best;
    }

    // 구름 색: 낮 흰색 → 노을 주황 → 밤 짙은 회색(조금 투명)
    private void SetClouds(float day, float dusk)
    {
        CloudLayer clouds = GetComponent<CloudLayer>();
        if (clouds == null) return;
        Color dayCloud = Color.Lerp(Color.white, DUSK_CLOUD, dusk);
        clouds.SetColor(Color.Lerp(NIGHT_CLOUD, dayCloud, day));
        NightSky nightSky = GetComponent<NightSky>();
        if (nightSky != null) nightSky.SetNight(Mathf.Clamp01(1 - day * 1.5f)); // 해 지고 조금 지나야 별이 보임
    }

    // [ ] 키: 시각을 15분씩 앞뒤로 (경로가 없는 지도에서도 낮·노을·밤을 볼 수 있게)
    private void Update()
    {
        if (!HasSun()) return;
        if (Input.GetKeyDown(KeyCode.RightBracket)) SetMinute(minute + KEY_STEP_MINUTES);
        if (Input.GetKeyDown(KeyCode.LeftBracket)) SetMinute(minute - KEY_STEP_MINUTES);
    }

    // 햇빛: 낮에는 해 쪽에서, 밤에는 달 쪽에서 약하게
    private void SetSunLight(float altitude, float azimuth, float day, float dusk)
    {
        if (sunLight == null) return;
        bool isNight = day < 0.2f;
        float height = isNight ? MOON.x : Mathf.Max(altitude, LOWEST_LIGHT_ALTITUDE);
        float direction = isNight ? MOON.y : azimuth;
        sunLight.transform.rotation = Quaternion.LookRotation(-DirectionTo(height, direction)); // 빛은 해에서 땅 쪽으로 감
        Color sunColor = Color.Lerp(NOON_LIGHT, SUNSET_LIGHT, dusk);
        sunLight.color = isNight ? MOON_LIGHT : sunColor;
        sunLight.intensity = isNight ? 0.22f : Mathf.Lerp(0.25f, 1.0f, day);
    }

    // 하늘 밝기·주변 빛·안개 색
    private void SetAir(float day, float dusk)
    {
        if (sky != null && sky.HasProperty("_Exposure")) sky.SetFloat("_Exposure", Mathf.Lerp(0.06f, 1.3f, day));
        RenderSettings.reflectionIntensity = Mathf.Lerp(0.12f, 1f, day); // 물·유리에 비치는 하늘 밝기 (밤에 강물이 허옇게 빛나지 않게)
        RenderSettings.ambientSkyColor = Color.Lerp(NIGHT_AMBIENT[0], DAY_AMBIENT[0], day);
        RenderSettings.ambientEquatorColor = Color.Lerp(NIGHT_AMBIENT[1], DAY_AMBIENT[1], day);
        RenderSettings.ambientGroundColor = Color.Lerp(NIGHT_AMBIENT[2], DAY_AMBIENT[2], day);
        Color dayFog = Color.Lerp(DAY_FOG, DUSK_FOG, dusk);
        RenderSettings.fogColor = Color.Lerp(NIGHT_FOG, dayFog, day);
        if (Camera.main != null) Camera.main.backgroundColor = RenderSettings.fogColor;
    }

    // 창문·간판 불빛 (재질의 "스스로 내는 빛" 세기)
    private void SetWindowLights(float lights)
    {
        if (nightMaterials == null) return;
        Color glow = Color.white * (lights * WINDOW_GLOW);
        foreach (Material material in nightMaterials) material.SetColor("_EmissionColor", glow);
        if (streetMaterials == null) return;
        foreach (Material material in streetMaterials) material.SetColor("_EmissionColor", STREET_GLOW * lights);
    }

    // 표에서 minute 때의 값. 표의 두 칸 사이면 섞음 (isAngle: 359도와 1도 사이처럼 한 바퀴 넘는 각도도 바르게 섞음)
    private float Lookup(float[] values, float minute, bool isAngle)
    {
        float[] minutes = sun.minutes;
        if (minute <= minutes[0]) return values[0];
        int i = 0;
        while (i < minutes.Length - 2 && minutes[i + 1] < minute) i++;
        float rate = Mathf.Clamp01((minute - minutes[i]) / (minutes[i + 1] - minutes[i]));
        return isAngle ? Mathf.LerpAngle(values[i], values[i + 1], rate) : Mathf.Lerp(values[i], values[i + 1], rate);
    }

    // 높이·방향(도) → 그쪽을 가리키는 방향 (북 = +z, 동 = +x, 위 = +y)
    private static Vector3 DirectionTo(float altitude, float azimuth)
    {
        float up = altitude * Mathf.Deg2Rad;
        float around = azimuth * Mathf.Deg2Rad;
        return new Vector3(Mathf.Sin(around) * Mathf.Cos(up), Mathf.Sin(up), Mathf.Cos(around) * Mathf.Cos(up));
    }
}
