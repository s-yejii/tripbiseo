// 카메라를 키보드·마우스로 움직임 (Main Camera에 붙어 있음)
//   W A S D : 앞·왼쪽·뒤·오른쪽      Q E : 아래·위      Shift : 빠르게
//   마우스 오른쪽 버튼을 누른 채 움직이기 : 둘러보기      마우스 휠 : 앞뒤로 빨리 가기
// 땅속으로 들어가지 않게, 항상 땅에서 2m 위보다 높게 둠
using UnityEngine;

public class CameraMove : MonoBehaviour
{
    private const float SPEED = 60f;        // 1초에 가는 거리(m)
    private const float FAST_TIMES = 6f;    // Shift를 누르면 몇 배 빠르게
    private const float WHEEL_STEP_M = 80f; // 휠 한 칸에 가는 거리
    private const float LOOK_SPEED = 3f;    // 마우스로 둘러보는 빠르기
    private const float MIN_HEIGHT_M = 2f;  // 땅에서 최소 이만큼 위

    private RetroMap map;
    private float turn;  // 좌우로 돌린 각도
    private float pitch; // 위아래로 숙인 각도

    private void Start()
    {
        map = FindAnyObjectByType<RetroMap>();
        turn = transform.eulerAngles.y;
        pitch = transform.eulerAngles.x;
    }

    // Update는 유니티가 화면을 한 번 그릴 때마다 불러 줌. Time.deltaTime = 지난번 그린 뒤 흐른 시간(초)
    private void Update()
    {
        if (Input.GetMouseButton(1)) // 1 = 마우스 오른쪽 버튼
        {
            turn += Input.GetAxis("Mouse X") * LOOK_SPEED;
            pitch = Mathf.Clamp(pitch - Input.GetAxis("Mouse Y") * LOOK_SPEED, -85, 85);
            transform.rotation = Quaternion.Euler(pitch, turn, 0);
        }

        // 누른 키를 방향으로 바꿈. GetAxis("Horizontal")은 A = −1, D = +1 / "Vertical"은 S = −1, W = +1
        Vector3 move = transform.right * Input.GetAxis("Horizontal") + transform.forward * Input.GetAxis("Vertical");
        if (Input.GetKey(KeyCode.E)) move += Vector3.up;
        if (Input.GetKey(KeyCode.Q)) move += Vector3.down;
        float speed = Input.GetKey(KeyCode.LeftShift) ? SPEED * FAST_TIMES : SPEED;

        Vector3 position = transform.position + move * speed * Time.deltaTime;
        position += transform.forward * Input.GetAxis("Mouse ScrollWheel") * 10 * WHEEL_STEP_M; // 휠 한 칸 = 0.1

        if (map != null && map.Ground != null)
        {
            position.y = Mathf.Max(position.y, map.Ground.HeightAt(position.x, position.z) + MIN_HEIGHT_M);
        }
        transform.position = position;
    }
}
