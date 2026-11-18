import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/providers.dart';
import '../../core/services/backup_service.dart';
import '../../core/settings/settings_controller.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';

/// Şifreli yedekleme ve geri yükleme.
class BackupScreen extends ConsumerStatefulWidget {
  const BackupScreen({super.key});
  @override
  ConsumerState<BackupScreen> createState() => _BackupScreenState();
}

class _BackupScreenState extends ConsumerState<BackupScreen> {
  bool _busy = false;
  String? _status;

  static const _lastBackupKey = 'last_backup_at';

  Future<String?> _password({required bool confirmIt}) async {
    final a = TextEditingController();
    final b = TextEditingController();
    String? err;
    final r = await showDialog<String>(
      context: context,
      builder: (c) => StatefulBuilder(
        builder: (c, set) => AlertDialog(
          title: Text(confirmIt ? 'Yedek parolası belirle' : 'Yedek parolası'),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            if (confirmIt)
              const Text('Bu parolayı unutursan yedek açılamaz. Parola hiçbir yere kaydedilmez.', style: TextStyle(fontSize: 13)),
            TextField(controller: a, obscureText: true, decoration: const InputDecoration(labelText: 'Parola (en az 6 karakter)')),
            if (confirmIt) ...[
              Gap.h8,
              TextField(controller: b, obscureText: true, decoration: InputDecoration(labelText: 'Parola tekrar', errorText: err)),
            ],
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c), child: const Text('Vazgeç')),
            FilledButton(
              onPressed: () {
                if (a.text.length < 6) return set(() => err = 'En az 6 karakter');
                if (confirmIt && a.text != b.text) return set(() => err = 'Parolalar eşleşmiyor');
                Navigator.pop(c, a.text);
              },
              child: const Text('Devam'),
            ),
          ],
        ),
      ),
    );
    a.dispose();
    b.dispose();
    return r;
  }

  Future<void> _backup({required bool share}) async {
    final pw = await _password(confirmIt: true);
    if (pw == null) return;
    setState(() {
      _busy = true;
      _status = 'Bütünlük kontrolü ve şifreleme yapılıyor…';
    });
    try {
      final bytes = await ref.read(backupServiceProvider).createBackup(pw);
      final stamp = DateTime.now();
      final name = 'hayallerim_cepte_${stamp.year}${stamp.month.toString().padLeft(2, '0')}${stamp.day.toString().padLeft(2, '0')}_'
          '${stamp.hour.toString().padLeft(2, '0')}${stamp.minute.toString().padLeft(2, '0')}.${BackupService.fileExtension}';
      if (share) {
        final f = await ref.read(mediaProvider).writeTemp(bytes, name);
        await SharePlus.instance.share(ShareParams(files: [XFile(f.path, mimeType: 'application/octet-stream')], subject: 'Hayallerim Cepte yedeği'));
        await f.delete();
      } else {
        final uri = await FilePicker.saveFile(fileName: name, bytes: bytes);
        if (uri == null) {
          setState(() => _status = 'Kaydetme iptal edildi.');
          return;
        }
      }
      await ref.read(prefsProvider).setString(_lastBackupKey, stamp.toIso8601String());
      setState(() => _status = 'Yedek hazır (${(bytes.length / 1024 / 1024).toStringAsFixed(1)} MB). Dosyayı güvenli bir yerde sakla.');
    } catch (e) {
      setState(() => _status = 'Yedek oluşturulamadı: $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _restore() async {
    final file = await FilePicker.pickFile(type: FileType.any);
    if (file == null) return;
    final pw = await _password(confirmIt: false);
    if (pw == null) return;
    setState(() {
      _busy = true;
      _status = 'Yedek çözülüyor ve doğrulanıyor…';
    });
    try {
      final svc = ref.read(backupServiceProvider);
      final contents = await svc.open(await file.readAsBytes(), pw);
      if (!mounted) return;
      setState(() => _busy = false);
      final mode = await showDialog<RestoreMode>(
        context: context,
        builder: (c) => AlertDialog(
          title: const Text('Yedek doğrulandı ✓'),
          content: Text(
            'Oluşturulma: ${Dates.dateTime(contents.createdAt)}\n'
            '${contents.count('dreams')} hayal · ${contents.count('tasks')} görev · ${contents.count('habits')} alışkanlık · '
            '${contents.count('journal_entries')} günlük · ${contents.count('savings_goals')} kumbara · ${contents.mediaFiles.length} medya dosyası\n\n'
            'Üzerine yaz: mevcut tüm veriler silinir, yedek yüklenir.\n'
            'Birleştir: mevcut veriler korunur, yedektekiler eklenir (aynı isimli kategoriler eşleştirilir).',
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c), child: const Text('Vazgeç')),
            OutlinedButton(onPressed: () => Navigator.pop(c, RestoreMode.merge), child: const Text('Birleştir')),
            FilledButton(
              style: FilledButton.styleFrom(backgroundColor: AppColors.danger),
              onPressed: () => Navigator.pop(c, RestoreMode.overwrite),
              child: const Text('Üzerine yaz'),
            ),
          ],
        ),
      );
      if (mode == null) {
        setState(() => _status = 'Geri yükleme iptal edildi.');
        return;
      }
      if (!mounted) return;
      if (mode == RestoreMode.overwrite &&
          !(await confirm(context, title: 'Emin misin?', message: 'Bu cihazdaki mevcut verilerin yerini yedek alacak.', ok: 'Üzerine yaz', destructive: true))) {
        return;
      }
      setState(() {
        _busy = true;
        _status = 'Geri yükleniyor…';
      });
      await svc.restore(contents, mode);
      // Ayarları yeniden oku; PIN bu cihazda yoksa kilidi kapat.
      final settings = ref.read(settingsProvider.notifier);
      await settings.reload();
      if (!await ref.read(lockServiceProvider).hasPin()) {
        await settings.update((s) => s.copyWith(lockEnabled: false, biometricEnabled: false));
      }
      await ref.read(reminderRepoProvider).resync();
      await ref.read(gameRepoProvider).evaluate();
      setState(() => _status = mode == RestoreMode.overwrite ? 'Veriler yedekten geri yüklendi ✓' : 'Yedek mevcut verilerle birleştirildi ✓');
    } on BackupException catch (e) {
      setState(() => _status = e.message);
    } catch (e) {
      setState(() => _status = 'Geri yükleme başarısız: $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final last = ref.watch(prefsProvider).getString(_lastBackupKey);
    return Scaffold(
      appBar: AppBar(title: const Text('Yedekleme')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 40),
        children: [
          GradientCard(
            gradient: AppColors.nightGradient,
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Row(children: [
                Icon(Icons.shield_rounded, color: AppColors.gold),
                SizedBox(width: 8),
                Text('Şifreli yedek', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
              ]),
              Gap.h8,
              const Text(
                'Hayaller, görevler, alışkanlıklar, fotoğraflar, ses kayıtları, günlükler, birikimler, ayarlar ve başarılar tek bir dosyada, '
                'senin parolanla AES-256 ile şifrelenir. Yedekten önce veritabanı bütünlüğü kontrol edilir.',
                style: TextStyle(color: Colors.white70, height: 1.4),
              ),
              Gap.h8,
              Text(last == null ? 'Henüz yedek alınmadı' : 'Son yedek: ${Dates.dateTime(DateTime.parse(last))}',
                  style: const TextStyle(color: AppColors.gold, fontWeight: FontWeight.w600)),
            ]),
          ),
          Gap.h16,
          FilledButton.icon(onPressed: _busy ? null : () => _backup(share: false), icon: const Icon(Icons.save_alt), label: const Text('Yedeği dosyaya kaydet')),
          Gap.h8,
          OutlinedButton.icon(
            onPressed: _busy ? null : () => _backup(share: true),
            icon: const Icon(Icons.ios_share),
            label: const Text('Yedeği paylaş (Dosyalar, AirDrop, USB…)'),
          ),
          Gap.h24,
          OutlinedButton.icon(onPressed: _busy ? null : _restore, icon: const Icon(Icons.settings_backup_restore), label: const Text('Yedekten geri yükle')),
          Gap.h16,
          if (_busy) const LinearProgressIndicator(),
          if (_status != null) Padding(padding: const EdgeInsets.only(top: 12), child: SoftCard(child: Text(_status!))),
          Gap.h16,
          Text(
            'Geri yükleme önce dosyayı çözer, tüm özetleri (SHA-256) doğrular ve sana içeriği gösterir. '
            'İşlem tek bir veritabanı işlemi içinde yapılır; bir hata olursa mevcut verilerin değişmez.',
            style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant),
          ),
        ],
      ),
    );
  }
}
