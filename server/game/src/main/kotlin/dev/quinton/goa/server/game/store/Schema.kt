package dev.quinton.goa.server.game.store

/**
 * The game server's tables (docs/ONLINE.md "Storage"). There is no rating column anywhere: the board is
 * derived from `matches`, and deleting everything else reproduces it. Every statement is safe to repeat.
 *
 * Accounts are anonymised, never removed, so `game_seats.account_id` needs no foreign key, and a game
 * outlives its room's row, so `games.room_code` and `rooms.game_id` have none either.
 */
internal object Schema {
    const val VERSION: Int = 1

    val STATEMENTS: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS schema_version (
            version INTEGER PRIMARY KEY,
            applied_at INTEGER NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS accounts (
            id TEXT PRIMARY KEY,
            display_name TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            deleted_at INTEGER
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS tokens (
            hash TEXT PRIMARY KEY,
            account_id TEXT NOT NULL REFERENCES accounts(id),
            created_at INTEGER NOT NULL,
            last_seen_at INTEGER NOT NULL,
            revoked_at INTEGER
        )
        """,
        "CREATE INDEX IF NOT EXISTS tokens_by_account ON tokens(account_id)",
        """
        CREATE TABLE IF NOT EXISTS recovery_codes (
            hash TEXT PRIMARY KEY,
            account_id TEXT NOT NULL REFERENCES accounts(id),
            created_at INTEGER NOT NULL,
            used_at INTEGER
        )
        """,
        "CREATE INDEX IF NOT EXISTS recovery_codes_by_account ON recovery_codes(account_id)",
        """
        CREATE TABLE IF NOT EXISTS rooms (
            code TEXT PRIMARY KEY,
            host_account TEXT NOT NULL REFERENCES accounts(id),
            map_id TEXT NOT NULL,
            length TEXT NOT NULL,
            team_size INTEGER NOT NULL,
            ranked INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            expires_at INTEGER NOT NULL,
            game_id TEXT,
            status TEXT NOT NULL CHECK (status IN ('open', 'started', 'closed'))
        )
        """,
        "CREATE INDEX IF NOT EXISTS rooms_by_status ON rooms(status)",
        """
        CREATE TABLE IF NOT EXISTS room_seats (
            code TEXT NOT NULL REFERENCES rooms(code) ON DELETE CASCADE,
            team TEXT NOT NULL,
            seat_index INTEGER NOT NULL,
            hero_id TEXT,
            account_id TEXT REFERENCES accounts(id),
            ready INTEGER NOT NULL,
            PRIMARY KEY (code, team, seat_index)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS games (
            id TEXT PRIMARY KEY,
            room_code TEXT NOT NULL,
            setup_json TEXT NOT NULL,
            start_save_json TEXT NOT NULL,
            rules_build TEXT NOT NULL,
            engine_version INTEGER NOT NULL,
            ranked INTEGER NOT NULL,
            status TEXT NOT NULL CHECK (status IN ('running', 'finished', 'voided')),
            void_reason TEXT,
            created_at INTEGER NOT NULL,
            finished_at INTEGER,
            away_since INTEGER
        )
        """,
        "CREATE INDEX IF NOT EXISTS games_by_status ON games(status, created_at, id)",
        "CREATE INDEX IF NOT EXISTS games_by_room ON games(room_code)",
        """
        CREATE TABLE IF NOT EXISTS game_seats (
            game_id TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
            hero_id TEXT NOT NULL,
            account_id TEXT NOT NULL,
            team TEXT NOT NULL,
            handicap INTEGER NOT NULL,
            seat_order INTEGER NOT NULL,
            left_at INTEGER,
            PRIMARY KEY (game_id, hero_id)
        )
        """,
        "CREATE INDEX IF NOT EXISTS game_seats_by_account ON game_seats(account_id)",
        """
        CREATE TABLE IF NOT EXISTS answers (
            game_id TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
            seq INTEGER NOT NULL,
            decision_id INTEGER NOT NULL,
            seat TEXT NOT NULL,
            choice_json TEXT NOT NULL,
            origin TEXT NOT NULL CHECK (origin IN ('human', 'clock', 'auto')),
            at INTEGER NOT NULL,
            events_hash TEXT NOT NULL,
            state_hash TEXT,
            PRIMARY KEY (game_id, seq)
        ) WITHOUT ROWID
        """,
        """
        CREATE TABLE IF NOT EXISTS locks (
            game_id TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
            seat TEXT NOT NULL,
            ordinal INTEGER NOT NULL,
            round INTEGER NOT NULL,
            turn INTEGER NOT NULL,
            grp TEXT NOT NULL,
            choice_json TEXT NOT NULL,
            origin TEXT NOT NULL CHECK (origin IN ('human', 'clock', 'auto')),
            at INTEGER NOT NULL,
            PRIMARY KEY (game_id, seat, ordinal)
        ) WITHOUT ROWID
        """,
        """
        CREATE TABLE IF NOT EXISTS matches (
            id TEXT PRIMARY KEY,
            played_at INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            match_json TEXT NOT NULL
        )
        """,
        "CREATE INDEX IF NOT EXISTS matches_in_replay_order ON matches(played_at, created_at, id)",
        """
        CREATE TABLE IF NOT EXISTS publish_state (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL,
            updated_at INTEGER NOT NULL
        )
        """,
    )
}
