import { describe, expect, it } from 'vitest'
import { validateSignup } from './validation'

const VALID = {
  email: 'me@example.com',
  password: 'pass1234',
  passwordConfirmation: 'pass1234',
  username: 'raise_me',
}

describe('validateSignup（サーバーと同じ条件）', () => {
  it('正しい入力なら誤りはない', () => {
    expect(validateSignup(VALID)).toEqual({})
  })

  it('空の項目は「入力してください」', () => {
    expect(
      validateSignup({ email: ' ', password: '', passwordConfirmation: '', username: '' }),
    ).toEqual({
      email: '入力してください',
      password: '入力してください',
      passwordConfirmation: '入力してください',
      username: '入力してください',
    })
  })

  it.each(['password', '12345678', 'pass123', 'a1'.repeat(37)])(
    'パスワードの条件を満たさない：%s',
    (password) => {
      expect(validateSignup({ ...VALID, password, passwordConfirmation: password }).password).toBe(
        '8〜72 文字で、英字と数字をそれぞれ 1 文字以上含めてください',
      )
    },
  )

  it('パスワード（確認）が違う', () => {
    expect(validateSignup({ ...VALID, passwordConfirmation: 'pass9999' })).toEqual({
      passwordConfirmation: 'パスワードが一致しません',
    })
  })

  it.each(['abc', 'a'.repeat(16), 'raise-me', 'レイズ'])(
    'ユーザー名の形式が違う：%s',
    (username) => {
      expect(validateSignup({ ...VALID, username }).username).toBe(
        '4〜15 文字の半角英数字と「_」で入力してください',
      )
    },
  )

  it('メールアドレスの形式が違う', () => {
    expect(validateSignup({ ...VALID, email: 'not-mail' }).email).toBe(
      'メールアドレスの形式が正しくありません',
    )
  })
})
