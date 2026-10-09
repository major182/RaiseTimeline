import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { makePost } from '../test/fixtures'
import { TEST_ME, authResponse, json, renderApp, stubApi } from '../test/render'

afterEach(() => localStorage.clear())

/** ホームを開き、投稿ボタンから投稿作成モーダルを開く。 */
async function openComposer(extra: Parameters<typeof stubApi>[0] = {}) {
  const fetchMock = stubApi({ 'POST /api/auth/refresh': authResponse(TEST_ME), ...extra })
  renderApp('/')
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: '投稿する' }))
  const dialog = await screen.findByRole('dialog', { name: '投稿を作成' })
  return { user, dialog, fetchMock, input: within(dialog).getByRole('textbox', { name: '本文' }) }
}

function submitButton(dialog: HTMLElement) {
  return within(dialog).getByRole('button', { name: '投稿する' })
}

describe('投稿作成モーダル（MD-01、F-PO-01）', () => {
  it('開いたら、すぐ入力できる状態にする', async () => {
    const { input } = await openComposer()
    await waitFor(() => expect(input).toHaveFocus())
    expect(input).toHaveAttribute('placeholder', 'いまどうしてる？')
  })

  it('本文が空か空白だけなら投稿できない（BR-11）', async () => {
    const { user, dialog, input } = await openComposer()
    expect(submitButton(dialog)).toBeDisabled()
    await user.type(input, '   ')
    expect(submitButton(dialog)).toBeDisabled()
  })

  it('280 文字を超えたら投稿できず、超えた数を出す', async () => {
    const { user, dialog, input } = await openComposer()
    await user.click(input)
    await user.paste('あ'.repeat(281))
    expect(submitButton(dialog)).toBeDisabled()
    expect(within(dialog).getByRole('status', { name: '1 文字超えています' })).toBeInTheDocument()
  })

  it('ちょうど 280 文字（絵文字を含む）なら投稿できる', async () => {
    const { user, dialog, input } = await openComposer()
    await user.click(input)
    await user.paste('😀'.repeat(280))
    expect(submitButton(dialog)).toBeEnabled()
  })

  it('フォームで送り、閉じて知らせ、タイムラインの先頭に足す', async () => {
    let sent: FormData | undefined
    const { user, dialog, input } = await openComposer({
      'GET /api/timeline/following': json(200, {
        highlights: [],
        items: [makePost({ id: 1, body: '前からある投稿' })],
        nextCursor: null,
      }),
      'POST /api/posts': (body) => {
        sent = body as FormData
        return json(201, makePost({ id: 2, body: 'はじめての投稿', isMine: true }))
      },
    })
    await screen.findByText('前からある投稿')
    await user.type(input, 'はじめての投稿')
    await user.click(submitButton(dialog))

    expect(await screen.findByText('投稿しました')).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: '投稿を作成' })).not.toBeInTheDocument(),
    )
    expect(sent).toBeInstanceOf(FormData)
    expect(sent!.get('body')).toBe('はじめての投稿')
    const bodies = screen.getAllByTestId('post-body').map((el) => el.textContent)
    expect(bodies).toEqual(['はじめての投稿', '前からある投稿'])
  })

  it('次に開いたときは、入力欄が空になっている', async () => {
    const { user, dialog, input } = await openComposer({
      'POST /api/posts': json(201, makePost({ isMine: true })),
    })
    await user.type(input, '投稿')
    await user.click(submitButton(dialog))
    await screen.findByText('投稿しました')
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: '投稿を作成' })).not.toBeInTheDocument(),
    )

    await user.click(screen.getByRole('button', { name: '投稿する' }))
    const again = await screen.findByRole('dialog', { name: '投稿を作成' })
    expect(within(again).getByRole('textbox', { name: '本文' })).toHaveValue('')
  })

  it('本文の誤りがサーバーから返ったら、入力欄の下に出す', async () => {
    const { user, dialog, input } = await openComposer({
      'POST /api/posts': json(400, {
        status: 400,
        code: 'VALIDATION_FAILED',
        detail: '入力内容を確認してください',
        errors: [
          { field: 'body', code: 'BODY_TOO_LONG', message: '280 文字以内で入力してください' },
        ],
      }),
    })
    await user.type(input, '本文')
    await user.click(submitButton(dialog))
    expect(await within(dialog).findByText('280 文字以内で入力してください')).toBeInTheDocument()
    expect(input).toHaveValue('本文') // 入力は消さない
  })

  it('通信に失敗したら画面の下に知らせ、モーダルは閉じない', async () => {
    const { user, dialog, input } = await openComposer({
      'POST /api/posts': json(500, {
        status: 500,
        code: 'INTERNAL_ERROR',
        detail: 'エラーが発生しました。時間をおいてもう一度お試しください',
      }),
    })
    await user.type(input, '本文')
    await user.click(submitButton(dialog))
    expect(
      await screen.findByText('エラーが発生しました。時間をおいてもう一度お試しください'),
    ).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: '投稿を作成' })).toBeInTheDocument()
  })
})

describe('入力の途中で閉じる（C-03）', () => {
  it('入力がなければ、確認せずに閉じる', async () => {
    const { user, dialog } = await openComposer()
    await user.click(within(dialog).getByRole('button', { name: '閉じる' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('入力があれば確認し、キャンセルなら入力を残す', async () => {
    const { user, dialog, input } = await openComposer()
    await user.type(input, '書きかけ')
    await user.click(within(dialog).getByRole('button', { name: '閉じる' }))
    const confirm = await screen.findByRole('dialog', { name: '編集内容を破棄しますか？' })
    expect(within(confirm).getByText('入力した内容は保存されません。')).toBeInTheDocument()
    await waitFor(() =>
      expect(within(confirm).getByRole('button', { name: 'キャンセル' })).toHaveFocus(),
    )
    await user.click(within(confirm).getByRole('button', { name: 'キャンセル' }))
    await waitFor(() =>
      expect(
        screen.queryByRole('dialog', { name: '編集内容を破棄しますか？' }),
      ).not.toBeInTheDocument(),
    )
    expect(
      within(screen.getByRole('dialog', { name: '投稿を作成' })).getByRole('textbox', {
        name: '本文',
      }),
    ).toHaveValue('書きかけ')
  })

  it('「破棄」なら閉じて、入力を消す', async () => {
    const { user, dialog, input } = await openComposer()
    await user.type(input, '書きかけ')
    await user.click(within(dialog).getByRole('button', { name: '閉じる' }))
    await user.click(await screen.findByRole('button', { name: '破棄' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: '投稿する' }))
    const again = await screen.findByRole('dialog', { name: '投稿を作成' })
    expect(within(again).getByRole('textbox', { name: '本文' })).toHaveValue('')
  })
})
