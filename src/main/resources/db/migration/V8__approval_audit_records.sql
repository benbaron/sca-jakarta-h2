CREATE TABLE IF NOT EXISTS approval_audit_record
(
    id UUID PRIMARY KEY,
    group_code VARCHAR(64) NOT NULL,
    workflow_type VARCHAR(64) NOT NULL,
    workflow_run_id UUID,
    decision VARCHAR(32) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    rationale VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE approval_audit_record
    ADD CONSTRAINT chk_approval_audit_decision
        CHECK (decision IN ('APPROVED', 'REJECTED', 'ESCALATED'));
