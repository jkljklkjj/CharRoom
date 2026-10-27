/** Prefix sums are rebuilt only when rows/heights change; scrolling uses binary search. */
export function virtualLayout(keys: readonly string[], heights: ReadonlyMap<string, number>, estimate = 100) {
  const offsets = [0]
  for (const key of keys) offsets.push(offsets[offsets.length - 1] + (heights.get(key) ?? estimate))
  const total = offsets[offsets.length - 1]
  function indexAt(position: number) {
    let low = 0, high = keys.length
    while (low < high) {
      const middle = (low + high) >>> 1
      if (offsets[middle + 1] <= position) low = middle + 1
      else high = middle
    }
    return Math.min(low, Math.max(0, keys.length - 1))
  }
  return { offsets, total, indexAt,
    range(top: number, height: number, overscan = 500) {
      return { start: indexAt(Math.max(0, top - overscan)), end: Math.min(keys.length, indexAt(top + height + overscan) + 1) }
    }
  }
}
