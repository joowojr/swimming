import { client } from '../../api/client'
import type { CursorPageQuery } from '../../api/types'
import type {
  CreateFolderRequest,
  Folder,
  FolderDetail,
  FolderTag,
  FolderTagNameRequest,
  UpdateFolderRequest,
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

/** size·cursor는 폴더에 딸린 할 일 목록의 페이지를 가리킨다. */
export async function getFolder(
  folderId: number,
  query: CursorPageQuery = {},
): Promise<FolderDetail> {
  const response = await client.get<FolderDetail>(`/folders/${folderId}`, {
    params: query,
  })
  return response.data
}

export async function updateFolder(
  folderId: number,
  request: UpdateFolderRequest,
): Promise<Folder> {
  const response = await client.patch<Folder>(`/folders/${folderId}`, request)
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
