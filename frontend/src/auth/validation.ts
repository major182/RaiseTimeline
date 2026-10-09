// 利用者登録の入力のチェック。サーバー（SignupRequest）と同じ条件にする（BR-02・BR-04・BR-05）。
// 画面で先に確かめるのは使いやすさのため。最後に守るのはサーバー
import type { SignupInput } from '../api/auth'
import { MESSAGES } from '../messages'

export type SignupErrors = Partial<Record<keyof SignupInput, string>>

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PASSWORD = /^(?=.*[A-Za-z])(?=.*[0-9]).{8,72}$/
const USERNAME = /^[A-Za-z0-9_]{4,15}$/

export function validateSignup(input: SignupInput): SignupErrors {
  const errors: SignupErrors = {}
  if (!input.email.trim()) errors.email = MESSAGES.required
  else if (!EMAIL.test(input.email.trim()) || input.email.trim().length > 254)
    errors.email = MESSAGES.emailInvalid

  if (!input.password) errors.password = MESSAGES.required
  else if (!PASSWORD.test(input.password)) errors.password = MESSAGES.passwordWeak

  if (!input.passwordConfirmation) errors.passwordConfirmation = MESSAGES.required
  else if (input.password !== input.passwordConfirmation)
    errors.passwordConfirmation = MESSAGES.passwordMismatch

  if (!input.username) errors.username = MESSAGES.required
  else if (!USERNAME.test(input.username)) errors.username = MESSAGES.usernameInvalid

  return errors
}
