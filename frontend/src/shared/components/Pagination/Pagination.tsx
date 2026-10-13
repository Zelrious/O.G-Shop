import { Button } from '../Button/Button';
import { Select } from '../Select/Select';
export function Pagination({ total, page, totalPages, onPage, disabled, size, onSize }: { total: number; page: number; totalPages: number; onPage: (page: number) => void; disabled?: boolean; size?: number; onSize?: (size: number) => void }) {
  return <nav className="og-pagination" aria-label="Phân trang">
    {onSize && <Select label="Số tin mỗi trang" size="sm" value={String(size)} options={[10, 20, 50].map(n => ({ value: String(n), label: String(n) }))} disabled={disabled} onChange={v => onSize(Number(v))} />}
    {total === 0 ? <span>0 kết quả</span> : <div><Button type="button" variant="ghost" size="sm" disabled={disabled || page === 0} onClick={() => onPage(page - 1)}>Trang trước</Button><span aria-live="polite">{page + 1}/{Math.max(1, totalPages)}</span><Button type="button" variant="ghost" size="sm" disabled={disabled || page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Trang sau</Button></div>}
  </nav>;
}
