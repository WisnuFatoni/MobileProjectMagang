import { describe, expect, it } from 'vitest'
import { rapikanJson } from './rapikan'

describe('rapikanJson', () => {
  it('merapikan JSON, membiarkan teks biasa, dan menangani kosong', () => {
    expect(rapikanJson('{"a":1,"b":[2]}')).toBe('{\n  "a": 1,\n  "b": [\n    2\n  ]\n}')
    expect(rapikanJson('bukan json')).toBe('bukan json')
    expect(rapikanJson(null)).toBe('')
  })
})
