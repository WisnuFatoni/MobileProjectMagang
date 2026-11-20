/** Tanda merek fAI26: tandan sawit di atas kotak hijau. */
export function Logo({ ukuran = 36 }: { ukuran?: number }) {
  return (
    <svg width={ukuran} height={ukuran} viewBox="0 0 64 64" role="img" aria-label="fAI26">
      <rect width="64" height="64" rx="14" fill="#15803d" />
      <path d="M32 12c-8 9-12 16-12 23a12 12 0 0 0 24 0c0-7-4-14-12-23z" fill="#fef3c7" />
      <path d="M32 26v24M32 36l-7-5M32 42l7-5" stroke="#15803d" strokeWidth="3" strokeLinecap="round" fill="none" />
    </svg>
  )
}
