import { Route, Routes } from 'react-router'
import { PublicOnly, RequireAuth } from './auth/RouteGuards'
import { AppLayout } from './components/AppLayout'
import { FollowListPage } from './pages/FollowListPage'
import { LikesPage } from './pages/LikesPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PostDetailPage } from './pages/PostDetailPage'
import { ProfilePage } from './pages/ProfilePage'
import { SignupPage } from './pages/SignupPage'
import { TimelinePage } from './pages/TimelinePage'

/** 画面と URL の対応（画面設計書 3 章）。 */
export function App() {
  return (
    <Routes>
      <Route element={<PublicOnly />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
      </Route>
      <Route element={<RequireAuth />}>
        {/* ログイン後の画面は、ナビのある枠の中に出す */}
        <Route element={<AppLayout />}>
          <Route path="/" element={<TimelinePage />} />
          <Route path="/posts/:postId" element={<PostDetailPage />} />
          <Route path="/posts/:postId/likes" element={<LikesPage />} />
          <Route path="/users/:username" element={<ProfilePage />} />
          <Route path="/users/:username/following" element={<FollowListPage tab="following" />} />
          <Route path="/users/:username/followers" element={<FollowListPage tab="followers" />} />
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
