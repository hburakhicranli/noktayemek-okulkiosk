import { doc, onSnapshot, setDoc } from 'firebase/firestore'
import { useEffect, useState, type FormEvent } from 'react'
import { db } from '../firebase'

const DEFAULT_IDLE_SEC = 600

export default function Screensaver() {
  const [imageUrls, setImageUrls] = useState<string[]>([])
  const [newUrl, setNewUrl] = useState('')
  const [saving, setSaving] = useState(false)
  const [idleSec, setIdleSec] = useState(DEFAULT_IDLE_SEC)
  const [savingIdle, setSavingIdle] = useState(false)

  useEffect(() => {
    return onSnapshot(doc(db, 'config', 'screensaver'), (snap) => {
      const data = snap.data()
      const urls = data?.imageUrls
      setImageUrls(Array.isArray(urls) ? urls : [])
      setIdleSec(typeof data?.idleTimeoutSec === 'number' ? data.idleTimeoutSec : DEFAULT_IDLE_SEC)
    })
  }, [])

  async function addUrl(e: FormEvent) {
    e.preventDefault()
    if (!newUrl.trim()) return
    setSaving(true)
    try {
      await setDoc(doc(db, 'config', 'screensaver'), { imageUrls: [...imageUrls, newUrl.trim()] }, { merge: true })
      setNewUrl('')
    } finally {
      setSaving(false)
    }
  }

  async function removeUrl(url: string) {
    await setDoc(
      doc(db, 'config', 'screensaver'),
      { imageUrls: imageUrls.filter((u) => u !== url) },
      { merge: true },
    )
  }

  async function saveIdleSec(e: FormEvent) {
    e.preventDefault()
    setSavingIdle(true)
    try {
      await setDoc(doc(db, 'config', 'screensaver'), { idleTimeoutSec: idleSec }, { merge: true })
    } finally {
      setSavingIdle(false)
    }
  }

  return (
    <div>
      <h1 style={{ fontSize: 22, marginBottom: 6 }}>Ekran Koruyucu</h1>
      <p className="muted" style={{ marginBottom: 20, maxWidth: 560 }}>
        Web adresiyle açılan tabletlerde belirlenen süre boyunca hareketsizlik sonrası bu
        görseller sırayla gösterilir. Bir NFC kart okutulunca kilitli sayfaya geri döner
        (dokunma ile açılmaz — bilerek kart isteniyor).
      </p>

      <section className="card">
        <h2>Bekleme süresi</h2>
        <form className="provision-form" onSubmit={saveIdleSec}>
          <label>
            Hareketsizlik süresi (saniye)
            <input
              type="number"
              min={10}
              value={idleSec}
              onChange={(e) => setIdleSec(Number(e.target.value))}
            />
          </label>
          <button type="submit" disabled={savingIdle}>
            {savingIdle ? 'Kaydediliyor…' : 'Kaydet'}
          </button>
        </form>
        <p className="muted" style={{ marginTop: 10 }}>
          Test için düşük tut (örn. 60 sn = 1 dk), yayına alırken 600'e (10 dk) döndür.
        </p>
      </section>

      <section className="card">
        <h2>Görseller</h2>
        {imageUrls.length === 0 ? (
          <p className="muted">Henüz görsel eklenmedi.</p>
        ) : (
          <div className="screensaver-grid">
            {imageUrls.map((url) => (
              <div className="screensaver-item" key={url}>
                <img src={url} alt="" />
                <button type="button" onClick={() => removeUrl(url)}>
                  Sil
                </button>
              </div>
            ))}
          </div>
        )}

        <form className="message-form" onSubmit={addUrl} style={{ marginTop: 16 }}>
          <input
            value={newUrl}
            onChange={(e) => setNewUrl(e.target.value)}
            placeholder="https://... (görsel adresi)"
          />
          <button type="submit" disabled={saving}>
            {saving ? 'Ekleniyor…' : 'Ekle'}
          </button>
        </form>
      </section>
    </div>
  )
}
