export type BackgroundAssetType = 'IMAGE' | 'VIDEO'

export interface BackgroundAsset {
  type: BackgroundAssetType
  key: string | null
  url: string | null
  thumbnailKey: string | null
  thumbnailUrl: string | null
}

export interface Place {
  id: number
  name: string
  backgroundAsset: BackgroundAsset
  defaultMusicUrl: string | null
}

export interface City {
  id: number
  name: string
  countryCode: string
  places: Place[]
}

// 세션 목록 응답에는 배경이 없다. 배경은 세션 상세에서만 내려온다.
export interface SessionPlace {
  id: number
  name: string
  defaultMusicUrl: string | null
  cityId: number
  cityName: string
}

export interface SessionDetailPlace extends SessionPlace {
  backgroundAsset: BackgroundAsset
}
