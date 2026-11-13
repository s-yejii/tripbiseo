// 그래픽 카드 아끼기: 카메라가 안 움직이고 재생도 멈춰 있으면 화면을 덜 자주 그림
//   OnDemandRendering.renderFrameInterval = N → N번에 한 번만 그림 (계산은 매번 하지만 그리기를 건너뜀)
//   마우스·키보드로 움직이거나 재생이 시작되면 바로 다시 매번 그림
using UnityEngine;
using UnityEngine.Rendering;

public class RenderSaver : MonoBehaviour
{
    private const int IDLE_INTERVAL = 10;   // 가만히 있을 때: 10번에 한 번 (30장/초 → 3장/초)
    private const float IDLE_SECONDS = 0.5f; // 이만큼 안 움직이면 "가만히 있음"
    private const float MOVE_EPSILON = 0.001f;

    private Vector3 lastPosition;
    private Quaternion lastRotation;
    private float stillTime;

    private void Update()
    {
        if (Camera.main == null) return;
        Transform eye = Camera.main.transform;
        bool hasMoved = Vector3.Distance(eye.position, lastPosition) > MOVE_EPSILON || Quaternion.Angle(eye.rotation, lastRotation) > 0.01f;
        lastPosition = eye.position;
        lastRotation = eye.rotation;
        stillTime = hasMoved ? 0 : stillTime + Time.unscaledDeltaTime;
        OnDemandRendering.renderFrameInterval = stillTime > IDLE_SECONDS ? IDLE_INTERVAL : 1;
    }
}
