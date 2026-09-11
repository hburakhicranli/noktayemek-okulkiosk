type IconProps = { size?: number }

const base = {
  viewBox: '0 0 24 24',
  fill: 'none' as const,
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
}

export function RestartIcon({ size = 16 }: IconProps) {
  return (
    <svg {...base} width={size} height={size}>
      <path d="M21 12a9 9 0 1 1-3-6.7" />
      <path d="M21 3v6h-6" />
    </svg>
  )
}

export function PowerIcon({ size = 16 }: IconProps) {
  return (
    <svg {...base} width={size} height={size}>
      <path d="M12 2v8" />
      <path d="M18.4 6.6a9 9 0 1 1-12.8 0" />
    </svg>
  )
}

export function UnlockIcon({ size = 16 }: IconProps) {
  return (
    <svg {...base} width={size} height={size}>
      <rect x="4" y="11" width="16" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 0 1 7.6-1.8" />
    </svg>
  )
}

export function LockIcon({ size = 16 }: IconProps) {
  return (
    <svg {...base} width={size} height={size}>
      <rect x="4" y="11" width="16" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </svg>
  )
}

export function MailIcon({ size = 16 }: IconProps) {
  return (
    <svg {...base} width={size} height={size}>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="m3 7 9 6 9-6" />
    </svg>
  )
}

export function SendIcon({ size = 16 }: IconProps) {
  return (
    <svg {...base} width={size} height={size}>
      <path d="M22 2 11 13" />
      <path d="M22 2 15 22l-4-9-9-4 20-7Z" />
    </svg>
  )
}
