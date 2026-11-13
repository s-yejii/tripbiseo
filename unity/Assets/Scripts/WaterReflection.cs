// 강물·유리에 주변이 비치게: 카메라 근처 물 위에서 사방을 한 번 찍어(큐브맵) 반사 사진으로 씀
//   매끈한 재질(강물·유리벽)이 이 사진을 비춰 보임 → 밤에 다리·아파트 불빛이 강물에 번져 보임
//   매 순간 찍으면 느려서: 카메라가 많이 움직였거나 하늘 시각이 바뀌었을 때만 다시 찍음
//   다시 찍을 때도 여섯 면을 한 번에 찍으면 그 순간 화면이 툭 끊겨서(깜빡) 한 장면에 한 면씩 나눠 찍음
using UnityEngine;
using UnityEngine.Rendering;

public class WaterReflection : MonoBehaviour
{
    private const int RESOLUTION = 128;      // 반사 사진 한 면 크기 (작아도 물결에 흐려져서 충분)
    private const float MOVE_M = 120f;       // 카메라가 이만큼 움직이면 다시 찍음
    private const float INTERVAL_S = 1.5f;   // 다시 찍는 가장 짧은 간격(초)
    private const float EYE_ABOVE_WATER_M = 6f;
    private const int FACE_COUNT = 6;        // 큐브맵 면 수

    private ReflectionProbe probe;  // 반사 사진을 물체들에 나눠 주는 부품
    private Camera shooter;         // 사방을 찍는 보조 카메라 (화면에는 안 나옴)
    private RenderTexture picture;  // 사방 사진 (큐브맵: 정육면체 여섯 면)
    private float waterLevel;
    private Vector3 lastSpot = Vector3.one * 1e9f;
    private float lastTime = -100;
    private bool isDirty = true;    // 다시 찍어야 하는지
    private int nextFace = FACE_COUNT; // 나눠 찍는 중 다음에 찍을 면 번호 (FACE_COUNT면 다 찍음)

    // 반사 카메라를 만듦 (RetroMap이 부름)
    public void Show(float waterLevel, float mapSizeM)
    {
        this.waterLevel = waterLevel;
        if (probe == null) MakeParts();
        probe.size = new Vector3(mapSizeM * 2, 4000, mapSizeM * 2); // 지도 전체의 매끈한 물체가 이 사진을 씀
        isDirty = true;
    }

    private void MakeParts()
    {
        GameObject holder = new GameObject("Reflection");
        holder.transform.SetParent(transform, false);
        probe = holder.AddComponent<ReflectionProbe>();
        probe.mode = ReflectionProbeMode.Custom; // 우리가 찍은 사진을 씀
        probe.importance = 10;

        picture = new RenderTexture(RESOLUTION, RESOLUTION, 16, RenderTextureFormat.DefaultHDR); // HDR: 밝은 불빛이 반사에서도 밝게
        picture.dimension = TextureDimension.Cube;
        picture.useMipMap = true;        // 흐린 반사(덜 매끈한 재질)용 작은 사진도
        picture.autoGenerateMips = true;
        probe.customBakedTexture = picture;

        shooter = holder.AddComponent<Camera>();
        shooter.enabled = false;  // 우리가 부를 때만 찍음
        shooter.farClipPlane = 6000;
        shooter.nearClipPlane = 1;
        shooter.clearFlags = CameraClearFlags.Skybox;
    }

    // 하늘 시각이 바뀌면 다시 찍게 표시 (SkyClock이 부름)
    public void MarkDirty()
    {
        isDirty = true;
    }

    // 지금 카메라 근처에서 바로 찍음 (캡처 도구도 부름)
    public void RenderNow()
    {
        if (probe == null || Camera.main == null) return;
        Vector3 eye = Camera.main.transform.position;
        Vector3 spot = new Vector3(eye.x, waterLevel + EYE_ABOVE_WATER_M, eye.z);
        shooter.transform.position = spot;
        shooter.RenderToCubemap(picture); // 여섯 방향을 찍어 큐브맵에 담음
        lastSpot = spot;
        lastTime = Time.time;
        isDirty = false;
    }

    private void Update()
    {
        if (probe == null || Camera.main == null) return;
        if (nextFace < FACE_COUNT)
        {
            shooter.RenderToCubemap(picture, 1 << nextFace); // 1 << n: n번 면만 찍으라는 표시 (0번 = 1, 1번 = 2, 2번 = 4 …)
            nextFace++;
            return;
        }
        Vector3 eye = Camera.main.transform.position;
        bool hasMoved = Vector2.Distance(new Vector2(eye.x, eye.z), new Vector2(lastSpot.x, lastSpot.z)) > MOVE_M;
        if (!hasMoved && !isDirty) return;
        if (Time.time - lastTime < INTERVAL_S) return;
        StartRender(eye);
    }

    // 나눠 찍기 시작: 자리를 옮기고 다음 장면부터 한 면씩
    private void StartRender(Vector3 eye)
    {
        Vector3 spot = new Vector3(eye.x, waterLevel + EYE_ABOVE_WATER_M, eye.z);
        shooter.transform.position = spot;
        lastSpot = spot;
        lastTime = Time.time;
        isDirty = false;
        nextFace = 0;
    }
}
