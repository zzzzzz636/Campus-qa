PRAGMA foreign_keys = ON;

DROP TABLE IF EXISTS knowledge_doc;
DROP TABLE IF EXISTS contribution;
DROP TABLE IF EXISTS query_log;
DROP TABLE IF EXISTS faq;
DROP TABLE IF EXISTS admin;
DROP TABLE IF EXISTS category;

CREATE TABLE category (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE faq (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    category TEXT NOT NULL,
    source TEXT,
    view_count INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE query_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    query_text TEXT NOT NULL,
    matched_faq_id INTEGER,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (matched_faq_id) REFERENCES faq(id)
);

CREATE TABLE contribution (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    question TEXT NOT NULL,
    suggested_answer TEXT,
    category TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL
);

CREATE TABLE knowledge_doc (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    category TEXT,
    source_url TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_faq_category ON faq(category);
CREATE INDEX idx_faq_question ON faq(question);
CREATE INDEX idx_query_log_created_at ON query_log(created_at);
CREATE INDEX idx_contribution_status ON contribution(status);
