// RaiseTimeline のプロトタイプ。画面・文言は docs/04_screen-design.md、ルールは docs/01_requirements.md に合わせる
// データはメモリの中だけに持ち、再読み込みすると data.js の初期状態に戻る
'use strict';

// ============================================================
// 状態
// ============================================================
const db = JSON.parse(JSON.stringify(SEED));
let meId = null;            // ログインしている利用者の ID（null ならログインしていない）
let timelineTab = 'following'; // タイムラインのタブ（BR-51。プロトタイプでは保存しない）
let returnTo = null;        // ログインの後に戻る画面
let nextId = { post: 1000, comment: 1000, user: 1000 };
const PAGE = 20;            // 一覧を一度に読み込む件数（BR-53）
const HIGHLIGHT_GAP = 6 * 60 * 60 * 1000; // 留守中のハイライトを出す間隔（BR-52）
const LIMIT = { body: 280, bio: 160, displayName: 50 };

const app = document.getElementById('app');
const modalRoot = document.getElementById('modal-root');

// ============================================================
// 小さな道具
// ============================================================
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const charCount = (s) => [...s].length; // コードポイントで数える（DB 設計書 D-7）
const me = () => db.users.find((u) => u.id === meId);
const userById = (id) => db.users.find((u) => u.id === id);
const userByName = (name) => db.users.find((u) => u.username.toLowerCase() === String(name).toLowerCase());
const postById = (id) => db.posts.find((p) => p.id === id);
const isFollowing = (a, b) => db.follows.some((f) => f.followerId === a && f.followeeId === b);
const followeeIds = (id) => db.follows.filter((f) => f.followerId === id).map((f) => f.followeeId);
const followerIds = (id) => db.follows.filter((f) => f.followeeId === id).map((f) => f.followerId);
const likeCount = (postId) => db.likes.filter((l) => l.postId === postId).length;
const commentCount = (postId) => db.comments.filter((c) => c.postId === postId).length;
const likedByMe = (postId) => db.likes.some((l) => l.postId === postId && l.userId === meId);
const byNewest = (a, b) => b.createdAt - a.createdAt || b.id - a.id;

