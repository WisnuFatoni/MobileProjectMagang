import { Alert, Button } from 'antd'
import { pesanGalat } from '@/lib/galat'

/** Galat muat data dengan tombol coba lagi. */
export function ErrorState({ error, onCoba }: { error: unknown; onCoba?: () => void }) {
  return (
    <Alert
      type="error"
      showIcon
      message="Gagal memuat data"
      description={pesanGalat(error)}
      action={onCoba && <Button size="small" onClick={onCoba}>Coba lagi</Button>}
      role="alert"
    />
  )
}
