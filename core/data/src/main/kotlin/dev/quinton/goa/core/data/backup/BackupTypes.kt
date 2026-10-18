package dev.quinton.goa.core.data.backup

/*
 * The backup file itself is pure and lives in core:domain (`dev.quinton.goa.core.domain.backup`),
 * so the hub's web export writes the same file (ADR-030 §1). These names keep the app's imports
 * as they were.
 */

typealias BackupDocument = dev.quinton.goa.core.domain.backup.BackupDocument
typealias BackupCodec = dev.quinton.goa.core.domain.backup.BackupCodec
typealias BackupSummary = dev.quinton.goa.core.domain.backup.BackupSummary
typealias ImportMode = dev.quinton.goa.core.domain.backup.ImportMode
typealias InvalidBackupException = dev.quinton.goa.core.domain.backup.InvalidBackupException
