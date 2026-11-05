CREATE TABLE enrollment_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    school_id BIGINT NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor_id BIGINT NULL,
    old_value VARCHAR(1000) NULL,
    new_value VARCHAR(1000) NULL,
    reason VARCHAR(500) NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_enrollment_audit_school FOREIGN KEY (school_id) REFERENCES schools(id)
);

CREATE INDEX idx_enrollment_audit_school_entity
    ON enrollment_audit(school_id, entity_type, entity_id, occurred_at);

ALTER TABLE students ADD COLUMN left_on DATE NULL;

ALTER TABLE promotion_decisions
    ADD COLUMN claimed_by VARCHAR(100) NULL;

CREATE INDEX idx_promotion_decision_claim
    ON promotion_decisions(status, claimed_until);
