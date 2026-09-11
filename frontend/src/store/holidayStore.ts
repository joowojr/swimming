import { create } from 'zustand'
import { getHolidayMonth } from '../features/calendar/holidayApi'

/**
 * 역할: 월별 공휴일의 메모리 캐시. 백엔드가 단일 출처이고 여기는 같은 달을 다시 부르지 않게만 한다.
 *
 * 공휴일은 표시 정보다. 조회가 실패해도 계획 기능은 그대로 쓸 수 있어야 하므로 실패를 던지지 않고
 * 실패한 달로만 기록한다. localStorage에 남기지 않는다. 탭을 닫으면 사라지고 다시 받는다.
 */
interface HolidayStoreState {
  // 'YYYY-MM-DD' -> 그날의 공휴일 명칭
  namesByDate: Record<string, string[]>
  loadedMonths: Set<string>
  failedMonths: Set<string>

  loadMonth: (monthKey: string) => Promise<void>
  reset: () => void
}

const inFlight = new Set<string>()

export const useHolidayStore = create<HolidayStoreState>((set, get) => ({
  namesByDate: {},
  loadedMonths: new Set(),
  failedMonths: new Set(),

  loadMonth: async (monthKey) => {
    const { loadedMonths } = get()
    if (loadedMonths.has(monthKey) || inFlight.has(monthKey)) return

    const [year, month] = monthKey.split('-').map(Number)
    if (!year || !month) return

    inFlight.add(monthKey)
    try {
      const loaded = await getHolidayMonth(year, month)
      set((state) => {
        const namesByDate = { ...state.namesByDate }
        for (const holiday of loaded.holidays) {
          namesByDate[holiday.date] = holiday.names
        }
        const failedMonths = new Set(state.failedMonths)
        failedMonths.delete(monthKey)
        return {
          namesByDate,
          loadedMonths: new Set(state.loadedMonths).add(monthKey),
          failedMonths,
        }
      })
    } catch {
      // 공급자가 없거나(503) 네트워크가 끊겨도 계획 기능은 계속 쓸 수 있어야 한다.
      set((state) => ({ failedMonths: new Set(state.failedMonths).add(monthKey) }))
    } finally {
      inFlight.delete(monthKey)
    }
  },

  reset: () => {
    inFlight.clear()
    set({ namesByDate: {}, loadedMonths: new Set(), failedMonths: new Set() })
  },
}))
