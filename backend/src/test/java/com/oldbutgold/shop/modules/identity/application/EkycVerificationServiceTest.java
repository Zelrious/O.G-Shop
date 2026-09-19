package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.ekyc.EkycClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EkycVerificationServiceTest {
    private final EkycClient client = mock(EkycClient.class);
    private final SellerVerificationWriter writer = mock(SellerVerificationWriter.class);
    private final EkycVerificationService service = new EkycVerificationService(client, writer);
    private final byte[] image = new byte[]{1, 2, 3};

    @Test
    void providerFailureFailsClosedWithoutCreatingVerification() {
        when(client.ocr(image, "card.jpg", "image/jpeg"))
                .thenThrow(new EkycUnavailableException("offline"));

        assertThatThrownBy(() -> service.verify(
                10L, image, "card.jpg", "image/jpeg", "data:image/jpeg;base64,AAAA"
        )).isInstanceOf(EkycUnavailableException.class);
        verify(writer, never()).recordVerified(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nonMatchingFaceDoesNotGrantSeller() {
        var ocr = new EkycClient.OcrResult("SUCCESS", "", new EkycClient.ExtractedData(
                "synthetic", "Demo User", "", "", "", ""
        ));
        var match = new EkycClient.MatchResult(
                "NOT_MATCHED", false, 0.8, 0.0, 0.5,
                "Khuôn mặt không khớp", "ArcFace", "test", true
        );
        when(client.ocr(image, "card.jpg", "image/jpeg")).thenReturn(ocr);
        when(client.match(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(match);

        var result = service.verify(10L, image, "card.jpg", "image/jpeg", "data:image/jpeg;base64,AAAA");

        assertThat(result.verificationId()).isNull();
        assertThat(result.match().match()).isFalse();
        verify(writer, never()).recordVerified(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }
}
