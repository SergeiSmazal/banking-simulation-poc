import { useEffect, useState } from 'react'
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

  useEffect(() => {
    const eventSource = new EventSource('/api/alerts/stream')

    eventSource.onmessage = (event) => {
      try {
        const alert = JSON.parse(event.data) as AnomalyAlert
        setAlerts(prev => [alert, ...prev].slice(0, 50))
      } catch (e) {
        console.error('Failed to parse alert:', e)
      }
    }

    eventSource.onerror = (err) => {
      console.error('SSE error:', err)
      eventSource.close()
    }

    return () => {
      eventSource.close()
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