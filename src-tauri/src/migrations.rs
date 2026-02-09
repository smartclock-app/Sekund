use tauri_plugin_sql::{Migration, MigrationKind};

pub fn get_migrations() -> Vec<Migration> {
    vec![Migration {
        version: 1,
        description: "Create initial tables",
        sql: include_str!("./migrations/initial_tables.sql"),
        kind: MigrationKind::Up,
    }]
}
