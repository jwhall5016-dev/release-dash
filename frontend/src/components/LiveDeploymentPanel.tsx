import { useQuery } from '@tanstack/react-query'
import { getLiveVersionsByRepository, hashesMatch, LiveVersion } from '../services/api'

interface Props {
  repository: string
  commitSha: string
  releaseEnvironment?: string
}

function statusLabel(version: LiveVersion, commitSha: string): { text: string; color: string } {
  if (version.status === 'not_configured') {
    return { text: 'Not configured', color: '#6b7280' }
  }
  if (version.status === 'unreachable' || version.status === 'error') {
    return { text: 'Unreachable', color: '#dc2626' }
  }
  if (!version.build_hash || version.status === 'unknown') {
    return { text: 'Unknown hash', color: '#ca8a04' }
  }
  if (hashesMatch(version.build_hash, commitSha)) {
    return { text: 'Running this release', color: '#16a34a' }
  }
  return { text: 'Different hash', color: '#ea580c' }
}

function shortHash(hash?: string): string {
  if (!hash) return '—'
  return hash.length > 12 ? `${hash.slice(0, 12)}…` : hash
}

export default function LiveDeploymentPanel({ repository, commitSha, releaseEnvironment }: Props) {
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ['live-versions-repo', repository],
    queryFn: () => getLiveVersionsByRepository(repository),
    enabled: !!repository,
    refetchInterval: 60_000,
  })

  return (
    <div style={{
      backgroundColor: 'white',
      borderRadius: 8,
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
      padding: '1.5rem',
      marginBottom: '2rem',
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <div>
          <h2 style={{ margin: 0 }}>Live deployments</h2>
          <p style={{ margin: '0.35rem 0 0', color: '#666', fontSize: '0.875rem' }}>
            Your browser polls each environment&apos;s <code>/version</code> endpoint for{' '}
            <code>{repository}</code> and compares it to this release&apos;s commit SHA.
          </p>
        </div>
        <button
          type="button"
          onClick={() => refetch()}
          disabled={isFetching}
          style={{
            padding: '0.4rem 0.75rem',
            backgroundColor: '#1f2937',
            color: 'white',
            border: 'none',
            borderRadius: 4,
            cursor: isFetching ? 'wait' : 'pointer',
          }}
        >
          {isFetching ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      {isLoading && <div style={{ color: '#666' }}>Probing version endpoints from your browser…</div>}
      {error && (
        <div style={{ color: '#991b1b' }}>
          {error instanceof Error ? error.message : String(error)}
        </div>
      )}

      {!isLoading && data && data.length === 0 && (
        <div style={{ color: '#6b7280' }}>
          No live service mapping for repository <code>{repository}</code>.
          Add it in <code>live-services.yml</code>.
        </div>
      )}

      {data && data.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          {data.map((version) => {
            const status = statusLabel(version, commitSha)
            const highlight =
              releaseEnvironment &&
              (version.environment === releaseEnvironment ||
                (releaseEnvironment === 'production' && version.environment === 'production-gov'))
            return (
              <div
                key={version.environment}
                style={{
                  border: `1px solid ${highlight ? '#93c5fd' : '#eee'}`,
                  backgroundColor: highlight ? '#eff6ff' : '#fafafa',
                  borderRadius: 6,
                  padding: '0.85rem 1rem',
                  display: 'grid',
                  gridTemplateColumns: '140px 1fr auto',
                  gap: '0.75rem',
                  alignItems: 'center',
                }}
              >
                <div>
                  <strong>{version.environment}</strong>
                </div>
                <div>
                  <div style={{ fontSize: '0.875rem' }}>
                    Live: <code title={version.build_hash}>{shortHash(version.build_hash)}</code>
                    {' · '}
                    Release: <code title={commitSha}>{shortHash(commitSha)}</code>
                  </div>
                  {version.url && (
                    <a
                      href={version.url}
                      target="_blank"
                      rel="noreferrer"
                      style={{ fontSize: '0.75rem', color: '#2563eb' }}
                    >
                      {version.url}
                    </a>
                  )}
                  {version.error && (
                    <div style={{ color: '#6b7280', fontSize: '0.75rem', marginTop: 2 }}>
                      {version.error}
                    </div>
                  )}
                </div>
                <span style={{
                  padding: '0.2rem 0.55rem',
                  borderRadius: 4,
                  backgroundColor: status.color,
                  color: 'white',
                  fontSize: '0.75rem',
                  whiteSpace: 'nowrap',
                }}>
                  {status.text}
                </span>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
