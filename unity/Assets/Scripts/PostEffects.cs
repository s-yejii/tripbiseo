// 화면 마무리 효과 (유니티 Post Processing 패키지): 게임처럼 보이게 하는 마지막 손질
//   빛 번짐(Bloom): 밤 창문·가로등처럼 밝은 곳이 주변으로 번져 빛나 보임
//   모서리 그늘(AO): 건물이 땅과 만나는 곳, 골목 안쪽이 살짝 어두워져 입체감이 생김
//   색 보정(ACES): 영화·게임에서 쓰는 밝기 곡선. 밝은 곳이 하얗게 날아가지 않고 색이 진해짐
using UnityEngine;
using UnityEngine.Rendering.PostProcessing;

public static class PostEffects
{
    private const float BLOOM_INTENSITY = 1.6f; // 빛 번짐 세기
    private const float BLOOM_THRESHOLD = 1.0f; // 이 밝기(1 = 흰색)를 넘는 곳만 번짐 → 낮의 흰 벽은 안 번지고 밤 불빛만
    private const float AO_INTENSITY = 0.7f;    // 모서리 그늘 진하기

    // 카메라에 효과를 붙임 (이미 붙어 있으면 그대로). resources: 패키지에 들어 있는 효과용 셰이더 모음
    public static void Apply(Camera camera, PostProcessResources resources)
    {
        if (camera == null || resources == null) return;
        if (camera.GetComponent<PostProcessLayer>() != null) return;

        PostProcessLayer layer = camera.gameObject.AddComponent<PostProcessLayer>(); // 이 카메라 화면에 효과를 입힘
        layer.Init(resources);
        layer.volumeTrigger = camera.transform;
        layer.volumeLayer = ~0; // 모든 층의 효과 상자를 봄
        layer.antialiasingMode = PostProcessLayer.Antialiasing.SubpixelMorphologicalAntialiasing; // 계단처럼 깨진 가장자리를 부드럽게

        GameObject holder = new GameObject("PostEffects");
        PostProcessVolume volume = holder.AddComponent<PostProcessVolume>(); // 효과 설정을 담는 상자
        volume.isGlobal = true; // 어디에 있든 화면 전체에 적용
        volume.profile = MakeProfile();
    }

    // 효과 설정 묶음
    private static PostProcessProfile MakeProfile()
    {
        PostProcessProfile profile = ScriptableObject.CreateInstance<PostProcessProfile>();

        Bloom bloom = profile.AddSettings<Bloom>();
        bloom.intensity.Override(BLOOM_INTENSITY);
        bloom.threshold.Override(BLOOM_THRESHOLD);
        bloom.softKnee.Override(0.6f);

        AddOcclusion(profile);
        AddColorAndVignette(profile);
        return profile;
    }

    private static void AddOcclusion(PostProcessProfile profile)
    {
        AmbientOcclusion occlusion = profile.AddSettings<AmbientOcclusion>();
        // 웹(WebGL)은 계산 셰이더를 못 써서 가벼운 방식으로
        occlusion.mode.Override(SystemInfo.supportsComputeShaders ? AmbientOcclusionMode.MultiScaleVolumetricObscurance : AmbientOcclusionMode.ScalableAmbientObscurance);
        occlusion.intensity.Override(AO_INTENSITY);
    }

    private static void AddColorAndVignette(PostProcessProfile profile)
    {
        ColorGrading grading = profile.AddSettings<ColorGrading>();
        grading.tonemapper.Override(Tonemapper.ACES);
        grading.postExposure.Override(0.35f); // ACES는 살짝 어두워져서 조금 밝힘
        grading.saturation.Override(8f);      // 색을 조금 진하게

        Vignette vignette = profile.AddSettings<Vignette>(); // 화면 가장자리를 살짝 어둡게 (사진처럼)
        vignette.intensity.Override(0.22f);
    }
}
