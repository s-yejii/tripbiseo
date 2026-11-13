// 물결이 흘러가 보이게: 강물 재질의 잔물결 그림 두 장을 천천히, 서로 다른 방향으로 밀어 줌
using UnityEngine;

public class WaterFlow : MonoBehaviour
{
    private Material material;
    private float flow; // 1초에 그림이 밀리는 양 (그림 한 장 = 1)

    public void Set(float flowPerSecond)
    {
        flow = flowPerSecond;
        material = GetComponent<MeshRenderer>().sharedMaterial;
    }

    private void Update()
    {
        if (material == null) return;
        float moved = Time.time * flow;
        material.mainTextureOffset = new Vector2(moved, moved * 0.6f);                        // 큰 물결 (첫째 그림의 위치는 이 값을 같이 씀)
        material.SetTextureOffset("_DetailAlbedoMap", new Vector2(-moved * 1.7f, moved * 1.1f)); // 작은 물결은 반대쪽으로
    }
}
