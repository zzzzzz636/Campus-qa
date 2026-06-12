PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS category (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS faq (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    category_id INTEGER,
    source TEXT,
    source_url TEXT,
    view_count INTEGER NOT NULL DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES category(id)
);

CREATE TABLE IF NOT EXISTS query_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    query_text TEXT NOT NULL,
    matched_type TEXT NOT NULL DEFAULT 'NONE',
    matched_faq_id INTEGER,
    matched_knowledge_id INTEGER,
    match_score INTEGER NOT NULL DEFAULT 0,
    found INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (matched_faq_id) REFERENCES faq(id),
    FOREIGN KEY (matched_knowledge_id) REFERENCES knowledge_doc(id),
    CHECK (matched_type IN ('FAQ', 'KNOWLEDGE_DOC', 'NONE'))
);

CREATE TABLE IF NOT EXISTS contribution (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    question TEXT NOT NULL,
    suggested_answer TEXT,
    category_id INTEGER,
    status TEXT NOT NULL DEFAULT 'PENDING',
    review_comment TEXT,
    reviewed_at TEXT,
    reviewed_by INTEGER,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES category(id),
    FOREIGN KEY (reviewed_by) REFERENCES admin(id),
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE TABLE IF NOT EXISTS admin (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name TEXT,
    role TEXT NOT NULL DEFAULT 'ADMIN',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TEXT
);

CREATE TABLE IF NOT EXISTS knowledge_doc (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    category TEXT,
    source_url TEXT,
    source_type TEXT NOT NULL DEFAULT 'MANUAL',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS import_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_name TEXT NOT NULL,
    import_type TEXT NOT NULL,
    success_count INTEGER NOT NULL DEFAULT 0,
    fail_count INTEGER NOT NULL DEFAULT 0,
    message TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_faq_category_id ON faq(category_id);
CREATE INDEX IF NOT EXISTS idx_faq_question ON faq(question);
CREATE INDEX IF NOT EXISTS idx_faq_enabled ON faq(enabled);
CREATE INDEX IF NOT EXISTS idx_query_log_created_at ON query_log(created_at);
CREATE INDEX IF NOT EXISTS idx_query_log_found ON query_log(found);
CREATE INDEX IF NOT EXISTS idx_query_log_matched_type ON query_log(matched_type);
CREATE INDEX IF NOT EXISTS idx_contribution_status ON contribution(status);
CREATE INDEX IF NOT EXISTS idx_contribution_category_id ON contribution(category_id);
CREATE INDEX IF NOT EXISTS idx_import_history_created_at ON import_history(created_at);
