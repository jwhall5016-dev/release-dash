import { useMemo, useState, type CSSProperties } from 'react'
import { useQueries, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  getLiveServicesCatalog,
  getLiveVersionsByEnvironment,
  LiveVersion,
  MENTAT_API_BASE,
  Release,
} from '../services/api'
import axios from 'axios'

const ENV_STORAGE_KEY = 'release-dash-live-env'

async function matchReleaseByBuildHash(
  repository: string,
  buildHash: string,
  environment: string
): Promise<Release | null> {
  try {
    const response = await axios.get<Release>(`${MENTAT_API_BASE}/build-hash-match`, {
      params: { repository, buildHash, environment },
      timeout: 120000,
    })
    return response.data
  } catch {
    return null
  }
}

function getStoredEnv(fallback: string): string {
  try {
    const stored = localStorage.getItem(ENV_STORAGE_KEY)
    if (stored?.trim()) return stored.trim()
  } catch {
    /* ignore */
  }
  return fallback
}

function statusColor(status: string): string {
  switch (status) {
    case 'ok':
      return '#16a34a'
    case 'unknown':
      return '#ca8a04'
    case 'not_configured':
      return '#6b7280'
    default:
      return '#dc2626'
  }
}

function shortHash(hash?: string): string {
  if (!hash) return '—'
  return hash.length > 12 ? `${hash.slice(0, 12)}…` : hash
}

