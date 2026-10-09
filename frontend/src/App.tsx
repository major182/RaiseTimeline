import { Route, Routes } from 'react-router'
import { PublicOnly, RequireAuth } from './auth/RouteGuards'
import { AppLayout } from './components/AppLayout'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
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
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
