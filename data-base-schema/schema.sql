-- 1. Enhanced Multi-Tenant Database Schema with User Isolation

-- User Management Table
CREATE TABLE IF NOT EXISTS users (
                                     id BIGSERIAL PRIMARY KEY,
                                     user_id VARCHAR(50) UNIQUE NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(100) UNIQUE NOT NULL,
    full_name VARCHAR(255),
    phone_number VARCHAR(20),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    tier VARCHAR(20) DEFAULT 'BASIC', -- BASIC, PREMIUM, PROFESSIONAL
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP WITH TIME ZONE,
    failed_login_attempts INTEGER DEFAULT 0,
    account_locked_until TIMESTAMP WITH TIME ZONE,
                                       email_verified BOOLEAN DEFAULT FALSE,
                                       phone_verified BOOLEAN DEFAULT FALSE,
                                       kyc_status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, VERIFIED, REJECTED
    risk_profile VARCHAR(20) DEFAULT 'MODERATE', -- CONSERVATIVE, MODERATE, AGGRESSIVE
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BLOCKED', 'PENDING')),
    CONSTRAINT chk_user_tier CHECK (tier IN ('BASIC', 'PREMIUM', 'PROFESSIONAL')),
    CONSTRAINT chk_kyc_status CHECK (kyc_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    CONSTRAINT chk_risk_profile CHECK (risk_profile IN ('CONSERVATIVE', 'MODERATE', 'AGGRESSIVE'))
    );

-- User Settings for Risk Management and Trading Preferences
CREATE TABLE IF NOT EXISTS user_settings (
                                             id BIGSERIAL PRIMARY KEY,
                                             user_id VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    setting_key VARCHAR(100) NOT NULL,
    setting_value TEXT NOT NULL,
    setting_type VARCHAR(20) DEFAULT 'STRING', -- STRING, NUMBER, BOOLEAN, JSON
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                             UNIQUE(user_id, tenant_id, setting_key),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    );

-- User Broker Authorizations (which brokers user can access)
CREATE TABLE IF NOT EXISTS user_broker_authorizations (
                                                          id BIGSERIAL PRIMARY KEY,
                                                          user_id VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    broker VARCHAR(20) NOT NULL,
    authorized BOOLEAN DEFAULT TRUE,
    authorization_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                     authorized_by VARCHAR(50), -- admin user who authorized
    notes TEXT,
    UNIQUE(user_id, tenant_id, broker),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    );

-- Enhanced User Broker Tokens with better security
CREATE TABLE IF NOT EXISTS user_broker_tokens (
                                                  id BIGSERIAL PRIMARY KEY,
                                                  user_id VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    broker VARCHAR(20) NOT NULL,
    access_token TEXT, -- Encrypted
    refresh_token TEXT, -- Encrypted
    access_token_expiry TIMESTAMP WITH TIME ZONE,
    refresh_token_expiry TIMESTAMP WITH TIME ZONE,
                                       token_hash VARCHAR(64), -- Hash for quick lookups without decryption
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_used TIMESTAMP WITH TIME ZONE,
                                       usage_count INTEGER DEFAULT 0,
                                       UNIQUE(user_id, tenant_id, broker),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    );

-- User-specific Positions with complete isolation
CREATE TABLE IF NOT EXISTS user_positions (
                                              id BIGSERIAL PRIMARY KEY,
                                              user_id VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    quantity BIGINT DEFAULT 0,
    average_price DECIMAL(15,4) DEFAULT 0,
    market_value DECIMAL(15,4) DEFAULT 0,
    realized_pnl DECIMAL(15,4) DEFAULT 0,
    unrealized_pnl DECIMAL(15,4) DEFAULT 0,
    total_trades INTEGER DEFAULT 0,
    winning_trades INTEGER DEFAULT 0,
    losing_trades INTEGER DEFAULT 0,
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                             version BIGINT DEFAULT 0, -- For optimistic locking
                             UNIQUE(user_id, tenant_id, symbol),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT chk_position_data CHECK (
(quantity = 0 AND average_price = 0) OR
(quantity != 0 AND average_price > 0)
    )
    );

-- User-specific Order Audit with complete traceability
CREATE TABLE IF NOT EXISTS user_order_audit (
                                                id BIGSERIAL PRIMARY KEY,
                                                user_id VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    trade_id VARCHAR(100) NOT NULL,
    parent_trade_id VARCHAR(100), -- For linked orders
    event_type VARCHAR(30) NOT NULL,
    order_id VARCHAR(100),
    strategy VARCHAR(50),
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(10),
    quantity INTEGER,
    price DECIMAL(12,4),
    executed_price DECIMAL(12,4),
    executed_quantity INTEGER,
    status VARCHAR(20),
    broker VARCHAR(20),
    broker_order_id VARCHAR(100),
    error_code VARCHAR(50),
    error_message TEXT,
    execution_time_ms INTEGER,
    commission DECIMAL(10,4),
    fees DECIMAL(10,4),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                             metadata JSONB,
                             client_ip INET,
                             user_agent TEXT,
                             session_id VARCHAR(100),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT chk_order_side CHECK (side IN ('BUY', 'SELL') OR side IS NULL),
    CONSTRAINT chk_positive_price CHECK (price IS NULL OR price > 0),
    CONSTRAINT chk_positive_quantity CHECK (quantity IS NULL OR quantity > 0)
    );

-- User Strategy Assignments (which strategies user can access)
CREATE TABLE IF NOT EXISTS user_strategy_assignments (
                                                         id BIGSERIAL PRIMARY KEY,
                                                         user_id VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    strategy_name VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    risk_multiplier DECIMAL(5,2) DEFAULT 1.0, -- User-specific risk adjustment
    max_position_size DECIMAL(15,4),
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                              assigned_by VARCHAR(50),
    UNIQUE(user_id, tenant_id, strategy_name),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    );

-- User Risk Limits (personalized risk management)
CREATE TABLE IF NOT EXISTS user_risk_limits (
                                                id BIGSERIAL PRIMARY KEY,
                                                user_id VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    limit_type VARCHAR(50) NOT NULL, -- MAX_ORDER_VALUE, DAILY_LOSS_LIMIT, etc.
    limit_value DECIMAL(15,4) NOT NULL,
    limit_currency VARCHAR(10) DEFAULT 'INR',
    is_active BOOLEAN DEFAULT TRUE,
    effective_from TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    effective_until TIMESTAMP WITH TIME ZONE,
                                  created_by VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                  UNIQUE(user_id, tenant_id, limit_type),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    );

-- User Sessions for concurrent session management
CREATE TABLE IF NOT EXISTS user_sessions (
                                             id BIGSERIAL PRIMARY KEY,
                                             session_id VARCHAR(100) UNIQUE NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    device_type VARCHAR(50),
    device_id VARCHAR(100),
    ip_address INET,
    user_agent TEXT,
    location VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_activity TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
                             is_active BOOLEAN DEFAULT TRUE,
                             logout_reason VARCHAR(50), -- MANUAL, TIMEOUT, SECURITY, ADMIN
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    );

-- User Activity Log for security monitoring
CREATE TABLE IF NOT EXISTS user_activity_log (
                                                 id BIGSERIAL PRIMARY KEY,
                                                 user_id VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    session_id VARCHAR(100),
    activity_type VARCHAR(50) NOT NULL, -- LOGIN, LOGOUT, ORDER_PLACE, etc.
    activity_details JSONB,
    ip_address INET,
    user_agent TEXT,
    success BOOLEAN DEFAULT TRUE,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                             FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    );

-- 2. Indexes for Performance and Security

-- Users table indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_user_id ON users(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_tenant_id ON users(tenant_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_tenant_status ON users(tenant_id, status);

-- User settings indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_settings_user_tenant ON user_settings(user_id, tenant_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_settings_key ON user_settings(user_id, setting_key);

-- User broker tokens indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_broker_tokens_user_tenant_broker
    ON user_broker_tokens(user_id, tenant_id, broker);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_broker_tokens_hash
    ON user_broker_tokens(token_hash) WHERE is_active = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_broker_tokens_expiry
    ON user_broker_tokens(access_token_expiry) WHERE is_active = true;

-- User positions indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_positions_user_tenant
    ON user_positions(user_id, tenant_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_positions_user_symbol
    ON user_positions(user_id, symbol);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_positions_tenant_updated
    ON user_positions(tenant_id, last_updated);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_positions_active
    ON user_positions(user_id) WHERE quantity != 0;

-- User order audit indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_order_audit_user_tenant
    ON user_order_audit(user_id, tenant_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_order_audit_trade_id
    ON user_order_audit(trade_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_order_audit_user_created
    ON user_order_audit(user_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_order_audit_symbol_created
    ON user_order_audit(user_id, symbol, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_order_audit_status
    ON user_order_audit(user_id, status, created_at DESC);

-- User sessions indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_sessions_user_id
    ON user_sessions(user_id, is_active);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_sessions_session_id
    ON user_sessions(session_id) WHERE is_active = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_sessions_expires
    ON user_sessions(expires_at) WHERE is_active = true;

-- User activity log indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_activity_user_created
    ON user_activity_log(user_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_activity_type
    ON user_activity_log(user_id, activity_type, created_at DESC);

-- 3. Row Level Security (RLS) Policies

-- Enable RLS on all user-specific tables
ALTER TABLE user_broker_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_positions ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_order_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_activity_log ENABLE ROW LEVEL SECURITY;

-- Create RLS policies for user data isolation
-- Users can only access their own data
CREATE POLICY user_data_isolation_broker_tokens ON user_broker_tokens
    FOR ALL TO application_role
    USING (user_id = current_setting('app.current_user_id', true));

CREATE POLICY user_data_isolation_positions ON user_positions
    FOR ALL TO application_role
    USING (user_id = current_setting('app.current_user_id', true));

CREATE POLICY user_data_isolation_order_audit ON user_order_audit
    FOR ALL TO application_role
    USING (user_id = current_setting('app.current_user_id', true));

CREATE POLICY user_data_isolation_settings ON user_settings
    FOR ALL TO application_role
    USING (user_id = current_setting('app.current_user_id', true));

CREATE POLICY user_data_isolation_sessions ON user_sessions
    FOR ALL TO application_role
    USING (user_id = current_setting('app.current_user_id', true));

CREATE POLICY user_data_isolation_activity_log ON user_activity_log
    FOR ALL TO application_role
    USING (user_id = current_setting('app.current_user_id', true));

-- Admin policies (for users with admin role)
CREATE POLICY admin_access_all_data ON user_broker_tokens
    FOR ALL TO admin_role
    USING (true);

CREATE POLICY admin_access_all_positions ON user_positions
    FOR ALL TO admin_role
    USING (true);

-- 4. Database Functions for User Data Management

-- Function to validate user access
CREATE OR REPLACE FUNCTION validate_user_access(target_user_id VARCHAR(50))
RETURNS BOOLEAN AS $
DECLARE
current_user_id VARCHAR(50);
    is_admin BOOLEAN;
BEGIN
    -- Get current user from session
    current_user_id := current_setting('app.current_user_id', true);
    is_admin := current_setting('app.is_admin', true)::BOOLEAN;

    -- Admin can access all data
    IF is_admin THEN
        RETURN TRUE;
END IF;

    -- User can only access their own data
RETURN current_user_id = target_user_id;
END;
$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to update user position atomically
CREATE OR REPLACE FUNCTION update_user_position(
    p_user_id VARCHAR(50),
    p_tenant_id VARCHAR(50),
    p_symbol VARCHAR(20),
    p_quantity INTEGER,
    p_price DECIMAL(12,4),
    p_side VARCHAR(10)
) RETURNS TABLE(new_quantity BIGINT, new_avg_price DECIMAL(15,4)) AS $
DECLARE
current_qty BIGINT := 0;
    current_avg_price DECIMAL(15,4) := 0;
    quantity_delta INTEGER;
    new_total_value DECIMAL(15,4);
    new_total_qty BIGINT;
BEGIN
    -- Validate user access
    IF NOT validate_user_access(p_user_id) THEN
        RAISE EXCEPTION 'Access denied for user %', p_user_id;
END IF;

    quantity_delta := CASE WHEN p_side = 'BUY' THEN p_quantity ELSE -p_quantity END;

    -- Get current position with row-level locking
SELECT COALESCE(quantity, 0), COALESCE(average_price, 0)
INTO current_qty, current_avg_price
FROM user_positions
WHERE user_id = p_user_id AND tenant_id = p_tenant_id AND symbol = p_symbol
    FOR UPDATE;

new_total_qty := current_qty + quantity_delta;

    -- Calculate new average price
    IF new_total_qty = 0 THEN
        current_avg_price := 0;
    ELSIF (current_qty > 0 AND quantity_delta > 0) OR (current_qty < 0 AND quantity_delta < 0) THEN
        -- Same direction, update average
        new_total_value := (current_qty * current_avg_price) + (quantity_delta * p_price);
        current_avg_price := new_total_value / new_total_qty;
    ELSIF (current_qty * quantity_delta) < 0 THEN
        -- Opposite direction
        IF ABS(quantity_delta) >= ABS(current_qty) THEN
            -- Position reversal or closure
            current_avg_price := CASE WHEN new_total_qty = 0 THEN 0 ELSE p_price END;
END IF;
END IF;

    -- Upsert position
INSERT INTO user_positions (user_id, tenant_id, symbol, quantity, average_price, last_updated, total_trades)
VALUES (p_user_id, p_tenant_id, p_symbol, new_total_qty, current_avg_price, CURRENT_TIMESTAMP, 1)
    ON CONFLICT (user_id, tenant_id, symbol)
    DO UPDATE SET
    quantity = new_total_qty,
               average_price = current_avg_price,
               last_updated = CURRENT_TIMESTAMP,
               total_trades = user_positions.total_trades + 1,
               version = user_positions.version + 1;

RETURN QUERY SELECT new_total_qty, current_avg_price;
END;
$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to get user trading summary
CREATE OR REPLACE FUNCTION get_user_trading_summary(p_user_id VARCHAR(50))
RETURNS TABLE(
    total_positions INTEGER,
    active_positions INTEGER,
    total_trades INTEGER,
    successful_orders BIGINT,
    failed_orders BIGINT,
    total_realized_pnl DECIMAL(15,4),
    total_unrealized_pnl DECIMAL(15,4)
) AS $
BEGIN
    -- Validate user access
    IF NOT validate_user_access(p_user_id) THEN
        RAISE EXCEPTION 'Access denied for user %', p_user_id;
END IF;

RETURN QUERY
    WITH position_stats AS (
        SELECT
            COUNT(*)::INTEGER as total_pos,
            COUNT(CASE WHEN quantity != 0 THEN 1 END)::INTEGER as active_pos,
            SUM(total_trades)::INTEGER as total_trd,
            SUM(realized_pnl) as total_realized,
            SUM(unrealized_pnl) as total_unrealized
        FROM user_positions
        WHERE user_id = p_user_id
    ),
    order_stats AS (
        SELECT
            COUNT(CASE WHEN event_type = 'ORDER_SUCCESS' THEN 1 END) as success_orders,
            COUNT(CASE WHEN event_type = 'ORDER_FAILURE' THEN 1 END) as failed_orders
        FROM user_order_audit
        WHERE user_id = p_user_id
    )
SELECT
    p.total_pos,
    p.active_pos,
    p.total_trd,
    o.success_orders,
    o.failed_orders,
    p.total_realized,
    p.total_unrealized
FROM position_stats p
         CROSS JOIN order_stats o;
END;
$ LANGUAGE plpgsql SECURITY DEFINER;

-- 5. Triggers for Data Integrity and Auditing

-- Trigger to automatically set user context on insert/update
CREATE OR REPLACE FUNCTION set_user_context_trigger()
RETURNS TRIGGER AS $
BEGIN
    -- Ensure user_id is set from current context if not provided
    IF NEW.user_id IS NULL THEN
        NEW.user_id := current_setting('app.current_user_id', true);
END IF;

    -- Ensure tenant_id is set from current context if not provided
    IF NEW.tenant_id IS NULL THEN
        NEW.tenant_id := current_setting('app.current_tenant_id', true);
END IF;

    -- Validate user can only modify their own data
    IF NOT validate_user_access(NEW.user_id) THEN
        RAISE EXCEPTION 'Access denied: cannot modify data for user %', NEW.user_id;
END IF;

RETURN NEW;
END;
$ LANGUAGE plpgsql;

-- Apply trigger to all user tables
CREATE TRIGGER set_user_context_broker_tokens
    BEFORE INSERT OR UPDATE ON user_broker_tokens
                         FOR EACH ROW EXECUTE FUNCTION set_user_context_trigger();

CREATE TRIGGER set_user_context_positions
    BEFORE INSERT OR UPDATE ON user_positions
                         FOR EACH ROW EXECUTE FUNCTION set_user_context_trigger();

CREATE TRIGGER set_user_context_order_audit
    BEFORE INSERT OR UPDATE ON user_order_audit
                         FOR EACH ROW EXECUTE FUNCTION set_user_context_trigger();

-- Trigger for automatic activity logging
CREATE OR REPLACE FUNCTION log_user_activity_trigger()
RETURNS TRIGGER AS $
BEGIN
INSERT INTO user_activity_log (
    user_id,
    tenant_id,
    session_id,
    activity_type,
    activity_details,
    ip_address,
    created_at
) VALUES (
             NEW.user_id,
             NEW.tenant_id,
             current_setting('app.session_id', true),
             TG_TABLE_NAME || '_' || TG_OP,
             jsonb_build_object(
                     'table', TG_TABLE_NAME,
                     'operation', TG_OP,
                     'record_id', NEW.id
             ),
             inet(current_setting('app.client_ip', true)),
             CURRENT_TIMESTAMP
         );

RETURN NEW;
END;
$ LANGUAGE plpgsql;

-- Apply activity logging to sensitive tables
CREATE TRIGGER log_broker_token_activity
    AFTER INSERT OR UPDATE ON user_broker_tokens
                        FOR EACH ROW EXECUTE FUNCTION log_user_activity_trigger();

CREATE TRIGGER log_position_activity
    AFTER INSERT OR UPDATE ON user_positions
                        FOR EACH ROW EXECUTE FUNCTION log_user_activity_trigger();

-- 6. Views for User Data Access

-- User dashboard view
CREATE OR REPLACE VIEW user_dashboard AS
SELECT
    u.user_id,
    u.username,
    u.full_name,
    u.status,
    u.tier,
    COUNT(DISTINCT p.symbol) as active_symbols,
    COUNT(DISTINCT ubt.broker) as connected_brokers,
    SUM(p.realized_pnl + p.unrealized_pnl) as total_pnl,
    MAX(p.last_updated) as last_trading_activity,
    COUNT(CASE WHEN oa.event_type = 'ORDER_SUCCESS' AND oa.created_at >= CURRENT_DATE THEN 1 END) as today_successful_orders,
    COUNT(CASE WHEN oa.event_type = 'ORDER_FAILURE' AND oa.created_at >= CURRENT_DATE THEN 1 END) as today_failed_orders
FROM users u
         LEFT JOIN user_positions p ON u.user_id = p.user_id
         LEFT JOIN user_broker_tokens ubt ON u.user_id = ubt.user_id AND ubt.is_active = true
         LEFT JOIN user_order_audit oa ON u.user_id = oa.user_id
WHERE u.user_id = current_setting('app.current_user_id', true)
GROUP BY u.user_id, u.username, u.full_name, u.status, u.tier;

-- User risk summary view
CREATE OR REPLACE VIEW user_risk_summary AS
SELECT
    u.user_id,
    u.risk_profile,
    COALESCE(SUM(ABS(p.quantity * p.average_price)), 0) as total_position_value,
    COUNT(CASE WHEN p.quantity != 0 THEN 1 END) as active_positions,
    COUNT(CASE WHEN oa.event_type = 'ORDER_FAILURE' AND oa.created_at >= CURRENT_DATE THEN 1 END) as today_failures,
    COALESCE(MAX(rl.limit_value) FILTER (WHERE rl.limit_type = 'MAX_ORDER_VALUE'), 0) as max_order_limit,
    COALESCE(MAX(rl.limit_value) FILTER (WHERE rl.limit_type = 'DAILY_LOSS_LIMIT'), 0) as daily_loss_limit
FROM users u
         LEFT JOIN user_positions p ON u.user_id = p.user_id
         LEFT JOIN user_order_audit oa ON u.user_id = oa.user_id
         LEFT JOIN user_risk_limits rl ON u.user_id = rl.user_id AND rl.is_active = true
WHERE u.user_id = current_setting('app.current_user_id', true)
GROUP BY u.user_id, u.risk_profile;

-- 7. Cleanup and Maintenance Functions

-- Function to cleanup expired sessions
CREATE OR REPLACE FUNCTION cleanup_expired_sessions()
RETURNS INTEGER AS $
DECLARE
deleted_count INTEGER;
BEGIN
UPDATE user_sessions
SET is_active = false, logout_reason = 'TIMEOUT'
WHERE expires_at < CURRENT_TIMESTAMP AND is_active = true;

GET DIAGNOSTICS deleted_count = ROW_COUNT;

RETURN deleted_count;
END;
$ LANGUAGE plpgsql;

-- Function to archive old audit data
CREATE OR REPLACE FUNCTION archive_old_audit_data(days_to_keep INTEGER DEFAULT 365)
RETURNS INTEGER AS $
DECLARE
deleted_count INTEGER;
BEGIN
DELETE FROM user_activity_log
WHERE created_at < CURRENT_TIMESTAMP - (days_to_keep || ' days')::INTERVAL;

GET DIAGNOSTICS deleted_count = ROW_COUNT;

-- Archive order audit older than specified days but keep summary stats
DELETE FROM user_order_audit
WHERE created_at < CURRENT_TIMESTAMP - (days_to_keep || ' days')::INTERVAL
    AND event_type NOT IN ('ORDER_SUCCESS', 'ORDER_FAILURE'); -- Keep these for reporting

RETURN deleted_count;
END;
$ LANGUAGE plpgsql;