function formatRelative(t) {
  const diff = Date.now() - t;
  const d = new Date(t);
  if (diff < 60 * 1000) return 'たった今';
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)}分`;
  if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / 3600000)}時間`;
  if (d.getFullYear() === new Date().getFullYear()) return `${d.getMonth() + 1}月${d.getDate()}日`;
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`;
}
function formatFull(t) {
  const d = new Date(t);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
const formatCount = (n) => (n >= 10000 ? `${(n / 10000).toFixed(1).replace(/\.0$/, '')}万` : String(n));

// アイコン（SVG）
const ICON = {
  home: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z"/></svg>',
  search: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>',
  user: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-6 8-6s8 2 8 6"/></svg>',
  gear: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>',
  feather: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>',
  back: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5m6-7-7 7 7 7"/></svg>',
  close: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 6l12 12M18 6 6 18"/></svg>',
  more: '<svg viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="12" r="2"/></svg>',
  comment: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/></svg>',
  heart: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 21s-7.5-4.6-9.5-9.3C1.1 8.3 3.3 4.5 7 4.5c2.1 0 3.6 1.2 5 3 1.4-1.8 2.9-3 5-3 3.7 0 5.9 3.8 4.5 7.2C19.5 16.4 12 21 12 21z"/></svg>',
  heartFill: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 21s-7.5-4.6-9.5-9.3C1.1 8.3 3.3 4.5 7 4.5c2.1 0 3.6 1.2 5 3 1.4-1.8 2.9-3 5-3 3.7 0 5.9 3.8 4.5 7.2C19.5 16.4 12 21 12 21z"/></svg>',
  image: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="m21 17-5-5-9 8"/></svg>',
  calendar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/></svg>',
  left: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m15 6-6 6 6 6"/></svg>',
  right: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m9 6 6 6-6 6"/></svg>',
};

function avatar(user, size) {
  const inner = user.avatar ? `<img src="${user.avatar}" alt="">` : esc([...user.displayName][0] || '?');
  return `<span class="avatar av-${size}" style="background:${user.color}">${inner}</span>`;
}

function snackbar(message, isError = false) {
  const el = document.getElementById('snackbar');
  el.textContent = message;
  el.className = 'snackbar' + (isError ? ' error' : '');
  el.hidden = false;
  clearTimeout(snackbar.timer);
  snackbar.timer = setTimeout(() => { el.hidden = true; }, 3000);
}

// ============================================================
// 入力のチェック（要件定義書 3.1・BR-09、画面設計書 6.3）
// ============================================================
const MSG = {
  E01: '入力してください',
  E02: 'メールアドレスの形式が正しくありません',
  E03: 'このメールアドレスは既に使われています',
  E04: '8〜72 文字で、英字と数字をそれぞれ 1 文字以上含めてください',
  E05: 'パスワードが一致しません',
  E06: '4〜15 文字の半角英数字と「_」で入力してください',
  E07: 'このユーザー名は既に使われています',
  E08: '1〜50 文字で入力してください',
  E09: '160 文字以内で入力してください',
  E10: 'JPEG・PNG・WebP・GIF の画像を選んでください',
  E11: '5MB 以下の画像を選んでください',
  E12: '画像は 4 枚まで添付できます',
  E20: 'メールアドレスかパスワードが違います',
  E21: 'ログインに続けて失敗したため、しばらくログインできません。15 分ほどたってからお試しください',
  E22: '現在のパスワードが違います',
};
const checkEmail = (v) => (!v ? MSG.E01 : /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v) ? null : MSG.E02);
const checkPassword = (v) => (!v ? MSG.E01 : v.length >= 8 && v.length <= 72 && /[A-Za-z]/.test(v) && /\d/.test(v) ? null : MSG.E04);
const checkUsername = (v) => (!v ? MSG.E01 : /^[A-Za-z0-9_]{4,15}$/.test(v) ? null : MSG.E06);
const usernameTaken = (v, exceptId) => db.users.some((u) => u.id !== exceptId && u.username.toLowerCase() === v.toLowerCase());

// フォームの入力欄に誤りを表示する。errors は { 項目名: メッセージ }
function showErrors(form, errors) {
  form.querySelectorAll('.field').forEach((field) => {
    const name = field.dataset.field;
    const err = field.querySelector('.err');
    field.classList.toggle('error', Boolean(errors[name]));
    if (err) { err.textContent = errors[name] || ''; err.hidden = !errors[name]; }
  });
  const first = form.querySelector('.field.error input, .field.error textarea');
  if (first) first.focus();
  return Object.keys(errors).length === 0;
}

function field({ name, label, type = 'text', value = '', help = '', prefix = '', toggle = false, textarea = false, counter = 0 }) {
  const input = textarea
    ? `<textarea name="${name}" rows="3">${esc(value)}</textarea>`
    : `<input name="${name}" type="${type}" value="${esc(value)}" autocomplete="off">`;
  return `
    <div class="field" data-field="${name}">
      <label>${label}</label>
      <div class="input-wrap">${prefix ? `<span class="prefix">${prefix}</span>` : ''}${input}${toggle ? '<button type="button" class="toggle" data-action="toggle-password">表示</button>' : ''}</div>
      <div class="field-foot"><div><div class="err" hidden></div>${help ? `<div class="help">${help}</div>` : ''}</div>${counter ? `<div class="counter" data-counter="${counter}"></div>` : ''}</div>
    </div>`;
}

// 文字数のカウンター（画面設計書 1.5）。残り 20 で黄色、超えたら赤
function updateCounter(el, text, limit) {
  const used = charCount(text);
  const left = limit - used;
  const ratio = Math.min(used / limit, 1);
  const r = 10;
  const len = 2 * Math.PI * r;
  const color = left < 0 ? 'var(--like)' : left <= 20 ? 'var(--yellow)' : 'var(--blue)';
  el.className = 'counter' + (left < 0 ? ' over' : left <= 20 ? ' warn' : '');
  el.innerHTML = `${left <= 20 ? `<span>${left}</span>` : ''}<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="${r}" fill="none" stroke="var(--border)" stroke-width="2"/><circle cx="12" cy="12" r="${r}" fill="none" stroke="${color}" stroke-width="2" stroke-dasharray="${len}" stroke-dashoffset="${len * (1 - ratio)}"/></svg>`;
  return left >= 0;
}

// ============================================================
// タイムライン（要件定義書 3.6、DB 設計書 5 章）
// ============================================================
// 一覧の項目は { post, likedVia }。likedVia はいいね経由の投稿だけ
function allTimeline() {
  return [...db.posts].sort(byNewest).map((post) => ({ post }));
}

function followingTimeline(excludeIds) {
  const mine = new Set([meId, ...followeeIds(meId)]);
  const followees = new Set(followeeIds(meId));
  const items = db.posts.filter((p) => mine.has(p.userId)).map((post) => ({ post, sortAt: post.createdAt }));
  // いいね経由の投稿：フォローしていない人の投稿のうち、フォロー中の人がいいねしたもの（BR-50-3）
  const via = new Map();
  for (const like of db.likes) {
    const post = postById(like.postId);
    if (!post || mine.has(post.userId) || !followees.has(like.userId)) continue;
    const entry = via.get(post.id) || { post, likers: [] };
    entry.likers.push(like);
    via.set(post.id, entry);
  }
  for (const { post, likers } of via.values()) {
    likers.sort((a, b) => b.createdAt - a.createdAt);
    const latest = userById(likers[0].userId);
    items.push({ post, sortAt: likers[0].createdAt, likedVia: { user: latest, others: likers.length - 1 } });
  }
  return items
    .filter((i) => !excludeIds.has(i.post.id))
    .sort((a, b) => b.sortAt - a.sortAt || b.post.id - a.post.id);
}

// 留守中のハイライト（BR-52）。最後に開いてから 6 時間以上なら、その間の反応の多い投稿を最大 3 件
function takeHighlights() {
  const last = db.lastTimelineViewedAt;
  db.lastTimelineViewedAt = Date.now();
  if (!last || Date.now() - last < HIGHLIGHT_GAP) return [];
  const followees = new Set(followeeIds(meId));
  return db.posts
    .filter((p) => followees.has(p.userId) && p.createdAt >= last)
    .map((post) => ({ post, score: likeCount(post.id) + commentCount(post.id) * 2 }))
    .filter((x) => x.score > 0)
    .sort((a, b) => b.score - a.score || b.post.createdAt - a.post.createdAt)
    .slice(0, 3)
    .map(({ post }) => ({ post }));
}

// おすすめの利用者（DB 設計書 5.7）
function recommendations() {
  const mine = new Set([meId, ...followeeIds(meId)]);
  const score = new Map();
  for (const f of followeeIds(meId)) {
    for (const g of followeeIds(f)) if (!mine.has(g)) score.set(g, (score.get(g) || 0) + 1);
  }
  const picked = [...score.entries()].sort((a, b) => b[1] - a[1]).map(([id]) => userById(id));
  const recent = db.users.filter((u) => !mine.has(u.id) && !score.has(u.id)).sort((a, b) => b.createdAt - a.createdAt);
  return [...picked, ...recent].slice(0, 5);
}

// ============================================================
// 部品：投稿カード（画面設計書 4.1）・利用者の行（4.2）
// ============================================================
function postCard(item, { detail = false } = {}) {
  const { post, likedVia } = item;
  const author = userById(post.userId);
  const liked = likedByMe(post.id);
  const mine = post.userId === meId;
  const n = post.images.length;
  const context = likedVia
    ? `<p class="post-context">${ICON.heartFill}${esc(likedVia.user.displayName)}さん${likedVia.others ? `、ほか ${likedVia.others} 人` : ''}がいいねしました</p>`
    : '';
  const images = n
    ? `<div class="images n${n}">${post.images.map((img, i) => `<img src="${img.url}" alt="添付画像 ${i + 1}" data-action="view-image" data-post="${post.id}" data-index="${i}">`).join('')}</div>`
    : '';
  const menu = mine
    ? `<div class="menu"><button class="icon-btn" data-action="post-menu" data-post="${post.id}" aria-label="メニュー">${ICON.more}</button></div>`
    : '';
  const actions = `
    <div class="post-actions">
      <button class="action comment" data-action="open-post" data-post="${post.id}" aria-label="コメント"><span class="ic">${ICON.comment}</span>${formatCount(commentCount(post.id))}</button>
      <button class="action like${liked ? ' on' : ''}" data-action="like" data-post="${post.id}" aria-label="いいね"><span class="ic">${liked ? ICON.heartFill : ICON.heart}</span>${formatCount(likeCount(post.id))}</button>
    </div>`;
  const head = `
    <div class="post-head">
      <a class="name" href="#/users/${author.username}">${esc(author.displayName)}</a>
      <a class="handle" href="#/users/${author.username}">@${esc(author.username)}</a>
      ${detail ? '' : `<span class="time">・${formatRelative(post.createdAt)}${post.editedAt ? '・編集済み' : ''}</span>`}
      ${menu}
    </div>`;

  if (detail) {
    return `
      <article class="post detail" data-post-id="${post.id}">
        <div style="display:flex;gap:10px;align-items:center">
          <a href="#/users/${author.username}">${avatar(author, 48)}</a>
          <div style="flex:1;min-width:0">${head}</div>
        </div>
        ${post.body ? `<p class="post-text">${esc(post.body)}</p>` : ''}
        ${images}
        <div class="detail-time">${formatFull(post.createdAt)}${post.editedAt ? '・編集済み' : ''}</div>
        <div class="detail-stats"><a href="#/posts/${post.id}/likes"><b>${formatCount(likeCount(post.id))}</b> 件のいいね</a><span><b>${formatCount(commentCount(post.id))}</b> 件のコメント</span></div>
        ${actions}
      </article>`;
  }
  return `
    <article class="post" data-action="open-post" data-post="${post.id}" data-post-id="${post.id}">
      <a href="#/users/${author.username}">${avatar(author, 48)}</a>
      <div class="post-body">
        ${context}${head}
        ${post.body ? `<p class="post-text clamp">${esc(post.body)}</p>` : ''}
        ${images}${actions}
      </div>
    </article>`;
}

function followButton(user) {
  if (user.id === meId) return '';
  const on = isFollowing(meId, user.id);
  return `<button class="btn btn-follow ${on ? 'btn-outline following' : ''}" data-action="follow" data-user="${user.id}">${on ? 'フォロー中' : 'フォロー'}</button>`;
}

function userRow(user) {
  return `
    <div class="user-row" data-action="open-user" data-username="${user.username}">
      ${avatar(user, 48)}
      <div class="info">
        <span class="name">${esc(user.displayName)}</span>
        <span class="handle">@${esc(user.username)}</span>
        ${user.bio ? `<div class="bio">${esc(user.bio)}</div>` : ''}
      </div>
      ${followButton(user)}
    </div>`;
}

// 一覧を 20 件ずつ表示し、下までスクロールしたら続きを読み込む（画面設計書 1.6）
function mountList(container, items, render, emptyHtml, endText = 'これ以上の投稿はありません') {
  if (!items.length) { container.innerHTML = `<div class="empty">${emptyHtml}</div>`; return; }
  let shown = 0;
  const sentinel = document.createElement('div');
  sentinel.style.minHeight = '1px'; // 高さ 0 だと、画面の下端に重なったときに見えたと判定されない
  const more = () => {
    const chunk = items.slice(shown, shown + PAGE);
    shown += chunk.length;
    sentinel.insertAdjacentHTML('beforebegin', chunk.map(render).join(''));
    if (shown >= items.length) {
      observer.disconnect();
      sentinel.className = 'end';
      sentinel.textContent = endText;
    }
  };
  const observer = new IntersectionObserver((entries) => {
    if (!entries[0].isIntersecting || shown >= items.length) return;
    sentinel.innerHTML = '<div class="spinner"></div>';
    sentinel.className = 'loading';
    setTimeout(() => { sentinel.innerHTML = ''; more(); }, 400); // 読み込み中の表示を見せるため少し待つ
  }, { rootMargin: '0px 0px 300px 0px' }); // 下端の 300px 手前で読み込み始める
  container.innerHTML = '';
  container.appendChild(sentinel);
  more();
  if (shown < items.length) observer.observe(sentinel);
}

// ============================================================
// 骨組み（画面設計書 1.1）
// ============================================================
function layout(active, mainHtml, { side = false } = {}) {
  const u = me();
  const navItem = (key, href, icon, label) => `<a class="nav-item${active === key ? ' active' : ''}" href="${href}">${icon}<span>${label}</span></a>`;
  const bottomItem = (key, href, icon, label) => `<a class="${active === key ? 'active' : ''}" href="${href}">${icon}${label}</a>`;
  const recs = side ? recommendations() : [];
  return `
    <div class="layout">
      <nav class="nav">
        <div class="nav-brand">RaiseTimeline</div>
        ${navItem('home', '#/', ICON.home, 'ホーム')}
        ${navItem('search', '#/search', ICON.search, '検索')}
        ${navItem('profile', `#/users/${u.username}`, ICON.user, 'プロフィール')}
        ${navItem('settings', '#/settings', ICON.gear, '設定')}
        <button class="btn btn-lg nav-post" data-action="compose">投稿する</button>
        <a class="nav-me" href="#/users/${u.username}">${avatar(u, 40)}<span><b>${esc(u.displayName)}</b><br><span style="color:var(--sub)">@${esc(u.username)}</span></span></a>
      </nav>
      <main class="main">${mainHtml}</main>
      ${side ? `<aside class="side"><div class="side-box"><h2>おすすめユーザー</h2>${recs.map(userRow).join('') || '<div class="empty">おすすめはありません</div>'}</div></aside>` : ''}
      <button class="fab" data-action="compose" aria-label="投稿する">${ICON.feather}</button>
      <nav class="bottom-nav">
        ${bottomItem('home', '#/', ICON.home, 'ホーム')}
        ${bottomItem('search', '#/search', ICON.search, '検索')}
        ${bottomItem('profile', `#/users/${u.username}`, ICON.user, 'プロフィール')}
        ${bottomItem('settings', '#/settings', ICON.gear, '設定')}
      </nav>
    </div>`;
}

const header = (title, { back = false, sub = '' } = {}) => `
  <div class="header"><div class="header-row">
    ${back ? `<button class="icon-btn" data-action="back" aria-label="戻る">${ICON.back}</button>` : ''}
    <div><h1>${title}</h1>${sub ? `<div class="sub">${sub}</div>` : ''}</div>
  </div></div>`;

// ============================================================
// 画面
// ============================================================
const pages = {
  // SC-01 ログイン
  login() {
    app.innerHTML = `
      <div class="auth">
        <div class="brand">RaiseTimeline</div>
        <h1>ログイン</h1>
        <form id="login-form" novalidate>
          <div class="alert" id="login-alert" hidden></div>
          ${field({ name: 'email', label: 'メールアドレス', type: 'email' })}
          ${field({ name: 'password', label: 'パスワード', type: 'password', toggle: true })}
          <button class="btn btn-lg" type="submit">ログイン</button>
        </form>
        <div class="auth-foot"><a href="#/signup">アカウントを作成</a></div>
        <div class="hint">プロトタイプ用のアカウント：<br>me@example.com ／ pass1234<br>（5 回続けて間違えると、ログインの一時停止を試せます）</div>
      </div>`;
    const form = document.getElementById('login-form');
    const alertEl = document.getElementById('login-alert');
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const email = form.email.value.trim();
      const password = form.password.value;
      alertEl.hidden = true;
      if (!showErrors(form, { ...(email ? {} : { email: MSG.E01 }), ...(password ? {} : { password: MSG.E01 }) })) return;
      const user = db.users.find((u) => u.email.toLowerCase() === email.toLowerCase());
      // ログインの一時停止（BR-08）。登録されていないメールアドレスは何も記録しない
      if (user && user.lockedUntil > Date.now()) { alertEl.textContent = MSG.E21; alertEl.hidden = false; return; }
      if (!user || user.password !== password) {
        if (user) {
          user.failed = (user.failed || 0) + 1;
          if (user.failed >= 5) { user.failed = 0; user.lockedUntil = Date.now() + 15 * 60 * 1000; alertEl.textContent = MSG.E21; alertEl.hidden = false; return; }
        }
        alertEl.textContent = MSG.E20; // どちらが違うかは教えない（BR-09）
        alertEl.hidden = false;
        return;
      }
      user.failed = 0;
      meId = user.id;
      location.hash = returnTo || '#/';
      returnTo = null;
    });
  },

  // SC-02 利用者登録
  signup() {
    app.innerHTML = `
      <div class="auth">
        <div class="brand">RaiseTimeline</div>
        <h1>アカウントを作成</h1>
        <form id="signup-form" novalidate>
          ${field({ name: 'email', label: 'メールアドレス', type: 'email' })}
          ${field({ name: 'password', label: 'パスワード', type: 'password', toggle: true, help: '8〜72 文字。英字と数字をそれぞれ 1 文字以上' })}
          ${field({ name: 'passwordConfirmation', label: 'パスワード（確認）', type: 'password' })}
          ${field({ name: 'username', label: 'ユーザー名', prefix: '@', help: '4〜15 文字の半角英数字と「_」。後から変更できます' })}
          <button class="btn btn-lg" type="submit">登録</button>
        </form>
        <div class="auth-foot"><a href="#/login">ログインはこちら</a></div>
      </div>`;
    const form = document.getElementById('signup-form');
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const v = Object.fromEntries(new FormData(form));
      v.email = v.email.trim();
      const errors = {};
      const add = (k, m) => { if (m) errors[k] = m; };
      add('email', checkEmail(v.email));
      add('password', checkPassword(v.password));
      add('passwordConfirmation', !v.passwordConfirmation ? MSG.E01 : v.password !== v.passwordConfirmation ? MSG.E05 : null);
      add('username', checkUsername(v.username));
      // 形式が正しいときだけ重複を確かめる（API 設計書 2.4）
      if (!Object.keys(errors).length) {
        if (db.users.some((u) => u.email.toLowerCase() === v.email.toLowerCase())) errors.email = MSG.E03;
        if (usernameTaken(v.username)) errors.username = MSG.E07;
      }
      if (!showErrors(form, errors)) return;
      const colors = ['#1DA1F2', '#17BF63', '#F45D22', '#794BC4', '#E0245E'];
      const user = { id: nextId.user++, username: v.username, displayName: v.username, email: v.email, password: v.password, bio: '', avatar: null, color: colors[Math.floor(Math.random() * colors.length)], createdAt: Date.now() };
      db.users.push(user);
      meId = user.id;
      db.lastTimelineViewedAt = null;
      location.hash = '#/';
      snackbar('ようこそ、RaiseTimeline へ');
    });
  },

  // SC-03 タイムライン
  home() {
    app.innerHTML = layout('home', `
      <div class="header">
        <div class="header-row"><h1>ホーム</h1></div>
        <div class="tabs">
          <button class="tab${timelineTab === 'following' ? ' active' : ''}" data-action="tab" data-tab="following">フォロー中</button>
          <button class="tab${timelineTab === 'all' ? ' active' : ''}" data-action="tab" data-tab="all">全体</button>
        </div>
      </div>
      <div id="highlights"></div>
      <div id="timeline"></div>`, { side: true });

    const list = document.getElementById('timeline');
    if (timelineTab === 'following') {
      const highlights = takeHighlights();
      if (highlights.length) {
        document.getElementById('highlights').innerHTML = `
          <div class="section-title">留守中のハイライト<button class="icon-btn" data-action="close-highlights" aria-label="閉じる">${ICON.close}</button></div>
          ${highlights.map((i) => postCard(i)).join('')}
          <div class="section-title" style="background:none">最新の投稿</div>`;
      }
      const items = followingTimeline(new Set(highlights.map((i) => i.post.id)));
      mountList(list, items, (i) => postCard(i),
        'まだ投稿がありません。ほかのユーザーをフォローしてみましょう<br><a class="btn" href="#/search">ユーザーを探す</a>');
    } else {
      mountList(list, allTimeline(), (i) => postCard(i), 'まだ投稿がありません。最初の投稿をしてみましょう');
    }
  },

  // SC-04 投稿の詳細
  post({ id }) {
    const post = postById(Number(id));
    if (!post) {
      app.innerHTML = layout('', `${header('投稿', { back: true })}<div class="empty">この投稿は削除されたか、見つかりません</div>`);
      return;
    }
    const comments = db.comments.filter((c) => c.postId === post.id).sort((a, b) => a.createdAt - b.createdAt || a.id - b.id);
    app.innerHTML = layout('', `
      ${header('投稿', { back: true })}
      ${postCard({ post }, { detail: true })}
      <form class="comment-form" id="comment-form">
        ${avatar(me(), 48)}
        <div style="flex:1">
          <textarea name="body" placeholder="コメントを書く" aria-label="コメント"></textarea>
          <div class="row"><div class="counter" id="comment-counter"></div><button class="btn" type="submit" disabled>コメントする</button></div>
        </div>
      </form>
      <div id="comments"></div>`);

    mountList(document.getElementById('comments'), comments, commentRow, 'まだコメントはありません', 'これ以上のコメントはありません');
    const form = document.getElementById('comment-form');
    const counter = document.getElementById('comment-counter');
    const submit = form.querySelector('button');
    const sync = () => {
      const ok = updateCounter(counter, form.body.value, LIMIT.body);
      submit.disabled = !ok || !form.body.value.trim();
    };
    form.body.addEventListener('input', sync);
    sync();
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const body = form.body.value.trim();
      if (!body || charCount(body) > LIMIT.body) return;
      db.comments.push({ id: nextId.comment++, postId: post.id, userId: meId, body, createdAt: Date.now() });
      pages.post({ id: post.id });
    });
  },

  // SC-05 プロフィール
  profile({ username }) {
    const user = userByName(username);
    if (!user) {
      app.innerHTML = layout('', `${header('プロフィール', { back: true })}<div class="empty">このユーザーは見つかりません</div>`);
      return;
    }
    const isMe = user.id === meId;
    const posts = db.posts.filter((p) => p.userId === user.id).sort(byNewest).map((post) => ({ post }));
    const since = new Date(user.createdAt);
    app.innerHTML = layout(isMe ? 'profile' : '', `
      ${header(esc(user.displayName), { back: !isMe, sub: `${posts.length} 件の投稿` })}
      <div class="profile-cover"></div>
      <div class="profile-info">
        <div class="profile-top">
          ${avatar(user, 134)}
          ${isMe ? '<a class="btn btn-outline" href="#/settings/profile">プロフィールを編集</a>' : followButton(user)}
        </div>
        <div class="profile-name">${esc(user.displayName)}</div>
        <div class="profile-handle">@${esc(user.username)}</div>
        ${user.bio ? `<p class="profile-bio">${esc(user.bio)}</p>` : '<div style="height:10px"></div>'}
        <div class="profile-meta">${ICON.calendar}${since.getFullYear()}年${since.getMonth() + 1}月から利用</div>
        <div class="profile-counts">
          <a href="#/users/${user.username}/following"><b>${formatCount(followeeIds(user.id).length)}</b> フォロー</a>
          <a href="#/users/${user.username}/followers"><b>${formatCount(followerIds(user.id).length)}</b> フォロワー</a>
        </div>
      </div>
      <div id="user-posts"></div>`);
    mountList(document.getElementById('user-posts'), posts, (i) => postCard(i), 'まだ投稿がありません');
  },

  // SC-06 プロフィールの編集
  editProfile() {
    const u = me();
    let newAvatar = u.avatar;
    app.innerHTML = layout('profile', `
      ${header('プロフィールを編集', { back: true })}
      <form class="form" id="profile-form" novalidate>
        <div class="field" data-field="avatar">
          <label>アイコン</label>
          <div class="avatar-edit"><span id="avatar-preview">${avatar(u, 134)}</span>
            <div><button type="button" class="btn btn-outline" data-action="pick-avatar">変更</button><div class="err" hidden></div></div>
          </div>
          <input type="file" id="avatar-file" accept="image/jpeg,image/png,image/webp,image/gif" hidden>
        </div>
        ${field({ name: 'displayName', label: '表示名', value: u.displayName })}
        ${field({ name: 'username', label: 'ユーザー名', prefix: '@', value: u.username, help: '変更すると、プロフィールの URL も変わります' })}
        ${field({ name: 'bio', label: '自己紹介', value: u.bio, textarea: true, counter: LIMIT.bio })}
        <div class="form-actions">
          <button type="button" class="btn btn-outline" data-action="cancel-profile">キャンセル</button>
          <button type="submit" class="btn">保存</button>
        </div>
      </form>`);
    const form = document.getElementById('profile-form');
    const counter = form.querySelector('[data-counter]');
    form.bio.addEventListener('input', () => updateCounter(counter, form.bio.value, LIMIT.bio));
    updateCounter(counter, form.bio.value, LIMIT.bio);

    document.getElementById('avatar-file').addEventListener('change', async (e) => {
      const file = e.target.files[0];
      e.target.value = '';
      const errEl = form.querySelector('[data-field="avatar"] .err');
      const err = checkImageFile(file);
      errEl.textContent = err || '';
      errEl.hidden = !err;
      if (err) return;
      newAvatar = (await readImage(file)).url;
      document.getElementById('avatar-preview').innerHTML = avatar({ ...u, avatar: newAvatar }, 134);
    });

    const dirty = () => form.displayName.value !== u.displayName || form.username.value !== u.username || form.bio.value !== u.bio || newAvatar !== u.avatar;
    pages.editProfile.dirty = dirty;
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const v = Object.fromEntries(new FormData(form));
      const errors = {};
      const nameLen = charCount(v.displayName.trim());
      if (nameLen < 1 || nameLen > LIMIT.displayName) errors.displayName = nameLen ? MSG.E08 : MSG.E01;
      const userErr = checkUsername(v.username) || (usernameTaken(v.username, u.id) ? MSG.E07 : null);
      if (userErr) errors.username = userErr;
      if (charCount(v.bio) > LIMIT.bio) errors.bio = MSG.E09;
      if (!showErrors(form, errors)) return;
      Object.assign(u, { displayName: v.displayName.trim(), username: v.username, bio: v.bio, avatar: newAvatar });
      location.hash = `#/users/${u.username}`;
      snackbar('プロフィールを保存しました');
    });
  },

  // SC-07 利用者の検索
  search({ query }) {
    const q = (query.get('q') || '').trim();
    app.innerHTML = layout('search', `
      <div class="header"><div class="search-box"><div class="input-wrap">${ICON.search}<input id="search-input" placeholder="ユーザー名・表示名で検索" value="${esc(q)}" maxlength="50" aria-label="検索"></div></div></div>
      <div id="search-results"></div>`);
    const input = document.getElementById('search-input');
    const results = document.getElementById('search-results');
    const run = (term) => {
      if (!term) {
        const recs = recommendations();
        results.innerHTML = `<div class="section-title" style="background:none">おすすめユーザー</div>${recs.map(userRow).join('') || '<div class="empty">おすすめはありません</div>'}`;
        return;
      }
      const t = term.toLowerCase();
      const found = db.users
        .filter((u) => u.username.toLowerCase().includes(t) || u.displayName.toLowerCase().includes(t))
        .sort((a, b) => (b.username.toLowerCase() === t) - (a.username.toLowerCase() === t) || b.id - a.id);
      mountList(results, found, userRow, `「${esc(term)}」に一致するユーザーは見つかりませんでした`, 'これ以上のユーザーはいません');
    };
    run(q);
    input.focus();
    input.addEventListener('input', () => {
      clearTimeout(run.timer);
      // 入力をやめて 0.3 秒たったら検索する（画面設計書 5.10）
      run.timer = setTimeout(() => {
        const term = input.value.trim();
        history.replaceState(null, '', term ? `#/search?q=${encodeURIComponent(term)}` : '#/search');
        run(term);
      }, 300);
    });
  },

  // SC-08 フォロー／フォロワー一覧
  follows({ username, kind }) {
    const user = userByName(username);
    if (!user) { pages.profile({ username }); return; }
    const relations = kind === 'following'
      ? db.follows.filter((f) => f.followerId === user.id).map((f) => ({ id: f.followeeId, at: f.createdAt }))
      : db.follows.filter((f) => f.followeeId === user.id).map((f) => ({ id: f.followerId, at: f.createdAt }));
    const users = relations.sort((a, b) => b.at - a.at).map((r) => userById(r.id));
    app.innerHTML = layout('', `
      <div class="header">
        <div class="header-row"><button class="icon-btn" data-action="back" aria-label="戻る">${ICON.back}</button><div><h1>${esc(user.displayName)}</h1><div class="sub">@${esc(user.username)}</div></div></div>
        <div class="tabs">
          <a class="tab${kind === 'following' ? ' active' : ''}" href="#/users/${user.username}/following">フォロー</a>
          <a class="tab${kind === 'followers' ? ' active' : ''}" href="#/users/${user.username}/followers">フォロワー</a>
        </div>
      </div>
      <div id="follow-list"></div>`);
    mountList(document.getElementById('follow-list'), users, userRow,
      kind === 'following' ? 'まだ誰もフォローしていません' : 'まだフォロワーはいません', 'これ以上のユーザーはいません');
  },

  // SC-09 いいねした人の一覧
  likes({ id }) {
    const post = postById(Number(id));
    if (!post) { pages.post({ id }); return; }
    const users = db.likes.filter((l) => l.postId === post.id).sort((a, b) => b.createdAt - a.createdAt).map((l) => userById(l.userId));
    app.innerHTML = layout('', `${header('いいねしたユーザー', { back: true })}<div id="like-list"></div>`);
    mountList(document.getElementById('like-list'), users, userRow, 'まだいいねはありません', 'これ以上のユーザーはいません');
  },

  // SC-10 設定
  settings() {
    app.innerHTML = layout('settings', `
      ${header('設定')}
      <a class="settings-link" href="#/settings/profile">プロフィールの編集</a>
      <div class="settings-section">
        <h2>パスワードの変更</h2>
        <form class="form" id="password-form" novalidate>
          ${field({ name: 'currentPassword', label: '現在のパスワード', type: 'password' })}
          ${field({ name: 'newPassword', label: '新しいパスワード', type: 'password', help: '8〜72 文字。英字と数字をそれぞれ 1 文字以上' })}
          ${field({ name: 'newPasswordConfirmation', label: '新しいパスワード（確認）', type: 'password' })}
          <div class="form-actions"><button class="btn" type="submit">変更</button></div>
        </form>
      </div>
      <div class="form"><button class="btn btn-outline btn-lg" data-action="logout">ログアウト</button></div>`);
    const form = document.getElementById('password-form');
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const v = Object.fromEntries(new FormData(form));
      const errors = {};
      if (!v.currentPassword) errors.currentPassword = MSG.E01;
      else if (v.currentPassword !== me().password) errors.currentPassword = MSG.E22;
      const pw = checkPassword(v.newPassword);
      if (pw) errors.newPassword = pw;
      if (!v.newPasswordConfirmation) errors.newPasswordConfirmation = MSG.E01;
      else if (v.newPassword !== v.newPasswordConfirmation) errors.newPasswordConfirmation = MSG.E05;
      if (!showErrors(form, errors)) return;
      me().password = v.newPassword;
      form.reset();
      snackbar('パスワードを変更しました');
    });
  },

  // SC-11 見つからない
  notFound() {
    app.innerHTML = layout('', `${header('ページが見つかりません')}<div class="empty">ページが見つかりません<br><a class="btn" href="#/">ホームへ</a></div>`);
  },
};