export default function LiveDeployments() {
  const { data: catalog, isLoading: catalogLoading, error: catalogError } = useQuery({
    queryKey: ['live-services'],
    queryFn: getLiveServicesCatalog,
  })

  const environments = catalog?.environments ?? ['staging', 'cui-staging', 'production', 'production-gov', 'cui-gov']
  const [environment, setEnvironment] = useState(() => getStoredEnv(environments[0] ?? 'staging'))

  const {
    data: liveVersions,
    isLoading: versionsLoading,
    error: versionsError,
    dataUpdatedAt,
    refetch,
    isFetching,
  } = useQuery({
    queryKey: ['live-versions', environment],
    queryFn: () => getLiveVersionsByEnvironment(environment),
    enabled: !!environment,
    refetchInterval: 60_000,
  })

  const matchQueries = useQueries({
    queries: (liveVersions ?? [])
      .filter((v) => v.status === 'ok' && v.build_hash && v.repository)
      .map((version) => ({
        queryKey: ['build-hash-match', version.repository, version.build_hash, environment],
        queryFn: () =>
          matchReleaseByBuildHash(version.repository, version.build_hash!, environment),
        staleTime: 5 * 60_000,
      })),
  })

  const matchByKey = useMemo(() => {
    const map = new Map<string, Release | null>()
    const matchable = (liveVersions ?? []).filter(
      (v) => v.status === 'ok' && v.build_hash && v.repository
    )
    matchable.forEach((version, index) => {
      const key = `${version.repository}::${version.build_hash}`
      map.set(key, matchQueries[index]?.data ?? null)
    })
    return map
  }, [liveVersions, matchQueries])

  const rows = useMemo(() => {
    return (liveVersions ?? []).map((version) => {
      const key = `${version.repository}::${version.build_hash}`
      const matched =
        version.status === 'ok' && version.build_hash
          ? matchByKey.get(key) ?? undefined
          : undefined
      return { version, matched: matched || undefined }
    })
  }, [liveVersions, matchByKey])

  const matchingInProgress = matchQueries.some((q) => q.isFetching || q.isLoading)

  const selectEnvironment = (env: string) => {
    setEnvironment(env)
    try {
      localStorage.setItem(ENV_STORAGE_KEY, env)
    } catch {
      /* ignore */
    }
  }

  if (catalogLoading) {
    return <div>Loading live service catalog…</div>
  }

  if (catalogError) {
    return (
      <div style={{ color: '#991b1b' }}>
        Failed to load live service catalog. Check that{' '}
        <code>live-services.yml</code> is present on the backend.
      </div>
    )
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ marginBottom: '0.5rem' }}>Live Deployments</h1>
          <p style={{ color: '#666', margin: 0, maxWidth: 720 }}>
            Currently deployed versions in the selected environment. Please wait for the page to
            finish loading — live data is fetched from each service and may take a moment.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
          <label style={{ fontSize: '0.875rem', color: '#444' }}>
            Environment{' '}
            <select
              value={environment}
              onChange={(e) => selectEnvironment(e.target.value)}
              style={{ marginLeft: 8, padding: '0.35rem 0.5rem' }}
            >
              {environments.map((env) => (
                <option key={env} value={env}>
                  {env}
                </option>
              ))}
            </select>
          </label>
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
      </div>

      {versionsError && (
        <div style={{ background: '#fee2e2', border: '1px solid #fca5a5', padding: '0.75rem', borderRadius: 8, marginBottom: '1rem' }}>
          Failed to fetch live versions: {versionsError instanceof Error ? versionsError.message : String(versionsError)}
        </div>
      )}

      {(versionsLoading) && !liveVersions && (
        <div style={{ color: '#666' }}>Probing version endpoints from your browser…</div>
      )}

      {liveVersions && (
        <>
          <h2 style={{ fontSize: '1.1rem', marginBottom: '0.5rem' }}>Live versions · {environment}</h2>
          <p style={{ color: '#6b7280', fontSize: '0.875rem', marginBottom: '0.75rem' }}>
            Last updated: {dataUpdatedAt ? new Date(dataUpdatedAt).toLocaleString() : '—'}
            {matchingInProgress ? ' · matching releases…' : ''}
          </p>
          <div style={{
            backgroundColor: 'white',
            borderRadius: 8,
            boxShadow: '0 2px 4px rgba(0,0,0,0.08)',
            overflow: 'auto',
          }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.925rem' }}>
              <thead>
                <tr style={{ background: '#f9fafb', textAlign: 'left' }}>
                  <th style={th}>Service</th>
                  <th style={th}>Repository</th>
                  <th style={th}>Live build hash</th>
                  <th style={th}>Status</th>
                  <th style={th}>Matched release</th>
                  <th style={th}>Endpoint</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(({ version, matched }) => (
                  <LiveRow key={`${version.service}-${version.environment}`} version={version} matched={matched} />
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  )
}

function LiveRow({ version, matched }: { version: LiveVersion; matched?: Release }) {
  return (
    <tr style={{ borderTop: '1px solid #eee' }}>
      <td style={td}>
        <strong>{version.label || version.service}</strong>
      </td>
      <td style={td}>
        <code>{version.repository}</code>
      </td>
      <td style={td}>
        <code title={version.build_hash}>{shortHash(version.build_hash)}</code>
      </td>
      <td style={td}>
        <span style={{
          display: 'inline-block',
          padding: '0.15rem 0.5rem',
          borderRadius: 4,
          backgroundColor: statusColor(version.status),
          color: 'white',
          fontSize: '0.75rem',
          textTransform: 'uppercase',
        }}>
          {version.status}
        </span>
        {version.error && (
          <div style={{ color: '#6b7280', fontSize: '0.75rem', marginTop: 4, maxWidth: 280 }}>
            {version.error}
          </div>
        )}
      </td>
      <td style={td}>
        {matched ? (
          <div>
            <Link to={`/mentat/releases/${matched.repository}/${matched.pr_number}`}>
              {matched.release_tag || `PR #${matched.pr_number}`}
            </Link>
            <div style={{ color: '#6b7280', fontSize: '0.75rem' }}>
              {matched.environment} · <code>{shortHash(matched.commit_sha)}</code>
            </div>
          </div>
        ) : version.status === 'ok' ? (
          <span style={{ color: '#6b7280' }}>Loading release version details…</span>
        ) : (
          '—'
        )}
      </td>
      <td style={td}>
        {version.url ? (
          <a href={version.url} target="_blank" rel="noreferrer" style={{ fontSize: '0.8rem' }}>
            open
          </a>
        ) : (
          '—'
        )}
      </td>
    </tr>
  )
}

const th: CSSProperties = {
  padding: '0.75rem 1rem',
  fontWeight: 600,
  color: '#374151',
  whiteSpace: 'nowrap',
}

const td: CSSProperties = {
  padding: '0.85rem 1rem',
  verticalAlign: 'top',
}
