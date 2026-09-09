import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { EMPTY_TASK_FILTER } from '../features/tasks/taskFilter'
import type { TaskFilter } from '../features/tasks/taskFilter'

export type PinboardPlannerView = 'daily' | 'matrix'
export type PinboardCalendarView = 'week' | 'month'

interface PinboardViewState {
  plannerView: PinboardPlannerView
  calendarView: PinboardCalendarView
  matrixFilter: TaskFilter
  setPlannerView: (view: PinboardPlannerView) => void
  setCalendarView: (view: PinboardCalendarView) => void
  setMatrixFilter: (filter: TaskFilter) => void
}

export const usePinboardViewStore = create<PinboardViewState>()(
  persist(
    (set) => ({
      plannerView: 'daily',
      calendarView: 'week',
      matrixFilter: EMPTY_TASK_FILTER,
      setPlannerView: (plannerView) => set({ plannerView }),
      setCalendarView: (calendarView) => set({ calendarView }),
      setMatrixFilter: (matrixFilter) => set({ matrixFilter }),
    }),
    {
      name: 'swimming-pinboard-view',
      partialize: ({ plannerView, calendarView, matrixFilter }) => ({
        plannerView,
        calendarView,
        matrixFilter,
      }),
    },
  ),
)
