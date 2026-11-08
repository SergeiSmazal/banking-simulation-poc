import { useEffect, useState } from 'react'
import { Account, TransferRequest, transfersApi, accountsApi, simulatorApi } from '../services/api'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import './Dashboard.css'

interface Transaction {
  id: string
  fromAccountId: string
  toAccountId: string
  amount: number
  status: string
  createdAt: string
}

export default function Dashboard() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [fromAccount, setFromAccount] = useState('')
  const [toAccount, setToAccount] = useState('')
  const [amount, setAmount] = useState('')
  const [simulatorRunning, setSimulatorRunning] = useState(false)
  const [txCount, setTxCount] = useState<number>(0)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    loadAccounts()
    loadTransactions()
    checkSimulatorStatus()
    const txInterval = setInterval(loadTransactions, 5000)
    return () => clearInterval(txInterval)
  }, [])

  const loadAccounts = async () => {
    const data = await accountsApi.getAll()
    setAccounts(data)
  }

  const loadTransactions = async () => {
    const data = await transfersApi.getHistory()
    const txWithStatus = data.map(tx => ({
      ...tx,
      fromAccountId: tx.accountId,
      toAccountId: '',
    }))
    setTransactions(txWithStatus as Transaction[])
    setTxCount(prev => prev + txWithStatus.length)
  }

  const checkSimulatorStatus = async () => {
    try {
      const status = await simulatorApi.getStatus()
      setSimulatorRunning(status === 'RUNNING')
    } catch {
      setSimulatorRunning(false)
    }
  }

  const handleTransfer = async () => {
    setError(null)
    try {
      const req: TransferRequest = {
        fromAccountId: fromAccount as any,
        toAccountId: toAccount as any,
        amount: parseFloat(amount) || 0,
      }
      await transfersApi.create(req)
      setAmount('')
      setFromAccount('')
      setToAccount('')
      await loadTransactions()
    } catch (e: any) {
      setError(e.response?.data || 'Transfer failed')
    }
  }

  const toggleSimulator = async () => {
    if (simulatorRunning) {
      await simulatorApi.stop()
    } else {
      await simulatorApi.start()
    }
    setSimulatorRunning(!simulatorRunning)
  }

  const data = transactions.slice(-20).map((tx, i) => ({
    name: `TX ${i + 1}`,
    count: tx.amount,
  }))

  return (
    <div className="dashboard">
      <header className="header">
        <h1>Fintech Transaction Simulator</h1>
        <button onClick={toggleSimulator} className={simulatorRunning ? 'stop' : 'start'}>
          {simulatorRunning ? 'Stop Simulator' : 'Start Simulator'}
        </button>
      </header>

      <main className="main-content">
        <section className="accounts-section">
          <h2>Accounts</h2>
          <div className="accounts-grid">
            {accounts.map(acc => (
              <div key={acc.id} className="account-card">
                <div className="account-name">{acc.ownerName}</div>
                <div className="account-balance">{acc.currency} {acc.balance.toFixed(2)}</div>
              </div>
            ))}
          </div>
        </section>

        <section className="transfer-section">
          <h2>Make Transfer</h2>
          <div className="transfer-form">
            <select value={fromAccount} onChange={e => setFromAccount(e.target.value)}>
              <option value="">From</option>
              {accounts.map(a => (
                <option key={a.id} value={a.id}>{a.ownerName}</option>
              ))}
            </select>
            <select value={toAccount} onChange={e => setToAccount(e.target.value)}>
              <option value="">To</option>
              {accounts.filter(a => a.id !== fromAccount).map(a => (
                <option key={a.id} value={a.id}>{a.ownerName}</option>
              ))}
            </select>
            <input
              type="number"
              value={amount}
              onChange={e => setAmount(e.target.value)}
              placeholder="Amount"
              min="0.01"
              step="0.01"
            />
            <button onClick={handleTransfer}>Send</button>
          </div>
          {error && <div className="error">{error}</div>}
        </section>

        <section className="transactions-section">
          <h2>Recent Transactions</h2>
          <div className="transactions-list">
            {transactions.slice(0, 10).map(tx => (
              <div key={tx.id} className="transaction-item">
                <span>{tx.status === 'COMPLETED' ? '✓' : '✗'}</span>
                <span>{tx.createdAt}</span>
                <span>{tx.amount} {accounts.find(a => a.id === tx.fromAccountId)?.currency || 'EUR'}</span>
              </div>
            ))}
          </div>
        </section>

        <section className="chart-section">
          <h2>Transaction Volume</h2>
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={data}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis />
              <Tooltip />
              <Line type="monotone" dataKey="count" stroke="#8884d8" />
            </LineChart>
          </ResponsiveContainer>
        </section>
      </main>
    </div>
  )
}