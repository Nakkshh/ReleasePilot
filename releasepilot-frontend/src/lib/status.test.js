import { describe, expect, it } from 'vitest'
import { verdictKind } from './status.js'

describe('verdictKind', () => {
  it.each([
    ['READY', 'success'],
    ['NOT_READY', 'danger'],
    ['UNKNOWN', 'neutral'],
    ['', 'neutral'],
    [null, 'neutral'],
    ['ready', 'success'],
    ['not_ready', 'danger'],
  ])('maps %s to %s', (value, expected) => {
    expect(verdictKind(value)).toBe(expected)
  })
})