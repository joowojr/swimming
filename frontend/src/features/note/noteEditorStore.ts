import { create } from 'zustand'

interface NoteEditorState {
  isConfirmingDelete: boolean

  recentlyArchivedId: number | null
  archiveSuggestionNoteId: number | null

  actionMessage: string | null

  setIsConfirmingDelete: (value: boolean) => void
  setRecentlyArchivedId: (value: number | null) => void
  setArchiveSuggestionNoteId: (value: number | null) => void
  setActionMessage: (value: string | null) => void
  reset: () => void
}

const initialState = {
  isConfirmingDelete: false,
  recentlyArchivedId: null,
  archiveSuggestionNoteId: null,
  actionMessage: null,
}

export const useNoteEditorStore = create<NoteEditorState>((set) => ({
  ...initialState,
  setIsConfirmingDelete: (value) => set({ isConfirmingDelete: value }),
  setRecentlyArchivedId: (value) => set({ recentlyArchivedId: value }),
  setArchiveSuggestionNoteId: (value) => set({ archiveSuggestionNoteId: value }),
  setActionMessage: (value) => set({ actionMessage: value }),
  reset: () => set(initialState),
}))
