import { client } from '../../api/client'
import type {
  CreateNoteRequest,
  GetNotesParams,
  NoteCreateResponse,
  NoteResponse,
  UpdateNoteRequest,
} from './noteTypes'

export async function createNote(
  request: CreateNoteRequest,
): Promise<NoteCreateResponse> {
  const response = await client.post<NoteCreateResponse>('/notes', request)
  return response.data
}

export async function getNotes(
  params: GetNotesParams = {},
): Promise<NoteResponse[]> {
  const response = await client.get<NoteResponse[]>('/notes', { params })
  return response.data
}

export async function getNote(noteId: number): Promise<NoteResponse> {
  const response = await client.get<NoteResponse>(`/notes/${noteId}`)
  return response.data
}

export async function updateNote(
  noteId: number,
  request: UpdateNoteRequest,
): Promise<NoteResponse> {
  const response = await client.patch<NoteResponse>(
    `/notes/${noteId}`,
    request,
  )
  return response.data
}

export async function archiveNote(noteId: number): Promise<void> {
  await client.patch(`/notes/${noteId}/archive`)
}

export async function restoreNote(noteId: number): Promise<void> {
  await client.patch(`/notes/${noteId}/restore`)
}

export async function deleteNote(noteId: number): Promise<void> {
  await client.delete(`/notes/${noteId}`)
}
