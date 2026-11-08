import { useEffect, useState } from 'react'
import { io, Socket } from 'socket.io-client'
import './Alerts.css'

interface AnomalyAlert {
  transactionId: string
  accountId: string
  amount: number
  reason: string
  timestamp: string
}

export default function Alerts() {
  const [alerts, setAlerts] = useState<AnomalyAlert[]>([])
  const [socket, setSocket] = useState<Socket | null>(null)

  useEffect(() => {
    const newSocket = io()
    newSocket.on('anomaly', (alert: AnomalyAlert) => {
      setAlerts(prev => [alert, ...prev].slice(0, 50))
    })

    setSocket(newSocket)
    return () => {
      newSocket.disconnect()
    }
  }, [])

  return (
    <div className="alerts-page">
      <h1>Anomaly Alerts</h1>
      <div className="alerts-list">
        {alerts.length === 0 ? (
          <div className="no-alerts">No anomalies detected yet</div>
        ) : (
          alerts.map((alert, idx) => (
            <div key={idx} className="alert-item">
              <span className="alert-timestamp">{new Date(alert.timestamp).toLocaleTimeString()}</span>
              <span className="alert-reason">{alert.reason}</span>
              <span className="alert-amount">{alert.amount}</span>
            </div>
          ))
        )}
      </div>
    </div>
  )
}