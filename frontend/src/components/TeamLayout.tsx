import { useEffect } from 'react'
import { Link, Outlet } from 'react-router-dom'

export interface TeamNavLink {
  to: string
  label: string
}

interface TeamLayoutProps {
  title: string
  homeTo: string
  links: TeamNavLink[]
}

const brandLinkStyle = { color: 'white', textDecoration: 'none', fontSize: '1.5rem', fontWeight: 'bold' } as const
const navLinkStyle = { color: 'white', textDecoration: 'none' } as const

/** Shared chrome for a team site. Each team supplies its own title and links. */
export default function TeamLayout({ title, homeTo, links }: TeamLayoutProps) {
  useEffect(() => {
    document.title = title
  }, [title])

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <header style={{
        backgroundColor: '#1a1a1a',
        color: 'white',
        padding: '1rem 2rem',
        boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
      }}>
        <nav style={{ display: 'flex', gap: '2rem', alignItems: 'center' }}>
          <Link to={homeTo} style={brandLinkStyle}>
            {title}
          </Link>
          {links.map((link) => (
            <Link key={link.to} to={link.to} style={navLinkStyle}>
              {link.label}
            </Link>
          ))}
        </nav>
      </header>
      <main style={{ flex: 1, padding: '2rem', maxWidth: '1400px', width: '100%', margin: '0 auto' }}>
        <Outlet />
      </main>
      <footer style={{
        backgroundColor: '#f5f5f5',
        padding: '1rem 2rem',
        textAlign: 'center',
        color: '#666',
        borderTop: '1px solid #ddd'
      }}>
        <p>Release Dashboard - Centralized artifact management</p>
      </footer>
    </div>
  )
}