function commentRow(c) {
  const u = userById(c.userId);
  const post = postById(c.postId);
  const deletable = c.userId === meId || post.userId === meId; // BR-35
  return `
    <div class="comment-item" data-comment-id="${c.id}">
      <a href="#/users/${u.username}">${avatar(u, 40)}</a>
      <div class="post-body">
        <div class="post-head">
          <a class="name" href="#/users/${u.username}">${esc(u.displayName)}</a>
          <a class="handle" href="#/users/${u.username}">@${esc(u.username)}</a>
          <span class="time">・${formatRelative(c.createdAt)}</span>
          ${deletable ? `<div class="menu"><button class="icon-btn" data-action="comment-menu" data-comment="${c.id}" aria-label="メニュー">${ICON.more}</button></div>` : ''}
        </div>
        <p class="post-text">${esc(c.body)}</p>
      </div>
    </div>`;
}

// ============================================================
// 画像（BR-20〜22）
// ============================================================
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
// 本番では中身で形式を判定する（BR-21）。プロトタイプではブラウザが判定した形式で代用する
const checkImageFile = (file) => (!IMAGE_TYPES.includes(file.type) ? MSG.E10 : file.size > 5 * 1024 * 1024 ? MSG.E11 : null);
function readImage(file) {
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => resolve({ url: reader.result, width: img.naturalWidth, height: img.naturalHeight });
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
}

