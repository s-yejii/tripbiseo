// 재질(색·질감) 모음: 이름을 주면 재질을 돌려줌
// 왜 따로 두나: 예전에는 "Tools > Retro > 처음 준비"를 눌러 재질 파일을 만들어 둬야 했고,
//   새 재질이 생겼는데 그걸 안 누르면 재질이 비어서 그 뒤 그리기가 통째로 멈췄음 (타워·건물이 안 보이던 원인)
//   → 이제는 재질 파일이 있으면 그걸 쓰고, 없으면 여기 적힌 색으로 그 자리에서 만들어 씀. ▶만 누르면 됨
using System.Collections.Generic;
using UnityEngine;

public static class Palette
{
    // 재질 이름 → 색
    private static readonly Dictionary<string, Color> COLORS = new Dictionary<string, Color>
    {
        { "Ground", new Color(0.56f, 0.62f, 0.47f) },    // 땅 (위성 사진을 못 받았을 때)
        { "FarGround", new Color(0.50f, 0.54f, 0.48f) }, // 지도 바깥 바닥
        { "Road", new Color(0.34f, 0.35f, 0.37f) },      // 찻길 (아스팔트)
        { "Pole", new Color(0.38f, 0.39f, 0.40f) },      // 가로등·전봇대
        { "Path", new Color(0.74f, 0.69f, 0.60f) },      // 걷는 길 (흙·보도블록)
        { "RoadLine", new Color(0.93f, 0.76f, 0.22f) },  // 찻길 가운데 노란 선
        { "RoadMark", new Color(0.92f, 0.92f, 0.90f) },  // 흰 차선·횡단보도
        { "Sidewalk", new Color(0.72f, 0.71f, 0.68f) },  // 인도 (그림이 없을 때 색)
        { "Curb", new Color(0.70f, 0.70f, 0.68f) },      // 연석·축대 (화강석)
        { "Wire", new Color(0.12f, 0.12f, 0.13f) },      // 전선
        { "Route", new Color(0.16f, 0.46f, 1.00f) },     // 걸은 경로 선
        { "Pin", new Color(0.93f, 0.36f, 0.24f) },       // 다녀온 곳 핀·걷는 사람
        { "RoofTile", new Color(0.50f, 0.27f, 0.22f) },  // 단독주택 기와지붕
        { "HanokRoof", new Color(0.25f, 0.26f, 0.28f) }, // 한옥 기와 (짙은 회색)
        { "Glass", Color.white },                        // 업무용 건물 유리벽 (그림은 실행할 때 붙임)
        { "Wall", Color.white },                         // 벽 (그림은 실행할 때 붙임)
        { "Concrete", new Color(0.62f, 0.62f, 0.60f) },  // 기초벽·옥상 난간
        { "RoofGray", new Color(0.55f, 0.56f, 0.57f) },
        { "RoofGreen", new Color(0.30f, 0.52f, 0.40f) }, // 옥상 방수 페인트
        { "RoofDark", new Color(0.36f, 0.35f, 0.36f) },
        { "Props", Color.white },                        // 소품 (색은 꼭짓점마다 따로)
        { "Tree", Color.white },                         // 나무 (색은 꼭짓점마다 따로)
        { "Water", new Color(0.12f, 0.22f, 0.25f) },     // 강물 (짙은 청록, 하늘이 비침)
        { "RideLine", new Color(1.00f, 0.55f, 0.15f) },  // 지하철·택시로 지나간 구간 선
        { "BoatLine", new Color(0.88f, 0.96f, 1.00f) },  // 배가 지나간 물길 선
        { "SubwayLine", new Color(0.56f, 0.40f, 0.95f) }, // 지하철 구간 선 (땅속이라 하늘로 휜 선)
        { "BusLine", new Color(0.16f, 0.66f, 0.36f) },   // 버스 구간 띠 (서울 지선버스 초록)
    };

    private static readonly Dictionary<string, Material> made = new Dictionary<string, Material>(); // 그 자리에서 만든 재질 (한 번만 만들게)

    // 모든 재질 이름 (처음 준비 도구가 파일로 저장할 때 씀)
    public static IEnumerable<string> Names()
    {
        return COLORS.Keys;
    }

    // 이름으로 재질 찾기: 저장된 재질 파일(Assets/Resources)이 있으면 그것, 없으면 만들어서
    public static Material Get(string name)
    {
        Material saved = Resources.Load<Material>(name);
        if (saved != null) return saved;
        if (!made.ContainsKey(name)) made[name] = Make(name);
        return made[name];
    }

    // 재질 새로 만들기
    public static Material Make(string name)
    {
        if (name == "Tree" || name == "Props") return new Material(Shader.Find("Retro/VertexColor")); // 하나하나 색이 달라서 꼭짓점 색 셰이더

        Material material = new Material(Shader.Find("Standard"));
        material.color = COLORS[name];
        material.SetFloat("_Glossiness", 0.05f); // 번들거림 줄이기
        if (name == "Glass")
        {
            material.SetFloat("_Glossiness", 0.8f); // 매끈해서 하늘빛이 비침
            material.SetFloat("_Metallic", 0.35f);
        }
        if (name == "Ground") material.EnableKeyword("_DETAIL_MULX2"); // 땅에 잔무늬를 겹쳐 그리는 기능
        bool isMarker = name == "Route" || name == "RideLine" || name == "BoatLine" || name == "Pin" || name == "SubwayLine" || name == "BusLine";
        if (isMarker) // 경로 선·핀은 밤에도 보이게 스스로 빛을 조금 냄
        {
            material.EnableKeyword("_EMISSION");
            material.SetColor("_EmissionColor", COLORS[name] * 0.6f);
        }
        if (name == "Water")
        {
            material.SetFloat("_Glossiness", 0.93f); // 물은 매끈해서 하늘·햇빛이 비침
            material.SetFloat("_Metallic", 0.2f);
        }
        return material;
    }
}
