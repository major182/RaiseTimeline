// 利用者の操作の割合（性能テスト設計書 3 章）。PT-02〜05 の 1 回の繰り返しで、割合に従って操作を 1 つ選んで行う
import { sleep } from 'k6'
import * as api from './actions.ts'
import type { Post, Tab } from './actions.ts'

interface Action {
  name: string
  weight: number
  run: () => void
}

// VU ごとの状態：最後に見たタイムライン（次のページ・いいね・コメントの対象に使う）
let lastTab: Tab = 'all'
let lastPosts: Post[] = []
let nextCursor: string | null = null

function pick<T>(items: T[]): T | undefined {
  return items[Math.floor(Math.random() * items.length)]
}

function openTimeline(): void {
  lastTab = Math.random() < 0.5 ? 'all' : 'following'
  const page = api.timeline(lastTab)
  lastPosts = page.items
  nextCursor = page.nextCursor
}

// 対象の投稿がまだなければ、先にタイムラインを開く（画面でも一覧から操作するため）
function targetPost(): Post | undefined {
  if (lastPosts.length === 0) {
    openTimeline()
  }
  return pick(lastPosts)
}

export const actions: Action[] = [
  { name: 'タイムライン', weight: 50, run: openTimeline },
  {
    name: '続きの読み込み',
    weight: 15,
    run: () => {
      if (nextCursor === null) {
        openTimeline()
        return
      }
      const page = api.timeline(lastTab, nextCursor)
      lastPosts = page.items
      nextCursor = page.nextCursor
    },
  },
  {
    name: 'プロフィール',
    weight: 10,
    run: () => {
      const post = targetPost()
      if (post) {
        api.userProfile(post.author.id)
        api.userPosts(post.author.id)
      }
    },
  },
  {
    name: 'いいね',
    weight: 10,
    run: () => {
      // いいねの数が増え続けないよう、今の状態を反対にする
      const post = targetPost()
      if (post) {
        if (post.likedByMe) {
          api.unlike(post.id)
        } else {
          api.like(post.id)
        }
        post.likedByMe = !post.likedByMe
      }
    },
  },
  {
    name: '投稿',
    weight: 8,
    run: () => {
      api.createPost(`性能テストの投稿 VU ${__VU} 回 ${__ITER}`)
    },
  },
  {
    name: 'コメント',
    weight: 5,
    run: () => {
      const post = targetPost()
      if (post) {
        const list = api.comments(post.id)
        const parent = pick(list.items)
        // 半分は既存のコメントへの返信（メンションつき）にする
        if (parent && Math.random() < 0.5) {
          api.createComment(post.id, `@${post.author.username} 性能テストの返信`, parent.id)
        } else {
          api.createComment(post.id, '性能テストのコメント')
        }
      }
    },
  },
  {
    name: '検索',
    weight: 2,
    run: () => {
      api.searchUsers(`perf_${Math.floor(Math.random() * 10)}`)
    },
  },
]

const totalWeight = actions.reduce((sum, a) => sum + a.weight, 0)

function chooseAction(): Action {
  let r = Math.random() * totalWeight
  for (const action of actions) {
    r -= action.weight
    if (r < 0) return action
  }
  return actions[0]
}

// 1 回の繰り返し：操作を 1 つ行い、画面を読む時間（1〜3 秒）だけ待つ
export function userIteration(): void {
  chooseAction().run()
  sleep(1 + Math.random() * 2)
}
