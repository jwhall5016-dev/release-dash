import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getReleases, getStats, Release } from '../services/api'
import { Link } from 'react-router-dom'
import { format } from 'date-fns'
import React from 'react'
import { compareVersions } from '../utils/versionCompare'

export default function ReleasesList() {
  const [selectedRepo, setSelectedRepo] = useState<string>('')
  const [selectedEnv, setSelectedEnv] = useState<string>('')
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set())

  // Fetch available environments dynamically
  const { data: stats } = useQuery({
    queryKey: ['stats'],
    queryFn: getStats,
  })

  const { data: releases, isLoading, error } = useQuery({
    queryKey: ['releases', selectedRepo, selectedEnv],
    queryFn: () => getReleases(selectedRepo || undefined, selectedEnv || undefined, 100000),
  })

  // Group releases by repository + environment, keeping latest first
  const groupedReleases = useMemo(() => {
    if (!releases || releases.length === 0) {
      console.log('No releases to group')
      return []
    }
    
    console.log('Grouping releases:', releases.length, 'total')
    const groups = new Map<string, Release[]>()
    
    // Group releases by repository + environment
    releases.forEach(release => {
      const key = `${release.repository}::${release.environment}`
      console.log(`Adding release ${release.release_tag || release.pr_number} to group: ${key}`)
      if (!groups.has(key)) {
        groups.set(key, [])
      }
      groups.get(key)!.push(release)
    })
    
    console.log('Created groups:', groups.size, Array.from(groups.keys()))
    
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
      console.log(`Group ${key}: ${groupReleases.length} releases, Versions:`, groupReleases.map(r => r.release_tag))
    })
    
    // Convert to sorted array of entries for consistent rendering order
    // Sort groups by: repository name, then environment, then latest PR number
    const sortedEntries = Array.from(groups.entries()).sort(([keyA, releasesA], [keyB, releasesB]) => {
      const [repoA, envA] = keyA.split('::')
      const [repoB, envB] = keyB.split('::')
      
      // Sort by repository first
      const repoCompare = repoA.localeCompare(repoB)
      if (repoCompare !== 0) return repoCompare
      
      // Then by environment
      const envCompare = envA.localeCompare(envB)
      if (envCompare !== 0) return envCompare
      
      // Then by latest version in group (descending)
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
    
    console.log('Sorted group keys:', sortedEntries.map(([key]) => key))
    console.log('Final grouped releases:', sortedEntries.map(([key, rels]) => `${key}: ${rels.length} releases`))
    
    // Return as array of [key, releases] tuples for consistent iteration
    return sortedEntries
  }, [releases])

  const toggleGroup = (key: string) => {
    setExpandedGroups(prev => {
      const next = new Set(prev)
      if (next.has(key)) {
        next.delete(key)
      } else {
        next.add(key)
      }
      return next
    })
  }

  const clearFilters = () => {
    setSelectedRepo('')
    setSelectedEnv('')
  }

  const hasActiveFilters = selectedRepo || selectedEnv

  return (
    <div>
      <h1 style={{ marginBottom: '2rem' }}>All Releases</h1>
      
      {/* Filters */}
      <div style={{
        display: 'flex',
        gap: '1rem',
        alignItems: 'flex-end',
        marginBottom: '2rem',
        padding: '1rem',
        backgroundColor: 'white',
        borderRadius: '8px',
        boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
      }}>
        <div>
          <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: '500' }}>
            Repository
          </label>
          <input
            type="text"
            value={selectedRepo}
            onChange={(e) => setSelectedRepo(e.target.value)}
            placeholder="Filter by repository..."
            style={{
              padding: '0.5rem',
              border: '1px solid #ddd',
              borderRadius: '4px',
              width: '200px'
            }}
          />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: '500' }}>
            Environment
          </label>
          <select
            value={selectedEnv}
            onChange={(e) => setSelectedEnv(e.target.value)}
            style={{
              padding: '0.5rem',
              border: '1px solid #ddd',
              borderRadius: '4px',
              width: '150px'
            }}
          >
            <option value="">All Environments</option>
            {stats?.environments
              .sort()
              .map((env) => (
                <option key={env} value={env}>
                  {env.charAt(0).toUpperCase() + env.slice(1)}
                </option>
              ))}
          </select>
        </div>
        {hasActiveFilters && (
          <button
            onClick={clearFilters}
            style={{
              padding: '0.5rem 1rem',
              backgroundColor: '#6b7280',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              height: 'fit-content'
            }}
          >
            Clear Filters
          </button>
        )}
      </div>

      {/* Error Display */}
      {error && (
        <div style={{
          backgroundColor: '#fee2e2',
          border: '1px solid #fca5a5',
          borderRadius: '8px',
          padding: '1rem',
          marginBottom: '1rem',
          color: '#991b1b'
        }}>
          <strong>Error loading releases:</strong> {error instanceof Error ? error.message : String(error)}
          <details style={{ marginTop: '0.5rem', fontSize: '0.875rem' }}>
            <summary>Show details</summary>
            <pre style={{ marginTop: '0.5rem', whiteSpace: 'pre-wrap' }}>
              {JSON.stringify(error, null, 2)}
            </pre>
          </details>
        </div>
      )}

      {/* Releases Table */}
      {isLoading ? (
        <div>Loading...</div>
      ) : groupedReleases.length > 0 ? (
        <div style={{
          backgroundColor: 'white',
          borderRadius: '8px',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
          overflow: 'hidden'
        }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ backgroundColor: '#f5f5f5', borderBottom: '2px solid #ddd' }}>
                <th style={{ textAlign: 'left', padding: '1rem', width: '30px' }}></th>
                <th style={{ textAlign: 'left', padding: '1rem' }}>Repository</th>
                <th style={{ textAlign: 'left', padding: '1rem' }}>Version</th>
                <th style={{ textAlign: 'left', padding: '1rem' }}>Environment</th>
                <th style={{ textAlign: 'left', padding: '1rem' }}>Created</th>
                <th style={{ textAlign: 'left', padding: '1rem' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {groupedReleases.map(([groupKey, groupReleases]) => {
                console.log(`Rendering group: ${groupKey} with ${groupReleases.length} releases:`, groupReleases.map(r => r.release_tag || r.pr_number))
                const isExpanded = expandedGroups.has(groupKey)
                const latestRelease = groupReleases[0]
                const otherReleases = groupReleases.slice(1)
                const hasMultiple = groupReleases.length > 1
                
                if (!latestRelease) {
                  console.error(`Group ${groupKey} has no releases!`)
                  return null
                }

                return (
                  <React.Fragment key={groupKey}>
                    {/* Latest release row */}
                    <tr style={{ 
                      borderBottom: hasMultiple ? '2px solid #ddd' : '1px solid #eee',
                      backgroundColor: isExpanded ? '#f9fafb' : 'white'
                    }}>
                      <td style={{ padding: '1rem', textAlign: 'center' }}>
                        {hasMultiple && (
                          <button
                            onClick={() => toggleGroup(groupKey)}
                            style={{
                              background: 'none',
                              border: 'none',
                              cursor: 'pointer',
                              fontSize: '1rem',
                              color: '#6b7280',
                              padding: '0.25rem'
                            }}
                          >
                            {isExpanded ? '▼' : '▶'}
                          </button>
                        )}
                      </td>
                      <td style={{ padding: '1rem' }}>
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
                      <td style={{ padding: '1rem', fontFamily: 'monospace', fontSize: '0.875rem', color: '#059669' }}>
                        {latestRelease.release_tag || 'N/A'}
                      </td>
                      <td style={{ padding: '1rem' }}>
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
                      <td style={{ padding: '1rem', color: '#666' }}>
                        {format(new Date(latestRelease.created_at), 'MMM d, yyyy HH:mm')}
                      </td>
                      <td style={{ padding: '1rem' }}>
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
                    {/* Other releases in group (when expanded) */}
                    {isExpanded && otherReleases.map((release) => (
                      <tr key={release.id} style={{ 
                        borderBottom: '1px solid #eee',
                        backgroundColor: '#fafafa'
                      }}>
                        <td style={{ padding: '1rem' }}></td>
                        <td style={{ padding: '1rem', paddingLeft: '2rem', color: '#6b7280' }}>
                          {release.repository}
                        </td>
                        <td style={{ padding: '1rem', fontFamily: 'monospace', fontSize: '0.875rem', color: '#059669' }}>
                          {release.release_tag || 'N/A'}
                        </td>
                        <td style={{ padding: '1rem' }}>
                          <span style={{
                            padding: '0.25rem 0.5rem',
                            borderRadius: '4px',
                            backgroundColor: release.environment === 'production' ? '#dc2626' : 
                                           release.environment === 'staging' ? '#f59e0b' : '#3b82f6',
                            color: 'white',
                            fontSize: '0.875rem',
                            opacity: 0.8
                          }}>
                            {release.environment}
                          </span>
                        </td>
                        <td style={{ padding: '1rem', color: '#9ca3af' }}>
                          {format(new Date(release.created_at), 'MMM d, yyyy HH:mm')}
                        </td>
                        <td style={{ padding: '1rem' }}>
                          <Link
                            to={`/mentat/releases/${release.repository}/${release.pr_number}`}
                            style={{
                              color: '#3b82f6',
                              textDecoration: 'none'
                            }}
                          >
                            View Details
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </React.Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <div style={{
          backgroundColor: 'white',
          padding: '2rem',
          borderRadius: '8px',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
          textAlign: 'center',
          color: '#666'
        }}>
          No releases found
        </div>
      )}
    </div>
  )
}