// ============================================================
// モーダル・ダイアログ（MD-01・MD-02・DL-01、画像の拡大）
// ============================================================
function openModal(html, { onClose } = {}) {
  modalRoot.innerHTML = `<div class="overlay" data-overlay>${html}</div>`;
  const overlay = modalRoot.firstElementChild;
  const close = () => { modalRoot.innerHTML = ''; document.removeEventListener('keydown', onKey); };
  const tryClose = async () => { if (!onClose || (await onClose())) close(); };
  const onKey = (e) => { if (e.key === 'Escape') tryClose(); };
  overlay.addEventListener('click', (e) => { if (e.target === overlay) tryClose(); });
  overlay.querySelectorAll('[data-close]').forEach((b) => b.addEventListener('click', tryClose));
  document.addEventListener('keydown', onKey);
  return { overlay, close };
}

// DL-01 削除確認など。選んだら true を返す
function confirmDialog({ title, body = '', ok, danger = true }) {
  return new Promise((resolve) => {
    const layer = document.createElement('div');
    layer.className = 'overlay';
    layer.innerHTML = `
      <div class="dialog" role="alertdialog" aria-modal="true">
        <h2>${title}</h2>${body ? `<p>${body}</p>` : '<p></p>'}
        <button class="btn ${danger ? 'btn-danger' : ''}" data-ok>${ok}</button>
        <button class="btn btn-outline" data-cancel>キャンセル</button>
      </div>`;
    document.body.appendChild(layer);
    const done = (v) => { layer.remove(); document.removeEventListener('keydown', onKey); resolve(v); };
    const onKey = (e) => { if (e.key === 'Escape') { e.stopImmediatePropagation(); done(false); } };
    layer.querySelector('[data-ok]').onclick = () => done(true);
    layer.querySelector('[data-cancel]').onclick = () => done(false);
    layer.addEventListener('click', (e) => { if (e.target === layer) done(false); });
    document.addEventListener('keydown', onKey, true);
    layer.querySelector('[data-cancel]').focus(); // 誤って削除しないよう、キャンセルを選んだ状態にする
  });
}
const confirmDiscard = () => confirmDialog({ title: '編集内容を破棄しますか？', body: '入力した内容は保存されません。', ok: '破棄' });

