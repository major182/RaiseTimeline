import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { countChars } from '../postLength'
import { CharCounter } from './CharCounter'

describe('文字数の数え方（DB 設計書 D-7）', () => {
  it('コードポイントで数え、絵文字は 1 文字にする', () => {
    expect(countChars('abc')).toBe(3)
    expect(countChars('あいう')).toBe(3)
    expect('😀'.length).toBe(2) // JavaScript の length では 2
    expect(countChars('😀')).toBe(1)
  })

  it('組み合わせた絵文字は部品の数で数える（サーバーと同じ）', () => {
    expect(countChars('👨‍👩‍👧')).toBe(5)
  })
})

describe('文字数のカウンター（画面設計書 1.5）', () => {
  it('残り 21 文字以上は数を出さない', () => {
    render(<CharCounter text={'あ'.repeat(259)} max={280} />)
    expect(screen.getByRole('status', { name: '残り 21 文字' })).toBeInTheDocument()
    expect(screen.queryByTestId('char-remaining')).not.toBeInTheDocument()
  })

  it('残り 20〜0 文字は残りの数を出す', () => {
    const { rerender } = render(<CharCounter text={'あ'.repeat(260)} max={280} />)
    expect(screen.getByTestId('char-remaining')).toHaveTextContent('20')
    rerender(<CharCounter text={'😀'.repeat(280)} max={280} />)
    expect(screen.getByTestId('char-remaining')).toHaveTextContent('0')
  })

  it('超えたら「-5」のように出す', () => {
    render(<CharCounter text={'あ'.repeat(285)} max={280} />)
    expect(screen.getByRole('status', { name: '5 文字超えています' })).toBeInTheDocument()
    expect(screen.getByTestId('char-remaining')).toHaveTextContent('-5')
  })
})
