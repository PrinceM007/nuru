-- Nuru V8: database-level immutability for append-only tables.
-- AFTER triggers roll back any UPDATE/DELETE that would touch real rows.
-- Zero-row operations (e.g. cascade paths from draft deletion with no children)
-- are allowed through so legitimate parent deletes keep working.

CREATE TRIGGER TRG_audit_events_immutable ON audit_events
AFTER UPDATE, DELETE
AS
BEGIN
  SET NOCOUNT ON;
  IF EXISTS (SELECT 1 FROM inserted) OR EXISTS (SELECT 1 FROM deleted)
  BEGIN
    RAISERROR ('audit_events is append-only: updates and deletes are rejected', 16, 1);
    ROLLBACK TRANSACTION;
  END
END;
GO

CREATE TRIGGER TRG_ledger_entries_immutable ON ledger_entries
AFTER UPDATE, DELETE
AS
BEGIN
  SET NOCOUNT ON;
  IF EXISTS (SELECT 1 FROM inserted) OR EXISTS (SELECT 1 FROM deleted)
  BEGIN
    RAISERROR ('ledger_entries is append-only: updates and deletes are rejected', 16, 1);
    ROLLBACK TRANSACTION;
  END
END;
GO

CREATE TRIGGER TRG_ballots_immutable ON ballots
AFTER UPDATE, DELETE
AS
BEGIN
  SET NOCOUNT ON;
  IF EXISTS (SELECT 1 FROM inserted) OR EXISTS (SELECT 1 FROM deleted)
  BEGIN
    RAISERROR ('ballots are immutable once cast', 16, 1);
    ROLLBACK TRANSACTION;
  END
END;
GO

CREATE TRIGGER TRG_vote_markers_immutable ON vote_markers
AFTER UPDATE, DELETE
AS
BEGIN
  SET NOCOUNT ON;
  IF EXISTS (SELECT 1 FROM inserted) OR EXISTS (SELECT 1 FROM deleted)
  BEGIN
    RAISERROR ('vote_markers are immutable once cast', 16, 1);
    ROLLBACK TRANSACTION;
  END
END;
GO

CREATE TRIGGER TRG_vote_eligibility_immutable ON vote_eligibility
AFTER UPDATE, DELETE
AS
BEGIN
  SET NOCOUNT ON;
  IF EXISTS (SELECT 1 FROM inserted) OR EXISTS (SELECT 1 FROM deleted)
  BEGIN
    RAISERROR ('vote_eligibility snapshots are immutable', 16, 1);
    ROLLBACK TRANSACTION;
  END
END;
GO