// MD-01 投稿作成
function openCompose() {
  const images = [];
  const { overlay, close } = openModal(`
    <div class="modal" role="dialog" aria-modal="true" aria-label="投稿作成">
      <div class="modal-head"><button class="icon-btn" data-close aria-label="閉じる">${ICON.close}</button><button class="btn" id="compose-submit" disabled>投稿する</button></div>
      <div class="modal-body">${avatar(me(), 48)}<textarea id="compose-text" placeholder="いまどうしてる？" aria-label="本文"></textarea></div>
      <div class="previews" id="compose-previews"></div>
      <div class="attach-err" id="compose-err" hidden></div>
      <div class="modal-foot">
        <button class="icon-btn" id="compose-attach" aria-label="画像を添付" style="color:var(--blue)">${ICON.image}</button>
        <div class="counter" id="compose-counter"></div>
      </div>
      <input type="file" id="compose-file" accept="image/jpeg,image/png,image/webp,image/gif" multiple hidden>
    </div>`, { onClose: async () => (!text.value.trim() && !images.length) || confirmDiscard() });

  const text = overlay.querySelector('#compose-text');
  const submit = overlay.querySelector('#compose-submit');
  const counter = overlay.querySelector('#compose-counter');
  const previews = overlay.querySelector('#compose-previews');
  const err = overlay.querySelector('#compose-err');
  const attach = overlay.querySelector('#compose-attach');
  const fileInput = overlay.querySelector('#compose-file');

  const sync = () => {
    const ok = updateCounter(counter, text.value, LIMIT.body);
    submit.disabled = !ok || (!text.value.trim() && !images.length); // 本文も画像もない投稿はできない（BR-11）
    attach.disabled = images.length >= 4;
    previews.innerHTML = images.map((img, i) => `<div class="preview"><img src="${img.url}" alt=""><button class="remove" data-remove="${i}" aria-label="画像を外す">×</button></div>`).join('');
  };
  text.addEventListener('input', () => { text.style.height = 'auto'; text.style.height = `${text.scrollHeight}px`; sync(); });
  previews.addEventListener('click', (e) => {
    const i = e.target.dataset.remove;
    if (i !== undefined) { images.splice(Number(i), 1); err.hidden = true; sync(); }
  });
  attach.addEventListener('click', () => fileInput.click());
  fileInput.addEventListener('change', async () => {
    const files = [...fileInput.files];
    fileInput.value = '';
    err.hidden = true;
    for (const file of files) {
      if (images.length >= 4) { err.textContent = MSG.E12; err.hidden = false; break; }
      const e = checkImageFile(file);
      if (e) { err.textContent = e; err.hidden = false; continue; }
      images.push(await readImage(file));
    }
    sync();
  });
  submit.addEventListener('click', () => {
    const body = text.value.trim();
    db.posts.push({ id: nextId.post++, userId: meId, body, images: [...images], createdAt: Date.now(), editedAt: null });
    close();
    snackbar('投稿しました');
    render();
  });
  sync();
  text.focus();
}

