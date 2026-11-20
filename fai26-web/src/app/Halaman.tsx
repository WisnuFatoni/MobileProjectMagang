import { Spin } from 'antd'
import { Suspense, type ReactNode } from 'react'
import { RequireIzin } from '@/features/auth/guards'

export function Halaman({ izin, children }: { izin: string; children: ReactNode }) {
  return (
    <RequireIzin izin={izin}>
      <Suspense fallback={<Spin style={{ display: 'block', margin: '64px auto' }} />}>{children}</Suspense>
    </RequireIzin>
  )
}
