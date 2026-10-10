// 利用者登録・プロフィールの編集・パスワードの変更の入力のチェック。サーバー（SignupRequest）と同じ条件にする（BR-02・BR-04・BR-05）。
// 画面で先に確かめるのは使いやすさのため。最後に守るのはサーバー
import type { PasswordChangeInput, SignupInput } from '../api/auth'
import { MESSAGES } from '../messages'
import { countChars } from '../postLength'

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

/** 表示名・自己紹介の長さの上限（DB 設計書。文字数はコードポイントで数える）。 */
export const DISPLAY_NAME_MAX_LENGTH = 50
export const BIO_MAX_LENGTH = 160

export type ProfileInput = { displayName: string; username: string; bio: string }
export type ProfileErrors = Partial<Record<keyof ProfileInput, string>>

/** プロフィールの編集の入力のチェック（画面設計書 5.9）。サーバー（UpdateProfileRequest）と同じ条件にする。 */
export function validateProfile(input: ProfileInput): ProfileErrors {
  const errors: ProfileErrors = {}
  const nameLength = countChars(input.displayName)
  if (nameLength < 1 || nameLength > DISPLAY_NAME_MAX_LENGTH)
    errors.displayName = MESSAGES.displayNameLength
  if (!input.username) errors.username = MESSAGES.required
  else if (!USERNAME.test(input.username)) errors.username = MESSAGES.usernameInvalid
  if (countChars(input.bio) > BIO_MAX_LENGTH) errors.bio = MESSAGES.bioTooLong
  return errors
}

export type PasswordChangeErrors = Partial<Record<keyof PasswordChangeInput, string>>

/** パスワードの変更の入力のチェック（画面設計書 5.13）。新しいパスワードは登録と同じ条件（E-04・E-05）。 */
export function validatePasswordChange(input: PasswordChangeInput): PasswordChangeErrors {
  const errors: PasswordChangeErrors = {}
  if (!input.currentPassword) errors.currentPassword = MESSAGES.required
  if (!input.newPassword) errors.newPassword = MESSAGES.required
  else if (!PASSWORD.test(input.newPassword)) errors.newPassword = MESSAGES.passwordWeak
  if (!input.newPasswordConfirmation) errors.newPasswordConfirmation = MESSAGES.required
  else if (input.newPassword !== input.newPasswordConfirmation)
    errors.newPasswordConfirmation = MESSAGES.passwordMismatch
  return errors
}
