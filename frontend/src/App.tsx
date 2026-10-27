import { Route, Routes } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { EmptyState, LoadingState } from './components/ui'
import { useGroups } from './groups/GroupContext'
import { Register, RequireAuth, SignIn } from './screens/Auth'
import { Dashboard } from './screens/Dashboard'
import { GroupDetail } from './screens/GroupDetail'
import { Groups } from './screens/Groups'
import { Finances } from './screens/Finances'
import { LoanDetail, Loans } from './screens/Loans'
import { MeetingDetail, Meetings } from './screens/Meetings'
import { VoteDetail, Votes } from './screens/Votes'
import { StatementDocumentView, Statements } from './screens/Statements'
import { Notifications } from './screens/Notifications'
import { Profile } from './screens/Profile'

/** Group-scoped screens need a selected group; show guidance otherwise. */
function RequireGroup({ children }: { children: React.JSX.Element }): React.JSX.Element {
  const { selected, loading } = useGroups()
  if (loading) return <LoadingState label="Loading your groups…" />
  if (!selected) {
    return (
      <EmptyState
        title="Choose a group first"
        hint="Pick a group in the top bar, or create one under Groups."
      />
    )
  }
  return children
}

function NotFound(): React.JSX.Element {
  return <EmptyState title="Page not found" hint="Use the navigation to find your way." />
}

export function App(): React.JSX.Element {
  return (
    <Routes>
      <Route path="/sign-in" element={<SignIn />} />
      <Route path="/register" element={<Register />} />
      <Route
        element={
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        }
      >
        <Route index element={<Dashboard />} />
        <Route path="groups" element={<Groups />} />
        <Route path="groups/:groupId" element={<GroupDetail />} />
        <Route path="contributions" element={<Finances />} />
        <Route path="loans" element={<Loans />} />
        <Route
          path="loans/:loanId"
          element={
            <RequireGroup>
              <LoanDetail />
            </RequireGroup>
          }
        />
        <Route path="meetings" element={<Meetings />} />
        <Route
          path="meetings/:meetingId"
          element={
            <RequireGroup>
              <MeetingDetail />
            </RequireGroup>
          }
        />
        <Route path="votes" element={<Votes />} />
        <Route
          path="votes/:voteId"
          element={
            <RequireGroup>
              <VoteDetail />
            </RequireGroup>
          }
        />
        <Route path="statements" element={<Statements />} />
        <Route
          path="statements/:requestId"
          element={
            <RequireGroup>
              <StatementDocumentView />
            </RequireGroup>
          }
        />
        <Route path="notifications" element={<Notifications />} />
        <Route path="profile" element={<Profile />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
