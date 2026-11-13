import axios from 'axios'

// Use relative URLs since frontend is served from the same origin as the backend
const API_BASE_URL = ''

/** Mentat team API. Other teams mount their own under /{team}/api. */
export const MENTAT_API_BASE = '/mentat/api'
export const FOOBAR_API_BASE = '/foobar/api'

export interface Release {
  id: number
  repository: string
  pr_number: number
  environment: string
  commit_sha: string
  branch: string
  workflow_run_id: string | null
  artifacts: {
    sbom_cyclonedx: string
    sbom_spdx: string
    sbom_summary: string
    release_notes: string
    trivy_report?: string
  }
  release_tag?: string
  created_at: string
  updated_at: string
}

export interface Stats {
  totalReleases: number
  repositories: string[]
  environments: string[]
}

/** Response from GET /debug/memory. Shape can vary by service; known keys are optional. */
export type DebugMemorySnapshot = Record<string, unknown> & {
  timestamp?: number
  timestamp_iso?: string
  rss_bytes?: number | null
  tracemalloc_traced?: boolean
  tracemalloc_top_20?: Array<Record<string, unknown>>
  gc?: { gc_count?: number[]; gc_garbage_len?: number }
  objgraph?: Record<string, number | null>
}

/** Fetch memory debug snapshot from a configurable base URL (e.g. rad-talos-agents). */
export const getDebugMemory = async (baseUrl: string): Promise<DebugMemorySnapshot> => {
  const url = baseUrl.replace(/\/$/, '') + '/debug/memory'
  const response = await axios.get<DebugMemorySnapshot>(url, { timeout: 10000 })
  return response.data
}

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Add request interceptor for debugging
api.interceptors.request.use(
  (config) => {
    console.log('API Request:', config.method?.toUpperCase(), config.url, config.params)
    return config
  },
  (error) => {
    console.error('API Request Error:', error)
    return Promise.reject(error)
  }
)

// Add response interceptor for debugging
api.interceptors.response.use(
  (response) => {
    console.log('API Response:', response.status, response.config.url, response.data)
    return response
  },
  (error) => {
    console.error('API Response Error:', error.response?.status, error.response?.data, error.message)
    return Promise.reject(error)
  }
)

async function fetchReleases(
  base: string,
  repository?: string,
  environment?: string,
  limit = 50,
  offset = 0
): Promise<Release[]> {
  const response = await api.get(`${base}/releases`, {
    params: { repository, environment, limit, offset },
  })
  return response.data
}

export const getReleases = (
  repository?: string,
  environment?: string,
  limit = 50,
  offset = 0
) => fetchReleases(MENTAT_API_BASE, repository, environment, limit, offset)

export const getRelease = async (
  repository: string,
  prNumber: number,
  environment?: string
): Promise<Release> => {
  const response = await api.get(`${MENTAT_API_BASE}/releases/${repository}/${prNumber}`, {
    params: { environment },
  })
  return response.data
}

async function fetchStats(base: string): Promise<Stats> {
  const response = await api.get(`${base}/stats`)
  return response.data
}

export const getStats = () => fetchStats(MENTAT_API_BASE)

export const getFoobarReleases = (
  repository?: string,
  environment?: string,
  limit = 50,
  offset = 0
) => fetchReleases(FOOBAR_API_BASE, repository, environment, limit, offset)

export const getFoobarStats = () => fetchStats(FOOBAR_API_BASE)

export interface LiveServiceDescriptor {
  id: string
  label: string
  repository: string
  version_path: string
  urls: Record<string, string>
}

export interface LiveServicesCatalog {
  environments: string[]
  services: LiveServiceDescriptor[]
}

export interface LiveVersion {
  service: string
  label: string
  repository: string
  environment: string
  url?: string
  build_hash?: string
  build_date?: string
  status: 'ok' | 'unknown' | 'unreachable' | 'not_configured' | 'error' | string
  error?: string
}

const VERSION_FETCH_TIMEOUT_MS = 8_000

async function fetchLiveServicesCatalog(base: string): Promise<LiveServicesCatalog> {
  const response = await api.get(`${base}/live-services`)
  return response.data
}

export const getLiveServicesCatalog = () => fetchLiveServicesCatalog(MENTAT_API_BASE)

export const getFoobarLiveServicesCatalog = () => fetchLiveServicesCatalog(FOOBAR_API_BASE)

