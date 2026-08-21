import { client } from '../../api/client'
import type { City } from './placeTypes'

export async function getCities(): Promise<City[]> {
  const response = await client.get<City[]>('/cities')
  return response.data
}
