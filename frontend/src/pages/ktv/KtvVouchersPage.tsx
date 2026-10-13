import { useAuth } from '../../features/auth';
import { KtvVoucherWorkbench } from '../../features/vouchers/KtvVoucherWorkbench';
export function KtvVouchersPage() { const { user } = useAuth(); return <KtvVoucherWorkbench key={user?.userId} />; }
