import client from './client'
import type { Role } from '../types'

export interface LoginResponse {
  token: string
  userId: string
  name: string
  email: string
  role: Role
}

export const login = (email: string, password: string) =>
  client.post<LoginResponse>('/auth/login', { email, password }).then(r => r.data)
