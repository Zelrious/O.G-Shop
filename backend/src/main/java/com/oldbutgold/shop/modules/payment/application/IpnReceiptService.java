package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentIpnRawReceiptEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentIpnRawReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class IpnReceiptService {

    private final PaymentIpnRawReceiptRepository receiptRepository;

    public IpnReceiptService(PaymentIpnRawReceiptRepository receiptRepository) {
        this.receiptRepository = receiptRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentIpnRawReceiptEntity createInitialReceipt(VnPayQueryParser.ParseResult parseResult, String ipAddress, Instant now) {
        PaymentIpnRawReceiptEntity receipt = new PaymentIpnRawReceiptEntity(
                parseResult.rawQuery(),
                parseResult.originalLength(),
                parseResult.truncated(),
                parseResult.sanitized(),
                parseResult.sha256Hex(),
                ipAddress,
                now
        );
        return receiptRepository.save(receipt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordForensicFields(Long receiptId, String txnRef, String txnNo, String amountText) {
        receiptRepository.findById(receiptId).ifPresent(r -> {
            String safeRef = sanitizeForensic(txnRef, 100);
            String safeTxnNo = sanitizeForensic(txnNo, 100);
            String safeAmount = sanitizeForensic(amountText, 255);
            r.setForensicFields(safeRef, safeTxnNo, safeAmount);
            receiptRepository.save(r);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markMalformed(Long receiptId, String error) {
        receiptRepository.findById(receiptId).ifPresent(r -> {
            r.markMalformed(error);
            receiptRepository.save(r);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSignatureInvalid(Long receiptId) {
        receiptRepository.findById(receiptId).ifPresent(r -> {
            r.markSignatureInvalid();
            receiptRepository.save(r);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSignatureValidUnlinked(Long receiptId) {
        receiptRepository.findById(receiptId).ifPresent(r -> {
            r.markSignatureValidUnlinked();
            receiptRepository.save(r);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessingFailed(Long receiptId, String error) {
        receiptRepository.findById(receiptId).ifPresent(r -> {
            r.markProcessingFailed(error);
            receiptRepository.save(r);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetryableError(Long receiptId, String error) {
        receiptRepository.findById(receiptId).ifPresent(r -> {
            r.markRetryableError(error);
            receiptRepository.save(r);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLinkedAndProcessed(Long receiptId) {
        receiptRepository.findById(receiptId).ifPresent(r -> {
            r.markLinked();
            r.markProcessed();
            receiptRepository.save(r);
        });
    }

    private String sanitizeForensic(String val, int maxLen) {
        if (val == null) return null;
        if (val.contains("\0")) {
            val = val.replace("\0", "");
        }
        if (val.length() > maxLen) {
            val = val.substring(0, maxLen);
        }
        return val.trim().isEmpty() ? null : val;
    }
}
