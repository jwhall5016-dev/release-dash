import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getRelease, downloadArtifact, viewArtifact, Release } from '../services/api'
import { format } from 'date-fns'
import ReactMarkdown from 'react-markdown'
import { useState } from 'react'
import LiveDeploymentPanel from './LiveDeploymentPanel'

export default function ReleaseDetail() {
  const { repository, prNumber } = useParams<{ repository: string; prNumber: string }>()
  const prNum = parseInt(prNumber || '0', 10)

  const { data: release, isLoading } = useQuery({
    queryKey: ['release', repository, prNum],
    queryFn: () => getRelease(repository!, prNum),
    enabled: !!repository && !!prNumber,
  })

  if (isLoading) {
    return <div>Loading...</div>
  }

  if (!release) {
    return <div>Release not found</div>
  }

  const handleDownload = (filename: string) => {
    const url = downloadArtifact(release.repository, release.pr_number, filename, release.environment)
    window.open(url, '_blank')
  }

  return (
    <div>
      <h1 style={{ marginBottom: '2rem' }}>
        Release: {release.repository} - PR #{release.pr_number}
      </h1>

      {/* Release Info */}
      <div style={{
        backgroundColor: 'white',
        borderRadius: '8px',
        boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
        padding: '1.5rem',
        marginBottom: '2rem'
      }}>
        <h2 style={{ marginBottom: '1rem' }}>Release Information</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '1rem' }}>
          <div>
            <strong>Repository:</strong> {release.repository}
          </div>
          <div>
            <strong>PR Number:</strong> #{release.pr_number}
          </div>
          <div>
            <strong>Environment:</strong>{' '}
            <span style={{
              padding: '0.25rem 0.5rem',
              borderRadius: '4px',
              backgroundColor: release.environment === 'production' ? '#dc2626' : 
                             release.environment === 'staging' ? '#f59e0b' : '#3b82f6',
              color: 'white',
              fontSize: '0.875rem'
            }}>
              {release.environment}
            </span>
          </div>
          <div>
            <strong>Branch:</strong> <code>{release.branch}</code>
          </div>
          <div>
            <strong>Commit SHA:</strong> <code>{release.commit_sha}</code>
          </div>
          <div>
            <strong>Workflow Run ID:</strong> {release.workflow_run_id || 'N/A'}
          </div>
          <div>
            <strong>Created:</strong> {format(new Date(release.created_at), 'PPpp')}
          </div>
          <div>
            <strong>Updated:</strong> {format(new Date(release.updated_at), 'PPpp')}
          </div>
        </div>
      </div>

      <LiveDeploymentPanel
        repository={release.repository}
        commitSha={release.commit_sha}
        releaseEnvironment={release.environment}
      />

      {/* Artifacts */}
      <div style={{
        backgroundColor: 'white',
        borderRadius: '8px',
        boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
        padding: '1.5rem'
      }}>
        <h2 style={{ marginBottom: '1rem' }}>Artifacts</h2>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '1rem',
            border: '1px solid #eee',
            borderRadius: '4px'
          }}>
            <div>
              <strong>SBOM (CycloneDX)</strong>
              <p style={{ color: '#666', fontSize: '0.875rem', marginTop: '0.25rem' }}>
                {release.artifacts.sbom_cyclonedx}
              </p>
            </div>
            <button
              onClick={() => handleDownload('sbom-cyclonedx.json')}
              disabled={!release.artifacts.sbom_cyclonedx}
              style={{
                padding: '0.5rem 1rem',
                backgroundColor: release.artifacts.sbom_cyclonedx ? '#3b82f6' : '#9ca3af',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: release.artifacts.sbom_cyclonedx ? 'pointer' : 'not-allowed',
                opacity: release.artifacts.sbom_cyclonedx ? 1 : 0.6
              }}
            >
              Download
            </button>
          </div>

          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '1rem',
            border: '1px solid #eee',
            borderRadius: '4px'
          }}>
            <div>
              <strong>SBOM (SPDX)</strong>
              <p style={{ color: '#666', fontSize: '0.875rem', marginTop: '0.25rem' }}>
                {release.artifacts.sbom_spdx}
              </p>
            </div>
            <button
              onClick={() => handleDownload('sbom-spdx.json')}
              disabled={!release.artifacts.sbom_spdx}
              style={{
                padding: '0.5rem 1rem',
                backgroundColor: release.artifacts.sbom_spdx ? '#3b82f6' : '#9ca3af',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: release.artifacts.sbom_spdx ? 'pointer' : 'not-allowed',
                opacity: release.artifacts.sbom_spdx ? 1 : 0.6
              }}
            >
              Download
            </button>
          </div>

          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '1rem',
            border: '1px solid #eee',
            borderRadius: '4px'
          }}>
            <div>
              <strong>SBOM Summary</strong>
              <p style={{ color: '#666', fontSize: '0.875rem', marginTop: '0.25rem' }}>
                {release.artifacts.sbom_summary}
              </p>
            </div>
            <button
              onClick={() => handleDownload('sbom-summary.txt')}
              disabled={!release.artifacts.sbom_summary}
              style={{
                padding: '0.5rem 1rem',
                backgroundColor: release.artifacts.sbom_summary ? '#3b82f6' : '#9ca3af',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: release.artifacts.sbom_summary ? 'pointer' : 'not-allowed',
                opacity: release.artifacts.sbom_summary ? 1 : 0.6
              }}
            >
              Download
            </button>
          </div>

          <ReleaseNotesSection 
            release={release} 
            onDownload={() => handleDownload('release-notes.md')}
          />

          {release.artifacts.trivy_report && (
            <TrivyReportSection 
              release={release}
              onDownload={() => {
                const trivyPath = release.artifacts.trivy_report;
                if (trivyPath) {
                  const filename = trivyPath.includes('/')
                    ? trivyPath.split('/').pop() || 'trivy-report.html'
                    : trivyPath;
                  handleDownload(filename);
                }
              }}
            />
          )}
        </div>
      </div>
    </div>
  )
}

