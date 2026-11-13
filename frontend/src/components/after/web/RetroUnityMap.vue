<script setup>
// ⑤ 웹 회고 3D 지도 (11/13): 유니티로 만든 3D 지도(WebGL)를 이 칸에 띄움
//   유니티 빌드 파일은 public/unity3d/ 에 둠 (Build/webgl.* + StreamingAssets/시연 자료)
//   tripData를 주면 그 여행 지도 자료(웹이 만든 JSON + 바닥 사진)를 그림 (landscape/unityTrip.js가 만듦)
//   tripUrl을 주면 그 주소의 .json + .jpg를 받아 그림
//   유니티 쪽 받는 곳: RetroMap.LoadTrip, RoutePlayer.Play · Pause · Seek (unity/Assets/Scripts)
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  tripUrl: { type: String, default: '' }, // 여행 지도 자료 주소 (확장자 빼고). 예: '/trips3d/1722'
  tripData: { type: Object, default: null }, // 웹이 만든 지도 자료 { json, photo(base64) }
  statusText: { type: String, default: '' }, // 지도 자료를 만드는 중이면 그 안내 글
})
const emit = defineEmits(['ready']) // 3D가 다 뜨면 알려 줌 (부모가 지금 재생 시각·시점을 넘기게)

const BUILD_URL = '/unity3d/Build/webgl'
const STREAMING_URL = '/unity3d/StreamingAssets'
const canvas = ref(null)
const progress = ref(0)       // 불러오기 진행률 (0 ~ 100)
const isReady = ref(false)
const errorText = ref('')
let unity = null              // 유니티 프로그램 (createUnityInstance가 돌려줌)

// <script> 태그로 유니티 로더 파일을 한 번만 불러옴
function loadScript(src) {
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = src
    script.onload = resolve
    script.onerror = () => reject(new Error('3D 지도 파일을 못 불러왔어요'))
    document.body.appendChild(script)
  })
}

onMounted(async () => {
  try {
    if (!window.createUnityInstance) await loadScript(BUILD_URL + '.loader.js')
    const config = {
      dataUrl: BUILD_URL + '.data',
      frameworkUrl: BUILD_URL + '.framework.js',
      codeUrl: BUILD_URL + '.wasm',
      streamingAssetsUrl: STREAMING_URL,
      companyName: 'tripbiseo',
      productName: 'retro3d',
      productVersion: '1',
    }
    unity = await window.createUnityInstance(canvas.value, config, (rate) => {
      progress.value = Math.round(rate * 100)
    })
    isReady.value = true
    if (props.tripUrl) unity.SendMessage('RetroMap', 'LoadTrip', props.tripUrl)
    sendTripData()
  } catch (e) {
    errorText.value = e.message
  }
})

// 웹이 만든 지도 자료를 유니티에 넘김 (유니티가 뜬 뒤 + 자료가 다 만들어진 뒤, 둘 다 됐을 때 한 번)
let isSent = false
function sendTripData() {
  if (!unity || !props.tripData || isSent) return
  isSent = true
  unity.SendMessage('RetroMap', 'LoadJson', props.tripData.json)
  unity.SendMessage('RetroMap', 'LoadGroundPhoto', props.tripData.photo)
  emit('ready') // 부모가 지금 재생 시각·시점을 넘기게
}
watch(() => props.tripData, sendTripData)

onBeforeUnmount(() => {
  if (unity) unity.Quit() // 화면을 떠나면 유니티를 끄고 메모리를 돌려줌
})

// 재생 조종 (부모 화면이 부름)
function play() {
  if (unity) unity.SendMessage('RetroMap', 'Play')
}
function pause() {
  if (unity) unity.SendMessage('RetroMap', 'Pause')
}
// minute: 여행 시작부터 몇 분째로 옮기기
function seek(minute) {
  if (unity) unity.SendMessage('RetroMap', 'Seek', minute)
}
// 웹 재생 시각 그대로 (첫날 0시부터 몇 분째. 예: 1일차 11:30 = 690). 3D는 혼자 재생하지 않고 이 시각만 보여 줌
function seekClock(minutes) {
  if (unity) unity.SendMessage('RetroMap', 'SeekClock', minutes)
}
// 시점: 'top' = 위에서 따라가기, 'street' = 내비(사람 눈높이)
function setView(view) {
  if (unity) unity.SendMessage('RetroMap', 'SetView', view)
}
// 따라가기 켜기·끄기
function setFollow(isOn) {
  if (unity) unity.SendMessage('RetroMap', 'SetFollow', isOn ? 1 : 0)
}

// 밤낮 바뀜 켜기·끄기 (끄면 늘 그날 낮 하늘)
function setDayNight(isOn) {
  if (unity) unity.SendMessage('RetroMap', 'SetDayNight', isOn ? 1 : 0)
}
// 위에서 보기 가까이(1)·멀리(-1)
function zoom(direction) {
  if (unity) unity.SendMessage('RetroMap', 'Zoom', direction)
}

defineExpose({ play, pause, seek, seekClock, setView, setFollow, setDayNight, zoom })
</script>

<template>
  <div class="unity3d">
    <!-- id가 꼭 있어야 함: 유니티가 "#id"로 이 그림판을 찾아 키보드를 연결함 (없으면 90%에서 멈춤) -->
    <canvas id="unity-canvas" ref="canvas" class="unity3d-canvas" tabindex="-1" />
    <div v-if="!isReady || statusText" class="unity3d-cover">
      <p v-if="errorText">{{ errorText }}</p>
      <p v-else-if="statusText">{{ statusText }}</p>
      <p v-else>3D 지도 불러오는 중 {{ progress }}%</p>
    </div>
  </div>
</template>

<style scoped>
.unity3d {
  position: relative;
  width: 100%;
  height: 100%;
  background: #1c2433;
}
.unity3d-canvas {
  width: 100%;
  height: 100%;
  display: block;
}
.unity3d-cover {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #e8ecf2;
  font-size: 15px;
}
</style>
