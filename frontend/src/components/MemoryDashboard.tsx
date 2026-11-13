import { useQuery } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
  Cell,
} from 'recharts'
import { getDebugMemory } from '../services/api'

const STORAGE_KEY = 'release-dash-memory-api-url'
const REFRESH_STORAGE_KEY = 'release-dash-memory-refresh-interval'
const BASE_URL_OPTIONS = [
  { label: 'staging: talos-planning-agent (multi-step)', value: 'https://talos-planning-agent.slingshotstaging.com' },
  { label: 'staging: talos-single-step', value: 'https://talos-single-step.slingshotstaging.com' },
  { label: 'staging: slingshot-simulation-api', value: 'https://simulation.slingshotstaging.com' },
] as const
const DEFAULT_BASE_URL = BASE_URL_OPTIONS[0].value
const REFRESH_OPTIONS = [
  { label: '5 seconds', value: 5 },
  { label: '15 seconds', value: 15 },
  { label: '30 seconds', value: 30 },
  { label: '60 seconds', value: 60 },
  { label: '5 minutes', value: 300 },
] as const
const DEFAULT_REFRESH_SECONDS = 5
const RSS_HISTORY_MAX = 60

function formatBytes(n: number | null | undefined): string {
  if (n == null) return '–'
  if (n >= 1e9) return (n / 1e9).toFixed(2) + ' GB'
  if (n >= 1e6) return (n / 1e6).toFixed(2) + ' MB'
  if (n >= 1e3) return (n / 1e3).toFixed(1) + ' KB'
  return n + ' B'
}

function shortFile(file: string, maxLen = 50): string {
  if (!file) return ''
  const parts = file.split('/')
  const last = parts[parts.length - 1] ?? file
  if (last.length > maxLen) return last.slice(0, maxLen - 3) + '…'
  return last
}

function getStoredBaseUrl(): string {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored?.trim()) {
      const normalized = stored.trim().replace(/\/$/, '')
      if (BASE_URL_OPTIONS.some((o) => o.value === normalized)) return normalized
    }
  } catch {
    /* ignore */
  }
  return DEFAULT_BASE_URL
}

function getStoredRefreshSeconds(): number {
  try {
    const stored = localStorage.getItem(REFRESH_STORAGE_KEY)
    if (stored != null) {
      const n = Number(stored)
      if (Number.isInteger(n) && REFRESH_OPTIONS.some((o) => o.value === n)) return n
    }
  } catch {
    /* ignore */
  }
  return DEFAULT_REFRESH_SECONDS
}

/** Single memory value from either Python (rss/rss_bytes) or JVM (heap + non_heap used_bytes). */
function getMemoryBytes(data: Record<string, unknown> | null | undefined): number | null {
  if (!data || typeof data !== 'object') return null
  const rss = data.rss_bytes ?? data.rss
  if (typeof rss === 'number' && !Number.isNaN(rss)) return rss
  const heap = data.heap as Record<string, unknown> | undefined
  const nonHeap = data.non_heap as Record<string, unknown> | undefined
  if (heap != null && nonHeap != null && typeof heap === 'object' && typeof nonHeap === 'object') {
    const used = (Number(heap.used_bytes) || 0) + (Number(nonHeap.used_bytes) || 0)
    if (used > 0) return used
  }
  return null
}

/** Possible keys for allocation "size" (in order of preference). */
const SIZE_KEYS = ['size_bytes', 'bytes', 'size'] as const
/** Possible keys for allocation "label" (file/path/name). */
const LABEL_KEYS = ['file', 'path', 'name', 'location', 'label'] as const

function getSize(item: Record<string, unknown>): number | null {
  for (const key of SIZE_KEYS) {
    const v = item[key]
    if (typeof v === 'number' && !Number.isNaN(v)) return v
    if (typeof v === 'string') {
      const n = Number(v)
      if (!Number.isNaN(n)) return n
    }
  }
  return null
}

