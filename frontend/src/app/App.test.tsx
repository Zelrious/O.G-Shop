import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App } from './App'

describe('App', () => {
  it('shows the approved project identity and foundation status', () => {
    render(<App />)

    expect(screen.getByRole('heading', { level: 1, name: 'O.G Shop' })).toBeInTheDocument()
    expect(screen.getByText('Nền tảng mua bán đồ cũ đáng tin cậy.')).toBeInTheDocument()
    expect(screen.getByText('Foundation · In progress')).toBeInTheDocument()
  })
})