// MD-02 投稿編集（本文だけ。BR-15）
function openEdit(postId) {
  const post = postById(postId);
  if (!post || post.userId !== meId) { snackbar('この操作はできません', true); return; }
  const { overlay, close } = openModal(`
    <div class="modal" role="dialog" aria-modal="true" aria-label="投稿編集">
      <div class="modal-head"><button class="icon-btn" data-close aria-label="閉じる">${ICON.close}</button><button class="btn" id="edit-submit" disabled>保存</button></div>
      <div class="modal-body">${avatar(me(), 48)}<textarea id="edit-text" aria-label="本文">${esc(post.body)}</textarea></div>
      ${post.images.length ? `<div class="previews">${post.images.map((img) => `<div class="preview"><img src="${img.url}" alt=""></div>`).join('')}</div>` : ''}
      <div class="modal-foot"><span style="color:var(--sub);font-size:13px">画像は変更できません</span><div class="counter" id="edit-counter"></div></div>
    </div>`, { onClose: async () => text.value === post.body || confirmDiscard() });
  const text = overlay.querySelector('#edit-text');
  const submit = overlay.querySelector('#edit-submit');
  const counter = overlay.querySelector('#edit-counter');
  const sync = () => {
    const ok = updateCounter(counter, text.value, LIMIT.body);
    const empty = !text.value.trim() && !post.images.length;
    submit.disabled = !ok || empty || text.value === post.body;
  };
  text.addEventListener('input', sync);
  submit.addEventListener('click', () => {
    post.body = text.value.trim();
    post.editedAt = Date.now();
    close();
    snackbar('投稿を編集しました');
    render();
  });
  sync();
  text.focus();
  text.setSelectionRange(text.value.length, text.value.length);
}

