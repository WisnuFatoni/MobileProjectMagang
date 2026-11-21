import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AvatarAkun } from './FotoProfil'

const bungkus = (isi: React.ReactNode) => (
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{isi}</QueryClientProvider>
)

describe('AvatarAkun', () => {
  it('tanpa foto menampilkan huruf pertama nama (huruf besar)', () => {
    render(bungkus(<AvatarAkun nama="wisnu" versi={null} />))
    expect(screen.getByText('W')).toBeTruthy()
  })

  it('tanpa nama dan tanpa foto tidak error', () => {
    const { container } = render(bungkus(<AvatarAkun nama={undefined} versi={undefined} />))
    expect(container.querySelector('.ant-avatar')).toBeTruthy()
  })
})
