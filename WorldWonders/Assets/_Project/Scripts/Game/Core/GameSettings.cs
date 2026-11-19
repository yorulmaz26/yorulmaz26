using UnityEngine;
using WorldWonders.Core;

namespace WorldWonders.Game
{
    /// <summary>
    /// Tasarımcının Inspector'dan ayarlayabileceği çalışma zamanı ayarları (ScriptableObject).
    /// Resources/GameSettings.asset yoksa varsayılanlar kullanılır. Menü: World Wonders → Oyun Ayarları Varlığı Oluştur.
    /// Oyun kuralı sabitleri (ödüller, XP, dağılımlar) kesinleşmiş tasarımdır ve GameConfig'te kalır.
    /// </summary>
    [CreateAssetMenu(menuName = "World Wonders/Game Settings", fileName = "GameSettings")]
    public sealed class GameSettings : ScriptableObject
    {
        [Tooltip("Bir canın dolma süresi (dakika). Tasarım başlangıç değeri: 30.")]
        [Min(1)] public int LifeRegenMinutes = GameConfig.DefaultLifeRegenMinutes;
        [Tooltip("Match-3'te ipucu gösterilmeden önceki boşta kalma süresi (sn).")]
        [Min(1)] public float HintDelaySeconds = 6f;
        [Tooltip("Hedef kare hızı (mobil pil dengesi).")]
        public int TargetFrameRate = 60;

        private static GameSettings _cached;

        public static GameSettings Load()
        {
            if (_cached != null) return _cached;
            _cached = Resources.Load<GameSettings>("GameSettings");
            if (_cached == null) _cached = CreateInstance<GameSettings>();
            return _cached;
        }
    }
}
