-- Akıllı Tahmin v6.3 veritabanı şeması (SQLite)
-- Zamanlar ISO-8601 UTC metin olarak saklanır ("2026-11-21T18:00:00Z"); sözlüksel sıralama kronolojiktir.
-- Değişmezlik: tahminler, oran anlık görüntüleri, adaylar, kararlar, retler ve denetimler yalnızca eklenir.

PRAGMA foreign_keys = ON;

-- ───────────────────────── Temel futbol verisi ─────────────────────────

CREATE TABLE IF NOT EXISTS leagues (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    country     TEXT NOT NULL,
    type        TEXT NOT NULL CHECK (type IN ('league', 'cup', 'continental')),
    active      INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS teams (
    id          INTEGER PRIMARY KEY,
    league_id   TEXT NOT NULL REFERENCES leagues(id),
    name        TEXT NOT NULL,
    UNIQUE (league_id, name)
);

CREATE TABLE IF NOT EXISTS fixtures (
    id                  INTEGER PRIMARY KEY,
    league_id           TEXT NOT NULL REFERENCES leagues(id),
    season              TEXT NOT NULL,
    round               INTEGER,
    home_team_id        INTEGER NOT NULL REFERENCES teams(id),
    away_team_id        INTEGER NOT NULL REFERENCES teams(id),
    kickoff_at          TEXT NOT NULL,
    status              TEXT NOT NULL DEFAULT 'scheduled'
                        CHECK (status IN ('scheduled', 'live', 'finished', 'postponed', 'abandoned', 'cancelled')),
    ft_home             INTEGER,
    ft_away             INTEGER,
    ht_home             INTEGER,
    ht_away             INTEGER,
    -- Geçmiş zaman veri deposu: sonuç bu andan itibaren "bilinir". Zaman kesimi sorguları bunu kullanır.
    result_recorded_at  TEXT,
    source              TEXT NOT NULL,
    is_synthetic        INTEGER NOT NULL DEFAULT 0,
    created_at          TEXT NOT NULL,
    CHECK (home_team_id <> away_team_id)
);
CREATE INDEX IF NOT EXISTS ix_fixtures_kickoff ON fixtures(kickoff_at);
CREATE INDEX IF NOT EXISTS ix_fixtures_league ON fixtures(league_id, kickoff_at);

-- Her veri değişikliğinin zaman damgalı kaydı (geçmiş zaman veri deposu).
CREATE TABLE IF NOT EXISTS data_revisions (
    id          INTEGER PRIMARY KEY,
    entity      TEXT NOT NULL,
    entity_id   INTEGER NOT NULL,
    field       TEXT NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    recorded_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_revisions_entity ON data_revisions(entity, entity_id, recorded_at);

-- ───────────────────────── Oranlar ─────────────────────────

CREATE TABLE IF NOT EXISTS odds_sources (
    code        TEXT PRIMARY KEY,
    label       TEXT NOT NULL,
    source_type TEXT NOT NULL CHECK (source_type IN ('iddaa', 'bookmaker', 'exchange', 'synthetic', 'historical_csv')),
    -- Yalnızca resmi İddaa kaynağı is_iddaa = 1 olabilir.
    is_iddaa    INTEGER NOT NULL DEFAULT 0 CHECK (is_iddaa = 0 OR source_type = 'iddaa'),
    verified    INTEGER NOT NULL DEFAULT 0,
    verified_at TEXT,
    notes       TEXT
);

CREATE TABLE IF NOT EXISTS odds_snapshots (
    id                  INTEGER PRIMARY KEY,
    fixture_id          INTEGER NOT NULL REFERENCES fixtures(id),
    market_id           TEXT NOT NULL,
    outcome_id          TEXT NOT NULL,
    decimal_odds        REAL NOT NULL CHECK (decimal_odds > 1.0),
    source              TEXT NOT NULL REFERENCES odds_sources(code),
    captured_at         TEXT NOT NULL,
    is_live             INTEGER NOT NULL DEFAULT 0,
    minute              INTEGER,
    timestamp_precision TEXT NOT NULL DEFAULT 'exact' CHECK (timestamp_precision IN ('exact', 'approximate')),
    ingested_at         TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_odds_fixture ON odds_snapshots(fixture_id, market_id, outcome_id, captured_at);

CREATE TABLE IF NOT EXISTS market_settlement_rules (
    market_id    TEXT NOT NULL,
    source       TEXT NOT NULL,   -- oran kaynağı kodu veya 'standard'
    rule_version TEXT NOT NULL,
    rule_text    TEXT NOT NULL,
    verified     INTEGER NOT NULL DEFAULT 0,
    verified_at  TEXT,
    verified_by  TEXT,
    PRIMARY KEY (market_id, source, rule_version)
);

-- ───────────────────────── Tahmin modelleri ─────────────────────────

CREATE TABLE IF NOT EXISTS prediction_runs (
    id              INTEGER PRIMARY KEY,
    kind            TEXT NOT NULL CHECK (kind IN ('prematch', 'backtest', 'live')),
    as_of           TEXT NOT NULL,
    data_cutoff_at  TEXT NOT NULL,
    started_at      TEXT NOT NULL,
    finished_at     TEXT,
    status          TEXT NOT NULL DEFAULT 'running' CHECK (status IN ('running', 'finished', 'failed')),
    is_synthetic    INTEGER NOT NULL DEFAULT 0,
    notes           TEXT
);

CREATE TABLE IF NOT EXISTS model_versions (
    id              INTEGER PRIMARY KEY,
    model           TEXT NOT NULL CHECK (model IN ('A', 'B')),
    league_id       TEXT,
    family          TEXT,
    version         TEXT NOT NULL,
    params          TEXT NOT NULL,
    training_rows   INTEGER NOT NULL,
    data_cutoff_at  TEXT NOT NULL,
    uses_odds       INTEGER NOT NULL,
    created_at      TEXT NOT NULL,
    CHECK (model <> 'A' OR uses_odds = 0)
);

CREATE TABLE IF NOT EXISTS calibration_maps (
    id              INTEGER PRIMARY KEY,
    model           TEXT NOT NULL CHECK (model IN ('A', 'B')),
    family          TEXT NOT NULL,
    method          TEXT NOT NULL,
    params          TEXT NOT NULL,
    n               INTEGER NOT NULL,
    adequate        INTEGER NOT NULL,
    tests           TEXT NOT NULL,
    data_cutoff_at  TEXT NOT NULL,
    created_at      TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS predictions (
    id                      INTEGER PRIMARY KEY,
    run_id                  INTEGER NOT NULL REFERENCES prediction_runs(id),
    model                   TEXT NOT NULL CHECK (model IN ('A', 'B')),
    model_version_id        INTEGER REFERENCES model_versions(id),
    fixture_id              INTEGER NOT NULL REFERENCES fixtures(id),
    market_id               TEXT NOT NULL,
    outcome_id              TEXT NOT NULL,
    raw_probability         REAL NOT NULL CHECK (raw_probability >= 0 AND raw_probability <= 1),
    calibrated_probability  REAL CHECK (calibrated_probability >= 0 AND calibrated_probability <= 1),
    probability_lower       REAL,
    probability_upper       REAL,
    void_probability        REAL NOT NULL DEFAULT 0,
    calibration_id          INTEGER REFERENCES calibration_maps(id),
    data_cutoff_at          TEXT NOT NULL,
    created_at              TEXT NOT NULL,
    uses_odds               INTEGER NOT NULL,
    is_oof                  INTEGER NOT NULL DEFAULT 1,
    input_manifest          TEXT NOT NULL,
    -- MODEL A oran verisi kullanamaz (şema düzeyinde izolasyon).
    CHECK (model <> 'A' OR uses_odds = 0),
    UNIQUE (run_id, model, fixture_id, market_id, outcome_id)
);
CREATE INDEX IF NOT EXISTS ix_predictions_fixture ON predictions(fixture_id, model, market_id, outcome_id);

-- ───────────────────────── Seçim modları (v6.3) ─────────────────────────

CREATE TABLE IF NOT EXISTS selection_modes (
    code            TEXT PRIMARY KEY CHECK (code IN ('highest_probability', 'best_value', 'balanced')),
    name            TEXT NOT NULL,
    short_name      TEXT NOT NULL,
    description     TEXT NOT NULL,
    requires_odds   INTEGER NOT NULL,
    ranking         TEXT NOT NULL,
    is_default      INTEGER NOT NULL DEFAULT 0,
    sort_order      INTEGER NOT NULL
);
-- Yalnızca bir varsayılan mod olabilir.
CREATE UNIQUE INDEX IF NOT EXISTS ux_selection_modes_default ON selection_modes(is_default) WHERE is_default = 1;

CREATE TABLE IF NOT EXISTS selection_mode_configs (
    id                  INTEGER PRIMARY KEY,
    mode                TEXT NOT NULL REFERENCES selection_modes(code),
    status              TEXT NOT NULL CHECK (status IN ('established', 'not_established')),
    thresholds          TEXT NOT NULL,
    source              TEXT NOT NULL DEFAULT 'chronological_validation',
    validation_start    TEXT,
    validation_end      TEXT,
    validation_metrics  TEXT NOT NULL,
    data_cutoff_at      TEXT NOT NULL,
    created_at          TEXT NOT NULL,
    -- Eşikler yalnızca zaman kesiminden önceki doğrulama verisiyle belirlenebilir.
    CHECK (validation_end IS NULL OR validation_end <= data_cutoff_at)
);
CREATE INDEX IF NOT EXISTS ix_mode_configs ON selection_mode_configs(mode, data_cutoff_at);

-- Moddan bağımsız aday değerlendirmesi: MODEL A/B olasılıkları, kalibrasyon, belirsizlik, dayanıklılık,
-- geçerlilik, oran/EV ve Kalite Kapısı'nın ortak kontrolleri. Üç mod AYNI değerlendirmeyi kullanır;
-- böylece mod değişimi MODEL A/B olasılıklarını değiştiremez.
-- Fikstür düzeyinde değerlendirme: veri durumu, geçerlilik ve güncellik (her seçenekte tekrarlanmaz).
CREATE TABLE IF NOT EXISTS fixture_evaluations (
    id              INTEGER PRIMARY KEY,
    run_id          INTEGER NOT NULL REFERENCES prediction_runs(id),
    fixture_id      INTEGER NOT NULL REFERENCES fixtures(id),
    data_cutoff_at  TEXT NOT NULL,
    evaluated_at    TEXT NOT NULL,
    validity        TEXT NOT NULL,
    freshness       TEXT NOT NULL,
    model_a         TEXT NOT NULL,
    UNIQUE (run_id, fixture_id)
);

CREATE TABLE IF NOT EXISTS candidate_evaluations (
    id                      INTEGER PRIMARY KEY,
    run_id                  INTEGER NOT NULL REFERENCES prediction_runs(id),
    fixture_id              INTEGER NOT NULL REFERENCES fixtures(id),
    market_id               TEXT NOT NULL,
    outcome_id              TEXT NOT NULL,
    model_a_prediction_id   INTEGER NOT NULL REFERENCES predictions(id),
    model_b_prediction_id   INTEGER REFERENCES predictions(id),
    odds_snapshot_id        INTEGER REFERENCES odds_snapshots(id),
    data_cutoff_at          TEXT NOT NULL,
    evaluated_at            TEXT NOT NULL,
    p_a_raw                 REAL NOT NULL,
    p_b_raw                 REAL,
    p_a_cal                 REAL,
    p_b_cal                 REAL,
    p_void                  REAL NOT NULL DEFAULT 0,
    estimated_probability   REAL,
    probability_method      TEXT,
    fair_probability        REAL,
    market_margin           REAL,
    football_signal         REAL,
    decimal_odds            REAL,
    ev_model_a              REAL,
    ev_model_b              REAL,
    estimated_ev            REAL,
    interval_width          REAL,
    relative_width          REAL,
    disagreement            REAL,
    market_deviation        REAL,
    robust_down             REAL,
    core_pass               INTEGER NOT NULL,
    odds_valid              INTEGER NOT NULL,
    uncertainty_metrics     TEXT NOT NULL,
    robustness              TEXT NOT NULL,
    -- Yalnızca sağlanmayan Kalite Kapısı kontrolleri saklanır; geçenler kod listesinden yeniden kurulur.
    gate_failed             TEXT NOT NULL,
    UNIQUE (run_id, fixture_id, market_id, outcome_id)
);
CREATE INDEX IF NOT EXISTS ix_evaluations_fixture ON candidate_evaluations(fixture_id, evaluated_at);

CREATE TABLE IF NOT EXISTS selection_mode_candidates (
    id                      INTEGER PRIMARY KEY,
    run_id                  INTEGER NOT NULL REFERENCES prediction_runs(id),
    evaluation_id           INTEGER NOT NULL REFERENCES candidate_evaluations(id),
    selection_mode          TEXT NOT NULL REFERENCES selection_modes(code),
    config_id               INTEGER REFERENCES selection_mode_configs(id),
    fixture_id              INTEGER NOT NULL REFERENCES fixtures(id),
    market_id               TEXT NOT NULL,
    outcome_id              TEXT NOT NULL,
    probability_lower       REAL,
    ev_lower                REAL,
    mode_checks             TEXT NOT NULL,
    eligible                INTEGER NOT NULL,
    selected                INTEGER NOT NULL,
    rank                    INTEGER,
    rank_score              REAL,
    UNIQUE (run_id, selection_mode, fixture_id, market_id, outcome_id)
);
CREATE INDEX IF NOT EXISTS ix_candidates_run_mode ON selection_mode_candidates(run_id, selection_mode, selected);

CREATE TABLE IF NOT EXISTS selection_audits (
    id              INTEGER PRIMARY KEY,
    candidate_id    INTEGER NOT NULL REFERENCES selection_mode_candidates(id),
    selection_mode  TEXT NOT NULL REFERENCES selection_modes(code),
    status          TEXT NOT NULL CHECK (status IN ('passed', 'failed')),
    checks          TEXT NOT NULL,
    auditor_version TEXT NOT NULL,
    audited_at      TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_audits_candidate ON selection_audits(candidate_id);

CREATE TABLE IF NOT EXISTS selection_mode_decisions (
    id                      INTEGER PRIMARY KEY,
    candidate_id            INTEGER NOT NULL UNIQUE REFERENCES selection_mode_candidates(id),
    audit_id                INTEGER NOT NULL REFERENCES selection_audits(id),
    run_id                  INTEGER NOT NULL REFERENCES prediction_runs(id),
    config_id               INTEGER REFERENCES selection_mode_configs(id),
    fixture_id              INTEGER NOT NULL REFERENCES fixtures(id),
    market_id               TEXT NOT NULL,
    outcome_id              TEXT NOT NULL,
    model_a_prediction_id   INTEGER NOT NULL REFERENCES predictions(id),
    model_b_prediction_id   INTEGER NOT NULL REFERENCES predictions(id),
    selection_mode          TEXT NOT NULL REFERENCES selection_modes(code),
    selection_timestamp     TEXT NOT NULL,
    odds_snapshot_id        INTEGER REFERENCES odds_snapshots(id),
    decimal_odds            REAL,
    data_cutoff_at          TEXT NOT NULL,
    estimated_probability   REAL NOT NULL,
    void_probability        REAL NOT NULL DEFAULT 0,
    estimated_ev            REAL,
    uncertainty_metrics     TEXT NOT NULL,
    audit_status            TEXT NOT NULL CHECK (audit_status IN ('passed', 'failed', 'pending')),
    decision_reason         TEXT NOT NULL,
    rank                    INTEGER,
    settlement_status       TEXT NOT NULL DEFAULT 'pending'
                            CHECK (settlement_status IN ('pending', 'won', 'lost', 'void')),
    settled_at              TEXT,
    profit_units            REAL,
    CHECK (data_cutoff_at <= selection_timestamp),
    -- Bir mod aynı maç/market/seçenek için yalnızca bir kez karar verir; tekrar sayılmaz.
    UNIQUE (selection_mode, fixture_id, market_id, outcome_id)
);
CREATE INDEX IF NOT EXISTS ix_decisions_mode ON selection_mode_decisions(selection_mode, selection_timestamp);

CREATE TABLE IF NOT EXISTS selection_mode_rejections (
    id                  INTEGER PRIMARY KEY,
    candidate_id        INTEGER NOT NULL UNIQUE REFERENCES selection_mode_candidates(id),
    run_id              INTEGER NOT NULL REFERENCES prediction_runs(id),
    selection_mode      TEXT NOT NULL REFERENCES selection_modes(code),
    fixture_id          INTEGER NOT NULL REFERENCES fixtures(id),
    market_id           TEXT NOT NULL,
    outcome_id          TEXT NOT NULL,
    failed_checks       TEXT NOT NULL,
    primary_reason_code TEXT NOT NULL,
    reason_text         TEXT NOT NULL,
    created_at          TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_rejections_run_mode ON selection_mode_rejections(run_id, selection_mode);

CREATE TABLE IF NOT EXISTS selection_mode_performance (
    id          INTEGER PRIMARY KEY,
    mode        TEXT NOT NULL REFERENCES selection_modes(code),
    period      TEXT NOT NULL,
    scope       TEXT NOT NULL,   -- overall | league | market | season
    scope_key   TEXT NOT NULL DEFAULT '',
    metrics     TEXT NOT NULL,
    as_of       TEXT NOT NULL,
    computed_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_mode_perf ON selection_mode_performance(mode, period, scope, as_of);

CREATE TABLE IF NOT EXISTS selection_mode_calibration (
    id          INTEGER PRIMARY KEY,
    mode        TEXT NOT NULL REFERENCES selection_modes(code),
    period      TEXT NOT NULL,
    bins        TEXT NOT NULL,
    ece         REAL,
    n           INTEGER NOT NULL,
    as_of       TEXT NOT NULL,
    computed_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS selection_mode_comparisons (
    id              INTEGER PRIMARY KEY,
    period          TEXT NOT NULL,
    window_start    TEXT,
    window_end      TEXT,
    pool_fixtures   INTEGER NOT NULL,
    pool_candidates INTEGER NOT NULL,
    pool_hash       TEXT NOT NULL,
    metrics         TEXT NOT NULL,
    as_of           TEXT NOT NULL,
    computed_at     TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS user_settings (
    user_id         TEXT PRIMARY KEY,
    default_mode    TEXT NOT NULL REFERENCES selection_modes(code),
    updated_at      TEXT NOT NULL
);

-- ───────────────────────── Canlı Gol Radar ─────────────────────────

-- Gol dakikaları (canlı model kalibrasyonu ve nadir olay doğrulaması için geçmiş zaman verisi).
CREATE TABLE IF NOT EXISTS goal_events (
    id          INTEGER PRIMARY KEY,
    fixture_id  INTEGER NOT NULL REFERENCES fixtures(id),
    minute      INTEGER NOT NULL CHECK (minute >= 1 AND minute <= 90),
    side        TEXT NOT NULL CHECK (side IN ('home', 'away')),
    recorded_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_goal_events ON goal_events(fixture_id, minute);

CREATE TABLE IF NOT EXISTS live_states (
    id          INTEGER PRIMARY KEY,
    fixture_id  INTEGER NOT NULL REFERENCES fixtures(id),
    minute      INTEGER NOT NULL CHECK (minute >= 0),
    home_goals  INTEGER NOT NULL,
    away_goals  INTEGER NOT NULL,
    home_xg     REAL,
    away_xg     REAL,
    home_shots  INTEGER,
    away_shots  INTEGER,
    home_red    INTEGER NOT NULL DEFAULT 0,
    away_red    INTEGER NOT NULL DEFAULT 0,
    captured_at TEXT NOT NULL,
    source      TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_live_states ON live_states(fixture_id, captured_at);

CREATE TABLE IF NOT EXISTS radar_calibration (
    id              INTEGER PRIMARY KEY,
    model           TEXT NOT NULL CHECK (model IN ('A', 'B')),
    region_low      REAL NOT NULL,
    n               INTEGER NOT NULL,
    mean_predicted  REAL,
    observed_rate   REAL,
    ci_low          REAL,
    adequate        INTEGER NOT NULL,
    data_cutoff_at  TEXT NOT NULL,
    created_at      TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS radar_evaluations (
    id                  INTEGER PRIMARY KEY,
    fixture_id          INTEGER NOT NULL REFERENCES fixtures(id),
    live_state_id       INTEGER REFERENCES live_states(id),
    minute              INTEGER NOT NULL,
    model_a_probability REAL,
    model_b_probability REAL,
    odds_snapshot_id    INTEGER REFERENCES odds_snapshots(id),
    conditions          TEXT NOT NULL,
    alarm               INTEGER NOT NULL,
    blocked_reason      TEXT,
    evaluated_at        TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS radar_alerts (
    id                  INTEGER PRIMARY KEY,
    fixture_id          INTEGER NOT NULL REFERENCES fixtures(id),
    evaluation_id       INTEGER NOT NULL REFERENCES radar_evaluations(id),
    target              TEXT NOT NULL,
    minute              INTEGER NOT NULL,
    model_a_probability REAL NOT NULL,
    model_b_probability REAL NOT NULL,
    odds_snapshot_id    INTEGER REFERENCES odds_snapshots(id),
    created_at          TEXT NOT NULL,
    notified            INTEGER NOT NULL DEFAULT 0,
    -- Mükerrer alarm engeli: aynı maç, hedef ve skor durumu için tek alarm.
    dedupe_key          TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS notification_permissions (
    user_id     TEXT PRIMARY KEY,
    granted     INTEGER NOT NULL,
    updated_at  TEXT NOT NULL
);

-- ───────────────────────── Değişmezlik tetikleyicileri ─────────────────────────

CREATE TRIGGER IF NOT EXISTS trg_predictions_no_update BEFORE UPDATE ON predictions
BEGIN SELECT RAISE(ABORT, 'predictions değiştirilemez (yalnızca eklenir)'); END;
CREATE TRIGGER IF NOT EXISTS trg_predictions_no_delete BEFORE DELETE ON predictions
BEGIN SELECT RAISE(ABORT, 'predictions silinemez'); END;

CREATE TRIGGER IF NOT EXISTS trg_odds_no_update BEFORE UPDATE ON odds_snapshots
BEGIN SELECT RAISE(ABORT, 'odds_snapshots değiştirilemez (yalnızca eklenir)'); END;
CREATE TRIGGER IF NOT EXISTS trg_odds_no_delete BEFORE DELETE ON odds_snapshots
BEGIN SELECT RAISE(ABORT, 'odds_snapshots silinemez'); END;

CREATE TRIGGER IF NOT EXISTS trg_fixture_eval_no_update BEFORE UPDATE ON fixture_evaluations
BEGIN SELECT RAISE(ABORT, 'fixture_evaluations değiştirilemez'); END;
CREATE TRIGGER IF NOT EXISTS trg_evaluations_no_update BEFORE UPDATE ON candidate_evaluations
BEGIN SELECT RAISE(ABORT, 'candidate_evaluations değiştirilemez'); END;
CREATE TRIGGER IF NOT EXISTS trg_evaluations_no_delete BEFORE DELETE ON candidate_evaluations
BEGIN SELECT RAISE(ABORT, 'candidate_evaluations silinemez'); END;

CREATE TRIGGER IF NOT EXISTS trg_candidates_no_update BEFORE UPDATE ON selection_mode_candidates
BEGIN SELECT RAISE(ABORT, 'selection_mode_candidates değiştirilemez'); END;
CREATE TRIGGER IF NOT EXISTS trg_candidates_no_delete BEFORE DELETE ON selection_mode_candidates
BEGIN SELECT RAISE(ABORT, 'selection_mode_candidates silinemez'); END;

CREATE TRIGGER IF NOT EXISTS trg_rejections_no_update BEFORE UPDATE ON selection_mode_rejections
BEGIN SELECT RAISE(ABORT, 'selection_mode_rejections değiştirilemez'); END;
CREATE TRIGGER IF NOT EXISTS trg_rejections_no_delete BEFORE DELETE ON selection_mode_rejections
BEGIN SELECT RAISE(ABORT, 'selection_mode_rejections silinemez'); END;

CREATE TRIGGER IF NOT EXISTS trg_audits_no_update BEFORE UPDATE ON selection_audits
BEGIN SELECT RAISE(ABORT, 'selection_audits değiştirilemez'); END;
CREATE TRIGGER IF NOT EXISTS trg_audits_no_delete BEFORE DELETE ON selection_audits
BEGIN SELECT RAISE(ABORT, 'selection_audits silinemez'); END;

CREATE TRIGGER IF NOT EXISTS trg_configs_no_update BEFORE UPDATE ON selection_mode_configs
BEGIN SELECT RAISE(ABORT, 'selection_mode_configs değiştirilemez; yeni sürüm ekleyin'); END;

-- Kararlar: yalnızca sonuçlandırma alanları, yalnızca bir kez ('pending' → sonuç) güncellenebilir.
CREATE TRIGGER IF NOT EXISTS trg_decisions_no_delete BEFORE DELETE ON selection_mode_decisions
BEGIN SELECT RAISE(ABORT, 'selection_mode_decisions silinemez'); END;

CREATE TRIGGER IF NOT EXISTS trg_decisions_core_immutable BEFORE UPDATE ON selection_mode_decisions
WHEN NEW.candidate_id IS NOT OLD.candidate_id
  OR NEW.audit_id IS NOT OLD.audit_id
  OR NEW.run_id IS NOT OLD.run_id
  OR NEW.config_id IS NOT OLD.config_id
  OR NEW.fixture_id IS NOT OLD.fixture_id
  OR NEW.market_id IS NOT OLD.market_id
  OR NEW.outcome_id IS NOT OLD.outcome_id
  OR NEW.model_a_prediction_id IS NOT OLD.model_a_prediction_id
  OR NEW.model_b_prediction_id IS NOT OLD.model_b_prediction_id
  OR NEW.selection_mode IS NOT OLD.selection_mode
  OR NEW.selection_timestamp IS NOT OLD.selection_timestamp
  OR NEW.odds_snapshot_id IS NOT OLD.odds_snapshot_id
  OR NEW.decimal_odds IS NOT OLD.decimal_odds
  OR NEW.data_cutoff_at IS NOT OLD.data_cutoff_at
  OR NEW.estimated_probability IS NOT OLD.estimated_probability
  OR NEW.void_probability IS NOT OLD.void_probability
  OR NEW.estimated_ev IS NOT OLD.estimated_ev
  OR NEW.uncertainty_metrics IS NOT OLD.uncertainty_metrics
  OR NEW.audit_status IS NOT OLD.audit_status
  OR NEW.decision_reason IS NOT OLD.decision_reason
  OR NEW.rank IS NOT OLD.rank
BEGIN SELECT RAISE(ABORT, 'Geçmiş seçimler geriye dönük değiştirilemez'); END;

CREATE TRIGGER IF NOT EXISTS trg_decisions_settle_once BEFORE UPDATE ON selection_mode_decisions
WHEN OLD.settlement_status <> 'pending'
BEGIN SELECT RAISE(ABORT, 'Sonuçlandırılmış seçim yeniden sonuçlandırılamaz'); END;

CREATE TRIGGER IF NOT EXISTS trg_radar_alerts_no_delete BEFORE DELETE ON radar_alerts
BEGIN SELECT RAISE(ABORT, 'radar_alerts silinemez'); END;
