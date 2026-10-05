import client from './client'

export interface AuditLogDto {
  id: string
  userId: string | null
  action: string
  resource: string | null
  ipAddress: string | null
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const getAuditLogs = (params: { action?: string; page?: number; size?: number }) =>
  client.get<Page<AuditLogDto>>('/audit', { params }).then(r => r.data)
