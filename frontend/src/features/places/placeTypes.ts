export type BackgroundAssetType = 'IMAGE' | 'VIDEO'

export interface BackgroundAsset {
  type: BackgroundAssetType
  key: string | null
  url: string | null
}

export interface Place {
  id: number
  name: string
  defaultMusicUrl: string | null
}

export interface City {
  id: number
  name: string
  countryCode: string
  places: Place[]
}

export interface SessionPlace extends Place {
  cityId: number
  cityName: string
}

export interface SessionDetailPlace extends SessionPlace {
  backgroundAsset: BackgroundAsset
}
