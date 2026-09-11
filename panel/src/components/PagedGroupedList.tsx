import type { Timestamp } from 'firebase/firestore'
import { useState, type ReactNode } from 'react'
import { groupByDate } from '../format'

interface PagedGroupedListProps<T> {
  items: T[]
  getDate: (item: T) => Timestamp | undefined
  renderItem: (item: T) => ReactNode
  emptyText: string
  pageSize?: number
}

export default function PagedGroupedList<T>({
  items,
  getDate,
  renderItem,
  emptyText,
  pageSize = 10,
}: PagedGroupedListProps<T>) {
  const [page, setPage] = useState(0)

  if (items.length === 0) {
    return <p className="muted">{emptyText}</p>
  }

  const totalPages = Math.max(1, Math.ceil(items.length / pageSize))
  const safePage = Math.min(page, totalPages - 1)
  const pageItems = items.slice(safePage * pageSize, safePage * pageSize + pageSize)
  const groups = groupByDate(pageItems, getDate)

  return (
    <div>
      <div className="grouped-list">
        {groups.map((group) => (
          <div className="log-group" key={group.label}>
            <div className="log-group-header static">{group.label}</div>
            <ul className="log-list">{group.items.map(renderItem)}</ul>
          </div>
        ))}
      </div>
      {totalPages > 1 && (
        <div className="pager">
          <button type="button" disabled={safePage === 0} onClick={() => setPage(safePage - 1)}>
            ← Önceki
          </button>
          <span>
            {safePage + 1} / {totalPages}
          </span>
          <button type="button" disabled={safePage >= totalPages - 1} onClick={() => setPage(safePage + 1)}>
            Sonraki →
          </button>
        </div>
      )}
    </div>
  )
}
