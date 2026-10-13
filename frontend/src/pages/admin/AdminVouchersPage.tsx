import { useAuth } from '../../features/auth';
import { AdminVoucherWorkbench } from '../../features/vouchers/AdminVoucherWorkbench';
export function AdminVouchersPage() { const { user } = useAuth(); return <AdminVoucherWorkbench key={user?.userId} />; }
