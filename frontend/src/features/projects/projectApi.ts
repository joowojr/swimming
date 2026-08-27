import { client } from '../../api/client'
import type {
  CreateProjectRequest,
  Project,
  ProjectDetail,
  ProjectTag,
  ProjectTagNameRequest,
  UpdateProjectRequest,
} from './projectTypes'

export async function getProjects(): Promise<Project[]> {
  const response = await client.get<Project[]>('/projects')
  return response.data
}

export async function createProject(
  request: CreateProjectRequest,
): Promise<Project> {
  const response = await client.post<Project>('/projects', request)
  return response.data
}

export async function getProject(projectId: number): Promise<ProjectDetail> {
  const response = await client.get<ProjectDetail>(`/projects/${projectId}`)
  return response.data
}

export async function updateProject(
  projectId: number,
  request: UpdateProjectRequest,
): Promise<Project> {
  const response = await client.patch<Project>(`/projects/${projectId}`, request)
  return response.data
}

export async function deleteProject(projectId: number): Promise<void> {
  await client.delete(`/projects/${projectId}`)
}

export async function getProjectTags(): Promise<ProjectTag[]> {
  const response = await client.get<ProjectTag[]>('/project-tags')
  return response.data
}

export async function createProjectTag(
  request: ProjectTagNameRequest,
): Promise<ProjectTag> {
  const response = await client.post<ProjectTag>('/project-tags', request)
  return response.data
}

export async function updateProjectTag(
  tagId: number,
  request: ProjectTagNameRequest,
): Promise<ProjectTag> {
  const response = await client.patch<ProjectTag>(`/project-tags/${tagId}`, request)
  return response.data
}

export async function deleteProjectTag(tagId: number): Promise<void> {
  await client.delete(`/project-tags/${tagId}`)
}
