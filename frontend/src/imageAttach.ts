// 投稿に添付する画像の確認と、送る前の縮小・再エンコード（画面設計書 5.4、要件定義書 BR-23・Q-05）
import { MESSAGES } from './messages'

/** 添付できる形式（API 設計書 4.4。サーバーと同じ）。 */
export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'] as const
/** 1 枚の大きさの上限（サーバーの max-file-size と同じ）。 */
export const IMAGE_MAX_BYTES = 5 * 1024 * 1024
/** 1 つの投稿に添付できる枚数。 */
export const IMAGE_MAX_COUNT = 4
/** 縮小するときの長い辺の上限（px）。 */
export const IMAGE_MAX_SIDE = 2048
/** アイコンの長い辺の上限（px）。表示は最大 80px なので、高解像度の画面でも足りる大きさにする。 */
export const AVATAR_MAX_SIDE = 400

/** 選んだファイルを確かめる。添付できなければ誤りの文言（E-10・E-11）を返す。 */
export function checkImage(file: File): string | undefined {
  if (!(IMAGE_TYPES as readonly string[]).includes(file.type)) return MESSAGES.imageType
  if (file.size > IMAGE_MAX_BYTES) return MESSAGES.imageTooLarge
  return undefined
}

/**
 * 送る前に、長い辺を maxSide（既定は IMAGE_MAX_SIDE）まで縮め、同じ形式で描き直す。描き直すと Exif（位置情報など）は消える。
 * GIF はアニメーションが止まってしまうので、そのまま送る（GIF には Exif がない）。
 * 描き直して 5MB を超えたとき（大きな PNG など）は、透過を保てる WebP にする。
 */
export async function prepareImage(file: File, maxSide: number = IMAGE_MAX_SIDE): Promise<File> {
  if (file.type === 'image/gif') return file
  // 向き（Exif の Orientation）は、描くときに画像へ反映する
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()

  let type = file.type
  let blob = await toBlob(canvas, type)
  if (blob.size > IMAGE_MAX_BYTES && type === 'image/png') {
    type = 'image/webp'
    blob = await toBlob(canvas, type)
  }
  if (blob.size > IMAGE_MAX_BYTES) throw new Error(MESSAGES.imageTooLarge)
  return new File([blob], file.name, { type })
}

function toBlob(canvas: HTMLCanvasElement, type: string): Promise<Blob> {
  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error(MESSAGES.imageType))),
      type,
      0.9,
    ),
  )
}
