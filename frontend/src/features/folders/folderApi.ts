import { client } from '../../api/client'
import type {
  CreateFolderRequest,
  Folder,
  FolderDetail,
  FolderTag,
  FolderTagNameRequest,
  PinFolderRequest,
  UpdateFolderRequest,
  UpdateFolderStatusRequest,
} from './folderTypes.ts'

export async function getFolders(): Promise<Folder[]> {
  const response = await client.get<Folder[]>('/folders')
  return response.data
}

export async function createFolder(
  request: CreateFolderRequest,
): Promise<Folder> {
  const response = await client.post<Folder>('/folders', request)
  return response.data
}

export async function getFolder(folderId: number): Promise<FolderDetail> {
  const response = await client.get<FolderDetail>(`/folders/${folderId}`)
  return response.data
}

export async function updateFolder(
  folderId: number,
  request: UpdateFolderRequest,
): Promise<Folder> {
  const response = await client.patch<Folder>(`/folders/${folderId}`, request)
  return response.data
}

export async function updateFolderStatus(
  folderId: number,
  status: UpdateFolderStatusRequest['status'],
): Promise<Folder> {
  const request: UpdateFolderStatusRequest = { status }
  const response = await client.patch<Folder>(`/folders/${folderId}/status`, request)
  return response.data
}

export async function pinFolder(
  folderId: number,
  pinned: boolean,
): Promise<Folder> {
  const request: PinFolderRequest = { pinned }
  const response = await client.patch<Folder>(`/folders/${folderId}/pin`, request)
  return response.data
}

export async function deleteFolder(folderId: number): Promise<void> {
  await client.delete(`/folders/${folderId}`)
}

export async function getTags(): Promise<FolderTag[]> {
  const response = await client.get<FolderTag[]>('/folder-tags')
  return response.data
}

export async function createFolderTag(
  request: FolderTagNameRequest,
): Promise<FolderTag> {
  const response = await client.post<FolderTag>('/folder-tags', request)
  return response.data
}

export async function updateFolderTag(
  tagId: number,
  request: FolderTagNameRequest,
): Promise<FolderTag> {
  const response = await client.patch<FolderTag>(`/folder-tags/${tagId}`, request)
  return response.data
}

export async function deleteFolderTag(tagId: number): Promise<void> {
  await client.delete(`/folder-tags/${tagId}`)
}