/**
 * Fetch a service's /version (or /version.json) from the user's browser.
 * Relies on the user having network access (VPN, etc.) and the endpoint allowing CORS.
 */
export async function fetchLiveVersionFromBrowser(
  service: LiveServiceDescriptor,
  environment: string
): Promise<LiveVersion> {
  const result: LiveVersion = {
    service: service.id,
    label: service.label,
    repository: service.repository,
    environment,
    status: 'not_configured',
  }

  const baseUrl = service.urls?.[environment]
  if (!baseUrl?.trim()) {
    result.error = `No URL configured for ${service.id} / ${environment}`
    return result
  }

  let path = service.version_path || '/version'
  if (!path.startsWith('/')) path = `/${path}`
  const url = `${baseUrl.trim().replace(/\/$/, '')}${path}`
  result.url = url

  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), VERSION_FETCH_TIMEOUT_MS)

  try {
    const response = await fetch(url, {
      method: 'GET',
      signal: controller.signal,
      credentials: 'omit',
    })

    if (!response.ok) {
      result.status = response.status === 401 || response.status === 403 ? 'error' : 'unreachable'
      result.error = `HTTP ${response.status}`
      return result
    }

    const json = (await response.json()) as Record<string, unknown>
    const buildHash = firstString(json, 'buildHash', 'build_hash', 'version')
    const buildDate = firstString(json, 'buildDate', 'build_date')
    result.build_hash = buildHash
    result.build_date = buildDate

    if (!buildHash || buildHash.toLowerCase() === 'unknown') {
      result.status = 'unknown'
      result.error = 'Version endpoint did not return a usable build hash'
    } else {
      result.status = 'ok'
    }
    return result
  } catch (e) {
    result.status = 'unreachable'
    if (e instanceof DOMException && e.name === 'AbortError') {
      result.error = 'Timed out'
    } else {
      const msg = e instanceof Error ? e.message : String(e)
      result.error =
        msg === 'Failed to fetch'
          ? 'CORS blocked or unreachable (Network may still show 200 without Access-Control-Allow-Origin)'
          : msg
    }
    return result
  } finally {
    window.clearTimeout(timer)
  }
}

