import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
})

export interface Account {
  id: string
  ownerName: string
  balance: number
  currency: string
  createdAt: string
}

export interface Transaction {
  id: string
  accountId: string
  amount: number
  status: string
  createdAt: string
  updatedAt: string
}

export interface TransferRequest {
  fromAccountId: string
  toAccountId: string
  amount: number
}

export const accountsApi = {
  getAll: () => api.get<Account[]>('/accounts').catch(() => [] as Account[]),
}

export const transfersApi = {
  create: (request: TransferRequest) => api.post('/transfer', request),
  getHistory: () => api.get<Transaction[]>('/transactions/history').catch(() => [] as Transaction[]),
}

export const simulatorApi = {
  start: () => api.post('/simulator/start'),
  stop: () => api.post('/simulator/stop'),
  toggle: () => api.post('/simulator/toggle'),
  getStatus: () => api.get<string>('/simulator/status'),
}

export default api