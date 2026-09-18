import celebrationUrl from '../../assets/sounds/timer-celebration.mp3'

/** 완료·구간 전환 모두 같은 소리다(snd piano 키트의 celebration). 구별이 필요해지면 여기서 나눈다. */
const VOLUME = 0.6

let context: AudioContext | null = null
let buffer: Promise<AudioBuffer | null> | null = null

/**
 * 역할: 타이머 알림음 하나를 준비하고 울린다.
 *
 * HTMLAudioElement 대신 Web Audio를 쓴다. 사용자 동작 안에서 한 번 깨운 AudioContext는
 * 다른 탭에 가 있어도 재생을 허락받고, iOS에서도 음량을 조절할 수 있다.
 */
function getContext() {
  context ??= new AudioContext()
  return context
}

function loadBuffer() {
  buffer ??= fetch(celebrationUrl)
    .then((response) => response.arrayBuffer())
    .then((data) => getContext().decodeAudioData(data))
    .catch((error: unknown) => {
      // 다음에 다시 받아 보도록 비운다. 소리가 없어도 타이머는 그대로다.
      buffer = null
      if (import.meta.env.DEV) console.warn('[timer-sound] 알림음을 불러오지 못했습니다.', error)
      return null
    })
  return buffer
}

/**
 * 사용자 동작 안에서 부른다. 브라우저는 사용자가 누르기 전에는 소리를 막으므로,
 * 누른 순간 오디오를 깨워 두어야 나중에 타이머가 끝날 때 울릴 수 있다.
 */
export function unlockTimerSound() {
  const audio = getContext()
  if (audio.state === 'suspended') void audio.resume().catch(() => undefined)
}

/** 알림음을 받아 둔다. 울릴 일이 생겼을 때만 부른다. */
export function preloadTimerSound() {
  void loadBuffer()
}

export async function playTimerSound() {
  const decoded = await loadBuffer()
  if (!decoded || !context) return

  // 새로고침한 뒤 한 번도 누르지 않았다면 여기서 깨우지 못하고 조용히 넘어간다.
  if (context.state === 'suspended') await context.resume().catch(() => undefined)

  const gain = context.createGain()
  gain.gain.value = VOLUME
  const source = context.createBufferSource()
  source.buffer = decoded
  source.connect(gain).connect(context.destination)
  source.start()
}
