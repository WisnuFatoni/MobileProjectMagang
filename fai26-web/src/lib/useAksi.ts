import { useMutation, useQueryClient, type QueryKey } from '@tanstack/react-query'
import { App } from 'antd'
import { pesanGalat } from './galat'

interface Opsi<T, V> {
  fn: (v: V) => Promise<T>
  /** Kunci query yang disegarkan setelah berhasil. */
  segarkan: QueryKey[]
  /** Pesan sukses; boleh fungsi dari hasil. */
  sukses?: string | ((hasil: T, v: V) => string)
  onSukses?: (hasil: T, v: V) => void
}

/** Mutasi dengan pesan sukses dan galat seragam, lalu menyegarkan daftar yang terkait. */
export function useAksi<T, V>({ fn, segarkan, sukses, onSukses }: Opsi<T, V>) {
  const qc = useQueryClient()
  const { message } = App.useApp()
  return useMutation<T, Error, V>({
    mutationFn: fn,
    onSuccess: async (hasil, v) => {
      await Promise.all(segarkan.map((k) => qc.invalidateQueries({ queryKey: k })))
      if (sukses) void message.success(typeof sukses === 'function' ? sukses(hasil, v) : sukses)
      onSukses?.(hasil, v)
    },
    onError: (e) => void message.error(pesanGalat(e)),
  })
}
