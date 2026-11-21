import { DownloadOutlined, FileExcelOutlined, FilePdfOutlined, PrinterOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, App, Button, Checkbox, DatePicker, Form, Modal, Segmented, Select, Space, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useState } from 'react'
import { divisiApi, ipdApi } from '@/api/endpoints'
import type { FilterIpd, IpdDetail } from '@/api/types'
import { pesanGalat } from '@/lib/galat'
import { cetakLaporan } from '@/lib/cetak'
import { eksporExcel, eksporPdf, type GambarEkspor, type LaporanEkspor } from '@/lib/ekspor'
import { tanggal } from '@/lib/format'
import { STATUS_IPD } from './ipdTampil'
import { NAMA_MODE, hitungPeriode, type ModePeriode } from './periode'
import { kumpulkan, tambahCatatanEkspor } from './pilihanEkspor'

/** Pilihan yang dibuat pengguna di dialog; diteruskan ke penyusun laporan untuk keterangan di bagian atas. */
export interface KonteksEkspor {
  dari: string
  sampai: string
  divisiNama?: string
  statusTeks?: string
}

export type AksiEkspor = 'ekspor' | 'cetak'

interface Props {
  aksi: AksiEkspor | null
  onTutup: () => void
  /** Nilai awal pilihan divisi (mis. dari filter halaman). */
  divisiAwal?: string
  /** Bila diisi, status dikunci ke nilai ini dan pilihannya tidak ditampilkan (laporan loses: hanya yang disetujui). */
  statusTetap?: string
  statusAwal?: string
  susun: (detail: IpdDetail[], foto: Map<string, GambarEkspor[]>, k: KonteksEkspor) => LaporanEkspor
}

/**
 * Dialog Ekspor dan Cetak: pilih periode (satu tanggal, satu bulan, satu tahun, atau rentang), divisi (dan status), lalu Excel
 * atau PDF (Ekspor) atau langsung Cetak. Jumlah inspeksi pada periode itu ditampilkan sebelum menekan tombol.
 */
