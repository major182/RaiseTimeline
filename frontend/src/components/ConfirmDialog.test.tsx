import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ConfirmDialog } from './ConfirmDialog'

function renderDialog(overrides: Partial<Parameters<typeof ConfirmDialog>[0]> = {}) {
  const props = {
    open: true,
    title: '投稿を削除しますか？',
    confirmLabel: '削除',
    onConfirm: vi.fn(),
    onCancel: vi.fn(),
    ...overrides,
  }
  render(<ConfirmDialog {...props} />)
  return props
}

describe('確認のダイアログ（画面設計書 5.6・6.2）', () => {
  it('UT-PO-05 実行のボタンで onConfirm、キャンセルで onCancel を呼ぶ', async () => {
    const props = renderDialog()

    await userEvent.click(screen.getByRole('button', { name: '削除' }))
    expect(props.onConfirm).toHaveBeenCalledTimes(1)

    await userEvent.click(screen.getByRole('button', { name: 'キャンセル' }))
    expect(props.onCancel).toHaveBeenCalledTimes(1)
  })

  it('UT-PO-05 Esc キーで閉じると、実行せずにキャンセルとして扱う', async () => {
    const props = renderDialog()

    await userEvent.keyboard('{Escape}')

    expect(props.onCancel).toHaveBeenCalledTimes(1)
    expect(props.onConfirm).not.toHaveBeenCalled()
  })

  it('UT-PO-05 誤って実行しないよう、最初は「キャンセル」に焦点がある', async () => {
    renderDialog()

    // 開く動き（トランジション）が終わってから焦点を移す
    await vi.waitFor(() => expect(screen.getByRole('button', { name: 'キャンセル' })).toHaveFocus())
  })

  it('UT-PO-05 説明の文は、渡したときだけ出す', () => {
    renderDialog({ body: 'この操作は取り消せません' })
    expect(screen.getByText('この操作は取り消せません')).toBeInTheDocument()
  })

  it('UT-PO-05 閉じているときは何も出さない', () => {
    renderDialog({ open: false })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
