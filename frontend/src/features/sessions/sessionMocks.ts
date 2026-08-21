export interface SessionPlaceMock {
  id: string
  city: string
  name: string
}

export interface GroupRoomMock {
  id: string
  city: string
  startsAtLabel: string
  durationMin: number
  participantCount: number
}

export const SESSION_PLACE_MOCKS: SessionPlaceMock[] = [
  { id: 'lisbon-alfama', city: 'Lisbon', name: 'Alfama Cafe' },
  { id: 'tokyo-shibuya', city: 'Tokyo', name: 'Shibuya Rooftop' },
  { id: 'berlin-kreuzberg', city: 'Berlin', name: 'Kreuzberg Studio' },
]

export const GROUP_ROOM_MOCK: GroupRoomMock = {
  id: 'lisbon-1400',
  city: 'Lisbon',
  startsAtLabel: '14:00',
  durationMin: 90,
  participantCount: 12,
}
