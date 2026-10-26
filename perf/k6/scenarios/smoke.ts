// PT-01 smoke：1 人で 1 分。主な API を一通り呼び、スクリプトと環境が正しいことを確かめる（性能テスト設計書 3 章）
import { sleep } from 'k6'
import type { Options } from 'k6/options'
import { commonThresholds } from '../lib/config.ts'
import * as api from '../lib/actions.ts'

export const options: Options = {
  vus: 1,
  duration: __ENV.DURATION || '1m',
  thresholds: commonThresholds,
}

export default function (): void {
  const self = api.me()

  const all = api.timeline('all')
  if (all.nextCursor) {
    api.timeline('all', all.nextCursor)
  }
  api.timeline('following')

  const post = all.items[0]
  if (post) {
    api.userProfile(post.author.id)
    api.userPosts(post.author.id)
    api.comments(post.id)
    api.like(post.id)
    api.unlike(post.id)
  }

  const created = api.createPost(`性能テスト smoke ${Date.now()}`)
  const comment = api.createComment(created.id, 'smoke のコメント')
  api.createComment(created.id, `@${self.username} smoke の返信`, comment.id)

  api.searchUsers('perf_00')
  sleep(1)
}
