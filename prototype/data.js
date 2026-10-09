// プロトタイプの初期データ。再読み込みすると、この状態に戻る（保存はしない）
// 時刻は「今」からの相対で作るので、いつ開いても同じ見え方になる
'use strict';

const SEED = (() => {
  const now = Date.now();
  const MIN = 60 * 1000;
  const HOUR = 60 * MIN;
  const DAY = 24 * HOUR;

  // 画像の代わりに使う、色付きの SVG（外部のファイルを使わない）
  function placeholderImage(label, color, w, h) {
    const svg =
      `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">` +
      `<rect width="100%" height="100%" fill="${color}"/>` +
      `<text x="50%" y="50%" fill="#fff" font-size="${Math.round(h / 8)}" font-family="sans-serif" ` +
      `text-anchor="middle" dominant-baseline="middle">${label}</text></svg>`;
    return { url: 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg), width: w, height: h };
  }

  // id 1 が自分。2・3・7 をフォロー中にする
  const users = [
    { id: 1, username: 'raise_me', displayName: 'レイズ', email: 'me@example.com', password: 'pass1234', bio: 'RaiseTimeline を作っています。Spring Boot と React を勉強中。', color: '#1DA1F2', createdAt: now - 40 * DAY },
    { id: 2, username: 'yamada_t', displayName: '山田 太郎', email: 'yamada@example.com', password: 'pass1234', bio: 'コーヒーとランニングが好きです。', color: '#17BF63', createdAt: now - 120 * DAY },
    { id: 3, username: 'sato_hanako', displayName: '佐藤 花子', email: 'sato@example.com', password: 'pass1234', bio: '写真を撮るのが趣味。週末はだいたい散歩してます📷', color: '#F45D22', createdAt: now - 200 * DAY },
    { id: 4, username: 'suzuki_dev', displayName: 'すずき', email: 'suzuki@example.com', password: 'pass1234', bio: 'Web エンジニア。TypeScript が好き。', color: '#794BC4', createdAt: now - 90 * DAY },
    { id: 5, username: 'tanaka_cook', displayName: '田中＠料理', email: 'tanaka@example.com', password: 'pass1234', bio: '毎日の晩ごはんを記録しています🍳', color: '#E0245E', createdAt: now - 60 * DAY },
    { id: 6, username: 'takahashi', displayName: '高橋', email: 'takahashi@example.com', password: 'pass1234', bio: '', color: '#FFAD1F', createdAt: now - 3 * DAY },
    { id: 7, username: 'ito_books', displayName: '伊藤｜読書記録', email: 'ito@example.com', password: 'pass1234', bio: '月に10冊が目標。おすすめの本を教えてください。', color: '#00A6A6', createdAt: now - 300 * DAY },
    { id: 8, username: 'kobayashi_m', displayName: '小林 美咲', email: 'kobayashi@example.com', password: 'pass1234', bio: 'イラストを描いています🎨', color: '#5E6AD2', createdAt: now - 150 * DAY },
  ].map((u) => ({ ...u, avatar: null }));

  // [フォローする人, フォローされる人]
  const follows = [
    [1, 2], [1, 3], [1, 7],
    [2, 1], [2, 3], [2, 4], [2, 8],
    [3, 1], [3, 2], [3, 8],
    [4, 2], [5, 3], [6, 1], [7, 4], [7, 5], [8, 3],
  ];

  // [投稿者, 本文, 何分前, 画像]
  const postDefs = [
    [3, '朝の公園、空気が澄んでいて気持ちよかった。', 20, [placeholderImage('朝の公園', '#7FB77E', 1200, 800)]],
    [2, '今日は 10km 走れた！久しぶりに自己ベスト更新。', 55, []],
    [4, 'TypeScript 6.0 の新しい機能を試してみた。型の推論がかなり賢くなってる。', 90, []],
    [1, 'タイムラインのカーソル方式、理解できてきた気がする。', 150, []],
    [7, '今月の5冊目読了。ミステリーは最後の50ページが止まらない。', 180, []],
    [8, '新しいイラスト描きました。', 240, [placeholderImage('イラスト 1', '#5E6AD2', 800, 1000), placeholderImage('イラスト 2', '#9B6BD8', 800, 1000)]],
    [2, 'ランチは近所の定食屋さん。からあげ定食がおいしかった。', 300, []],
    [5, '今日の晩ごはんは肉じゃが。じゃがいもがほくほく。', 330, [placeholderImage('肉じゃが', '#C98B4F', 1000, 1000)]],
    [3, '夕焼けがきれいだったので。', 400, [placeholderImage('夕焼け', '#F08A4B', 1200, 700), placeholderImage('夕焼け 2', '#E66B3A', 1200, 700), placeholderImage('夕焼け 3', '#C9503A', 1200, 700)]],
    [6, 'はじめまして！今日から使い始めました。', 600, []],
    [7, 'おすすめの本があったら教えてください。最近はエッセイを読みたい気分。', 720, []],
    [1, 'Material UI のテーマ、どんな色にしようか考え中。', 900, []],
    [2, '雨の日はカフェで読書。', 1100, []],
    [4, 'PostgreSQL の pg_trgm、部分一致の検索が速くなって感動した。', 1300, []],
    [3, '近所の猫。今日もご機嫌。', 1500, [placeholderImage('猫', '#A0A0A0', 900, 900), placeholderImage('猫 2', '#8C8C8C', 900, 900), placeholderImage('猫 3', '#7A7A7A', 900, 900), placeholderImage('猫 4', '#666666', 900, 900)]],
    [5, '週末の作り置き。5品つくった！', 1700, []],
    [8, 'ペンタブを新しくした。描き心地がぜんぜん違う。', 2000, []],
  ];
  // 一覧の「続きの読み込み」を試せるよう、古い投稿を足す
  const fillers = [
    'おはようございます。今日もがんばろう。', '今日はいい天気。', 'ちょっと休憩。', 'お昼なに食べよう。',
    '帰り道に寄り道した。', '新しいことを始めたい気分。', 'やっと週末。', '早寝早起きを続けて3日目。',
    '部屋の片付けをした。すっきり。', '映画を観てきた。よかった。', '散歩していたら桜が咲いていた。', '今日は早めに寝ます。',
  ];
  for (let i = 0; i < 40; i++) {
    postDefs.push([[1, 2, 3, 4, 5, 7, 8][i % 7], fillers[i % fillers.length], 2200 + i * 260, []]);
  }

  const posts = postDefs.map(([userId, body, minutesAgo, images], i) => ({
    id: i + 1,
    userId,
    body,
    images,
    createdAt: now - minutesAgo * MIN,
    editedAt: null,
  }));
  posts[11].editedAt = posts[11].createdAt + 10 * MIN; // 「編集済み」の見本

  // [投稿の ID, いいねした人, 何分前]
  const likeDefs = [
    [1, 2, 15], [1, 8, 10], [1, 1, 5],
    [2, 3, 50], [2, 1, 40], [2, 4, 30],
    [3, 2, 60], [3, 7, 45],              // すずき（未フォロー）の投稿に、山田と伊藤がいいね → いいね経由
    [5, 4, 170],
    [6, 2, 200], [6, 3, 190], [6, 1, 180],
    [8, 3, 100],                          // 田中（未フォロー）の投稿に佐藤がいいね → いいね経由
    [9, 2, 390], [9, 8, 380], [9, 1, 370], [9, 5, 300],
    [10, 1, 590],
    [11, 4, 700], [11, 5, 650],
    [14, 2, 1200], [14, 7, 1250],
    [15, 1, 1400], [15, 2, 1450], [15, 8, 1420],
    [17, 3, 1900],                        // 小林（未フォロー）の投稿に佐藤がいいね
  ];
  const likes = likeDefs.map(([postId, userId, minutesAgo]) => ({ postId, userId, createdAt: now - minutesAgo * MIN }));

  // [投稿の ID, コメントした人, 本文, 何分前]
  const commentDefs = [
    [1, 2, 'いい写真！どこの公園ですか？', 14],
    [1, 3, '駅の北側の公園です〜', 12],
    [2, 1, 'おめでとうございます！', 45],
    [2, 3, 'すごい！', 35],
    [4, 2, 'がんばって👍', 140],
    [7, 3, 'あそこのからあげ大きいですよね', 280],
    [9, 1, 'きれい！', 380],
    [11, 2, '「〇〇」がおすすめです。読みやすいですよ。', 700],
    [11, 3, '私も知りたいです', 690],
    [15, 1, 'かわいい😺', 1450],
  ];
  const comments = commentDefs.map(([postId, userId, body, minutesAgo], i) => ({
    id: i + 1, postId, userId, body, createdAt: now - minutesAgo * MIN,
  }));

  return {
    users,
    follows: follows.map(([a, b], i) => ({ followerId: a, followeeId: b, createdAt: now - (i + 1) * DAY })),
    posts,
    likes,
    comments,
    // 留守中のハイライトを試せるよう、最後に開いたのを 8 時間前にしておく
    lastTimelineViewedAt: now - 8 * HOUR,
  };
})();