function getLabel(item: Record<string, unknown>): string {
  for (const key of LABEL_KEYS) {
    const v = item[key]
    if (typeof v === 'string' && v.trim()) return v.trim()
  }
  return String(JSON.stringify(item)).slice(0, 60)
}

/**
 * Extract and normalize allocations from debug/memory response.
 * Handles varying keys: tracemalloc_top_20, allocations, top_allocations, etc.
 * Each item can use file/path/name and size_bytes/bytes/size.
 */
function normalizeAllocations(data: Record<string, unknown> | null | undefined): Array<{ name: string; bytes: number; fullFile: string }> {
  if (!data || typeof data !== 'object') return []
  const raw = (data.tracemalloc_top_20 ?? data.allocations ?? data.top_allocations ?? data.top ?? data.items) as unknown
  if (!Array.isArray(raw)) return []
  const out: Array<{ name: string; bytes: number; fullFile: string }> = []
  for (const entry of raw) {
    if (entry == null || typeof entry !== 'object') continue
    const item = entry as Record<string, unknown>
    const size = getSize(item)
    if (size == null || size < 0) continue
    const fullFile = getLabel(item)
    out.push({
      name: shortFile(fullFile),
      bytes: size,
      fullFile,
    })
  }
  return out
}

export default function MemoryDashboard() {
  const [baseUrl, setBaseUrlState] = useState(getStoredBaseUrl)
  const [refreshSeconds, setRefreshSecondsState] = useState(getStoredRefreshSeconds)
  const [rssHistory, setRssHistory] = useState<Array<{ time: string; rss: number }>>([])

  const setBaseUrl = useCallback((url: string) => {
    const normalized = url.trim().replace(/\/$/, '') || DEFAULT_BASE_URL
    setBaseUrlState(normalized)
    try {
      localStorage.setItem(STORAGE_KEY, normalized)
    } catch {
      /* ignore */
    }
  }, [])

  const setRefreshSeconds = useCallback((seconds: number) => {
    setRefreshSecondsState(seconds)
    try {
      localStorage.setItem(REFRESH_STORAGE_KEY, String(seconds))
    } catch {
      /* ignore */
    }
  }, [])

  const refreshMs = refreshSeconds * 1000

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: ['debug-memory', baseUrl],
    queryFn: () => getDebugMemory(baseUrl),
    refetchInterval: refreshMs,
    enabled: !!baseUrl,
    staleTime: Math.max(0, refreshMs - 500),
  })

  useEffect(() => {
    const memory = getMemoryBytes(data ?? undefined)
    if (memory == null) return
    const ts = String(data?.timestamp_iso ?? data?.timestamp ?? '')
    const time = ts.slice(11, 19) || new Date().toISOString().slice(11, 19)
    setRssHistory((prev) => {
      const next = [...prev, { time, rss: memory }]
      if (next.length > RSS_HISTORY_MAX) return next.slice(1)
      return next
    })
  }, [data])

  const rssHistoryWithLatest = rssHistory

  const allocChartData = useMemo(() => normalizeAllocations(data ?? undefined), [data])

  return (
    <div>
      <h1 style={{ marginBottom: '1rem' }}>Memory debug</h1>
      <p style={{ color: '#666', marginBottom: '1rem', fontSize: '0.9rem' }}>
        Query the <code style={{ background: '#eee', padding: '0.1rem 0.3rem', borderRadius: 4 }}>/debug/memory</code> endpoint
        from a running service (e.g. rad-talos-agents). Enable with <code>ENABLE_DEBUG_MEMORY_ENDPOINT=true</code>.
      </p>

      <div style={{ marginBottom: '1.5rem', display: 'flex', flexWrap: 'wrap', gap: '1.5rem', alignItems: 'flex-end' }}>
        <div>
          <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}>
            API base URL
          </label>
          <select
            value={baseUrl}
            onChange={(e) => setBaseUrl(e.target.value)}
            style={{
              padding: '0.5rem 0.75rem',
              border: '1px solid #ccc',
              borderRadius: 6,
              fontSize: '0.9rem',
              minWidth: 220,
            }}
          >
            {BASE_URL_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}>
            Auto-refresh
          </label>
          <select
            value={refreshSeconds}
            onChange={(e) => setRefreshSeconds(Number(e.target.value))}
            style={{
              padding: '0.5rem 0.75rem',
              border: '1px solid #ccc',
              borderRadius: 6,
              fontSize: '0.9rem',
              minWidth: 120,
            }}
          >
            {REFRESH_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {error && (
        <div
          style={{
            backgroundColor: '#fee2e2',
            border: '1px solid #fca5a5',
            borderRadius: 8,
            padding: '1rem',
            color: '#991b1b',
            marginBottom: '1.5rem',
          }}
        >
          <strong>Error loading memory data:</strong>{' '}
          {error instanceof Error ? error.message : String(error)}
          {baseUrl && (
            <div style={{ marginTop: '0.5rem', fontSize: '0.875rem' }}>
              Ensure the service is running and <code>/debug/memory</code> is enabled. Cross-origin requests require CORS on the target server.
            </div>
          )}
        </div>
      )}

      {isLoading && !data && <div style={{ color: '#666' }}>Loading…</div>}

      {data && !('error' in data) && (
        <>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
              gap: '1rem',
              marginBottom: '1.5rem',
            }}
          >
            <div
              style={{
                backgroundColor: 'white',
                padding: '1rem',
                borderRadius: 8,
                boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
              }}
            >
              <div style={{ fontSize: '0.75rem', color: '#666', textTransform: 'uppercase', marginBottom: '0.25rem' }}>
                Memory
              </div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>{formatBytes(getMemoryBytes(data))}</div>
            </div>
            <div
              style={{
                backgroundColor: 'white',
                padding: '1rem',
                borderRadius: 8,
                boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
              }}
            >
              <div style={{ fontSize: '0.75rem', color: '#666', textTransform: 'uppercase', marginBottom: '0.25rem' }}>
                GC
              </div>
              <div style={{ fontSize: '1.25rem', fontWeight: 600 }}>
                {(() => {
                  const gc = data.gc as Record<string, unknown> | undefined
                  if (!gc) return '–'
                  const collectors = gc.collectors as Array<{ name?: string; collectionCount?: number; collectionTime?: number }> | undefined
                  if (Array.isArray(collectors) && collectors.length > 0) {
                    const totalCount = gc.total_collection_count as number | undefined
                    const totalMs = gc.total_collection_time_ms as number | undefined
                    const countStr = totalCount != null ? `${totalCount} collections` : ''
                    const timeStr = totalMs != null ? ` ${totalMs} ms` : ''
                    return countStr || timeStr ? `${countStr}${timeStr}`.trim() : collectors.map((c) => `${c.name ?? '?'}: ${c.collectionCount ?? 0}`).join(', ')
                  }
                  const gcCount = gc.gc_count
                  return Array.isArray(gcCount) ? gcCount.join(' / ') : gcCount != null ? String(gcCount) : '–'
                })()}
              </div>
            </div>
            <div
              style={{
                backgroundColor: 'white',
                padding: '1rem',
                borderRadius: 8,
                boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
              }}
            >
              <div style={{ fontSize: '0.75rem', color: '#666', textTransform: 'uppercase', marginBottom: '0.25rem' }}>
                GC garbage
              </div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
                {(data.gc as Record<string, unknown> | undefined)?.gc_garbage_len != null
                  ? String((data.gc as Record<string, unknown>).gc_garbage_len)
                  : '–'}
              </div>
            </div>
            <div
              style={{
                backgroundColor: 'white',
                padding: '1rem',
                borderRadius: 8,
                boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
              }}
            >
              <div style={{ fontSize: '0.75rem', color: '#666', textTransform: 'uppercase', marginBottom: '0.25rem' }}>
                Last update
              </div>
              <div style={{ fontSize: '0.9rem', color: '#666' }}>{data.timestamp_iso != null ? String(data.timestamp_iso) : (data.timestamp != null ? String(data.timestamp) : '–')}</div>
            </div>
          </div>

          {rssHistoryWithLatest.length > 0 && (
            <div
              style={{
                backgroundColor: 'white',
                borderRadius: 8,
                boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
                padding: '1rem',
                marginBottom: '1.5rem',
              }}
            >
              <h2 style={{ margin: '0 0 0.5rem', fontSize: '1rem', color: '#666' }}>Memory over time</h2>
              <div style={{ height: 280 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={rssHistoryWithLatest} margin={{ top: 5, right: 20, left: 10, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#eee" />
                    <XAxis dataKey="time" tick={{ fontSize: 11 }} />
                    <YAxis tick={{ fontSize: 11 }} tickFormatter={(v) => `${(v / 1e6).toFixed(0)}M`} />
                    <Tooltip
                      formatter={(value: number) => [formatBytes(value), 'Memory']}
                      labelFormatter={(_, payload) => payload[0]?.payload?.time ?? ''}
                    />
                    <Line
                      type="monotone"
                      dataKey="rss"
                      stroke="#3b82f6"
                      fill="rgba(59, 130, 246, 0.1)"
                      strokeWidth={2}
                      dot={false}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          {allocChartData.length > 0 && (
            <div
              style={{
                backgroundColor: 'white',
                borderRadius: 8,
                boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
                padding: '1rem',
                marginBottom: '1.5rem',
              }}
            >
              <h2 style={{ margin: '0 0 0.5rem', fontSize: '1rem', color: '#666' }}>
                Allocations
              </h2>
              <div style={{ height: 320 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={allocChartData}
                    layout="vertical"
                    margin={{ top: 5, right: 30, left: 0, bottom: 5 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" stroke="#eee" />
                    <XAxis type="number" tick={{ fontSize: 11 }} tickFormatter={(v) => `${(v / 1024).toFixed(0)} KB`} />
                    <YAxis type="category" dataKey="name" width={120} tick={{ fontSize: 10 }} />
                    <Tooltip
                      formatter={(value: number) => [formatBytes(value), 'Size']}
                      content={({ active, payload }) => {
                        if (!active || !payload?.length) return null
                        const raw = payload[0]?.payload as Record<string, unknown> | undefined
                        const bytes = typeof raw?.bytes === 'number' ? raw.bytes : Number(raw?.size_bytes ?? raw?.size ?? 0) || 0
                        const fullFile = typeof raw?.fullFile === 'string' ? raw.fullFile : String(raw?.file ?? raw?.path ?? raw?.name ?? '')
                        return (
                          <div
                            style={{
                              background: '#fff',
                              border: '1px solid #ccc',
                              borderRadius: 6,
                              padding: '0.5rem 0.75rem',
                              fontSize: '0.8rem',
                              maxWidth: 400,
                            }}
                          >
                            <div style={{ fontWeight: 600, marginBottom: 4 }}>{formatBytes(bytes)}</div>
                            <div style={{ color: '#666', wordBreak: 'break-all' }}>{fullFile || '–'}</div>
                          </div>
                        )
                      }}
                    />
                    <Bar dataKey="bytes" radius={[0, 4, 4, 0]}>
                      {allocChartData.map((_, i) => (
                        <Cell key={i} fill="rgba(59, 130, 246, 0.7)" />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          <p style={{ fontSize: '0.8rem', color: '#666' }}>
            {isFetching ? 'Updating…' : `Auto-refresh every ${refreshSeconds}s`}
            {' · '}
            <a href={baseUrl + '/debug/memory'} target="_blank" rel="noreferrer">
              Open raw JSON
            </a>
          </p>
        </>
      )}
    </div>
  )
}
