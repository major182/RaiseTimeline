import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { prepareImage } from '../imageAttach'
import { makePost } from '../test/fixtures'
import { TEST_ME, authResponse, json, renderApp, stubApi } from '../test/render'

// 縮小・再エンコードは canvas を使い、テストの環境（jsdom）では動かないので、そのまま返す形に置き換える
vi.mock('../imageAttach', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../imageAttach')>()),
  prepareImage: vi.fn(async (file: File) => file),
}))

beforeEach(() => {
  URL.createObjectURL = vi.fn((file: Blob) => `blob:${(file as File).name}`)
  URL.revokeObjectURL = vi.fn()
})
afterEach(() => localStorage.clear())

function image(name: string, type = 'image/png', size = 10) {
  return new File([new Uint8Array(size)], name, { type })
}

async function openComposer(extra: Parameters<typeof stubApi>[0] = {}) {
  stubApi({ 'POST /api/auth/refresh': authResponse(TEST_ME), ...extra })
  renderApp('/')
  // 形式の違うファイルも選べるように、accept で弾かない
  const user = userEvent.setup({ applyAccept: false })
  await user.click(await screen.findByRole('button', { name: '投稿する' }))
  const dialog = await screen.findByRole('dialog', { name: '投稿を作成' })
  const input = within(dialog).getByTestId('image-input') as HTMLInputElement
  return { user, dialog, input }
}

const previews = (dialog: HTMLElement) =>
  within(dialog)
    .queryAllByRole('img')
    .map((img) => img.getAttribute('src'))

describe('画像の添付（画面設計書 5.4）', () => {
  it('選んだ画像をプレビューに出し、本文がなくても投稿できる（BR-11）', async () => {
    const { user, dialog, input } = await openComposer()
    const submit = within(dialog).getByRole('button', { name: '投稿する' })
    expect(submit).toBeDisabled()
    await user.upload(input, [image('1.png'), image('2.jpg', 'image/jpeg')])
    await waitFor(() => expect(previews(dialog)).toEqual(['blob:1.png', 'blob:2.jpg']))
    expect(submit).toBeEnabled()
  })

  it('選んだ順に images として送る', async () => {
    let sent: FormData | undefined
    const { user, dialog, input } = await openComposer({
      'POST /api/posts': (body) => {
        sent = body as FormData
        return json(201, makePost({ isMine: true }))
      },
    })
    await user.upload(input, [image('1.png'), image('2.png')])
    await waitFor(() => expect(previews(dialog)).toHaveLength(2))
    await user.click(within(dialog).getByRole('button', { name: '投稿する' }))
    await screen.findByText('投稿しました')
    expect(sent!.get('body')).toBe('')
    expect((sent!.getAll('images') as File[]).map((f) => f.name)).toEqual(['1.png', '2.png'])
  })

  it('「×」で外せる', async () => {
    const { user, dialog, input } = await openComposer()
    await user.upload(input, [image('1.png'), image('2.png')])
    await user.click(await within(dialog).findByRole('button', { name: '画像 1 を外す' }))
    expect(previews(dialog)).toEqual(['blob:2.png'])
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:1.png')
  })

  it('4 枚になったらボタンを押せず、超えた分は E-12 を出して添付しない', async () => {
    const { user, dialog, input } = await openComposer()
    const button = within(dialog).getByRole('button', { name: '画像を添付' })
    await user.upload(
      input,
      ['1', '2', '3', '4', '5'].map((n) => image(`${n}.png`)),
    )
    await waitFor(() => expect(previews(dialog)).toHaveLength(4))
    expect(within(dialog).getByRole('alert')).toHaveTextContent('画像は 4 枚まで添付できます')
    expect(button).toBeDisabled()
  })

  it('形式が違えば E-10、5MB を超えれば E-11 を出して添付しない', async () => {
    const { user, dialog, input } = await openComposer()
    await user.upload(input, image('a.svg', 'image/svg+xml'))
    expect(within(dialog).getByRole('alert')).toHaveTextContent(
      'JPEG・PNG・WebP・GIF の画像を選んでください',
    )
    await user.upload(input, image('big.png', 'image/png', 5 * 1024 * 1024 + 1))
    expect(within(dialog).getByRole('alert')).toHaveTextContent('5MB 以下の画像を選んでください')
    expect(previews(dialog)).toEqual([])
  })

  it('画像だけを添付していても、閉じるときに確認する（C-03）', async () => {
    const { user, dialog, input } = await openComposer()
    await user.upload(input, image('1.png'))
    await waitFor(() => expect(previews(dialog)).toHaveLength(1))
    await user.click(within(dialog).getByRole('button', { name: '閉じる' }))
    expect(
      await screen.findByRole('dialog', { name: '編集内容を破棄しますか？' }),
    ).toBeInTheDocument()
  })
})

describe('品質チェックの修正（Issue #51）', () => {
  it('B-3 縮小には投稿の既定の大きさを使う（配列の番号を上限として渡さない）', async () => {
    const { user, dialog, input } = await openComposer()
    const files = [image('1.png'), image('2.png'), image('3.png')]
    await user.upload(input, files)
    await waitFor(() => expect(previews(dialog)).toHaveLength(3))
    for (const file of files) expect(prepareImage).toHaveBeenCalledWith(file)
  })

  it('B-1 縮小の途中で閉じたら、次に開いたモーダルに前の画像を足さない', async () => {
    let finish: (file: File) => void = () => {}
    vi.mocked(prepareImage).mockImplementationOnce(
      () => new Promise<File>((resolve) => (finish = resolve)),
    )
    const { user, dialog, input } = await openComposer()
    const file = image('1.png')
    await user.upload(input, file)
    // 縮小の途中（添付はまだ 0 枚）なので、確認せずに閉じる
    await user.click(within(dialog).getByRole('button', { name: '閉じる' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: '投稿する' }))
    const again = await screen.findByRole('dialog', { name: '投稿を作成' })
    finish(file)
    await new Promise((r) => setTimeout(r, 50))
    expect(previews(again)).toEqual([])
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })
})
