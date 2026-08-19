import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.tsx'
import ErrorBoundary from './app/ErrorBoundary.tsx'
import { loadRealtimeRecoverySnapshots } from './core/realtime/realtimeRecoveryLoader.ts'
import {
  setRealtimeRecoverySnapshotLoader,
  startRealtimeRuntime,
} from './core/realtime/realtimeRuntime.ts'
import { bootstrapAuthSession } from './features/auth/authBootstrap.ts'
import './index.css'

async function startApp() {
  await bootstrapAuthSession()
  setRealtimeRecoverySnapshotLoader(loadRealtimeRecoverySnapshots)
  startRealtimeRuntime()

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <ErrorBoundary>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </ErrorBoundary>
    </StrictMode>,
  )
}

void startApp()
