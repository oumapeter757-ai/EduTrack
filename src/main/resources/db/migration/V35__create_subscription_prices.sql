CREATE TABLE subscription_prices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tier VARCHAR(50) NOT NULL,
    billing_period VARCHAR(50) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from DATE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT uk_subscription_price_tier_period UNIQUE (tier, billing_period)
);
