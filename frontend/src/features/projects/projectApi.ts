import { client } from '../../api/client'
import type {
  CreateProjectRequest,
  Project,
  ProjectTag,
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

export async function getProject(projectId: number): Promise<Project> {
  const response = await client.get<Project>(`/projects/${projectId}`)
  return response.data
}

export async function updateProject(
  projectId: number,
  request: UpdateProjectRequest,
): Promise<Project> {
  const response = await client.patch<Project>(`/projects/${projectId}`, request)
  return response.data
}

export async function getProjectTags(): Promise<ProjectTag[]> {
  const response = await client.get<ProjectTag[]>('/project-tags')
  return response.data
}
