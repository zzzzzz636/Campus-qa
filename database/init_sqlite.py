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
        connection.executescript(SAMPLE_DATA_PATH.read_text(encoding="utf-8"))
        connection.commit()
    finally:
        connection.close()

    print(f"SQLite database initialized: {DB_PATH}")


if __name__ == "__main__":
    main()

