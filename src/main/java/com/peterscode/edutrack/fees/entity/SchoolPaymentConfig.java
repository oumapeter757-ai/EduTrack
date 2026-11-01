package com.peterscode.edutrack.fees.entity;

import com.peterscode.edutrack.fees.enums.MpesaEnvironment;
import com.peterscode.edutrack.fees.enums.ShortcodeType;
import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Per-school Mpesa payment configuration with encrypted credentials.
 * Each school registers its own shortcode so money goes directly to the school.
 */
@Entity
@Table(
        name = "school_payment_config",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_config_school", columnNames = "school_id"),
                @UniqueConstraint(name = "uk_payment_config_shortcode", columnNames = "shortcode")
        },
        indexes = {
                @Index(name = "idx_payment_config_shortcode", columnList = "shortcode"),
                @Index(name = "idx_payment_config_token_hash", columnList = "callback_token_hash")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SchoolPaymentConfig extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(nullable = false, length = 20)
    private String shortcode;

    @Enumerated(EnumType.STRING)
    @Column(name = "shortcode_type", nullable = false, length = 10)
    @Builder.Default
    private ShortcodeType shortcodeType = ShortcodeType.PAYBILL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private MpesaEnvironment environment = MpesaEnvironment.SANDBOX;

    @Column(name = "encrypted_consumer_key", columnDefinition = "TEXT")
    private String encryptedConsumerKey;

    @Column(name = "encrypted_consumer_secret", columnDefinition = "TEXT")
    private String encryptedConsumerSecret;

    @Column(name = "encrypted_passkey", columnDefinition = "TEXT")
    private String encryptedPasskey;

    @Column(name = "callback_token_hash", nullable = false, length = 128)
    private String callbackTokenHash;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Version
    @Builder.Default
    private Integer version = 0;
}
