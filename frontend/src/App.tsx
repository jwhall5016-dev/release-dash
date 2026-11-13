import { BrowserRouter as Router, Navigate, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ReleasesList from './components/ReleasesList'
import ReleaseDetail from './components/ReleaseDetail'
import Dashboard from './components/Dashboard'
import MemoryDashboard from './components/MemoryDashboard'
import LiveDeployments from './components/LiveDeployments'
import Cmdb from './components/Cmdb'
import CmdbNew from './components/CmdbNew'
import CmdbDetail from './components/CmdbDetail'
import Layout from './components/Layout'
import FoobarLayout from './teams/foobar/FoobarLayout'
import FoobarHome from './teams/foobar/FoobarHome'
import FoobarCmdb from './teams/foobar/FoobarCmdb'
import { FoobarLive, FoobarMemory, FoobarReleases } from './teams/foobar/FoobarSections'
import './App.css'

const queryClient = new QueryClient()

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <Router>
        <Routes>
          {/* Temporary: the original site root was the Mentat dashboard. */}
          <Route path="/" element={<Navigate to="/mentat" replace />} />
          <Route path="/mentat" element={<Layout />}>
            <Route index element={<Dashboard />} />
            <Route path="releases" element={<ReleasesList />} />
            <Route path="releases/:repository/:prNumber" element={<ReleaseDetail />} />
            <Route path="live" element={<LiveDeployments />} />
            <Route path="cmdb" element={<Cmdb />} />
            <Route path="cmdb/new" element={<CmdbNew />} />
            <Route path="cmdb/:ciNumber" element={<CmdbDetail />} />
            <Route path="memory" element={<MemoryDashboard />} />
          </Route>
          <Route path="/foobar" element={<FoobarLayout />}>
            <Route index element={<FoobarHome />} />
            <Route path="releases" element={<FoobarReleases />} />
            <Route path="live" element={<FoobarLive />} />
            <Route path="cmdb" element={<FoobarCmdb />} />
            <Route path="memory" element={<FoobarMemory />} />
          </Route>
        </Routes>
      </Router>
    </QueryClientProvider>
  )
}

export default App
