import { theme, type ThemeConfig } from 'antd'

// Warna merek sama dengan aplikasi HP: hijau kebun dan emas panen.
export const WARNA = {
  hijau: '#15803d',
  hijauTua: '#0f2a1a',
  emas: '#a16207',
  latarTerang: '#f4f7f1',
  latarGelap: '#0d1a12',
} as const

export function buatTema(gelap: boolean): ThemeConfig {
  return {
    algorithm: gelap ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
      colorPrimary: WARNA.hijau,
      colorWarning: '#ca8a04',
      colorSuccess: '#16a34a',
      colorError: '#dc2626',
      colorInfo: '#0e7490',
      borderRadius: 10,
      fontSize: 14,
      fontFamily:
        "'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
      colorBgLayout: gelap ? WARNA.latarGelap : WARNA.latarTerang,
      colorBgContainer: gelap ? '#14231a' : '#ffffff',
    },
    components: {
      Layout: {
        siderBg: WARNA.hijauTua,
        triggerBg: '#12361f',
        headerBg: gelap ? '#14231a' : '#ffffff',
        headerPadding: '0 24px',
        bodyBg: gelap ? WARNA.latarGelap : WARNA.latarTerang,
      },
      Menu: {
        darkItemBg: 'transparent',
        darkSubMenuItemBg: 'transparent',
        darkItemSelectedBg: WARNA.hijau,
        darkItemHoverBg: 'rgba(255,255,255,0.08)',
        itemBorderRadius: 8,
        itemMarginInline: 10,
      },
      Card: { borderRadiusLG: 14 },
      Table: { headerBg: gelap ? '#1a2d21' : '#f0f5ed', headerColor: gelap ? '#d1e7d8' : '#14231a', borderRadiusLG: 12 },
      Button: { controlHeight: 36, primaryShadow: 'none' },
    },
  }
}
