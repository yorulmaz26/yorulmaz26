import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:cryptography/cryptography.dart';
import 'package:drift/drift.dart';
import 'package:path/path.dart' as p;

import '../database/app_database.dart';
import 'crypto_service.dart';
import 'media_service.dart';

enum RestoreMode { overwrite, merge }

class BackupException implements Exception {
  BackupException(this.message);
  final String message;
  @override
  String toString() => message;
}

/// Yedek dosyasının çözülmüş ve doğrulanmış içeriği.
class BackupContents {
  BackupContents({
    required this.schemaVersion,
    required this.createdAt,
    required this.tables,
    required this.mediaFiles,
    required this.backupDataKey,
  });

  final int schemaVersion;
  final DateTime createdAt;
  final Map<String, List<Map<String, Object?>>> tables;
  final Map<String, Uint8List> mediaFiles; // göreli yol → içerik
  final String backupDataKey;

  int count(String table) => tables[table]?.length ?? 0;
}

/// Şifreli yedekleme ve geri yükleme.
///
/// Dosya biçimi (.hcyedek):
///   "HCBACKUP" (8 bayt) | sürüm (1 bayt) | PBKDF2 tekrar sayısı (4 bayt, BE)
///   | tuz (16 bayt) | AES-256-GCM( nonce(12) | şifreli ZIP | MAC(16) )
/// ZIP içeriği: manifest.json (SHA-256 özetleri), data.json (tüm tablolar),
/// media/* (fotoğraf ve ses dosyaları).
class BackupService {
  BackupService(this.db, this.crypto, this.media);

  final AppDatabase db;
  final CryptoService crypto;
  final MediaService media;

  static const magic = 'HCBACKUP';
  static const formatVersion = 1;
  static const fileExtension = 'hcyedek';

  /// Yedeğin yabancı anahtar eşlemesi (birleştirme modunda id yeniden
  /// numaralandırması için): tablo → {sütun: hedef tablo}
  static const _fks = <String, Map<String, String>>{
    'dreams': {'category_id': 'dream_categories'},
    'dream_milestones': {'dream_id': 'dreams'},
    'dream_media': {'dream_id': 'dreams'},
    'goals': {'dream_id': 'dreams'},
    'tasks': {'goal_id': 'goals', 'dream_id': 'dreams'},
    'habits': {'dream_id': 'dreams'},
    'habit_completions': {'habit_id': 'habits'},
    'savings_goals': {'dream_id': 'dreams'},
    'savings_transactions': {'savings_goal_id': 'savings_goals'},
    'journal_media': {'entry_id': 'journal_entries', 'capsule_id': 'time_capsules'},
    'garden_trees': {'dream_id': 'dreams'},
    'focus_sessions': {'goal_id': 'goals', 'dream_id': 'dreams'},
  };

  static const _reminderRef = <String, String>{
    'task': 'tasks',
    'habit': 'habits',
    'milestone': 'dream_milestones',
    'savings': 'savings_goals',
    'capsule': 'time_capsules',
    'dream': 'dreams',
    'goal': 'goals',
  };

  static const _activityRef = <String, String>{
    'task': 'tasks',
    'milestone': 'dream_milestones',
    'dream': 'dreams',
    'dream_created': 'dreams',
    'habit': 'habits',
    'deposit': 'savings_goals',
    'focus': 'focus_sessions',
    'journal': 'journal_entries',
    'goal': 'goals',
    'balance': 'life_balance_entries',
  };

