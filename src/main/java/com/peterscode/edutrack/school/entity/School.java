package com.peterscode.edutrack.school.entity;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Entity
@Setter
@Getter
@SuperBuilder
@Table(name = "schools")
public class School extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(unique = true, nullable = false)
    private String subdomain;

    private String phone;

    private String email;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "address_line_1")
    private String addressLine1;

    @Column(name = "address_line_2")
    private String addressLine2;

    private String city;

    private String state;

    private String country;

    @Column(name = "postal_code")
    private String postalCode;

    @Builder.Default
    @Column(name = "timezone", nullable = false)
    private String timezone = "UTC";

    @Builder.Default
    @Column(name = "currency", nullable = false)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "subscription_tier", nullable = false)
    private SubscriptionTier subscriptionTier = SubscriptionTier.STANDARD;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "subscription_status", nullable = false)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.ACTIVE;

    @Column(name = "subscription_start_date")
    private LocalDate subscriptionStartDate;

    @Column(name = "subscription_end_date")
    private LocalDate subscriptionEndDate;

    @Column(name = "max_students")
    private Integer maxStudents;

    @Column(name = "max_teachers")
    private Integer maxTeachers;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "school_enabled_modules", joinColumns = @JoinColumn(name = "school_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "module_name")
    @Builder.Default
    private Set<ServiceModule> enabledModules = new HashSet<>();

    @Builder.Default
    private boolean active = true;

    /**
     * Checks if a particular service module is enabled for this school.
     */
    public boolean isModuleEnabled(ServiceModule module) {
        return enabledModules != null && enabledModules.contains(module);
    }

    /**
     * Enables a service module for this school.
     */
    public void enableModule(ServiceModule module) {
        if (this.enabledModules == null) {
            this.enabledModules = new HashSet<>();
        }
        this.enabledModules.add(module);
    }

    /**
     * Disables a service module for this school.
     */
    public void disableModule(ServiceModule module) {
        if (this.enabledModules != null) {
            this.enabledModules.remove(module);
        }
    }

    /**
     * Backward-compatible address getter.
     */
    public String getAddress() {
        StringBuilder sb = new StringBuilder();
        if (addressLine1 != null && !addressLine1.isBlank()) sb.append(addressLine1);
        if (city != null && !city.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(city);
        }
        if (state != null && !state.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(state);
        }
        if (country != null && !country.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(country);
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
