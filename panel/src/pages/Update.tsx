import { doc, onSnapshot, serverTimestamp, setDoc } from 'firebase/firestore'
import { useEffect, useState, type FormEvent } from 'react'
import { db } from '../firebase'

interface UpdateConfig {
  versionCode?: number
  apkUrl?: string
  notes?: string
  publishedAt?: { toDate: () => Date }
}

export default function Update() {
  const [current, setCurrent] = useState<UpdateConfig | null>(null)
  const [versionCode, setVersionCode] = useState('')
  const [apkUrl, setApkUrl] = useState('')
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    return onSnapshot(doc(db, 'config', 'update'), (snap) => {
      const data = (snap.data() ?? {}) as UpdateConfig
      setCurrent(data)
    })
  }, [])

  async function publish(e: FormEvent) {
    e.preventDefault()
    const code = Number(versionCode)
    if (!code || !apkUrl.trim()) return
    setSaving(true)
    try {
      await setDoc(doc(db, 'config', 'update'), {
        versionCode: code,
        apkUrl: apkUrl.trim(),
        notes: notes.trim(),
        publishedAt: serverTimestamp(),
      })
      setVersionCode('')
      setApkUrl('')
      setNotes('')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <h1 style={{ fontSize: 22, marginBottom: 6 }}>Güncelleme</h1>
      <p className="muted" style={{ marginBottom: 20, maxWidth: 620 }}>
        Buraya yayınlanan sürüm, kurulu sürümünden yüksekse tüm tabletler bir sonraki
        heartbeat'te (en geç 5 dk içinde) APK'yı indirip kendiliğinden kurar — hiçbir
        onay ekranı çıkmaz, tablette biri olması gerekmez.
      </p>

      <section className="card">
        <h2>Şu an yayınlanan sürüm</h2>
        {current?.versionCode ? (
          <dl className="stat-grid">
            <dt>versionCode</dt>
            <dd>{current.versionCode}</dd>
            <dt>APK adresi</dt>
            <dd style={{ wordBreak: 'break-all' }}>{current.apkUrl}</dd>
            {current.notes && (
              <>
                <dt>Not</dt>
                <dd>{current.notes}</dd>
              </>
            )}
          </dl>
        ) : (
          <p className="muted">Henüz bir sürüm yayınlanmadı.</p>
        )}
      </section>

      <section className="card">
        <h2>Yeni sürüm yayınla</h2>
        <ol className="muted" style={{ marginBottom: 16, paddingLeft: 18, maxWidth: 620 }}>
          <li>Android Studio'da <code>versionCode</code>'u artır, Build → Generate Signed/Build APK ile derle.</li>
          <li>
            GitHub reposunda yeni bir Release oluştur, çıkan APK'yı o release'e dosya olarak
            ekle (Firebase Hosting'in ücretsiz planı .apk dosyalarına izin vermiyor, o yüzden
            GitHub Releases kullanıyoruz).
          </li>
          <li>Release'deki APK dosyasının linkine sağ tık → "bağlantı adresini kopyala", aşağıya yapıştır.</li>
        </ol>
        <form className="provision-form" onSubmit={publish}>
          <label>
            Yeni versionCode
            <input
              type="number"
              value={versionCode}
              onChange={(e) => setVersionCode(e.target.value)}
              placeholder="örn. 3"
            />
          </label>
          <label>
            APK adresi
            <input
              value={apkUrl}
              onChange={(e) => setApkUrl(e.target.value)}
              placeholder="https://noktago-kiosk.web.app/app-release-3.apk"
            />
          </label>
          <label>
            Not (opsiyonel)
            <input
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="örn. Gece yarısı yeniden başlatma eklendi"
            />
          </label>
          <button type="submit" disabled={saving}>
            {saving ? 'Yayınlanıyor…' : 'Yayınla'}
          </button>
        </form>
      </section>
    </div>
  )
}
