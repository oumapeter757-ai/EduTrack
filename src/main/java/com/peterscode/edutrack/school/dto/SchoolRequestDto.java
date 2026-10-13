package com.peterscode.edutrack.school.dto;

import com.peterscode.edutrack.school.enums.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SchoolRequestDto extends CreateSchoolRequestDto {

    private String address;
    private String subscriptionPlan;

    public void setSubscriptionPlan(String plan) {
        this.subscriptionPlan = plan;
        if (plan != null && !plan.isBlank()) {
            try {
                setSubscriptionTier(SubscriptionTier.valueOf(plan.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                setSubscriptionTier(SubscriptionTier.STANDARD);
            }
        }
    }

    public void setAddress(String address) {
        this.address = address;
        if (address != null && !address.isBlank() && getAddressLine1() == null) {
            setAddressLine1(address);
        }
    }
}