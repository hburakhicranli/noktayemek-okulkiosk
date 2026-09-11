import { addDoc, collection, serverTimestamp } from 'firebase/firestore'
import { db } from './firebase'
import type { DeviceCommandType } from './types'

export async function sendCommand(
  deviceId: string,
  type: DeviceCommandType,
  payload?: Record<string, unknown>,
) {
  await addDoc(collection(db, 'devices', deviceId, 'commands'), {
    type,
    payload: payload ?? {},
    status: 'pending',
    createdAt: serverTimestamp(),
  })
}

export async function sendCommandToAll(
  deviceIds: string[],
  type: DeviceCommandType,
  payload?: Record<string, unknown>,
) {
  await Promise.all(deviceIds.map((id) => sendCommand(id, type, payload)))
}
