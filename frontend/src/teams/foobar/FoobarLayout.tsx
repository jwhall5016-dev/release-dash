import TeamLayout from '../../components/TeamLayout'

const links = [
  { to: '/foobar/releases', label: 'All Releases' },
  { to: '/foobar/live', label: 'Live Deployments' },
  { to: '/foobar/cmdb', label: 'CMDB' },
  { to: '/foobar/memory', label: 'Memory' },
]

export default function FoobarLayout() {
  return (
    <TeamLayout
      title="Foobar Team Release Dashboard"
      homeTo="/foobar"
      links={links}
    />
  )
}
