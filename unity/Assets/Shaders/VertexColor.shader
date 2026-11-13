// 꼭짓점에 적힌 색으로 칠하는 셰이더 (나무용). 셰이더 = 물체를 화면에 어떤 색으로 그릴지 정하는 작은 프로그램
// 유니티 기본 재질(Standard)은 물체 전체를 한 색으로만 칠해서, 나무마다 색이 다른 숲을 한 덩어리로 그리려고 따로 만듦
Shader "Retro/VertexColor"
{
    SubShader
    {
        Tags { "RenderType" = "Opaque" }

        CGPROGRAM
        // surf 함수로 색을 정하고, 빛·그림자 계산은 유니티 기본(Lambert)에 맡김. addshadow = 그림자도 드리움
        #pragma surface surf Lambert addshadow

        struct Input
        {
            float4 color : COLOR; // TreeMaker.cs가 꼭짓점마다 넣은 색
        };

        void surf(Input IN, inout SurfaceOutput o)
        {
            o.Albedo = IN.color.rgb;
        }
        ENDCG
    }
    FallBack "Diffuse"
}
