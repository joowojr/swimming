import { client } from '../../api/client'

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export async function changePassword(request: ChangePasswordRequest): Promise<void> {
  await client.patch('/users/me/password', request)
}
