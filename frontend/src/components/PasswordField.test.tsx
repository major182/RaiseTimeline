import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { PasswordField } from './PasswordField'

function Form() {
  const [value, setValue] = useState('')
  return (
    <PasswordField label="パスワード" value={value} onChange={(e) => setValue(e.target.value)} />
  )
}

describe('PasswordField', () => {
  it.each([
    ['あｓｄｆ', ''],
    ['abcあ1', 'abc1'],
    ['a b~1', 'ab~1'],
    ['a!~1', 'a!~1'],
  ])('%s と入れると %s になる', (input, expected) => {
    render(<Form />)
    const field = screen.getByLabelText('パスワード')

    fireEvent.change(field, { target: { value: input } })

    expect(field).toHaveValue(expected)
  })
})
