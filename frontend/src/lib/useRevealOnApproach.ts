import { useEffect, useRef, useState } from 'react'
import type { PointerEvent as ReactPointerEvent } from 'react'

/**
 * 역할: 평소에는 접혀 있다가 다가가면 펼쳐지는 플로팅 요소의 열림 상태.
 *
 * 화면 위에 늘 떠 있는 것들은 평소에 자리를 적게 차지해야 한다. 펼쳐 보이는 계기는
 * 셋이고 뜻이 같다: 마우스를 올리거나, 키보드 초점이 닿거나, (마우스가 없는 기기에서)
 * 한 번 누르거나. 손가락으로는 hover가 없어 마우스일 때만 다가감으로 친다. 그러지 않으면
 * 첫 탭이 펼치기가 아니라 실행이 되어, 무엇인지 보기도 전에 눌리게 된다.
 *
 * 눌러서 펼친 상태만 스스로 닫아야 한다. 바깥을 누르거나 Esc를 받으면 접는다.
 *
 * 열고 닫는 데 DOM을 갈아끼우지 않는 것은 쓰는 쪽의 몫이다. 접혀 있는 동안에도 스크린
 * 리더가 읽을 내용은 남아야 한다.
 */
export function useRevealOnApproach<T extends HTMLElement>(isHeldOpen = false) {
  const ref = useRef<T>(null)
  /** 눌러서 펼친 상태. 마우스가 없는 기기에서 유일하게 펼치는 길이다. */
  const [isPinned, setIsPinned] = useState(false)
  const [isHovered, setIsHovered] = useState(false)
  const [isFocused, setIsFocused] = useState(false)

  useEffect(() => {
    if (!isPinned) return

    const handlePointerDown = (event: PointerEvent) => {
      const node = event.target instanceof Node ? event.target : null
      if (!node || !ref.current?.contains(node)) setIsPinned(false)
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsPinned(false)
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isPinned])

  return {
    isOpen: isHeldOpen || isPinned || isHovered || isFocused,
    ref,
    /** 펼침을 여닫는 요소에 그대로 펼쳐 넣는다. 누름은 쓰는 쪽마다 달라 여기 없다. */
    approachProps: {
      onPointerEnter: (event: ReactPointerEvent<T>) => {
        if (event.pointerType === 'mouse') setIsHovered(true)
      },
      onPointerLeave: () => setIsHovered(false),
      onFocus: () => setIsFocused(true),
      onBlur: () => setIsFocused(false),
    },
    pin: () => setIsPinned(true),
    collapse: () => setIsPinned(false),
  }
}
