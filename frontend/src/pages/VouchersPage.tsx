import { useAuth } from '../features/auth';
import { VoucherWallet } from '../features/vouchers/VoucherWallet';
export function VouchersPage() { const { user } = useAuth(); return <VoucherWallet key={user?.userId} />; }