// 画像の拡大表示（画面設計書 4.3）
function openViewer(images, index) {
  const layer = document.createElement('div');
  layer.className = 'viewer';
  document.body.appendChild(layer);
  const show = () => {
    layer.innerHTML = `
      <button class="icon-btn close" aria-label="閉じる">${ICON.close}</button>
      ${images.length > 1 ? `<button class="icon-btn prev" aria-label="前の画像">${ICON.left}</button><button class="icon-btn next" aria-label="次の画像">${ICON.right}</button>` : ''}
      <img src="${images[index].url}" alt="画像 ${index + 1} / ${images.length}">`;
  };
  const move = (d) => { index = (index + d + images.length) % images.length; show(); };
  const close = () => { layer.remove(); document.removeEventListener('keydown', onKey); };
  const onKey = (e) => { if (e.key === 'Escape') close(); if (e.key === 'ArrowLeft') move(-1); if (e.key === 'ArrowRight') move(1); };
  layer.addEventListener('click', (e) => {
    if (e.target.closest('.prev')) move(-1);
    else if (e.target.closest('.next')) move(1);
    else if (e.target.tagName !== 'IMG') close();
  });
  let startX = null; // スマホは左右のスワイプで切り替える
  layer.addEventListener('touchstart', (e) => { startX = e.touches[0].clientX; });
  layer.addEventListener('touchend', (e) => { const dx = e.changedTouches[0].clientX - startX; if (Math.abs(dx) > 50) move(dx < 0 ? 1 : -1); });
  document.addEventListener('keydown', onKey);
  show();
}

// 投稿・コメントの「︙」メニュー
function openMenu(button, items) {
  document.querySelectorAll('.popup').forEach((p) => p.remove());
  const popup = document.createElement('div');
  popup.className = 'popup';
  popup.innerHTML = items.map((it, i) => `<button class="${it.danger ? 'danger' : ''}" data-i="${i}">${it.label}</button>`).join('');
  button.parentElement.appendChild(popup);
  popup.addEventListener('click', (e) => {
    e.stopPropagation();
    const i = e.target.dataset.i;
    popup.remove();
    if (i !== undefined) items[Number(i)].run();
  });
  setTimeout(() => document.addEventListener('click', () => popup.remove(), { once: true }));
}

