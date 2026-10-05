/**
 * Formats a number to Vietnamese currency string format (xxx,yyy,zzz VNĐ or xxx,yyy,zzz).
 * If includeUnit is true (default), appends " VNĐ".
 * If includeUnit is false (e.g. when title/header already contains "VNĐ"), returns "xxx,yyy,zzz".
 */
export function formatVnd(amount: number | null | undefined, includeUnit = true): string {
  if (amount === null || amount === undefined || isNaN(amount)) {
    return includeUnit ? '0 VNĐ' : '0';
  }
  const formatted = Math.round(amount).toLocaleString('en-US');
  return includeUnit ? `${formatted} VNĐ` : formatted;
}