// Release Notes Component with inline markdown display
function ReleaseNotesSection({ release, onDownload }: { release: Release, onDownload: () => void }) {
  const [isExpanded, setIsExpanded] = useState(false)
  const [content, setContent] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const handleToggle = async () => {
    if (!isExpanded && !content) {
      setIsLoading(true)
      try {
        const markdown = await viewArtifact(
          release.repository,
          release.pr_number,
          'release-notes.md',
          release.environment
        )
        setContent(markdown)
      } catch (error) {
        console.error('Failed to load release notes:', error)
        setContent('*Failed to load release notes*')
      } finally {
        setIsLoading(false)
      }
    }
    setIsExpanded(!isExpanded)
  }

  return (
    <div style={{
      border: '1px solid #eee',
      borderRadius: '4px',
      overflow: 'hidden'
    }}>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '1rem',
        backgroundColor: isExpanded ? '#f9fafb' : 'white',
        cursor: 'pointer'
      }}
      onClick={handleToggle}
      >
        <div style={{ flex: 1 }}>
          <strong>Release Notes</strong>
          <p style={{ color: '#666', fontSize: '0.875rem', marginTop: '0.25rem' }}>
            {release.artifacts.release_notes}
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button
            onClick={(e) => {
              e.stopPropagation()
              onDownload()
            }}
            disabled={!release.artifacts.release_notes}
            style={{
              padding: '0.5rem 1rem',
              backgroundColor: release.artifacts.release_notes ? '#6b7280' : '#9ca3af',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: release.artifacts.release_notes ? 'pointer' : 'not-allowed',
              opacity: release.artifacts.release_notes ? 1 : 0.6
            }}
          >
            Download
          </button>
          <button
            style={{
              padding: '0.5rem 1rem',
              backgroundColor: '#3b82f6',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer'
            }}
          >
            {isExpanded ? 'Hide' : 'View'}
          </button>
        </div>
      </div>
      {isExpanded && (
        <div style={{
          padding: '1.5rem',
          backgroundColor: 'white',
          borderTop: '1px solid #eee',
          maxHeight: '600px',
          overflowY: 'auto'
        }}>
          {isLoading ? (
            <div style={{ padding: '2rem', textAlign: 'center', color: '#6b7280' }}>Loading release notes...</div>
          ) : content ? (
            <div style={{
              lineHeight: '1.8',
              color: '#374151',
              fontSize: '0.95rem'
            }}>
              <ReactMarkdown
                components={{
                  h1: ({node, ...props}) => <h1 style={{ fontSize: '1.75rem', marginTop: '1.5rem', marginBottom: '1rem', fontWeight: 'bold' }} {...props} />,
                  h2: ({node, ...props}) => <h2 style={{ fontSize: '1.5rem', marginTop: '1.25rem', marginBottom: '0.75rem', fontWeight: 'bold' }} {...props} />,
                  h3: ({node, ...props}) => <h3 style={{ fontSize: '1.25rem', marginTop: '1rem', marginBottom: '0.5rem', fontWeight: '600' }} {...props} />,
                  p: ({node, ...props}) => <p style={{ marginBottom: '1rem' }} {...props} />,
                  ul: ({node, ...props}) => <ul style={{ marginBottom: '1rem', paddingLeft: '1.5rem', listStyleType: 'disc' }} {...props} />,
                  ol: ({node, ...props}) => <ol style={{ marginBottom: '1rem', paddingLeft: '1.5rem', listStyleType: 'decimal' }} {...props} />,
                  li: ({node, ...props}) => <li style={{ marginBottom: '0.5rem' }} {...props} />,
                  code: ({node, inline, ...props}: any) => 
                    inline ? (
                      <code style={{ 
                        backgroundColor: '#f3f4f6', 
                        padding: '0.125rem 0.375rem', 
                        borderRadius: '0.25rem',
                        fontSize: '0.875em',
                        fontFamily: 'monospace'
                      }} {...props} />
                    ) : (
                      <code style={{ 
                        display: 'block',
                        backgroundColor: '#f3f4f6', 
                        padding: '1rem', 
                        borderRadius: '0.5rem',
                        fontSize: '0.875em',
                        fontFamily: 'monospace',
                        overflowX: 'auto',
                        marginBottom: '1rem'
                      }} {...props} />
                    ),
                  blockquote: ({node, ...props}) => <blockquote style={{ 
                    borderLeft: '4px solid #3b82f6', 
                    paddingLeft: '1rem', 
                    marginLeft: '0',
                    marginBottom: '1rem',
                    fontStyle: 'italic',
                    color: '#6b7280'
                  }} {...props} />,
                  a: ({node, ...props}) => <a style={{ color: '#3b82f6', textDecoration: 'underline' }} target="_blank" rel="noopener noreferrer" {...props} />,
                }}
              >
                {content}
              </ReactMarkdown>
            </div>
          ) : (
            <div style={{ padding: '2rem', textAlign: 'center', color: '#6b7280' }}>No content available</div>
          )}
        </div>
      )}
    </div>
  )
}

