// API が返すデータの形（API 設計書 2.5・3 章）。項目名はサーバーの JSON と同じにする

/** 利用者の要約（API 設計書 3.1）。一覧の行・投稿者の表示に使う。 */
export type UserSummary = {
  id: number
  username: string
  displayName: string
  /** 期限つきの署名つき URL。未設定なら null（既定のアイコンを出す）。 */
  avatarUrl: string | null
  bio: string
  followedByMe: boolean
  /** true ならフォローボタンを出さない。 */
  isMe: boolean
}

/** 投稿の画像。 */
export type PostImage = { url: string; width: number; height: number }

/** いいね経由の表示（「山田さん、ほか 2 人がいいねしました」）。 */
export type LikedVia = { displayName: string; username: string; othersCount: number }

/** 投稿（API 設計書 3.4）。インプレッション数・リツイートの数は持たない（BR-40、BR-41）。 */
export type Post = {
  id: number
  author: UserSummary
  body: string
  images: PostImage[]
  /** ISO 8601 の UTC。日本時間への変換は画面で行う。 */
  createdAt: string
  /** 未編集なら null。 */
  editedAt: string | null
  likeCount: number
  commentCount: number
  likedByMe: boolean
  /** フォロー中タブのいいね経由の投稿だけ値がある。 */
  likedVia: LikedVia | null
  /** true のときだけ編集・削除のメニューを出す（BR-12）。 */
  isMine: boolean
}

/** 一覧（API 設計書 2.5）。nextCursor が null なら終わり。 */
export type CursorPage<T> = { items: T[]; nextCursor: string | null }

/** フォロー中タブ（API 設計書 4.3）。highlights は最初の読み込みのときだけ入る。 */
export type FollowingTimeline = CursorPage<Post> & { highlights: Post[] }

/** コメント（API 設計書 3.5）。 */
export type Comment = {
  id: number
  postId: number
  author: UserSummary
  body: string
  createdAt: string
  /** コメントした本人か投稿した本人なら true（削除のメニューを出す。BR-35）。 */
  deletable: boolean
}
