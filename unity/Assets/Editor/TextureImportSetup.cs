// (편집기 도구) Resources/Textures 안의 질감 사진을 들여올 때 설정을 자동으로 맞춤. 게임에는 들어가지 않는 코드
//   "이름_Normal.jpg" → 노멀 맵(표면의 울퉁불퉁함)으로, 나머지는 색 그림으로. 둘 다 되풀이해서 붙이고 비스듬히 봐도 덜 뭉개지게
using UnityEditor;

public class TextureImportSetup : AssetPostprocessor // AssetPostprocessor: 파일을 들여올 때 유니티가 불러 주는 도구
{
    private void OnPreprocessTexture()
    {
        if (!assetPath.Contains("Resources/Textures/")) return;
        TextureImporter importer = (TextureImporter)assetImporter;
        if (assetPath.EndsWith("_Normal.jpg")) importer.textureType = TextureImporterType.NormalMap;
        importer.wrapMode = UnityEngine.TextureWrapMode.Repeat;
        importer.anisoLevel = 8;
        importer.maxTextureSize = 1024;
    }
}