export function DialogEkspor({ aksi, onTutup, divisiAwal, statusTetap, statusAwal, susun }: Props) {
  const { message } = App.useApp()
  const [mode, setMode] = useState<ModePeriode>('bulan')
  const [tgl, setTgl] = useState<Dayjs>(dayjs())
  const [rentang, setRentang] = useState<[Dayjs, Dayjs]>([dayjs().startOf('month'), dayjs()])
  const [divisiKode, setDivisiKode] = useState<string | undefined>(divisiAwal)
  const [status, setStatus] = useState<string | undefined>(statusAwal)
  const [denganFoto, setDenganFoto] = useState(false)
  const [sibuk, setSibuk] = useState<string | null>(null)
  const [pesan, setPesan] = useState('')

  const divisi = useQuery({ queryKey: ['divisi'], queryFn: divisiApi.daftar, enabled: aksi !== null })
  const { dari, sampai } = hitungPeriode(mode, tgl, rentang)
  const statusPakai = statusTetap ?? status
  const filter: FilterIpd = { dari, sampai, divisi_kode: divisiKode, status: statusPakai }
  const hitung = useQuery({
    queryKey: ['ipd-hitung', dari, sampai, divisiKode, statusPakai],
    queryFn: async () => (await ipdApi.daftar({ ...filter, halaman: 1, per_halaman: 1 })).total,
    enabled: aksi !== null,
  })
  const jumlah = hitung.data

  async function jalankan(tujuan: 'xlsx' | 'pdf' | 'cetak') {
    setSibuk(tujuan)
    try {
      const k = await kumpulkan(filter, denganFoto, setPesan)
      const ctx: KonteksEkspor = {
        dari,
        sampai,
        divisiNama: divisi.data?.find((d) => d.kode === divisiKode)?.nama,
        statusTeks: statusTetap ? undefined : statusPakai ? STATUS_IPD[statusPakai as keyof typeof STATUS_IPD]?.teks : undefined,
      }
      const lap = tambahCatatanEkspor(susun(k.detail, k.foto, ctx), k.catatan)
      setPesan('Menyusun berkas…')
      if (tujuan === 'xlsx') await eksporExcel(lap)
      else if (tujuan === 'pdf') await eksporPdf(lap)
      else await cetakLaporan(lap)
      void message.success(tujuan === 'cetak' ? 'Dialog cetak dibuka' : 'Berkas siap diunduh')
      onTutup()
    } catch (e) {
      void message.error(`Gagal: ${pesanGalat(e)}`)
    } finally {
      setSibuk(null)
      setPesan('')
    }
  }

  const kosong = jumlah === 0
  const tidakBisa = sibuk !== null || kosong || hitung.isLoading

  return (
    <Modal
      open={aksi !== null}
      onCancel={sibuk ? undefined : onTutup}
      maskClosable={!sibuk}
      title={aksi === 'cetak' ? 'Cetak laporan inspeksi' : 'Ekspor laporan inspeksi'}
      destroyOnClose
      width={520}
      footer={
        aksi === 'cetak' ? (
          <Space>
            <Button onClick={onTutup} disabled={sibuk !== null}>Batal</Button>
            <Button type="primary" icon={<PrinterOutlined />} loading={sibuk === 'cetak'} disabled={tidakBisa} onClick={() => void jalankan('cetak')}>
              Cetak
            </Button>
          </Space>
        ) : (
          <Space wrap>
            <Button onClick={onTutup} disabled={sibuk !== null}>Batal</Button>
            <Button icon={<FileExcelOutlined />} loading={sibuk === 'xlsx'} disabled={tidakBisa} onClick={() => void jalankan('xlsx')}>
              Unduh Excel
            </Button>
            <Button type="primary" icon={<FilePdfOutlined />} loading={sibuk === 'pdf'} disabled={tidakBisa} onClick={() => void jalankan('pdf')}>
              Unduh PDF
            </Button>
          </Space>
        )
      }
    >
      <Form layout="vertical" disabled={sibuk !== null}>
        <Form.Item label="Periode">
          <Space direction="vertical" style={{ width: '100%' }}>
            <Segmented<ModePeriode> block value={mode} onChange={setMode} options={(Object.keys(NAMA_MODE) as ModePeriode[]).map((m) => ({ value: m, label: NAMA_MODE[m] }))} />
            {mode === 'rentang' ? (
              <DatePicker.RangePicker
                style={{ width: '100%' }}
                value={rentang}
                allowClear={false}
                format="D MMM YYYY"
                onChange={(v) => v?.[0] && v[1] && setRentang([v[0], v[1]])}
                aria-label="Rentang tanggal"
              />
            ) : (
              <DatePicker
                style={{ width: '100%' }}
                picker={mode === 'hari' ? 'date' : mode === 'bulan' ? 'month' : 'year'}
                value={tgl}
                allowClear={false}
                format={mode === 'hari' ? 'dddd, D MMMM YYYY' : mode === 'bulan' ? 'MMMM YYYY' : 'YYYY'}
                onChange={(v) => v && setTgl(v)}
                aria-label="Pilih periode"
              />
            )}
            <Typography.Text type="secondary">
              {dari === sampai ? tanggal(dari) : `${tanggal(dari)} sampai ${tanggal(sampai)}`}
            </Typography.Text>
          </Space>
        </Form.Item>
        <Form.Item label="Divisi">
          <Select
            allowClear
            placeholder="Semua divisi"
            value={divisiKode}
            onChange={setDivisiKode}
            options={(divisi.data ?? []).map((d) => ({ value: d.kode, label: d.nama }))}
            aria-label="Divisi"
          />
        </Form.Item>
        {!statusTetap && (
          <Form.Item label="Status">
            <Select
              allowClear
              placeholder="Semua status"
              value={status}
              onChange={setStatus}
              options={Object.entries(STATUS_IPD).map(([k, v]) => ({ value: k, label: v.teks }))}
              aria-label="Status inspeksi"
            />
          </Form.Item>
        )}
        <Form.Item>
          <Checkbox checked={denganFoto} onChange={(e) => setDenganFoto(e.target.checked)}>
            Sertakan foto bukti (lebih lama)
          </Checkbox>
        </Form.Item>
      </Form>
      {statusTetap === 'DISETUJUI' && <Typography.Paragraph type="secondary">Hanya inspeksi yang sudah disetujui asisten.</Typography.Paragraph>}
      {hitung.isError ? (
        <Alert type="error" showIcon message="Jumlah inspeksi tidak bisa dimuat" />
      ) : kosong ? (
        <Alert type="warning" showIcon message="Tidak ada inspeksi pada periode ini. Ganti periode atau filternya." />
      ) : (
        <Alert type="info" showIcon icon={<DownloadOutlined />} message={hitung.isLoading ? 'Menghitung inspeksi…' : `${jumlah} inspeksi pada periode ini`} />
      )}
      {sibuk && <Typography.Paragraph style={{ marginTop: 12 }}>{pesan || 'Menyiapkan…'}</Typography.Paragraph>}
    </Modal>
  )
}
