"""Room's exported schema JSON as the only source of the asset DDL (plan §5.1, §5.3).

Every statement comes from `app/schemas/<DictionaryDatabase fqcn>/<version>.json`: the entities'
`createSql` (with `${TABLE_NAME}` substituted), their indices, the FTS content-sync triggers, and the
version for `PRAGMA user_version`. Room's `setupQueries` (room_master_table) are deliberately not run:
the asset has no room_master_table, so Room validates the schema on first open.
"""

from __future__ import annotations

import json
import re
import sqlite3
from dataclasses import dataclass
from pathlib import Path

TABLE_NAME_PLACEHOLDER = "${TABLE_NAME}"

# Schema files are named by their database version: "1.json", "2.json", …
_SCHEMA_FILE_NAME = re.compile(r"^(?P<version>[0-9]+)\.json$")


@dataclass(frozen=True, slots=True)
class Column:
    name: str
    affinity: str
    not_null: bool
    default: str | None


@dataclass(frozen=True, slots=True)
class Table:
    name: str
    create_sql: str
    column_defs: tuple[Column, ...]
    primary_key: tuple[str, ...]
    index_sqls: tuple[str, ...]
    trigger_sqls: tuple[str, ...]
    is_fts: bool

    @property
    def columns(self) -> tuple[str, ...]:
        return tuple(column.name for column in self.column_defs)

    def insert_sql(self) -> str:
        """`INSERT INTO `table` (`c1`, …) VALUES (?, …)` with the schema's columns in schema order."""
        names = ", ".join(f"`{name}`" for name in self.columns)
        placeholders = ", ".join("?" for _ in self.columns)
        return f"INSERT INTO `{self.name}` ({names}) VALUES ({placeholders})"


@dataclass(frozen=True, slots=True)
class Schema:
    version: int
    identity_hash: str
    tables: tuple[Table, ...]

    def table(self, name: str) -> Table:
        for table in self.tables:
            if table.name == name:
                return table
        raise KeyError(f"no table {name!r} in schema version {self.version}")

    def ddl(self) -> list[str]:
        """All DDL in execution order: tables (schema order), then indices, then triggers."""
        return [
            *(table.create_sql for table in self.tables),
            *(sql for table in self.tables for sql in table.index_sqls),
            *(sql for table in self.tables for sql in table.trigger_sqls),
        ]


def latest_schema_path(schema_dir: Path) -> Path:
    """The `<version>.json` with the highest version in `schema_dir`."""
    candidates = [
        (int(match.group("version")), path)
        for path in schema_dir.iterdir()
        if (match := _SCHEMA_FILE_NAME.match(path.name)) and path.is_file()
    ]
    if not candidates:
        raise FileNotFoundError(f"no <version>.json schema in {schema_dir}")
    return max(candidates)[1]


def load_schema(path: Path) -> Schema:
    """Parse a Room schema export into tables with ready-to-run SQL."""
    database = json.loads(path.read_text(encoding="utf-8"))["database"]
    return Schema(
        version=int(database["version"]),
        identity_hash=database["identityHash"],
        tables=tuple(_table(entity) for entity in database["entities"]),
    )


def create_database(conn: sqlite3.Connection, schema: Schema) -> None:
    """Execute the schema's DDL on an empty database and set `PRAGMA user_version`."""
    for statement in schema.ddl():
        conn.execute(statement)
    set_user_version(conn, schema.version)


def set_user_version(conn: sqlite3.Connection, version: int) -> None:
    conn.execute(f"PRAGMA user_version = {int(version)}")


def _table(entity: dict) -> Table:
    name = entity["tableName"]

    def substitute(sql: str) -> str:
        return sql.replace(TABLE_NAME_PLACEHOLDER, name)

    return Table(
        name=name,
        create_sql=substitute(entity["createSql"]),
        column_defs=tuple(
            Column(
                name=field["columnName"],
                affinity=field["affinity"],
                not_null=bool(field.get("notNull", False)),
                default=field.get("defaultValue"),
            )
            for field in entity["fields"]
        ),
        primary_key=tuple(entity.get("primaryKey", {}).get("columnNames", ())),
        index_sqls=tuple(substitute(index["createSql"]) for index in entity.get("indices", ())),
        trigger_sqls=tuple(substitute(sql) for sql in entity.get("contentSyncTriggers", ())),
        is_fts="ftsVersion" in entity,
    )
