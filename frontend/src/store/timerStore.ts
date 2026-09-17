import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/**
 * 역할: 세션과 무관하게 재는 타이머 하나. 시작 시각과 길이만 들고 남은 시간은 timerClock이 센다.
 * 새로고침해도 이어지도록 브라우저에 남긴다. 기록·통계에는 남지 않는다.
 */
export const TIMER_MIN_MINUTES = 1
export const TIMER_MAX_MINUTES = 600

export interface FreeTimer {
  startedAt: string
  plannedDurationSec: number
}

interface TimerState {
  timer: FreeTimer | null
  start: (minutes: number) => void
  stop: () => void
}

export function isValidTimerMinutes(minutes: number) {
  return Number.isInteger(minutes) && minutes >= TIMER_MIN_MINUTES && minutes <= TIMER_MAX_MINUTES
}

export const useTimerStore = create<TimerState>()(
  persist(
    (set) => ({
      timer: null,
      start: (minutes) => {
        if (!isValidTimerMinutes(minutes)) return
        set({ timer: { startedAt: new Date().toISOString(), plannedDurationSec: minutes * 60 } })
      },
      stop: () => set({ timer: null }),
    }),
    {
      name: 'swimming-free-timer',
      partialize: ({ timer }) => ({ timer }),
      // 저장해 둔 값이 깨져 있으면 비어 있는 타이머로 시작한다.
      merge: (persisted, current) => {
        const saved = (persisted as Partial<TimerState> | undefined)?.timer
        const isValid = saved != null
          && !Number.isNaN(Date.parse(saved.startedAt))
          && Number.isInteger(saved.plannedDurationSec)
          && saved.plannedDurationSec > 0
        return { ...current, timer: isValid ? saved : null }
      },
    },
  ),
)
