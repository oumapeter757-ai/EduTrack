package com.peterscode.edutrack.payment.service;

import com.peterscode.edutrack.payment.dto.MpesaCallbackRequest;

public interface MpesaCallbackService {

    MpesaCallbackRequest handleValidation(MpesaCallbackRequest callback);

    MpesaCallbackRequest handleConfirmation(MpesaCallbackRequest callback);
}
