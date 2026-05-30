CREATE TABLE category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE faq (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question VARCHAR(255) NOT NULL,
    answer TEXT NOT NULL,
    category_id BIGINT,
    source_url VARCHAR(500),
    source_name VARCHAR(100),
    view_count INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_faq_category FOREIGN KEY (category_id) REFERENCES category(id)
);

CREATE TABLE query_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    query_text VARCHAR(255) NOT NULL,
    matched_faq_id BIGINT,
    query_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_query_log_faq FOREIGN KEY (matched_faq_id) REFERENCES faq(id)
);

CREATE TABLE contribution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question VARCHAR(255) NOT NULL,
    suggested_answer TEXT,
    category_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    audit_time DATETIME,
    audit_comment VARCHAR(255),
    CONSTRAINT fk_contribution_category FOREIGN KEY (category_id) REFERENCES category(id)
);

CREATE TABLE admin_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'ADMIN'
);

CREATE TABLE knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    category_id BIGINT,
    source_url VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_knowledge_category FOREIGN KEY (category_id) REFERENCES category(id)
);

