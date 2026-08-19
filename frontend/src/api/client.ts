import axios, { AxiosError } from 'axios'

interface ProblemDetail {
  code?: string
  detail?: string
  errors?: Record<string, string>
}

export interface ApiError {
  status?: number
  code?: string
  message?: string
  errors?: Record<string, string>
}

export const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ProblemDetail>) => {
    const problemDetail = error.response?.data
    const apiError: ApiError = {
      status: error.response?.status,
      code: problemDetail?.code,
      message: problemDetail?.detail,
      errors: problemDetail?.errors,
    }
    return Promise.reject(apiError)
  },
)
