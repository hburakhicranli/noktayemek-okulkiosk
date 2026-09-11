import type { Timestamp } from 'firebase/firestore'

export interface Device {
  id: string
  label: string
  targetPackage: string
  webUrl?: string
  authUid: string
  locked?: boolean
  batteryPct?: number
  charging?: boolean
  wifiRssi?: number
  appVersionName?: string
  storageFreeMb?: number
  uptimeSec?: number
  lastSeenAt?: Timestamp
  createdAt?: Timestamp
}

export type DeviceEventType =
  | 'boot'
  | 'crash'
  | 'unauthorized_exit'
  | 'command_ack'
  | 'update_installed'
  | 'update_failed'

export interface DeviceEvent {
  id: string
  type: DeviceEventType
  message?: string
  createdAt?: Timestamp
}

export type DeviceCommandType = 'restart_app' | 'reboot' | 'show_message' | 'unlock' | 'lock'
export type DeviceCommandStatus = 'pending' | 'done'

export interface DeviceCommand {
  id: string
  type: DeviceCommandType
  payload?: Record<string, unknown>
  status: DeviceCommandStatus
  createdAt?: Timestamp
  doneAt?: Timestamp
}

/** A device counts as online if we heard from it more recently than this. Must comfortably
 *  exceed the Android app's 5-minute heartbeat interval, or devices flicker offline between
 *  beats -- one missed beat plus a buffer. */
export const ONLINE_THRESHOLD_MS = 7 * 60 * 1000
/** Battery percentage at/below this while not charging triggers a warning. */
export const LOW_BATTERY_PCT = 20
