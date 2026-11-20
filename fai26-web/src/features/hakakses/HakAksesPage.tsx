import { useQuery } from '@tanstack/react-query'
import { Alert, App, Button, Card, Checkbox, Space, Table, Tag, Tooltip } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { hakAksesApi } from '@/api/endpoints'
import type { HakAksesRole, IzinKatalog } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { useAuth } from '@/features/auth/AuthProvider'
import { useAksi } from '@/lib/useAksi'
import { dariServer, hitungPerubahan, kotakDikunci, ubahKotak, type DraftHak, type PerubahanRole } from './perubahan'

const NAMA_MODUL: Record<string, string> = {
  Panen: 'Panen',
  Master: 'Data master',
  Laporan: 'Laporan',
  ERP: 'ERP',
  Akses: 'Akses pengguna',
  Audit: 'Audit',
  Model: 'Model AI',
}

function kelompokModul(katalog: IzinKatalog[]): { modul: string; izin: IzinKatalog[] }[] {
  const peta = new Map<string, IzinKatalog[]>()
  for (const k of katalog) peta.set(k.modul, [...(peta.get(k.modul) ?? []), k])
  return [...peta.entries()].map(([modul, izin]) => ({ modul, izin }))
}

/** Matriks hak akses: baris permission, kolom role. Perubahan disimpan per role (mengganti seluruh hak role itu). */
export function HakAksesPage() {
  const { modal } = App.useApp()
  const { muatUlangProfil } = useAuth()
  const q = useQuery({ queryKey: ['hak-akses'], queryFn: hakAksesApi.daftar })
  const [draft, setDraft] = useState<DraftHak>({})

  useEffect(() => {
    if (q.data) setDraft(dariServer(q.data.role))
  }, [q.data])

  const perubahan = useMemo(() => (q.data ? hitungPerubahan(q.data.role, draft) : []), [q.data, draft])

  const simpan = useAksi({
    fn: async (daftar: PerubahanRole[]) => {
      // Satu per satu: bila ada yang ditolak, yang sebelumnya sudah tersimpan dan pesannya jelas.
      for (const p of daftar) await hakAksesApi.ubah(p.roleId, [...(draft[p.roleId] ?? [])].sort())
      return { status: 'OK' }
    },
    segarkan: [['hak-akses']],
    sukses: 'Hak akses disimpan. HP mengikuti saat sinkron berikutnya.',
    onSukses: () => void muatUlangProfil(), // hak akun yang sedang dipakai ikut berubah
  })

  const tanyaSimpan = () =>
    modal.confirm({
      title: `Simpan perubahan hak ${perubahan.length} role?`,
      width: 560,
      okText: 'Simpan',
      cancelText: 'Batal',
      content: (
        <div>
          <p>Berlaku langsung di server, dan di HP setelah sinkron berikutnya.</p>
          {perubahan.map((p) => (
            <div key={p.roleId} style={{ marginBottom: 8 }}>
              <strong>{p.kode}</strong>
              <div>
                {p.tambah.map((k) => (
                  <Tag key={k} color="green">+ {k}</Tag>
                ))}
                {p.cabut.map((k) => (
                  <Tag key={k} color="red">- {k}</Tag>
                ))}
              </div>
            </div>
          ))}
        </div>
      ),
      onOk: () => simpan.mutateAsync(perubahan),
    })

  const role: HakAksesRole[] = q.data?.role ?? []
  const berubah = new Set(perubahan.map((p) => p.roleId))

  return (
    <>
      <PageHeader
        judul="Hak Akses"
        keterangan="Atur apa yang boleh dilakukan tiap role. Centang lalu simpan; hak ikut berlaku untuk semua akun dengan role itu."
        aksi={
          <Space>
            <Button disabled={perubahan.length === 0 || simpan.isPending} onClick={() => q.data && setDraft(dariServer(q.data.role))}>
              Batalkan
            </Button>
            <Button type="primary" disabled={perubahan.length === 0} loading={simpan.isPending} onClick={tanyaSimpan}>
              {perubahan.length > 0 ? `Simpan perubahan (${perubahan.length} role)` : 'Simpan perubahan'}
            </Button>
          </Space>
        }
      />
      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Hak role ADMIN untuk mengelola hak akses tidak bisa dicabut, supaya tidak ada yang terkunci dari halaman ini."
      />
      <Space direction="vertical" size="middle" style={{ display: 'flex' }}>
        {kelompokModul(q.data?.permission ?? []).map((g) => (
          <Card key={g.modul} title={NAMA_MODUL[g.modul] ?? g.modul} loading={q.isLoading} styles={{ body: { padding: 0 } }}>
            <Table<IzinKatalog>
              rowKey="kode"
              size="small"
              pagination={false}
              dataSource={g.izin}
              scroll={{ x: 640 }}
              columns={[
                {
                  title: 'Hak',
                  key: 'izin',
                  render: (_: unknown, i) => (
                    <>
                      {i.nama}
                      <div className="tabel-sel-kode" style={{ opacity: 0.65 }}>{i.kode}</div>
                    </>
                  ),
                },
                ...role.map((r) => ({
                  title: (
                    <span style={berubah.has(r.role_id) ? { color: '#d97706' } : undefined}>
                      {r.nama}
                      {berubah.has(r.role_id) ? ' •' : ''}
                    </span>
                  ),
                  key: r.kode,
                  width: 110,
                  align: 'center' as const,
                  render: (_: unknown, i: IzinKatalog) => {
                    const kunci = kotakDikunci(r.kode, i.kode)
                    const kotak = (
                      <Checkbox
                        checked={draft[r.role_id]?.has(i.kode) ?? false}
                        disabled={kunci || simpan.isPending}
                        aria-label={`${r.nama}: ${i.nama}`}
                        onChange={(e) => setDraft((d) => ubahKotak(d, r.role_id, i.kode, e.target.checked))}
                      />
                    )
                    return kunci ? <Tooltip title="Tidak bisa dicabut dari ADMIN">{kotak}</Tooltip> : kotak
                  },
                })),
              ]}
            />
          </Card>
        ))}
      </Space>
    </>
  )
}
