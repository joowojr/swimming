export interface GroupRoomMock {
  id: string
  city: string
  startsAtLabel: string
  durationMin: number
  participantCount: number
}

export const GROUP_ROOM_MOCK: GroupRoomMock = {
  id: 'lisbon-1400',
  city: 'Lisbon',
  startsAtLabel: '14:00',
  durationMin: 90,
  participantCount: 12,
}
