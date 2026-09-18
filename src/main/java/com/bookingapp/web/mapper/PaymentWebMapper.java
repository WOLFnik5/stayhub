package com.bookingapp.web.mapper;

import com.bookingapp.domain.model.PageResult;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.infrastructure.config.MapStructConfig;
import com.bookingapp.persistence.PaymentFilterQuery;
import com.bookingapp.web.dto.PageResponse;
import com.bookingapp.web.dto.PaymentCancelResponse;
import com.bookingapp.web.dto.PaymentCancelResult;
import com.bookingapp.web.dto.PaymentResponse;
import com.bookingapp.web.dto.PaymentSessionResult;
import com.bookingapp.web.dto.PaymentSuccessResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface PaymentWebMapper {

    default PaymentFilterQuery toFilterQuery(Long userId) {
        return new PaymentFilterQuery(userId);
    }

    PaymentResponse toResponse(Payment payment);

    @Mapping(target = "id", source = "paymentId")
    @Mapping(target = "status", source = "status")
    PaymentResponse toResponse(PaymentSessionResult paymentSessionResult);

    default PageResponse<PaymentResponse> toPageResponse(PageResult<Payment> result) {
        return new PageResponse<>(result.content().stream().map(this::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    PaymentCancelResponse toCancelResponse(PaymentCancelResult result);

    @Mapping(target = "message", constant = "Payment completed successfully.")
    @Mapping(target = "payment", source = "payment")
    PaymentSuccessResponse toSuccessResponse(Payment payment);

    default PaymentStatus map(String status) {
        return status == null ? null : PaymentStatus.valueOf(status);
    }
}
