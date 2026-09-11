import type { Timestamp } from 'firebase/firestore'
import { LOW_BATTERY_PCT, ONLINE_THRESHOLD_MS, type Device } from './types'

export type DeviceStatus = 'online' | 'low-battery' | 'offline' | 'unprovisioned' | 'unlocked'

export function deviceStatus(device: Device): DeviceStatus {
  if (!device.targetPackage && !device.webUrl) return 'unprovisioned'
  const lastSeenMs = device.lastSeenAt?.toMillis() ?? 0
  const isOnline = Date.now() - lastSeenMs < ONLINE_THRESHOLD_MS
  if (!isOnline) return 'offline'
  // Surface "unlocked" ahead of battery/online noise -- an admin needs to notice this
  // at a glance, it's the one state where the tablet isn't doing its job.
  if (device.locked === false) return 'unlocked'
  if (device.batteryPct !== undefined && device.batteryPct <= LOW_BATTERY_PCT && !device.charging) {
    return 'low-battery'
  }
  return 'online'
}

export function statusLabel(status: DeviceStatus): string {
  switch (status) {
    case 'online':
      return 'Çevrimiçi'
    case 'low-battery':
      return 'Pil düşük'
    case 'offline':
      return 'Çevrimdışı'
    case 'unprovisioned':
      return 'Kurulum bekliyor'
    case 'unlocked':
      return 'Kilit açık'
  }
}

export function formatRelativeTime(ts?: Timestamp): string {
  if (!ts) return 'Hiç görülmedi'
  const diffMs = Date.now() - ts.toMillis()
  const diffSec = Math.round(diffMs / 1000)
  if (diffSec < 5) return 'Az önce'
  if (diffSec < 60) return `${diffSec} sn önce`
  const diffMin = Math.round(diffSec / 60)
  if (diffMin < 60) return `${diffMin} dk önce`
  const diffHour = Math.round(diffMin / 60)
  if (diffHour < 24) return `${diffHour} sa önce`
  const diffDay = Math.round(diffHour / 24)
  return `${diffDay} gün önce`
}

function startOfDay(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
}

export function dateGroupLabel(ts?: Timestamp): string {
  if (!ts) return 'Tarih bilinmiyor'
  const d = ts.toDate()
  const now = new Date()
  const diffDays = Math.round((startOfDay(now) - startOfDay(d)) / 86_400_000)
  if (diffDays === 0) return 'Bugün'
  if (diffDays === 1) return 'Dün'
  return d.toLocaleDateString('tr-TR', {
    day: 'numeric',
    month: 'long',
    year: d.getFullYear() !== now.getFullYear() ? 'numeric' : undefined,
  })
}

export interface DateGroup<T> {
  label: string
  items: T[]
}

/** Buckets an already-newest-first list into "Bugün" / "Dün" / dated groups. */
export function groupByDate<T>(items: T[], getDate: (item: T) => Timestamp | undefined): DateGroup<T>[] {
  const groups: DateGroup<T>[] = []
  for (const item of items) {
    const label = dateGroupLabel(getDate(item))
    const last = groups[groups.length - 1]
    if (last && last.label === label) {
      last.items.push(item)
    } else {
      groups.push({ label, items: [item] })
    }
  }
  return groups
}
