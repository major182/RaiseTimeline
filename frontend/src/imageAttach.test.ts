import { describe, expect, it } from 'vitest'
import { IMAGE_MAX_BYTES, checkImage, prepareImage } from './imageAttach'
import { MESSAGES } from './messages'

function fileOf(type: string, size = 10) {
  return new File([new Uint8Array(size)], 'a', { type })
}

describe('添付する画像の確認（E-10・E-11）', () => {
  it.each(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])('%s は添付できる', (type) => {
    expect(checkImage(fileOf(type))).toBeUndefined()
  })

  it('ほかの形式は E-10', () => {
    expect(checkImage(fileOf('image/svg+xml'))).toBe(MESSAGES.imageType)
    expect(checkImage(fileOf('application/pdf'))).toBe(MESSAGES.imageType)
  })

  it('5MB ちょうどまでは添付でき、超えたら E-11', () => {
    expect(checkImage(fileOf('image/png', IMAGE_MAX_BYTES))).toBeUndefined()
    expect(checkImage(fileOf('image/png', IMAGE_MAX_BYTES + 1))).toBe(MESSAGES.imageTooLarge)
  })

  it('GIF はアニメーションを保つため、描き直さずにそのまま送る', async () => {
    const gif = fileOf('image/gif')
    expect(await prepareImage(gif)).toBe(gif)
  })
})
