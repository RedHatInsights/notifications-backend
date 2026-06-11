-- Step 1: Add nullable column (instant, no table rewrite)
ALTER TABLE event ADD COLUMN severity_order SMALLINT;

-- Step 2: Auto-maintain severity_order via trigger
CREATE OR REPLACE FUNCTION set_severity_order() RETURNS trigger AS $$
BEGIN
    NEW.severity_order := CASE NEW.severity
        WHEN 'CRITICAL' THEN 10
        WHEN 'IMPORTANT' THEN 20
        WHEN 'MODERATE' THEN 30
        WHEN 'LOW' THEN 40
        WHEN 'NONE' THEN 50
        WHEN 'UNDEFINED' THEN 60
        ELSE 70
    END;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_event_severity_order
    BEFORE INSERT OR UPDATE OF severity ON event
    FOR EACH ROW EXECUTE FUNCTION set_severity_order();

-- Index created upfront on the nullable column (essentially empty while severity_order is NULL).
-- The backfill API populates rows in batches, incrementally building the index.
CREATE INDEX ix_event_org_id_severity_order_created
    ON event (org_id, severity_order, created DESC) INCLUDE (id);
