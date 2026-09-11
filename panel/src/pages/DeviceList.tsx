import { collection, onSnapshot, orderBy, query } from 'firebase/firestore'
import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import ConfirmDialog from '../components/ConfirmDialog'
import { PowerIcon, RestartIcon, SendIcon } from '../components/icons'
import { sendCommandToAll } from '../commands'
import { db } from '../firebase'
import { deviceStatus, formatRelativeTime, statusLabel } from '../format'
import type { Device } from '../types'

interface PendingBulkAction {
  title: string
  description: string
  run: () => void
}

export default function DeviceList() {
  const [devices, setDevices] = useState<Device[] | null>(null)
  const [message, setMessage] = useState('')
  const [sending, setSending] = useState(false)
  const [pendingAction, setPendingAction] = useState<PendingBulkAction | null>(null)

  useEffect(() => {
    const q = query(collection(db, 'devices'), orderBy('createdAt', 'asc'))
    return onSnapshot(q, (snap) => {
      setDevices(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as Device))
    })
  }, [])

  if (devices === null) {
    return <p className="muted">Yükleniyor…</p>
  }

  const deviceIds = devices.map((d) => d.id)

  function confirmBulk(title: string, description: string, run: () => void) {
    setPendingAction({ title, description, run: () => { run(); setPendingAction(null) } })
  }

  async function handleBulkMessage(e: FormEvent) {
    e.preventDefault()
    if (!message.trim() || deviceIds.length === 0) return
    setSending(true)
    try {
      await sendCommandToAll(deviceIds, 'show_message', { text: message.trim() })
      setMessage('')
    } finally {
      setSending(false)
    }
  }

  const online = devices.filter((d) => deviceStatus(d) === 'online').length
  const lowBattery = devices.filter((d) => deviceStatus(d) === 'low-battery').length
  const offline = devices.filter((d) => deviceStatus(d) === 'offline').length
  const unprovisioned = devices.filter((d) => deviceStatus(d) === 'unprovisioned').length
  const unlocked = devices.filter((d) => deviceStatus(d) === 'unlocked').length

  return (
    <div>
      <h1 style={{ fontSize: 22, marginBottom: 18 }}>Cihazlar</h1>

      <div className="stat-row">
        <div className="stat-card">
          <span className="stat-value">{devices.length}</span>
          <span className="stat-label">Toplam cihaz</span>
        </div>
        <div className="stat-card ok">
          <span className="stat-value">{online}</span>
          <span className="stat-label">Çevrimiçi</span>
        </div>
        <div className="stat-card navy">
          <span className="stat-value">{unlocked}</span>
          <span className="stat-label">Kilit açık</span>
        </div>
        <div className="stat-card warn">
          <span className="stat-value">{lowBattery}</span>
          <span className="stat-label">Pil uyarısı</span>
        </div>
        <div className="stat-card crit">
          <span className="stat-value">{offline}</span>
          <span className="stat-label">Çevrimdışı</span>
        </div>
        {unprovisioned > 0 && (
          <div className="stat-card muted">
            <span className="stat-value">{unprovisioned}</span>
            <span className="stat-label">Kurulum bekliyor</span>
          </div>
        )}
      </div>

      {devices.length === 0 && (
        <p className="muted">
          Henüz kayıtlı cihaz yok. Kiosk uygulamasını bir tablette açtığında burada
          otomatik olarak görünecek.
        </p>
      )}

      {devices.length > 0 && (
        <section className="card">
          <h2>Tüm Cihazlara Toplu Komut</h2>
          <p className="muted" style={{ marginBottom: 12 }}>
            Aşağıdaki işlemler kayıtlı {devices.length} cihazın hepsine aynı anda gönderilir.
          </p>
          <div className="action-row">
            <button
              className="action-btn neutral"
              onClick={() =>
                confirmBulk(
                  'Tüm uygulamaları yeniden başlat',
                  `${devices.length} cihazda kilitli duran hedef kapatılıp yeniden açılacak. Ekranlarda kısa bir kesinti olur.`,
                  () => sendCommandToAll(deviceIds, 'restart_app'),
                )
              }
            >
              <RestartIcon /> Tüm uygulamaları yeniden başlat
            </button>
            <button
              className="action-btn danger"
              onClick={() =>
                confirmBulk(
                  'Tüm cihazları yeniden başlat',
                  `${devices.length} tablet aynı anda kapanıp yeniden açılacak, bu birkaç dakika sürebilir. Emin misin?`,
                  () => sendCommandToAll(deviceIds, 'reboot'),
                )
              }
            >
              <PowerIcon /> Tüm cihazları yeniden başlat
            </button>
          </div>
          <label className="message-label">Tüm ekranlara mesaj gönder (10 dakika görünür kalır)</label>
          <form className="message-form" onSubmit={handleBulkMessage}>
            <input
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder="örn. Bugün bakım nedeniyle 5 dk kapalı kalacağız"
            />
            <button type="submit" className="action-btn neutral" disabled={sending}>
              <SendIcon /> {sending ? 'Gönderiliyor…' : 'Gönder'}
            </button>
          </form>
        </section>
      )}

      <div className="device-grid">
        {devices.map((device) => {
          const status = deviceStatus(device)
          return (
            <Link to={`/devices/${device.id}`} className={`device-card status-${status}`} key={device.id}>
              <div className={`status-pill status-${status}`}>{statusLabel(status)}</div>
              <div className="device-name">{device.label || 'İsimsiz cihaz'}</div>
              {device.batteryPct !== undefined && (
                <div className="batt-row">
                  <span className="batt-shell">
                    <span
                      className={`batt-fill batt-${status === 'low-battery' ? 'warn' : 'ok'}`}
                      style={{ width: `${device.batteryPct}%` }}
                    />
                  </span>
                  {device.batteryPct}% {device.charging ? '· şarjda' : ''}
                </div>
              )}
              <div className="last-seen">{formatRelativeTime(device.lastSeenAt)}</div>
            </Link>
          )
        })}
      </div>

      {pendingAction && (
        <ConfirmDialog
          title={pendingAction.title}
          description={pendingAction.description}
          onConfirm={pendingAction.run}
          onCancel={() => setPendingAction(null)}
        />
      )}
    </div>
  )
}
