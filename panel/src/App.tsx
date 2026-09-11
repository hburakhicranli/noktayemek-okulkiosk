import { signOut } from 'firebase/auth'
import { BrowserRouter, Link, Route, Routes, useLocation } from 'react-router-dom'
import { auth } from './firebase'
import DeviceDetail from './pages/DeviceDetail'
import DeviceList from './pages/DeviceList'
import Login from './pages/Login'
import Screensaver from './pages/Screensaver'
import Update from './pages/Update'
import { useAuthUser } from './useAuthUser'

function NavTabs() {
  const location = useLocation()
  const onDevices = location.pathname === '/' || location.pathname.startsWith('/devices')
  const onScreensaver = location.pathname === '/screensaver'
  const onUpdate = location.pathname === '/update'
  return (
    <nav className="nav-tabs">
      <Link to="/" className={onDevices ? 'nav-tab active' : 'nav-tab'}>
        Cihazlar
      </Link>
      <Link to="/screensaver" className={onScreensaver ? 'nav-tab active' : 'nav-tab'}>
        Ekran Koruyucu
      </Link>
      <Link to="/update" className={onUpdate ? 'nav-tab active' : 'nav-tab'}>
        Güncelleme
      </Link>
    </nav>
  )
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <Link to="/" className="brand-mark">
            <img src="/nylogo.png" alt="Nokta Yemek" className="brand-mark-img" />
          </Link>
          <span className="brand-sub">Kiosk Yönetim Paneli</span>
          <NavTabs />
        </div>
        <button className="link-button" onClick={() => signOut(auth)}>
          Çıkış yap
        </button>
      </header>
      <main>{children}</main>
    </div>
  )
}

export default function App() {
  const { user, loading } = useAuthUser()

  if (loading) return null
  if (!user) return <Login />

  return (
    <BrowserRouter>
      <Shell>
        <Routes>
          <Route path="/" element={<DeviceList />} />
          <Route path="/devices/:deviceId" element={<DeviceDetail />} />
          <Route path="/screensaver" element={<Screensaver />} />
          <Route path="/update" element={<Update />} />
        </Routes>
      </Shell>
    </BrowserRouter>
  )
}
