import json
import sqlite3
import time
from contextlib import contextmanager
from pathlib import Path

from .models import Route


class Storage:
    """SQLite, one connection per operation; never keep a transaction across an await."""

    def __init__(self, path: str):
        self.path = path
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as db:
            db.executescript("""
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS plans (
                    id TEXT PRIMARY KEY, owner TEXT NOT NULL, data TEXT NOT NULL,
                    expires REAL NOT NULL
                );
                CREATE TABLE IF NOT EXISTS routes (
                    id TEXT PRIMARY KEY, owner TEXT NOT NULL, plan_id TEXT NOT NULL,
                    data TEXT NOT NULL, created REAL NOT NULL,
                    UNIQUE(owner, plan_id)
                );
                CREATE INDEX IF NOT EXISTS routes_owner ON routes(owner, created DESC);
                CREATE TABLE IF NOT EXISTS http_cache (
                    key TEXT PRIMARY KEY, data TEXT NOT NULL, expires REAL NOT NULL
                );
            """)

    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=10)
        try:
            db.row_factory = sqlite3.Row
            with db:
                yield db
        finally:
            db.close()

    def store_plan(self, owner: str, route: Route):
        with self.connect() as db:
            db.execute("DELETE FROM plans WHERE expires < ?", (time.time(),))
            db.execute("INSERT INTO plans VALUES (?, ?, ?, ?)",
                       (route.id, owner, route.model_dump_json(), time.time() + 86400))

    def get_plan(self, owner: str, plan_id: str) -> Route | None:
        with self.connect() as db:
            row = db.execute("SELECT data FROM plans WHERE id=? AND owner=? AND expires>?",
                             (plan_id, owner, time.time())).fetchone()
        return Route.model_validate_json(row["data"]) if row else None

    def save_route(self, owner: str, plan_id: str, route: Route) -> Route:
        with self.connect() as db:
            db.execute("INSERT OR IGNORE INTO routes VALUES (?, ?, ?, ?, ?)",
                       (route.id, owner, plan_id, route.model_dump_json(), time.time()))
            row = db.execute("SELECT data FROM routes WHERE owner=? AND plan_id=?",
                             (owner, plan_id)).fetchone()
        return Route.model_validate_json(row["data"])

    def list_routes(self, owner: str) -> list[Route]:
        with self.connect() as db:
            rows = db.execute("SELECT data FROM routes WHERE owner=? ORDER BY created DESC",
                              (owner,)).fetchall()
        return [Route.model_validate_json(row["data"]) for row in rows]

    def get_route(self, owner: str, route_id: str) -> Route | None:
        with self.connect() as db:
            row = db.execute("SELECT data FROM routes WHERE id=? AND owner=?",
                             (route_id, owner)).fetchone()
        return Route.model_validate_json(row["data"]) if row else None

    def delete_route(self, owner: str, route_id: str) -> bool:
        with self.connect() as db:
            return db.execute("DELETE FROM routes WHERE id=? AND owner=?", (route_id, owner)).rowcount > 0

    def cache_get(self, key: str):
        with self.connect() as db:
            row = db.execute("SELECT data FROM http_cache WHERE key=? AND expires>?",
                             (key, time.time())).fetchone()
        return json.loads(row["data"]) if row else None

    def cache_put(self, key: str, data, ttl: int):
        with self.connect() as db:
            db.execute("DELETE FROM http_cache WHERE expires < ?", (time.time(),))
            db.execute("INSERT OR REPLACE INTO http_cache VALUES (?, ?, ?)",
                       (key, json.dumps(data), time.time() + ttl))