// ============================================================
// 操作（クリックをまとめて受け取る）
// ============================================================
// 投稿カードを、今のデータで描き直す（いいね・コメントの数の更新）
function refreshPostCards(postId) {
  document.querySelectorAll(`[data-post-id="${postId}"]`).forEach((el) => {
    const detail = el.classList.contains('detail');
    const context = el.querySelector('.post-context');
    const tmp = document.createElement('div');
    tmp.innerHTML = postCard({ post: postById(postId) }, { detail });
    const next = tmp.firstElementChild;
    if (context) next.querySelector('.post-head').before(context); // いいね経由の表示は残す
    el.replaceWith(next);
  });
}

async function deletePost(postId) {
  const ok = await confirmDialog({ title: '投稿を削除しますか？', body: 'この操作は取り消せません。コメントといいねも削除されます。', ok: '削除' });
  if (!ok) return;
  // コメント・いいね・画像も一緒に消す（BR-13）
  db.posts = db.posts.filter((p) => p.id !== postId);
  db.comments = db.comments.filter((c) => c.postId !== postId);
  db.likes = db.likes.filter((l) => l.postId !== postId);
  snackbar('投稿を削除しました');
  if (location.hash.startsWith(`#/posts/${postId}`)) history.back();
  else document.querySelectorAll(`[data-post-id="${postId}"]`).forEach((el) => el.remove());
}

async function deleteComment(commentId) {
  const ok = await confirmDialog({ title: 'コメントを削除しますか？', body: 'この操作は取り消せません。', ok: '削除' });
  if (!ok) return;
  const c = db.comments.find((x) => x.id === commentId);
  db.comments = db.comments.filter((x) => x.id !== commentId);
  document.querySelector(`[data-comment-id="${commentId}"]`)?.remove();
  refreshPostCards(c.postId);
  snackbar('コメントを削除しました');
}

const actions = {
  compose: () => openCompose(),
  back: () => (history.length > 1 ? history.back() : (location.hash = '#/')),
  tab: (el) => { timelineTab = el.dataset.tab; render(); },
  'close-highlights': () => { document.getElementById('highlights').innerHTML = ''; },
  'open-post': (el) => { location.hash = `#/posts/${el.dataset.post}`; },
  'open-user': (el) => { location.hash = `#/users/${el.dataset.username}`; },
  'view-image': (el) => openViewer(postById(Number(el.dataset.post)).images, Number(el.dataset.index)),
  like: (el) => {
    // いいねは 1 人 1 回。もう一度押すと取り消す（BR-33）
    const postId = Number(el.dataset.post);
    if (likedByMe(postId)) db.likes = db.likes.filter((l) => !(l.postId === postId && l.userId === meId));
    else db.likes.push({ postId, userId: meId, createdAt: Date.now() });
    refreshPostCards(postId);
  },
  follow: (el) => {
    const id = Number(el.dataset.user);
    if (isFollowing(meId, id)) db.follows = db.follows.filter((f) => !(f.followerId === meId && f.followeeId === id));
    else db.follows.push({ followerId: meId, followeeId: id, createdAt: Date.now() });
    // フォロワー数などが変わるので、プロフィールは描き直す。一覧ではボタンだけ切り替える
    if (/^#\/users\/[^/]+$/.test(location.hash)) render();
    else document.querySelectorAll(`[data-action="follow"][data-user="${id}"]`).forEach((b) => { b.outerHTML = followButton(userById(id)); });
  },
  'post-menu': (el) => {
    const postId = Number(el.dataset.post);
    openMenu(el, [
      { label: '編集', run: () => openEdit(postId) },
      { label: '削除', danger: true, run: () => deletePost(postId) },
    ]);
  },
  'comment-menu': (el) => openMenu(el, [{ label: '削除', danger: true, run: () => deleteComment(Number(el.dataset.comment)) }]),
  'toggle-password': (el) => {
    const input = el.parentElement.querySelector('input');
    input.type = input.type === 'password' ? 'text' : 'password';
    el.textContent = input.type === 'password' ? '表示' : '隠す';
  },
  'pick-avatar': () => document.getElementById('avatar-file').click(),
  'cancel-profile': async () => {
    if (pages.editProfile.dirty() && !(await confirmDiscard())) return;
    location.hash = `#/users/${me().username}`;
  },
  logout: async () => {
    if (!(await confirmDialog({ title: 'ログアウトしますか？', ok: 'ログアウト', danger: false }))) return;
    meId = null;
    location.hash = '#/login';
  },
};

document.addEventListener('click', (e) => {
  // リンク・入力欄・ボタンを押したときは、カード全体のクリック（投稿の詳細へ）を動かさない
  const target = e.target.closest('[data-action]');
  if (!target) return;
  if (target.dataset.action === 'open-post' && target.tagName === 'ARTICLE' && e.target.closest('a, button, img, input, textarea')) return;
  if (target.dataset.action === 'open-user' && e.target.closest('a, button')) return;
  e.preventDefault();
  e.stopPropagation();
  actions[target.dataset.action]?.(target);
});

// ============================================================
// 画面の切り替え（画面設計書 3 章の URL を # の後ろに付けて表す）
// ============================================================
const routes = [
  [/^\/login$/, 'login', true],
  [/^\/signup$/, 'signup', true],
  [/^\/$/, 'home'],
  [/^\/posts\/(?<id>\d+)$/, 'post'],
  [/^\/posts\/(?<id>\d+)\/likes$/, 'likes'],
  [/^\/users\/(?<username>[^/]+)$/, 'profile'],
  [/^\/users\/(?<username>[^/]+)\/(?<kind>following|followers)$/, 'follows'],
  [/^\/settings\/profile$/, 'editProfile'],
  [/^\/settings$/, 'settings'],
  [/^\/search$/, 'search'],
];

function render() {
  const raw = location.hash.slice(1) || '/';
  const [path, qs = ''] = raw.split('?');
  modalRoot.innerHTML = '';
  for (const [re, name, isPublic] of routes) {
    const m = path.match(re);
    if (!m) continue;
    if (!isPublic && meId === null) { returnTo = location.hash || '#/'; location.hash = '#/login'; return; }
    if (isPublic && meId !== null) { location.hash = '#/'; return; }
    pages[name]({ ...m.groups, query: new URLSearchParams(qs) });
    window.scrollTo(0, 0);
    return;
  }
  if (meId === null) { location.hash = '#/login'; return; }
  pages.notFound();
}

window.addEventListener('hashchange', render);
render();
