/** 역할: Note 기능 화면에서만 쓰는 공통 표시 타입을 정의한다. */
export interface ProjectOption {
  id: number
  name: string
}

export type LoadStatus = 'loading' | 'ready' | 'error'
export type SaveStatus = 'idle' | 'saving' | 'saved' | 'error'