  // ------------------------------ Yedekleme ------------------------------
  Future<Uint8List> createBackup(String password) async {
    if (password.length < 6) throw BackupException('Yedek parolası en az 6 karakter olmalı.');
    final problems = await db.integrityProblems();
    if (problems.isNotEmpty) {
      throw BackupException('Veritabanı bütünlük kontrolü başarısız: ${problems.first}');
    }

    final tables = <String, List<Map<String, Object?>>>{};
    for (final t in AppDatabase.tableOrder) {
      final rows = await db.customSelect('SELECT * FROM "$t"').get();
      tables[t] = rows.map((r) => Map<String, Object?>.from(r.data)).toList();
    }
    final dataJson = utf8.encode(jsonEncode({'tables': tables}));

    final archive = Archive();
    final fileHashes = <String, String>{};
    final mediaDir = await media.mediaDir();
    if (await mediaDir.exists()) {
      await for (final f in mediaDir.list(recursive: true)) {
        if (f is! File) continue;
        final rel = p.join(MediaService.folder, p.relative(f.path, from: mediaDir.path)).replaceAll(r'\', '/');
        final bytes = await f.readAsBytes();
        fileHashes[rel] = await CryptoService.sha256Hex(bytes);
        archive.addFile(ArchiveFile.bytes(rel, bytes));
      }
    }

    final manifest = {
      'app': 'hayallerim_cepte',
      'format': formatVersion,
      'schemaVersion': db.schemaVersion,
      'createdAt': DateTime.now().toIso8601String(),
      'dataSha256': await CryptoService.sha256Hex(dataJson),
      'files': fileHashes,
      'counts': {for (final e in tables.entries) e.key: e.value.length},
      'dataKey': await crypto.exportDataKey(),
    };
    archive.addFile(ArchiveFile.bytes('data.json', dataJson));
    archive.addFile(ArchiveFile.string('manifest.json', jsonEncode(manifest)));
    final zip = ZipEncoder().encodeBytes(archive);

    final salt = CryptoService.randomBytes(16);
    final key = await CryptoService.deriveKey(password, salt);
    final enc = await crypto.encryptBytes(zip, key);

    final header = BytesBuilder()
      ..add(ascii.encode(magic))
      ..addByte(formatVersion)
      ..add(_u32(CryptoService.pbkdf2Iterations))
      ..add(salt)
      ..add(enc);
    return header.toBytes();
  }

  static List<int> _u32(int v) => [(v >> 24) & 0xff, (v >> 16) & 0xff, (v >> 8) & 0xff, v & 0xff];

  // ------------------------- Çözme ve doğrulama --------------------------
  Future<BackupContents> open(Uint8List bytes, String password) async {
    if (bytes.length < 8 + 1 + 4 + 16 + 28 || ascii.decode(bytes.sublist(0, 8), allowInvalid: true) != magic) {
      throw BackupException('Bu dosya bir Hayallerim Cepte yedeği değil.');
    }
    final version = bytes[8];
    if (version > formatVersion) {
      throw BackupException('Bu yedek uygulamanın daha yeni bir sürümüyle oluşturulmuş. Lütfen uygulamayı güncelleyin.');
    }
    final iterations = (bytes[9] << 24) | (bytes[10] << 16) | (bytes[11] << 8) | bytes[12];
    final salt = bytes.sublist(13, 29);
    final key = await CryptoService.deriveKey(password, salt, iterations: iterations);
    List<int> zip;
    try {
      zip = await crypto.decryptBytes(bytes.sublist(29), key);
    } on SecretBoxAuthenticationError {
      throw BackupException('Parola yanlış ya da dosya bozulmuş.');
    }

    final archive = ZipDecoder().decodeBytes(zip);
    final manifestFile = archive.findFile('manifest.json');
    final dataFile = archive.findFile('data.json');
    if (manifestFile == null || dataFile == null) throw BackupException('Yedek dosyası eksik (manifest/data).');
    final manifest = jsonDecode(utf8.decode(manifestFile.content)) as Map<String, dynamic>;
    final schema = manifest['schemaVersion'] as int;
    if (schema > db.schemaVersion) {
      throw BackupException('Yedek daha yeni bir veritabanı sürümüne ait ($schema). Uygulamayı güncelleyin.');
    }
    final dataBytes = dataFile.content;
    if (await CryptoService.sha256Hex(dataBytes) != manifest['dataSha256']) {
      throw BackupException('Veri bütünlüğü doğrulanamadı (data.json özeti uyuşmuyor).');
    }
    final files = <String, Uint8List>{};
    final hashes = (manifest['files'] as Map<String, dynamic>).cast<String, String>();
    for (final entry in hashes.entries) {
      final f = archive.findFile(entry.key);
      if (f == null) throw BackupException('Medya dosyası eksik: ${entry.key}');
      final c = f.content;
      if (await CryptoService.sha256Hex(c) != entry.value) {
        throw BackupException('Medya dosyası bozuk: ${entry.key}');
      }
      if (entry.key.contains('..')) throw BackupException('Geçersiz dosya yolu.');
      files[entry.key] = c;
    }
    final data = jsonDecode(utf8.decode(dataBytes)) as Map<String, dynamic>;
    final tables = <String, List<Map<String, Object?>>>{};
    (data['tables'] as Map<String, dynamic>).forEach((k, v) {
      tables[k] = (v as List).map((e) => (e as Map).cast<String, Object?>()).toList();
    });
    return BackupContents(
      schemaVersion: schema,
      createdAt: DateTime.parse(manifest['createdAt'] as String),
      tables: tables,
      mediaFiles: files,
      backupDataKey: manifest['dataKey'] as String,
    );
  }

  // ------------------------------ Geri yükleme ---------------------------
  Future<void> restore(BackupContents b, RestoreMode mode) async {
    final backupKey = SecretKey(base64Decode(b.backupDataKey));
    final sameKey = b.backupDataKey == await crypto.exportDataKey();

    Future<Object?> reencrypt(Object? v) async {
      if (sameKey || v is! String || !CryptoService.isEncrypted(v)) return v;
      final plain = await crypto.decryptWithKey(v, backupKey);
      return crypto.encrypt(plain);
    }

    // Tablolardaki şifreli metinleri yerel anahtara çevir (işlemden önce).
    final prepared = <String, List<Map<String, Object?>>>{};
    for (final t in AppDatabase.tableOrder) {
      final rows = b.tables[t] ?? const [];
      final out = <Map<String, Object?>>[];
      for (final r in rows) {
        final m = <String, Object?>{};
        for (final e in r.entries) {
          m[e.key] = await reencrypt(e.value);
        }
        out.add(m);
      }
      prepared[t] = out;
    }
    final columns = await _columns();

    // Medyayı önce hazırlık klasörüne yaz.
    final mediaDir = await media.mediaDir();
    final base = mediaDir.parent;
    final staging = Directory(p.join(base.path, 'media_restore_tmp'));
    if (await staging.exists()) await staging.delete(recursive: true);
    await staging.create(recursive: true);
    for (final e in b.mediaFiles.entries) {
      final rel = p.relative(e.key, from: MediaService.folder);
      final f = File(p.join(staging.path, rel));
      await f.parent.create(recursive: true);
      await f.writeAsBytes(e.value, flush: true);
    }

    try {
      await db.transaction(() async {
        await db.customStatement('PRAGMA defer_foreign_keys = ON');
        if (mode == RestoreMode.overwrite) {
          for (final t in AppDatabase.tableOrder.reversed) {
            await db.customStatement('DELETE FROM "$t"');
          }
          for (final t in AppDatabase.tableOrder) {
            for (final row in prepared[t]!) {
              await _insert(t, row, columns[t]!, orIgnore: false);
            }
          }
        } else {
          await _merge(prepared, columns);
        }
      });
    } catch (e) {
      await staging.delete(recursive: true);
      if (e is BackupException) rethrow;
      throw BackupException('Geri yükleme başarısız oldu, verileriniz değiştirilmedi. ($e)');
    }

    // Veritabanı başarılı: medyayı yerleştir.
    if (mode == RestoreMode.overwrite) {
      final old = Directory(p.join(base.path, 'media_old_tmp'));
      if (await old.exists()) await old.delete(recursive: true);
      await mediaDir.rename(old.path);
      await staging.rename(mediaDir.path);
      await old.delete(recursive: true);
    } else {
      await for (final f in staging.list(recursive: true)) {
        if (f is! File) continue;
        final target = File(p.join(mediaDir.path, p.relative(f.path, from: staging.path)));
        if (!await target.exists()) {
          await target.parent.create(recursive: true);
          await f.copy(target.path);
        }
      }
      await staging.delete(recursive: true);
    }
  }

  Future<Map<String, Set<String>>> _columns() async {
    final out = <String, Set<String>>{};
    for (final t in AppDatabase.tableOrder) {
      final rows = await db.customSelect('PRAGMA table_info("$t")').get();
      out[t] = rows.map((r) => r.data['name'] as String).toSet();
    }
    return out;
  }

  /// Yalnızca mevcut şemada olan sütunlar yazılır (eski yedekler için
  /// eksik sütunlar varsayılan değerini alır).
  Future<int> _insert(String table, Map<String, Object?> row, Set<String> cols, {required bool orIgnore}) async {
    final keys = row.keys.where(cols.contains).toList();
    if (keys.isEmpty) return 0;
    final sql = 'INSERT ${orIgnore ? 'OR IGNORE ' : ''}INTO "$table" (${keys.map((k) => '"$k"').join(',')}) '
        'VALUES (${List.filled(keys.length, '?').join(',')})';
    return db.customInsert(sql, variables: [for (final k in keys) _var(row[k])]);
  }

  static Variable _var(Object? v) {
    if (v == null) return const Variable(null);
    if (v is int) return Variable.withInt(v);
    if (v is double) return Variable.withReal(v);
    if (v is bool) return Variable.withBool(v);
    if (v is String) return Variable.withString(v);
    if (v is List<int>) return Variable.withBlob(Uint8List.fromList(v));
    return Variable.withString(v.toString());
  }

  Future<void> _merge(Map<String, List<Map<String, Object?>>> data, Map<String, Set<String>> columns) async {
    final idMap = <String, Map<int, int>>{};
    // Kategoriler: aynı isimli yerel kategori varsa ona eşle.
    final localCats = await db.customSelect('SELECT id, name FROM dream_categories').get();
    final catByName = {for (final r in localCats) (r.data['name'] as String).toLowerCase(): r.data['id'] as int};

    for (final t in AppDatabase.tableOrder) {
      final rows = data[t]!;
      final map = idMap.putIfAbsent(t, () => {});
      for (final original in rows) {
        final row = Map<String, Object?>.from(original);
        switch (t) {
          case 'user_profiles':
            continue; // yerel profil korunur
          case 'app_settings':
            await _insert(t, row, columns[t]!, orIgnore: true);
            continue;
          case 'achievements':
            row.remove('id');
            await _insert(t, row, columns[t]!, orIgnore: true);
            continue;
          case 'dream_categories':
            final existing = catByName[(row['name'] as String).toLowerCase()];
            if (existing != null) {
              map[row['id'] as int] = existing;
              continue;
            }
        }
        final oldId = row.remove('id') as int?;
        // Yabancı anahtarları yeniden eşle
        for (final fk in (_fks[t] ?? const <String, String>{}).entries) {
          final v = row[fk.key];
          if (v is int) row[fk.key] = idMap[fk.value]?[v];
        }
        if (t == 'reminders' || t == 'activity_log') {
          final kind = (t == 'reminders' ? row['kind'] : row['type']) as String;
          final refs = t == 'reminders' ? _reminderRef : _activityRef;
          final target = refs[kind];
          final v = row['ref_id'];
          int? mapped;
          if (target != null && v is int) mapped = idMap[target]?[v];
          row['ref_id'] = mapped;
          if (t == 'reminders' && target != null && row['ref_id'] == null) continue;
        }
        // Zorunlu FK'si eşlenemeyen satırları atla (ör. silinmiş üst kayıt)
        if (t == 'dreams' && row['category_id'] == null) row['category_id'] = await db.fallbackCategoryId();
        if ((t == 'dream_milestones' || t == 'garden_trees') && row['dream_id'] == null) continue;
        if (t == 'habit_completions' && row['habit_id'] == null) continue;
        final newId = await _insert(t, row, columns[t]!, orIgnore: t == 'habit_completions' || t == 'garden_trees');
        if (oldId != null && newId > 0) map[oldId] = newId;
      }
    }
  }
}
