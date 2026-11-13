import { useQuery } from '@tanstack/react-query'
import { getStats, getReleases, Release } from '../services/api'
import { Link } from 'react-router-dom'
import { format } from 'date-fns'
import { useMemo } from 'react'
import { compareVersions } from '../utils/versionCompare'

export default function Dashboard() {
  const { data: stats, isLoading: statsLoading, error: statsError } = useQuery({
    queryKey: ['stats'],
    queryFn: getStats,
  })

  const { data: allReleases, isLoading: releasesLoading, error: releasesError } = useQuery({
    queryKey: ['releases', 'dashboard'],
    queryFn: () => getReleases(undefined, undefined, 1000), // Fetch many releases to ensure we get all repositories
  })

  // Filter to only staging and production, then group by repository + environment, keeping latest first
  const groupedReleases = useMemo(() => {
    if (!allReleases || allReleases.length === 0) {
      console.log('Dashboard: No releases to group')
      return []
    }
    
    // Filter out preview environments - only show staging and production
    const filteredReleases = allReleases.filter(release => 
      release.environment === 'staging' || release.environment === 'production'
    )
    
    console.log('Dashboard: Filtered', filteredReleases.length, 'releases (staging/production) from', allReleases.length, 'total')
    const groups = new Map<string, Release[]>()
    
    // Group releases by repository + environment
    filteredReleases.forEach(release => {
      const key = `${release.repository}::${release.environment}`
      if (!groups.has(key)) {
        groups.set(key, [])
      }
      groups.get(key)!.push(release)
    })
    
    console.log('Dashboard: Created', groups.size, 'groups:', Array.from(groups.keys()))
    
    // Sort each group by version (descending) to have latest first
    groups.forEach((groupReleases, key) => {
      groupReleases.sort((a, b) => {
        // Compare by version tag if available
        const versionA = a.release_tag || ''
        const versionB = b.release_tag || ''
        if (versionA && versionB) {
          const versionDiff = compareVersions(versionB, versionA) // Reverse for descending
          if (versionDiff !== 0) return versionDiff
        }
        // Fall back to created_at if versions are equal or missing
        return new Date(b.created_at).getTime() - new Date(a.created_at).getTime()
      })
      console.log(`Dashboard: Group ${key} has ${groupReleases.length} releases, Versions:`, groupReleases.map(r => r.release_tag))
    })
    
    // Convert to sorted array of entries
    const sortedEntries = Array.from(groups.entries()).sort(([keyA, releasesA], [keyB, releasesB]) => {
      const [repoA, envA] = keyA.split('::')
      const [repoB, envB] = keyB.split('::')
      
      const repoCompare = repoA.localeCompare(repoB)
      if (repoCompare !== 0) return repoCompare
      
      const envCompare = envA.localeCompare(envB)
      if (envCompare !== 0) return envCompare
      
      // Sort by latest version (descending)
      const latestVersionA = releasesA[0]?.release_tag || ''
      const latestVersionB = releasesB[0]?.release_tag || ''
      if (latestVersionA && latestVersionB) {
        return compareVersions(latestVersionB, latestVersionA) // Reverse for descending
      }
      // Fall back to created_at if versions are missing
      const latestDateA = releasesA[0]?.created_at ? new Date(releasesA[0].created_at).getTime() : 0
      const latestDateB = releasesB[0]?.created_at ? new Date(releasesB[0].created_at).getTime() : 0
      return latestDateB - latestDateA
    })
    
    console.log('Dashboard: Final grouped releases:', sortedEntries.map(([key]) => key))
    return sortedEntries
  }, [allReleases])

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
          color: '#991b1b'
        }}>
          <strong>Error loading data:</strong>
          {statsError && <div>Stats: {statsError instanceof Error ? statsError.message : String(statsError)}</div>}
          {releasesError && <div>Releases: {releasesError instanceof Error ? releasesError.message : String(releasesError)}</div>}
          <details style={{ marginTop: '0.5rem', fontSize: '0.875rem' }}>
            <summary>Show details</summary>
            <pre style={{ marginTop: '0.5rem', whiteSpace: 'pre-wrap' }}>
              {JSON.stringify({ statsError, releasesError }, null, 2)}
            </pre>
          </details>
        </div>
      </div>
    )
  }

  return (
    <div>
      <h1 style={{ marginBottom: '2rem' }}>Release Dashboard</h1>
      
      {/* Stats Cards */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
        gap: '1rem',
        marginBottom: '2rem'
      }}>
        <div style={{
          backgroundColor: 'white',
          padding: '1.5rem',
          borderRadius: '8px',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
        }}>
          <h3 style={{ color: '#666', fontSize: '0.9rem', marginBottom: '0.5rem' }}>Total Releases</h3>
          <p style={{ fontSize: '2rem', fontWeight: 'bold' }}>{stats?.totalReleases ?? 0}</p>
        </div>
        
        <div style={{
          backgroundColor: 'white',
          padding: '1.5rem',
          borderRadius: '8px',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
        }}>
          <h3 style={{ color: '#666', fontSize: '0.9rem', marginBottom: '0.5rem' }}>Repositories</h3>
          <p style={{ fontSize: '2rem', fontWeight: 'bold' }}>{stats?.repositories?.length ?? 0}</p>
        </div>
        
        <div style={{
          backgroundColor: 'white',
          padding: '1.5rem',
          borderRadius: '8px',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
        }}>
          <h3 style={{ color: '#666', fontSize: '0.9rem', marginBottom: '0.5rem' }}>Environments</h3>
          <p style={{ fontSize: '2rem', fontWeight: 'bold' }}>{stats?.environments?.length ?? 0}</p>
        </div>
      </div>

      {/* Recent Releases */}
      <div style={{
        backgroundColor: 'white',
        borderRadius: '8px',
        boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
        padding: '1.5rem'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h2 style={{ margin: 0 }}>Recent Releases</h2>
          <Link
            to="/mentat/releases"
            style={{
              color: '#3b82f6',
              textDecoration: 'none',
              fontSize: '0.875rem'
            }}
          >
            View All →
          </Link>
        </div>
        {groupedReleases.length > 0 ? (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid #eee' }}>
                <th style={{ textAlign: 'left', padding: '0.75rem' }}>Repository</th>
                <th style={{ textAlign: 'left', padding: '0.75rem' }}>Version</th>
                <th style={{ textAlign: 'left', padding: '0.75rem' }}>Environment</th>
                <th style={{ textAlign: 'left', padding: '0.75rem' }}>Created</th>
                <th style={{ textAlign: 'left', padding: '0.75rem' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {groupedReleases.map(([groupKey, groupReleases]) => {
                const latestRelease = groupReleases[0]
                const hasMultiple = groupReleases.length > 1
                
                return (
                  <tr key={groupKey} style={{ borderBottom: '1px solid #eee' }}>
                    <td style={{ padding: '0.75rem' }}>
                      {latestRelease.repository}
                      {hasMultiple && (
                        <span style={{
                          marginLeft: '0.5rem',
                          padding: '0.125rem 0.375rem',
                          borderRadius: '12px',
                          backgroundColor: '#e5e7eb',
                          color: '#6b7280',
                          fontSize: '0.75rem',
                          fontWeight: '500'
                        }}>
                          {groupReleases.length}
                        </span>
                      )}
                    </td>
                    <td style={{ padding: '0.75rem', fontFamily: 'monospace', fontSize: '0.875rem', color: '#059669' }}>
                      {latestRelease.release_tag || 'N/A'}
                    </td>
                    <td style={{ padding: '0.75rem' }}>
                      <span style={{
                        padding: '0.25rem 0.5rem',
                        borderRadius: '4px',
                        backgroundColor: latestRelease.environment === 'production' ? '#dc2626' : 
                                       latestRelease.environment === 'staging' ? '#f59e0b' : '#3b82f6',
                        color: 'white',
                        fontSize: '0.875rem'
                      }}>
                        {latestRelease.environment}
                      </span>
                    </td>
                    <td style={{ padding: '0.75rem', color: '#666' }}>
                      {format(new Date(latestRelease.created_at), 'MMM d, yyyy HH:mm')}
                    </td>
                    <td style={{ padding: '0.75rem' }}>
                      <Link
                        to={`/mentat/releases/${latestRelease.repository}/${latestRelease.pr_number}`}
                        style={{
                          color: '#3b82f6',
                          textDecoration: 'none'
                        }}
                      >
                        View Details
                      </Link>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        ) : (
          <p style={{ color: '#666' }}>No releases found</p>
        )}
      </div>
    </div>
  )
}
