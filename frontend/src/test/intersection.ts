// IntersectionObserver（画面に見えたかを知らせるブラウザの仕組み）の代わり。
// jsdom にはないので、テストから「見えた」ことを起こせるようにする
import { act } from '@testing-library/react'
import { vi } from 'vitest'

export function installIntersectionObserver() {
  const callbacks = new Set<IntersectionObserverCallback>()
  class FakeObserver {
    private readonly callback: IntersectionObserverCallback
    constructor(callback: IntersectionObserverCallback) {
      this.callback = callback
    }
    observe() {
      callbacks.add(this.callback)
    }
    disconnect() {
      callbacks.delete(this.callback)
    }
    unobserve() {}
    takeRecords() {
      return []
    }
  }
  vi.stubGlobal('IntersectionObserver', FakeObserver)
  return {
    /** 見張っているものが、すべて画面に見えたことにする。 */
    showAll() {
      act(() => {
        for (const callback of [...callbacks]) {
          callback(
            [{ isIntersecting: true } as IntersectionObserverEntry],
            {} as IntersectionObserver,
          )
        }
      })
    },
    get watching() {
      return callbacks.size
    },
  }
}
