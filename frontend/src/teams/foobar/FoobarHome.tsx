import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getFoobarReleases, getFoobarStats } from '../../services/api'
import FoobarReleaseTable from './FoobarReleaseTable'

const statCardStyle = {
  backgroundColor: 'white',
  padding: '1.5rem',
  borderRadius: '8px',
  boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
}

export default function FoobarHome() {
  const { data: stats, isLoading: statsLoading, error: statsError } = useQuery({
    queryKey: ['foobar', 'stats'],
    queryFn: getFoobarStats,
  })

  const { data: releases, isLoading: releasesLoading, error: releasesError } = useQuery({
    queryKey: ['foobar', 'releases', 'dashboard'],
    queryFn: () => getFoobarReleases(undefined, undefined, 1000),
  })

  if (statsLoading || releasesLoading) {
    return <div>Loading...</div>
  }

  if (statsError || releasesError) {
    return (
      <div>
        <h1 style={{ marginBottom: '2rem' }}>Release Dashboard</h1>
        <div style={{
          backgroundColor: '#fee2e2',
          border: '1px solid #fca5a5',
          borderRadius: '8px',
          padding: '1rem',
          color: '#991b1b',
        }}>
          <strong>Error loading data:</strong>
          {statsError && <div>Stats: {statsError instanceof Error ? statsError.message : String(statsError)}</div>}
          {releasesError && <div>Releases: {releasesError instanceof Error ? releasesError.message : String(releasesError)}</div>}
        </div>
      </div>
    )
  }

  return (
    <div>
      <h1 style={{ marginBottom: '2rem' }}>Release Dashboard</h1>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
        gap: '1rem',
        marginBottom: '2rem',
      }}>
        <div style={statCardStyle}>
          <h3 style={{ color: '#666', fontSize: '0.9rem', marginBottom: '0.5rem' }}>Total Releases</h3>
          <p style={{ fontSize: '2rem', fontWeight: 'bold' }}>{stats?.totalReleases ?? 0}</p>
        </div>
        <div style={statCardStyle}>
          <h3 style={{ color: '#666', fontSize: '0.9rem', marginBottom: '0.5rem' }}>Repositories</h3>
          <p style={{ fontSize: '2rem', fontWeight: 'bold' }}>{stats?.repositories?.length ?? 0}</p>
        </div>
        <div style={statCardStyle}>
          <h3 style={{ color: '#666', fontSize: '0.9rem', marginBottom: '0.5rem' }}>Environments</h3>
          <p style={{ fontSize: '2rem', fontWeight: 'bold' }}>{stats?.environments?.length ?? 0}</p>
        </div>
      </div>

      <div style={{
        backgroundColor: 'white',
        borderRadius: '8px',
        boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
        padding: '1.5rem',
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h2 style={{ margin: 0 }}>Recent Releases</h2>
          <Link
            to="/foobar/releases"
            style={{ color: '#3b82f6', textDecoration: 'none', fontSize: '0.875rem' }}
          >
            View All →
          </Link>
        </div>
        <FoobarReleaseTable releases={releases ?? []} />
      </div>
    </div>
  )
}
