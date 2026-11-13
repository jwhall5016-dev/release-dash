/**
 * Compare semantic versions
 * Returns:
 * - negative if a < b
 * - 0 if a === b
 * - positive if a > b
 *
 * Base version (major.minor.patch) and prerelease segments are compared numerically
 * where applicable, so v1.127.9 < v1.127.11 and rc.9 < rc.10 < rc.11.
 *
 * Handles versions like:
 * - "1.125.0", "v1.127.11" (full versions: patch 11 > patch 9)
 * - "1.126.0-rc.5", "1.126.0-rc.11" (prerelease: rc.11 > rc.9)
 * - "2.0.0-beta.1"
 */
export function compareVersions(a: string, b: string): number {
  // Remove 'v' prefix if present
  const normalize = (v: string) => v.startsWith('v') ? v.substring(1) : v
  
  const versionA = normalize(a || '0.0.0')
  const versionB = normalize(b || '0.0.0')
  
  // Split into base version and pre-release identifier
  const parseVersion = (v: string) => {
    const parts = v.split('-')
    const base = parts[0]
    const prerelease = parts.length > 1 ? parts.slice(1).join('-') : null
    
    const numbers = base.split('.').map(n => {
      const num = parseInt(n, 10)
      return isNaN(num) ? 0 : num
    })
    
    // Ensure we have at least 3 parts (major.minor.patch)
    while (numbers.length < 3) {
      numbers.push(0)
    }
    
    return { numbers, prerelease }
  }
  
  const parsedA = parseVersion(versionA)
  const parsedB = parseVersion(versionB)
  
  // Compare base version numbers (major.minor.patch)
  for (let i = 0; i < 3; i++) {
    if (parsedA.numbers[i] !== parsedB.numbers[i]) {
      return parsedA.numbers[i] - parsedB.numbers[i]
    }
  }
  
  // If base versions are equal, compare pre-release identifiers
  // Versions without pre-release identifiers are considered newer
  if (parsedA.prerelease === null && parsedB.prerelease === null) {
    return 0
  }
  if (parsedA.prerelease === null) {
    return 1 // A is a release version, B is pre-release, so A > B
  }
  if (parsedB.prerelease === null) {
    return -1 // A is pre-release, B is release, so A < B
  }
  
  // Both have pre-release identifiers: compare with natural ordering
  // so that rc.9 < rc.10 < rc.11 (not lexicographic rc.1, rc.10, rc.11, rc.2, ... rc.9)
  return comparePrerelease(parsedA.prerelease, parsedB.prerelease)
}

/**
 * Compare pre-release strings with natural ordering for numeric segments.
 * e.g. rc.9 < rc.10 < rc.11, beta.2 < beta.10
 */
function comparePrerelease(a: string, b: string): number {
  const tokenize = (s: string): (string | number)[] => {
    const tokens: (string | number)[] = []
    let i = 0
    while (i < s.length) {
      const start = i
      if (/\d/.test(s[i])) {
        while (i < s.length && /\d/.test(s[i])) i++
        tokens.push(parseInt(s.slice(start, i), 10))
      } else {
        while (i < s.length && !/\d/.test(s[i])) i++
        const segment = s.slice(start, i)
        if (segment.length) tokens.push(segment)
      }
    }
    return tokens
  }
  const tokensA = tokenize(a)
  const tokensB = tokenize(b)
  const len = Math.max(tokensA.length, tokensB.length)
  for (let i = 0; i < len; i++) {
    const ta = tokensA[i]
    const tb = tokensB[i]
    if (ta === undefined) return -1
    if (tb === undefined) return 1
    if (typeof ta === 'number' && typeof tb === 'number') {
      if (ta !== tb) return ta - tb
    } else {
      const sa = String(ta)
      const sb = String(tb)
      const cmp = sa.localeCompare(sb)
      if (cmp !== 0) return cmp
    }
  }
  return 0
}
