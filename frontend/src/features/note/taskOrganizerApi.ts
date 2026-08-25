import { client } from '../../api/client'
import type {
  TaskOrganizeConfirmRequest,
  TaskOrganizeConfirmResponse,
  TaskOrganizeRequest,
  TaskOrganizeResponse,
} from './taskOrganizerTypes'

export async function previewTaskOrganization(
  request: TaskOrganizeRequest,
): Promise<TaskOrganizeResponse> {
  const response = await client.post<TaskOrganizeResponse>(
    '/task-organizer/preview',
    request,
  )
  return response.data
}

export async function confirmTaskOrganization(
  request: TaskOrganizeConfirmRequest,
): Promise<TaskOrganizeConfirmResponse> {
  const response = await client.post<TaskOrganizeConfirmResponse>(
    '/task-organizer/confirm',
    request,
  )
  return response.data
}
