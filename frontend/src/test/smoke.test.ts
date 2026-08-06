import { describe, it, expect } from 'vitest'

// Vitest 설정 스모크 테스트: 테스트 러너 + 커버리지 파이프라인이 동작하는지 확인
describe('Vitest 설정 확인', () => {
  it('테스트 러너가 동작한다', () => {
    expect(1 + 1).toBe(2)
  })

  it('타입스크립트가 동작한다', () => {
    const message: string = 'job-tracker'
    expect(message.toUpperCase()).toBe('JOB-TRACKER')
  })
})
