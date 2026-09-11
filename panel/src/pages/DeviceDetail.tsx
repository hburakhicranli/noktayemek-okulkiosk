import { collection, doc, limit, onSnapshot, orderBy, query, updateDoc } from 'firebase/firestore'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import ConfirmDialog from '../components/ConfirmDialog'
import { LockIcon, PowerIcon, RestartIcon, SendIcon, UnlockIcon } from '../components/icons'
import PagedGroupedList from '../components/PagedGroupedList'
import { sendCommand } from '../commands'
import { db } from '../firebase'
import { deviceStatus, formatRelativeTime, statusLabel } from '../format'
import type { Device, DeviceCommand, DeviceEvent } from '../types'

interface PendingAction {
  title: string
  description: string
  run: () => void
}

const EVENT_LABELS: Record<string, string> = {
  boot: 'Açılış',
  crash: 'Çökme / yeniden başlatma',
  unauthorized_exit: 'İzinsiz çıkış denemesi',
  command_ack: 'Komut uygulandı',
  update_installed: 'Güncelleme kuruldu',
  update_failed: 'Güncelleme başarısız oldu',
}

const COMMAND_LABELS: Record<string, string> = {
  restart_app: 'Uygulamayı yeniden başlat',
  reboot: 'Cihazı yeniden başlat',
  show_message: 'Ekrana mesaj gönder',
  unlock: 'Kilidi aç',
  lock: 'Kilitle',
}

