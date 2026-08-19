import { useSyncExternalStore } from 'react'
import {
  logout as requestLogout,
  refreshAuthentication,
} from '../features/auth/authApi'
import type {
  AuthResponse,
  AuthStatus,
  AuthUser,
} from '../features/auth/authTypes'
import { AUTH_SESSION_EXPIRED_EVENT } from '../api/client'

interface AuthSnapshot {
  status: AuthStatus
  user: AuthUser | null
}

const ACCESS_TOKEN_KEY = 'accessToken'
const AUTH_USER_KEY = 'authUser'

let snapshot: AuthSnapshot = {
  status: 'checking',
  user: null,
}
let initialization: Promise<void> | null = null
const listeners = new Set<() => void>()

function emit(nextSnapshot: AuthSnapshot) {
  snapshot = nextSnapshot
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function getSnapshot() {
  return snapshot
}

function readStoredUser(): AuthUser | null {
  const storedUser = localStorage.getItem(AUTH_USER_KEY)
  if (!storedUser) {
    return null
  }

  try {
    return JSON.parse(storedUser) as AuthUser
  } catch {
    localStorage.removeItem(AUTH_USER_KEY)
    return null
  }
}

function clearAuthentication() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(AUTH_USER_KEY)
  emit({ status: 'unauthenticated', user: null })
}

function completeLogin(response: AuthResponse) {
  localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken)
  localStorage.setItem(AUTH_USER_KEY, JSON.stringify(response.user))
  emit({ status: 'authenticated', user: response.user })
}

async function initialize() {
  if (initialization) {
    return initialization
  }

  initialization = (async () => {
    const storedUser = readStoredUser()

    if (!storedUser) {
      clearAuthentication()
      return
    }

    try {
      const response = await refreshAuthentication()
      localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken)
      emit({ status: 'authenticated', user: storedUser })
    } catch {
      clearAuthentication()
    }
  })()

  return initialization
}

async function logout() {
  try {
    await requestLogout()
  } finally {
    clearAuthentication()
  }
}

window.addEventListener(AUTH_SESSION_EXPIRED_EVENT, clearAuthentication)

export function useAuthStore(): AuthSnapshot {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot)
}

export const authActions = {
  completeLogin,
  initialize,
  logout,
}
