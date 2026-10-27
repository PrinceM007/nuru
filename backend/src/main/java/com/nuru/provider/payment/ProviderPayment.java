package com.nuru.provider.payment;

import com.nuru.domain.RecordedVia;

public record ProviderPayment(RecordedVia recordedVia, String reference, String label) {
}
