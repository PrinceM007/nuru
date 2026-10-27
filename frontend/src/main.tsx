import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './styles.css'
import { App } from './App'
import { AssistantProvider } from './ai/AssistantContext'
import { AuthProvider } from './auth/AuthContext'
import { GroupProvider } from './groups/GroupContext'
import { ThemeProvider } from './theme/ThemeContext'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <ThemeProvider>
        <AuthProvider>
          <GroupProvider>
            <AssistantProvider>
              <App />
            </AssistantProvider>
          </GroupProvider>
        </AuthProvider>
      </ThemeProvider>
    </BrowserRouter>
  </StrictMode>,
)
