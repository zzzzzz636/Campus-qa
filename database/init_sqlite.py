from pathlib import Path
import sqlite3


ROOT = Path(__file__).resolve().parents[1]
DB_PATH = ROOT / "database" / "campusqa.db"
SCHEMA_PATH = ROOT / "database" / "schema.sql"
SAMPLE_DATA_PATH = ROOT / "database" / "sample-data.sql"


def main():
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(DB_PATH)
    try:
        connection.executescript(SCHEMA_PATH.read_text(encoding="utf-8"))
        migrate_existing_database(connection)
        connection.executescript(SAMPLE_DATA_PATH.read_text(encoding="utf-8"))
        connection.commit()
    finally:
        connection.close()

    print(f"SQLite database initialized: {DB_PATH}")


def migrate_existing_database(connection):
    columns = {
        row[1]
        for row in connection.execute("PRAGMA table_info(knowledge_doc)").fetchall()
    }
    if "category" not in columns:
        connection.execute("ALTER TABLE knowledge_doc ADD COLUMN category TEXT")
        connection.execute(
            "UPDATE knowledge_doc SET category = ("
            "SELECT name FROM category WHERE category.id = knowledge_doc.category_id"
            ") WHERE category IS NULL AND category_id IS NOT NULL"
        )
    if "source_type" not in columns:
        connection.execute(
            "ALTER TABLE knowledge_doc ADD COLUMN source_type TEXT NOT NULL DEFAULT 'MANUAL'"
        )

    query_log_columns = {
        row[1]
        for row in connection.execute("PRAGMA table_info(query_log)").fetchall()
    }
    if "matched_type" not in query_log_columns:
        connection.execute(
            "ALTER TABLE query_log ADD COLUMN matched_type TEXT NOT NULL DEFAULT 'NONE'"
        )
        connection.execute(
            "UPDATE query_log SET matched_type = CASE "
            "WHEN matched_faq_id IS NOT NULL THEN 'FAQ' "
            "ELSE 'NONE' END"
        )
    if "matched_knowledge_id" not in query_log_columns:
        connection.execute("ALTER TABLE query_log ADD COLUMN matched_knowledge_id INTEGER")
    if "match_score" not in query_log_columns:
        connection.execute(
            "ALTER TABLE query_log ADD COLUMN match_score INTEGER NOT NULL DEFAULT 0"
        )

    connection.execute(
        "CREATE TABLE IF NOT EXISTS import_history ("
        "id INTEGER PRIMARY KEY AUTOINCREMENT,"
        "file_name TEXT NOT NULL,"
        "import_type TEXT NOT NULL,"
        "success_count INTEGER NOT NULL DEFAULT 0,"
        "fail_count INTEGER NOT NULL DEFAULT 0,"
        "message TEXT,"
        "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
        ")"
    )
    connection.execute(
        "CREATE INDEX IF NOT EXISTS idx_knowledge_doc_category ON knowledge_doc(category)"
    )
    connection.execute(
        "CREATE INDEX IF NOT EXISTS idx_knowledge_doc_source_type ON knowledge_doc(source_type)"
    )
    connection.execute(
        "CREATE INDEX IF NOT EXISTS idx_import_history_created_at ON import_history(created_at)"
    )
    connection.execute(
        "CREATE INDEX IF NOT EXISTS idx_query_log_matched_type ON query_log(matched_type)"
    )


if __name__ == "__main__":
    main()
