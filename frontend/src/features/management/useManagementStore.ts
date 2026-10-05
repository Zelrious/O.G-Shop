import { useEffect, useState } from 'react';
import { managementStore } from './managementStore';

export function useManagementStore() {
  const [, setTick] = useState(0);

  useEffect(() => {
    const unsubscribe = managementStore.subscribe(() => {
      setTick((t) => t + 1);
    });
    return unsubscribe;
  }, []);

  return {
    // KTV data
    escrows: managementStore.getEscrows(),
    users: managementStore.getNormalUsers(),
    disputes: managementStore.getDisputes(),
    kycList: managementStore.getKycList(),
    complaints: managementStore.getComplaints(),
    grantedVouchers: managementStore.getGrantedVouchers(),

    // Admin data
    emergencyAlerts: managementStore.getEmergencyAlerts(),
    unreadAlertsCount: managementStore.getUnreadAlertsCount(),
    ktvAccounts: managementStore.getKtvAccounts(),
    auditLogs: managementStore.getAuditLogs(),
    systemFees: managementStore.getSystemFees(),
    vouchers: managementStore.getVouchers(),
    broadcasts: managementStore.getBroadcasts(),

    // Actions
    lockUser: managementStore.lockUser.bind(managementStore),
    unlockUser: managementStore.unlockUser.bind(managementStore),
    resolveDispute: managementStore.resolveDispute.bind(managementStore),
    approveKyc: managementStore.approveKyc.bind(managementStore),
    rejectKyc: managementStore.rejectKyc.bind(managementStore),
    resolveComplaint: managementStore.resolveComplaint.bind(managementStore),
    escalateComplaintToAdmin: managementStore.escalateComplaintToAdmin.bind(managementStore),
    grantVoucher: managementStore.grantVoucher.bind(managementStore),
    revokeVoucher: managementStore.revokeVoucher.bind(managementStore),

    acknowledgeAlert: managementStore.acknowledgeAlert.bind(managementStore),
    resolveAlert: managementStore.resolveAlert.bind(managementStore),
    createKtvAccount: managementStore.createKtvAccount.bind(managementStore),
    updateKtvAccount: managementStore.updateKtvAccount.bind(managementStore),
    toggleKtvStatus: managementStore.toggleKtvStatus.bind(managementStore),
    updateSystemFees: managementStore.updateSystemFees.bind(managementStore),
    createVoucher: managementStore.createVoucher.bind(managementStore),
    toggleVoucherStatus: managementStore.toggleVoucherStatus.bind(managementStore),
    sendBroadcast: managementStore.sendBroadcast.bind(managementStore),

    exportUserDataCsv: managementStore.exportUserDataCsv.bind(managementStore),
    exportRevenueDataCsv: managementStore.exportRevenueDataCsv.bind(managementStore),
    exportTopSearchCsv: managementStore.exportTopSearchCsv.bind(managementStore),
    exportReturnComplaintCsv: managementStore.exportReturnComplaintCsv.bind(managementStore),
  };
}
