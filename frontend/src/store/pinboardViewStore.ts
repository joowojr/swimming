import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type PinboardPlannerView = 'daily' | 'matrix'
export type PinboardCalendarView = 'week' | 'month'

interface PinboardViewState {
  plannerView: PinboardPlannerView
  calendarView: PinboardCalendarView
  setPlannerView: (view: PinboardPlannerView) => void
  setCalendarView: (view: PinboardCalendarView) => void
}

export const usePinboardViewStore = create<PinboardViewState>()(
  persist(
    (set) => ({
      plannerView: 'daily',
      calendarView: 'week',
      setPlannerView: (plannerView) => set({ plannerView }),
      setCalendarView: (calendarView) => set({ calendarView }),
    }),
    {
      name: 'swimming-pinboard-view',
      partialize: ({ plannerView, calendarView }) => ({ plannerView, calendarView }),
    },
  ),
)