export default function DeviceDetail() {
  const { deviceId } = useParams<{ deviceId: string }>()
  const [device, setDevice] = useState<Device | null>(null)
  const [events, setEvents] = useState<DeviceEvent[]>([])
  const [commands, setCommands] = useState<DeviceCommand[]>([])
  const [label, setLabel] = useState('')
  const [targetPackage, setTargetPackage] = useState('')
  const [webUrl, setWebUrl] = useState('')
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)
  const [provisionError, setProvisionError] = useState<string | null>(null)
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null)
  const [historyTab, setHistoryTab] = useState<'events' | 'commands'>('events')

  useEffect(() => {
    if (!deviceId) return
    return onSnapshot(doc(db, 'devices', deviceId), (snap) => {
      if (!snap.exists()) {
        setDevice(null)
        return
      }
      const data = { id: snap.id, ...snap.data() } as Device
      setDevice(data)
      setLabel(data.label ?? '')
      setTargetPackage(data.targetPackage ?? '')
      setWebUrl(data.webUrl ?? '')
    })
  }, [deviceId])

  useEffect(() => {
    if (!deviceId) return
    const q = query(
      collection(db, 'devices', deviceId, 'events'),
      orderBy('createdAt', 'desc'),
      limit(50),
    )
    return onSnapshot(q, (snap) => {
      setEvents(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as DeviceEvent))
    })
  }, [deviceId])

  useEffect(() => {
    if (!deviceId) return
    const q = query(
      collection(db, 'devices', deviceId, 'commands'),
      orderBy('createdAt', 'desc'),
      limit(50),
    )
    return onSnapshot(q, (snap) => {
      setCommands(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as DeviceCommand))
    })
  }, [deviceId])

  async function saveProvisioning(e: FormEvent) {
    e.preventDefault()
    if (!deviceId) return
    if (targetPackage.trim() && webUrl.trim()) {
      setProvisionError(
        'Paket adı ve web adresini aynı anda dolduramazsın — hangisi açılacaksa sadece onu doldur, diğerini boş bırak.',
      )
      return
    }
    setProvisionError(null)
    setSaving(true)
    try {
      await updateDoc(doc(db, 'devices', deviceId), { label, targetPackage, webUrl })
    } finally {
      setSaving(false)
    }
  }

  function confirmCommand(
    title: string,
    description: string,
    type: DeviceCommand['type'],
    payload?: Record<string, unknown>,
  ) {
    setPendingAction({
      title,
      description,
      run: () => {
        if (deviceId) sendCommand(deviceId, type, payload)
        setPendingAction(null)
      },
    })
  }

  async function handleSendMessage(e: FormEvent) {
    e.preventDefault()
    if (!message.trim() || !deviceId) return
    await sendCommand(deviceId, 'show_message', { text: message.trim() })
    setMessage('')
  }

  if (!deviceId) return null
  if (device === null) return <p className="muted">Yükleniyor…</p>

  const status = deviceStatus(device)

  return (
    <div>
      <Link to="/" className="back-link">
        ← Cihaz listesi
      </Link>

      <div className="detail-header">
        <h1>{device.label || 'İsimsiz cihaz'}</h1>
        <div className={`status-pill status-${status}`}>{statusLabel(status)}</div>
      </div>

      <section className="card">
        <h2>Cihaz Durumu</h2>
        <dl className="stat-grid">
          <dt>Kilit</dt>
          <dd>
            {device.locked === false ? (
              <span className="status-pill status-unlocked">Kilit açık</span>
            ) : (
              <span className="status-pill status-online">Kilitli</span>
            )}
          </dd>
          <dt>Pil</dt>
          <dd>
            {device.batteryPct !== undefined ? `${device.batteryPct}%` : '—'}
            {device.charging ? ' (şarjda)' : ''}
          </dd>
          <dt>Son görülme</dt>
          <dd>{formatRelativeTime(device.lastSeenAt)}</dd>
          <dt>Wi-Fi sinyali</dt>
          <dd>{device.wifiRssi !== undefined ? `${device.wifiRssi} dBm` : '—'}</dd>
          <dt>Boş depolama</dt>
          <dd>{device.storageFreeMb !== undefined ? `${device.storageFreeMb} MB` : '—'}</dd>
          <dt>Uygulama sürümü</dt>
          <dd>{device.appVersionName ?? '—'}</dd>
        </dl>
      </section>

      <section className="card">
        <h2>Cihaz Ayarları</h2>
        <p className="muted" style={{ marginBottom: 12 }}>
          Paket adı ile web adresinden sadece biri doldurulmalı — hangisi doluysa tablet onu açar.
        </p>
        <form className="provision-form" onSubmit={saveProvisioning}>
          <label>
            Cihaz adı
            <input
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              placeholder="örn. Hazım Uluşahin İlkokulu"
            />
          </label>
          <label>
            Kilitlenecek uygulamanın paket adı
            <input
              value={targetPackage}
              onChange={(e) => setTargetPackage(e.target.value)}
              placeholder="örn. com.example.app"
            />
          </label>
          <label>
            Tam ekran açılacak web adresi (opsiyonel)
            <input
              value={webUrl}
              onChange={(e) => setWebUrl(e.target.value)}
              placeholder="örn. https://menu.noktago.com"
            />
          </label>
          <button type="submit" disabled={saving}>
            {saving ? 'Kaydediliyor…' : 'Kaydet'}
          </button>
        </form>
        {provisionError && (
          <p className="error-text" style={{ marginTop: 10 }}>
            {provisionError}
          </p>
        )}
        {webUrl.trim() && (
          <p className="muted" style={{ marginTop: 10 }}>
            Web adresi doluyken tablet paket adını değil, bu sayfayı tam ekran açar.
          </p>
        )}
      </section>

      <section className="card">
        <h2>Uzaktan Kontrol</h2>
        <div className="action-row">
          <button
            className="action-btn neutral"
            onClick={() =>
              confirmCommand(
                'Uygulamayı yeniden başlat',
                'Tablette kilitli duran hedef kapatılıp yeniden açılacak. Ekranda kısa bir kesinti olur.',
                'restart_app',
              )
            }
          >
            <RestartIcon /> Uygulamayı yeniden başlat
          </button>
          <button
            className="action-btn danger"
            onClick={() =>
              confirmCommand(
                'Cihazı yeniden başlat',
                'Tablet tamamen kapanıp yeniden açılacak, bu birkaç dakika sürebilir. Emin misin?',
                'reboot',
              )
            }
          >
            <PowerIcon /> Cihazı yeniden başlat
          </button>
          <button
            className="action-btn warn"
            onClick={() =>
              confirmCommand(
                'Kilidi aç',
                'Tablet kilitten çıkıp normal bir Android cihazı gibi kullanılabilecek. Süreye bağlı değil: sen "Kilitle" demeden ya da tablette kiosk\'a dönülmeden kilitlenmez.',
                'unlock',
              )
            }
          >
            <UnlockIcon /> Kilidi aç
          </button>
          <button
            className="action-btn ok"
            onClick={() =>
              confirmCommand(
                'Kilitle',
                'Tablet, şu an ekranda ne varsa bırakıp hedefi yeniden açıp kilitleyecek.',
                'lock',
              )
            }
          >
            <LockIcon /> Kilitle
          </button>
        </div>
        <label className="message-label">Ekranda gösterilecek mesaj (10 dakika boyunca görünür kalır)</label>
        <form className="message-form" onSubmit={handleSendMessage}>
          <input
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder="örn. Bakım için 5 dk kapalı kalacak"
          />
          <button type="submit" className="action-btn neutral">
            <SendIcon /> Gönder
          </button>
        </form>
      </section>

      <section className="card">
        <h2>Geçmiş</h2>
        <div className="tab-row">
          <button
            type="button"
            className={historyTab === 'events' ? 'tab active' : 'tab'}
            onClick={() => setHistoryTab('events')}
          >
            Olaylar
          </button>
          <button
            type="button"
            className={historyTab === 'commands' ? 'tab active' : 'tab'}
            onClick={() => setHistoryTab('commands')}
          >
            Komutlar
          </button>
        </div>

        {historyTab === 'events' ? (
          <PagedGroupedList
            key="events"
            items={events}
            getDate={(ev) => ev.createdAt}
            emptyText="Henüz olay yok."
            renderItem={(ev) => (
              <li key={ev.id}>
                <span className="log-type">{EVENT_LABELS[ev.type] ?? ev.type}</span>
                {ev.message && <span className="log-message">{ev.message}</span>}
                <span className="log-time">{formatRelativeTime(ev.createdAt)}</span>
              </li>
            )}
          />
        ) : (
          <PagedGroupedList
            key="commands"
            items={commands}
            getDate={(cmd) => cmd.createdAt}
            emptyText="Henüz komut gönderilmedi."
            renderItem={(cmd) => (
              <li key={cmd.id}>
                <span className="log-type">{COMMAND_LABELS[cmd.type] ?? cmd.type}</span>
                <span className={`cmd-status cmd-${cmd.status}`}>
                  {cmd.status === 'done' ? 'Uygulandı' : 'Bekliyor'}
                </span>
                <span className="log-time">{formatRelativeTime(cmd.createdAt)}</span>
              </li>
            )}
          />
        )}
      </section>

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