// Trivy Report Component with inline HTML display
function TrivyReportSection({ release, onDownload }: { release: Release, onDownload: () => void }) {
  const [isExpanded, setIsExpanded] = useState(false)
  const [content, setContent] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const handleToggle = async () => {
    if (!isExpanded && !content) {
      setIsLoading(true)
      try {
        const trivyPath = release.artifacts.trivy_report
        if (trivyPath) {
          const filename = trivyPath.includes('/')
            ? trivyPath.split('/').pop() || 'trivy-report.html'
            : trivyPath
          const html = await viewArtifact(
            release.repository,
            release.pr_number,
            filename,
            release.environment
          )
          setContent(html)
        }
      } catch (error) {
        console.error('Failed to load Trivy report:', error)
        setContent('<p>Failed to load security report</p>')
      } finally {
        setIsLoading(false)
      }
    }
    setIsExpanded(!isExpanded)
  }

  return (
    <div style={{
      border: '1px solid #eee',
      borderRadius: '4px',
      overflow: 'hidden'
    }}>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '1rem',
        backgroundColor: isExpanded ? '#f9fafb' : 'white',
        cursor: 'pointer'
      }}
      onClick={handleToggle}
      >
        <div style={{ flex: 1 }}>
          <strong>Trivy Security Report</strong>
          <p style={{ color: '#666', fontSize: '0.875rem', marginTop: '0.25rem' }}>
            {release.artifacts.trivy_report}
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button
            onClick={(e) => {
              e.stopPropagation()
              onDownload()
            }}
            disabled={!release.artifacts.trivy_report}
            style={{
              padding: '0.5rem 1rem',
              backgroundColor: release.artifacts.trivy_report ? '#6b7280' : '#9ca3af',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: release.artifacts.trivy_report ? 'pointer' : 'not-allowed',
              opacity: release.artifacts.trivy_report ? 1 : 0.6
            }}
          >
            Download
          </button>
          <button
            style={{
              padding: '0.5rem 1rem',
              backgroundColor: '#3b82f6',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer'
            }}
          >
            {isExpanded ? 'Hide' : 'View'}
          </button>
        </div>
      </div>
      {isExpanded && (
        <div style={{
          padding: '0',
          backgroundColor: 'white',
          borderTop: '1px solid #eee',
          height: '800px',
          overflow: 'hidden'
        }}>
          {isLoading ? (
            <div style={{ padding: '2rem', textAlign: 'center' }}>Loading security report...</div>
          ) : content ? (
            <iframe
              srcDoc={content}
              style={{
                width: '100%',
                height: '100%',
                border: 'none'
              }}
              title="Trivy Security Report"
            />
          ) : (
            <div style={{ padding: '2rem' }}>No content available</div>
          )}
        </div>
      )}
    </div>
  )
}
