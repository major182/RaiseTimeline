// 画面だけで確かめる誤りの文言（画面設計書 6 章）。
// サーバーが返す誤りは、サーバーの message をそのまま出すので、ここには置かない
export const MESSAGES = {
  required: '入力してください', // E-01
  emailInvalid: 'メールアドレスの形式が正しくありません', // E-02
  passwordWeak: '8〜72 文字で、英字と数字をそれぞれ 1 文字以上含めてください', // E-04
  passwordMismatch: 'パスワードが一致しません', // E-05
  usernameInvalid: '4〜15 文字の半角英数字と「_」で入力してください', // E-06
  displayNameLength: '1〜50 文字で入力してください', // E-08
  bioTooLong: '160 文字以内で入力してください', // E-09
  imageType: 'JPEG・PNG・WebP・GIF の画像を選んでください', // E-10
  imageTooLarge: '5MB 以下の画像を選んでください', // E-11
  imageTooMany: '画像は 4 枚まで添付できます', // E-12
} as const
