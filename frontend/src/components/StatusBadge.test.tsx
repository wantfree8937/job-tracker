import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import StatusBadge from './StatusBadge'
import { STATUS_LABEL, ALL_STATUSES } from '../types'

describe('StatusBadge', () => {
  it.each(ALL_STATUSES)('%s 상태의 한국어 라벨을 렌더링한다', (status) => {
    render(<StatusBadge status={status} />)
    expect(screen.getByText(STATUS_LABEL[status])).toBeInTheDocument()
  })
})
