import { describe, expect, it, vi } from 'vitest'
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

/**
 * 縮小・描き直しを動かすための準備。jsdom には画像の読み込み（createImageBitmap）と canvas の描画がないので、偽物にする。
 * blobSizes には、描き直した結果の大きさを、呼ばれる順に入れる（WebP への切り替えを確かめるため）。
 */
function stubCanvas(width: number, height: number, blobSizes: number[] = [100]) {
  vi.stubGlobal(
    'createImageBitmap',
    vi.fn(async () => ({ width, height, close: vi.fn() })),
  )
  const drawImage = vi.fn()
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
    drawImage,
  } as unknown as CanvasRenderingContext2D)
  const sizes = [...blobSizes]
  const toBlob = vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function (
    this: HTMLCanvasElement,
    callback,
    type,
  ) {
    callback(new Blob([new Uint8Array(sizes.shift() ?? 100)], { type }))
  })
  return { drawImage, toBlob }
}

describe('送る前の縮小・描き直し（BR-23、画面設計書 5.4）', () => {
  it.each([
    [4096, 1024, 2048, 512],
    [1024, 4096, 512, 2048],
    [2049, 1000, 2048, 1000], // 1000 × 2048 / 2049 = 999.5 を四捨五入
  ])(
    'UT-PO-07 長い辺が 2048px を超える（%i×%i）なら、縦横の比を保って %i×%i に縮める',
    async (w, h, ew, eh) => {
      const { drawImage } = stubCanvas(w, h)

      await prepareImage(fileOf('image/jpeg'))

      expect(drawImage).toHaveBeenCalledWith(expect.anything(), 0, 0, ew, eh)
    },
  )

  it.each([
    [2048, 2048],
    [800, 600],
  ])(
    'UT-PO-07 長い辺が 2048px 以下（%i×%i）なら、大きさを変えずに描き直す（Exif を消すため）',
    async (w, h) => {
      const { drawImage } = stubCanvas(w, h)

      const result = await prepareImage(fileOf('image/jpeg'))

      expect(drawImage).toHaveBeenCalledWith(expect.anything(), 0, 0, w, h)
      expect(result.type).toBe('image/jpeg')
    },
  )

  it('UT-PO-07 アイコンは長い辺の上限を指定して縮める', async () => {
    const { drawImage } = stubCanvas(1200, 800)

    await prepareImage(fileOf('image/png'), 400)

    expect(drawImage).toHaveBeenCalledWith(expect.anything(), 0, 0, 400, 267)
  })

  it('UT-PO-07 PNG を描き直して 5MB を超えたら、WebP にして送る', async () => {
    const { toBlob } = stubCanvas(1000, 1000, [IMAGE_MAX_BYTES + 1, 100])

    const result = await prepareImage(fileOf('image/png'))

    expect(result.type).toBe('image/webp')
    expect(toBlob.mock.calls.map((c) => c[1])).toEqual(['image/png', 'image/webp'])
  })

  it('UT-PO-07 JPEG は 5MB を超えても形式を変えず、E-11 の誤りにする', async () => {
    stubCanvas(1000, 1000, [IMAGE_MAX_BYTES + 1])

    await expect(prepareImage(fileOf('image/jpeg'))).rejects.toThrow(MESSAGES.imageTooLarge)
  })

  it('UT-PO-07 WebP にしても 5MB を超えたら、E-11 の誤りにする', async () => {
    stubCanvas(1000, 1000, [IMAGE_MAX_BYTES + 1, IMAGE_MAX_BYTES + 1])

    await expect(prepareImage(fileOf('image/png'))).rejects.toThrow(MESSAGES.imageTooLarge)
  })
})
