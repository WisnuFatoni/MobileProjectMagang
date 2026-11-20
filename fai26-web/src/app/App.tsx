import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp, ConfigProvider } from 'antd'
import idID from 'antd/locale/id_ID'
import { useState, type ReactNode } from 'react'
import { RouterProvider } from 'react-router-dom'
import { ApiError } from '@/api/http'
import { AuthProvider } from '@/features/auth/AuthProvider'
import { buatTema } from '@/theme/theme'
import { router } from './router'
import { TemaProvider, useTema } from './tema'

function buatQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        // Jangan mengulang galat 4xx (isi salah, tidak berhak); ulangi sekali untuk jaringan atau server.
        retry: (n, e) => !(e instanceof ApiError && e.status >= 400 && e.status < 500) && n < 1,
      },
    },
  })
}

function Tampilan({ children }: { children: ReactNode }) {
  const { mode } = useTema()
  return (
    <ConfigProvider theme={buatTema(mode === 'gelap')} locale={idID}>
      <AntApp>{children}</AntApp>
    </ConfigProvider>
  )
}

export function App() {
  const [qc] = useState(buatQueryClient)
  return (
    <QueryClientProvider client={qc}>
      <TemaProvider>
        <Tampilan>
          <AuthProvider>
            <RouterProvider router={router} future={{ v7_startTransition: true }} />
          </AuthProvider>
        </Tampilan>
      </TemaProvider>
    </QueryClientProvider>
  )
}
