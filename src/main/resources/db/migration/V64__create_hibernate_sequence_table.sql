-- Hibernate SequenceStyleGenerator table for SEQUENCE-based ID generation.
-- On PostgreSQL, Hibernate uses a real sequence (global_id_seq).
-- On MySQL, Hibernate uses this table to emulate sequence behavior.
-- This enables Hibernate batch inserts (batch_size: 25) and reduces
-- round-trips during bulk operations compared to GenerationType.IDENTITY.

CREATE TABLE IF NOT EXISTS global_seq (
    next_val BIGINT NOT NULL,
    PRIMARY KEY (next_val)
) ENGINE=InnoDB;

INSERT IGNORE INTO global_seq (next_val) VALUES (1000000);
