import { create } from 'zustand'
import { getPlaces } from '../features/places/placeApi'
import type { City, Place } from '../features/places/placeTypes'

/**
 * 역할: 공간 카탈로그를 단일 출처로 들고 있어, 홈 위젯·세션 생성 모달·세션 화면이 각자 조회하지 않게 한다.
 * 카탈로그는 세션 중에 바뀌지 않는 정적 데이터라 한 번 채워지면 다시 요청하지 않는다.
 */
type PlaceCatalogStatus = 'idle' | 'loading' | 'ready' | 'error'

interface PlaceStoreState {
  cities: City[]
  status: PlaceCatalogStatus
  load: () => Promise<void>
}

// 여러 화면이 같은 순간에 load()를 불러도 요청은 하나로 합친다.
let inFlight: Promise<void> | null = null

export const usePlaceStore = create<PlaceStoreState>((set, get) => ({
  cities: [],
  status: 'idle',

  load: async () => {
    if (get().status === 'ready') return
    if (inFlight) return inFlight

    set({ status: 'loading' })
    inFlight = getPlaces()
      .then((cities) => { set({ cities, status: 'ready' }) })
      .catch(() => { set({ status: 'error' }) })
      .finally(() => { inFlight = null })

    return inFlight
  },
}))

export interface PickedPlace {
  cityName: string
  place: Place
}

/**
 * 재생 가능한 배경을 가진 공간 중 하나를 고른다. seed는 호출한 화면이 마운트 시점에 정해 두는 값이라,
 * 카탈로그가 늦게 도착해도 렌더마다 공간이 바뀌지 않는다.
 */
export function pickRandomPlace(cities: City[], seed: number): PickedPlace | null {
  const candidates = cities.flatMap((city) => city.places.flatMap(
    (place) => place.backgroundAsset.url?.trim() ? [{ cityName: city.name, place }] : [],
  ))
  if (candidates.length === 0) return null
  return candidates[Math.floor(seed * candidates.length)]
}
