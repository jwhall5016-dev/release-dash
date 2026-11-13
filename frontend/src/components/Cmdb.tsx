import { useMemo, useState, type CSSProperties } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  CmdbNotEnabledError,
  CmdbNotImplementedError,
  getCmdbItems,
  refreshCmdbCatalog,
} from '../services/api'
import { categoryDisplayName } from '../utils/cmdbSchema'

export default function Cmdb() {
  const queryClient = useQueryClient()
  const [category, setCategory] = useState('')
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  const [refreshError, setRefreshError] = useState<string | null>(null)

  const { data, isLoading, error, dataUpdatedAt } = useQuery({
    queryKey: ['cmdb-items'],
    queryFn: getCmdbItems,
    retry: false,
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  })

  const notEnabled = error instanceof CmdbNotEnabledError
  const notImplemented = error instanceof CmdbNotImplementedError

  const categories = useMemo(
    () => [...new Set((data ?? []).map((row) => row.category).filter(Boolean))].sort(),
    [data]
  )
  const statuses = useMemo(
    () => [...new Set((data ?? []).map((row) => row.status).filter(Boolean))].sort(),
    [data]
  )

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    return (data ?? []).filter((row) => {
      if (category && row.category !== category) return false
      if (status && row.status !== status) return false
      if (!q) return true
      return [row.ci_number, row.name, row.parent, row.notes]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(q))
    })
  }, [data, category, status, search])

  const onRefresh = async () => {
    setRefreshing(true)
    setRefreshError(null)
    try {
      const items = await refreshCmdbCatalog()
      queryClient.setQueryData(['cmdb-items'], items)
    } catch (err) {
      setRefreshError(err instanceof Error ? err.message : String(err))
    } finally {
      setRefreshing(false)
    }
  }

  return (
    <div>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'baseline',
        gap: '1rem',
        marginBottom: '1.5rem',
        flexWrap: 'wrap',
      }}>
        <h1 style={{ margin: 0 }}>CMDB</h1>
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
          <button
            type="button"
            onClick={onRefresh}
            disabled={refreshing || notEnabled}
            style={{
              backgroundColor: 'white',
              border: '1px solid #d1d5db',
              borderRadius: '6px',
              padding: '0.5rem 0.9rem',
              cursor: refreshing || notEnabled ? 'not-allowed' : 'pointer',
            }}
          >
            {refreshing ? 'Refreshing…' : 'Refresh'}
          </button>
          <Link
            to="/mentat/cmdb/new"
            style={{
              backgroundColor: '#3b82f6',
              color: 'white',
              textDecoration: 'none',
              borderRadius: '6px',
              padding: '0.5rem 0.9rem',
            }}
          >
            Propose New CI
          </Link>
        </div>
      </div>
      <p style={{ color: '#555', marginBottom: '1.5rem', maxWidth: '42rem' }}>
        Configuration items from <code>slingshot-aerospace-inc/mentatcmdb</code>.
        New CIs open a GitHub pull request. Editing in the UI is still read-only.
      </p>

      {isLoading && <div>Loading…</div>}

      {(notEnabled || notImplemented) && (
        <div style={{
          backgroundColor: '#eff6ff',
          border: '1px solid #bfdbfe',
          borderRadius: '8px',
          padding: '1.25rem',
          color: '#1e3a5f',
          maxWidth: '42rem',
          marginBottom: '1.5rem',
        }}>
          <strong style={{ display: 'block', marginBottom: '0.5rem' }}>
            CMDB is not configured yet
          </strong>
          <p style={{ margin: 0, lineHeight: 1.5 }}>
            Enable <code>CMDB_ENABLED</code> and inject <code>CMDB_GITHUB_TOKEN</code> via
            External Secrets.
          </p>
        </div>
      )}

      {(refreshError || (error && !notEnabled && !notImplemented)) && (
        <div style={{
          backgroundColor: '#fee2e2',
          border: '1px solid #fca5a5',
          borderRadius: '8px',
          padding: '1rem',
          color: '#991b1b',
          marginBottom: '1.5rem',
        }}>
          <strong>Error loading CMDB:</strong>{' '}
          {refreshError ?? (error instanceof Error ? error.message : String(error))}
        </div>
      )}

      <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
        <select value={category} onChange={(e) => setCategory(e.target.value)} style={filterStyle}>
          <option value="">All categories</option>
          {categories.map((value) => (
            <option key={value} value={value}>{categoryDisplayName(value)}</option>
          ))}
        </select>
        <select value={status} onChange={(e) => setStatus(e.target.value)} style={filterStyle}>
          <option value="">All statuses</option>
          {statuses.map((value) => (
            <option key={value} value={value}>{value}</option>
          ))}
        </select>
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search name or CI number"
          style={{ ...filterStyle, minWidth: '16rem' }}
        />
      </div>

      {dataUpdatedAt > 0 && !error && (
        <p style={{ color: '#6b7280', fontSize: '0.85rem', marginBottom: '0.75rem' }}>
          Last fetched {new Date(dataUpdatedAt).toLocaleString()}
        </p>
      )}

      <table style={{
        width: '100%',
        borderCollapse: 'collapse',
        backgroundColor: 'white',
        borderRadius: '8px',
        overflow: 'hidden',
      }}>
        <thead>
          <tr style={{ textAlign: 'left', backgroundColor: '#f3f4f6' }}>
            {['CI number', 'Name', 'Category', 'Status', 'Parent', 'Notes'].map((heading) => (
              <th key={heading} style={{ padding: '0.75rem 1rem', fontWeight: 600 }}>{heading}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {filtered.length > 0 ? (
            filtered.map((row) => (
              <tr key={row.ci_number} style={{ borderTop: '1px solid #e5e7eb' }}>
                <td style={{ padding: '0.75rem 1rem' }}>
                  <Link to={`/mentat/cmdb/${encodeURIComponent(row.ci_number)}`} style={{ color: '#1d4ed8' }}>
                    {row.ci_number}
                  </Link>
                </td>
                <td style={{ padding: '0.75rem 1rem' }}>{row.name}</td>
                <td style={{ padding: '0.75rem 1rem' }}>{categoryDisplayName(row.category)}</td>
                <td style={{ padding: '0.75rem 1rem' }}>{row.status}</td>
                <td style={{ padding: '0.75rem 1rem' }}>{row.parent ?? '—'}</td>
                <td style={{ padding: '0.75rem 1rem' }}>{row.notes ?? '—'}</td>
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={6} style={{ padding: '1rem', color: '#6b7280' }}>
                No items loaded.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

const filterStyle: CSSProperties = {
  padding: '0.45rem 0.65rem',
  border: '1px solid #d1d5db',
  borderRadius: '6px',
  backgroundColor: 'white',
}
