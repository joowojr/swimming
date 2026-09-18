import axios, {
  AxiosError,
  type InternalAxiosRequestConfig,
} from 'axios'

interface ProblemDetail {
  code?: string
  detail?: string
  errors?: Record<string, string>
  targetCategory?: { nodeId: string; title: string }
}

export interface ApiError {
  status?: number
  code?: string
  message?: string
  errors?: Record<string, string>
  targetCategory?: { nodeId: string; title: string }
}

interface RefreshResponse {
  accessToken: string
}

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

const ACCESS_TOKEN_KEY = 'accessToken'
export const API_BASE_URL = import.meta.env.API_BASE_URL

if (!API_BASE_URL) {
  throw new Error('API_BASE_URL 환경변수가 필요합니다.')
}

export const AUTH_SESSION_EXPIRED_EVENT = 'auth:session-expired'

export const client = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
})

const refreshClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
})

let refreshRequest: Promise<string> | null = null

function refreshAccessToken(): Promise<string> {
  if (!refreshRequest) {
    refreshRequest = refreshClient
      .post<RefreshResponse>('/auth/refresh')
      .then((response) => {
        localStorage.setItem(ACCESS_TOKEN_KEY, response.data.accessToken)
        return response.data.accessToken
      })
      .finally(() => {
        refreshRequest = null
      })
  }

  return refreshRequest
}

function toApiError(error: AxiosError<ProblemDetail>): ApiError {
  const problemDetail = error.response?.data
  return {
    status: error.response?.status,
    code: problemDetail?.code,
    message: problemDetail?.detail,
    errors: problemDetail?.errors,
    targetCategory: problemDetail?.targetCategory,
  }
}

client.interceptors.request.use((config) => {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ProblemDetail>) => {
    const originalRequest = error.config as RetryableRequestConfig | undefined
    const isAuthenticationRequest = originalRequest?.url?.startsWith('/auth/')

    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !isAuthenticationRequest
    ) {
      originalRequest._retry = true

      try {
        const accessToken = await refreshAccessToken()
        originalRequest.headers.Authorization = `Bearer ${accessToken}`
        return client(originalRequest)
      } catch {
        localStorage.removeItem(ACCESS_TOKEN_KEY)
        window.dispatchEvent(new Event(AUTH_SESSION_EXPIRED_EVENT))
      }
    }

    return Promise.reject(toApiError(error))
  },
)