function firstString(
  json: Record<string, unknown>,
  ...keys: string[]
): string | undefined {
  for (const key of keys) {
    const value = json[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return undefined
}

/** Browser-side: catalog from backend, version HTTP from the user's browser. */
export const getLiveVersionsByEnvironment = async (
  environment: string
): Promise<LiveVersion[]> => {
  const catalog = await getLiveServicesCatalog()
  return Promise.all(
    catalog.services.map((service) => fetchLiveVersionFromBrowser(service, environment))
  )
}

/** Browser-side: all configured environments for one repository's service. */
export const getLiveVersionsByRepository = async (
  repository: string
): Promise<LiveVersion[]> => {
  const catalog = await getLiveServicesCatalog()
  const service = catalog.services.find((s) => s.repository === repository)
  if (!service) return []
  return Promise.all(
    catalog.environments.map((environment) =>
      fetchLiveVersionFromBrowser(service, environment)
    )
  )
}

export interface CmdbWriteResult {
  path: string
  branch?: string
  mode?: string
  pr_url?: string
  pr_number?: number
  html_url?: string
}

export interface CmdbItem {
  name: string
  category: string
  ci_number: string
  parent: string | null
  status: string
  notes?: string
  doc_type?: string | null
  revision?: number | null
  git_path?: string
  html_url?: string
}

export class CmdbNotEnabledError extends Error {
  constructor(message = 'CMDB is not enabled') {
    super(message)
    this.name = 'CmdbNotEnabledError'
  }
}

export class CmdbNotImplementedError extends Error {
  constructor(message = 'CMDB GitHub integration is not implemented yet') {
    super(message)
    this.name = 'CmdbNotImplementedError'
  }
}

function readCmdbItems(data: unknown): CmdbItem[] {
  const payload = data as { items?: CmdbItem[] } | CmdbItem[]
  if (Array.isArray(payload)) return payload
  return payload.items ?? []
}

export const getFoobarCmdbItems = async (): Promise<CmdbItem[]> => {
  const response = await api.get(`${FOOBAR_API_BASE}/cmdb/items`)
  return readCmdbItems(response.data)
}

function rethrowCmdbHttpError(error: unknown): never {
  if (axios.isAxiosError(error) && error.response?.status === 503) {
    throw new CmdbNotEnabledError(
      typeof error.response.data?.error === 'string'
        ? error.response.data.error
        : undefined
    )
  }
  if (axios.isAxiosError(error) && error.response?.status === 501) {
    throw new CmdbNotImplementedError(
      typeof error.response.data?.error === 'string'
        ? error.response.data.error
        : undefined
    )
  }
  if (axios.isAxiosError(error) && typeof error.response?.data?.error === 'string') {
    throw new Error(error.response.data.error)
  }
  throw error
}

export const getCmdbSchema = async (): Promise<Record<string, unknown>> => {
  const response = await api.get(`${MENTAT_API_BASE}/cmdb/schema`)
  return response.data
}

/** List CMDB items. Throws CmdbNotEnabledError (503) until CMDB_ENABLED is on. */
export const getCmdbItems = async (): Promise<CmdbItem[]> => {
  try {
    const response = await api.get(`${MENTAT_API_BASE}/cmdb/items`)
    return readCmdbItems(response.data)
  } catch (error) {
    rethrowCmdbHttpError(error)
  }
}

export const refreshCmdbCatalog = async (): Promise<CmdbItem[]> => {
  try {
    const response = await api.post(`${MENTAT_API_BASE}/cmdb/refresh`)
    return readCmdbItems(response.data)
  } catch (error) {
    rethrowCmdbHttpError(error)
  }
}

export const getCmdbItem = async (ciNumber: string): Promise<CmdbItem> => {
  try {
    const response = await api.get(`${MENTAT_API_BASE}/cmdb/items/${encodeURIComponent(ciNumber)}`)
    return response.data
  } catch (error) {
    rethrowCmdbHttpError(error)
  }
}

export const createCmdbItem = async (item: CmdbItem): Promise<CmdbWriteResult> => {
  try {
    const response = await api.post(`${MENTAT_API_BASE}/cmdb/items`, item)
    return response.data
  } catch (error) {
    rethrowCmdbHttpError(error)
  }
}

export const updateCmdbItem = async (
  ciNumber: string,
  item: CmdbItem,
  baseSha?: string
): Promise<unknown> => {
  try {
    const response = await api.put(`${MENTAT_API_BASE}/cmdb/items/${encodeURIComponent(ciNumber)}`, item, {
      params: baseSha ? { baseSha } : undefined,
    })
    return response.data
  } catch (error) {
    rethrowCmdbHttpError(error)
  }
}

/** True if either hash is a prefix of the other (short SHA vs full SHA). */
export const hashesMatch = (a?: string | null, b?: string | null): boolean => {
  if (!a || !b) return false
  const left = a.trim().toLowerCase()
  const right = b.trim().toLowerCase()
  if (!left || !right || left === 'unknown' || right === 'unknown') return false
  return left === right || left.startsWith(right) || right.startsWith(left)
}

export const findReleaseByBuildHash = (
  releases: Release[],
  repository: string,
  buildHash: string,
  preferredEnvironment?: string
): Release | undefined => {
  const matches = releases.filter(
    (r) =>
      r.repository === repository &&
      hashesMatch(r.commit_sha, buildHash)
  )
  if (matches.length === 0) return undefined
  if (preferredEnvironment) {
    const envMatch = matches.find((r) => r.environment === preferredEnvironment)
    if (envMatch) return envMatch
  }
  // Prefer production, then staging, then newest by created_at
  const rank = (env: string) =>
    env === 'production' ? 0 : env === 'staging' ? 1 : 2
  return [...matches].sort((a, b) => {
    const envDiff = rank(a.environment) - rank(b.environment)
    if (envDiff !== 0) return envDiff
    return new Date(b.created_at).getTime() - new Date(a.created_at).getTime()
  })[0]
}

export const downloadArtifact = (
  repository: string,
  prNumber: number,
  filename: string,
  environment?: string
): string => {
  const params = environment ? `?environment=${environment}` : ''
  return `${API_BASE_URL}${MENTAT_API_BASE}/artifacts/${repository}/${prNumber}/download/${filename}${params}`
}

export const viewArtifact = async (
  repository: string,
  prNumber: number,
  filename: string,
  environment?: string
): Promise<string> => {
  const params = new URLSearchParams()
  if (environment) params.append('environment', environment)
  const response = await api.get(
    `${MENTAT_API_BASE}/artifacts/${repository}/${prNumber}/view/${filename}`,
    { params }
  )
  return response.data
}
