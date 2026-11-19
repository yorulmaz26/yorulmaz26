using System.IO;
using Newtonsoft.Json;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using WorldWonders.Core;
using WorldWonders.Core.Match3;

namespace WorldWonders.EditorTools
{
    /// <summary>
    /// Proje ilk açıldığında (ve menüden) URP varlığını, oyuncu ayarlarını ve açılış sahnesini kurar.
    /// Oyun sahne dosyasına bağımlı değildir (GameBootstrap); sahne yalnız yapı listesi için gereklidir.
    /// </summary>
    [InitializeOnLoad]
    public static class ProjectSetup
    {
        private const string SettingsDir = "Assets/_Project/Settings";
        private const string ScenePath = "Assets/_Project/Scenes/Main.unity";

        static ProjectSetup()
        {
            EditorApplication.delayCall += () =>
            {
                if (SessionState.GetBool("ww_setup_checked", false)) return;
                SessionState.SetBool("ww_setup_checked", true);
                if (GraphicsSettings.defaultRenderPipeline == null || !File.Exists(ScenePath)) Run();
            };
        }

        [MenuItem("World Wonders/Projeyi Kur (URP, sahne, oyuncu ayarları)")]
        public static void Run()
        {
            Directory.CreateDirectory(SettingsDir);
            Directory.CreateDirectory(Path.GetDirectoryName(ScenePath));
            EnsureUrp();
            EnsurePlayerSettings();
            EnsureScene();
            CreateSettingsAsset();
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();
            Debug.Log("[World Wonders] Proje kurulumu tamamlandı. Main sahnesini açıp Play'e basın.");
        }

        private static void EnsureUrp()
        {
            if (GraphicsSettings.defaultRenderPipeline != null) return;
            string assetPath = SettingsDir + "/WW_URP.asset";
            var asset = AssetDatabase.LoadAssetAtPath<UniversalRenderPipelineAsset>(assetPath);
            if (asset == null)
            {
                var renderer = ScriptableObject.CreateInstance<UniversalRendererData>();
                AssetDatabase.CreateAsset(renderer, SettingsDir + "/WW_URP_Renderer.asset");
                asset = UniversalRenderPipelineAsset.Create(renderer);
                asset.supportsHDR = false;          // mobil
                asset.msaaSampleCount = 2;
                asset.shadowDistance = 0f;          // stilize şader gölge kullanmaz
                AssetDatabase.CreateAsset(asset, assetPath);
            }
            GraphicsSettings.defaultRenderPipeline = asset;
            int current = QualitySettings.GetQualityLevel();
            for (int i = 0; i < QualitySettings.names.Length; i++)
            {
                QualitySettings.SetQualityLevel(i, false);
                QualitySettings.renderPipeline = asset;
            }
            QualitySettings.SetQualityLevel(current, false);
        }

        private static void EnsurePlayerSettings()
        {
            PlayerSettings.companyName = "World Wonders";
            PlayerSettings.productName = "World Wonders";
            PlayerSettings.SetApplicationIdentifier(NamedBuildTarget.Android, "com.worldwonders.game");
            PlayerSettings.SetApplicationIdentifier(NamedBuildTarget.iOS, "com.worldwonders.game");
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.Portrait;
            PlayerSettings.allowedAutorotateToLandscapeLeft = false;
            PlayerSettings.allowedAutorotateToLandscapeRight = false;
            PlayerSettings.allowedAutorotateToPortraitUpsideDown = false;
            PlayerSettings.colorSpace = ColorSpace.Linear;
            PlayerSettings.SetScriptingBackend(NamedBuildTarget.Android, ScriptingImplementation.IL2CPP);
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            PlayerSettings.SetManagedStrippingLevel(NamedBuildTarget.Android, ManagedStrippingLevel.Low);
            PlayerSettings.SetManagedStrippingLevel(NamedBuildTarget.iOS, ManagedStrippingLevel.Low);
            PlayerSettings.bundleVersion = "0.1.0";
        }

        private static void EnsureScene()
        {
            if (!File.Exists(ScenePath))
            {
                var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
                EditorSceneManager.SaveScene(scene, ScenePath);
            }
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) };
        }

        [MenuItem("World Wonders/Oyun Ayarları Varlığı Oluştur")]
        public static void CreateSettingsAsset()
        {
            const string path = "Assets/_Project/Resources/GameSettings.asset";
            var existing = AssetDatabase.LoadAssetAtPath<WorldWonders.Game.GameSettings>(path);
            if (existing == null)
            {
                existing = ScriptableObject.CreateInstance<WorldWonders.Game.GameSettings>();
                AssetDatabase.CreateAsset(existing, path);
                AssetDatabase.SaveAssets();
            }
            Selection.activeObject = existing;
        }

        [MenuItem("World Wonders/Bölüm Verisini Doğrula (420)")]
        public static void ValidateLevels()
        {
            int ok = 0, bad = 0;
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                var ta = Resources.Load<TextAsset>("Levels/wonder_" + w);
                if (ta == null) { Debug.LogError("Eksik: Levels/wonder_" + w); bad += 60; continue; }
                var pack = JsonConvert.DeserializeObject<LevelPack>(ta.text);
                foreach (var d in pack.Levels)
                {
                    try { d.Validate(); var g = new Match3Game(d); if (!g.Board.HasPossibleMove()) throw new System.Exception("hamle yok"); ok++; }
                    catch (System.Exception e) { bad++; Debug.LogError("Bölüm " + d.Id + ": " + e.Message); }
                }
            }
            Debug.Log("[World Wonders] Bölüm doğrulama: " + ok + " geçerli, " + bad + " hatalı.");
        }

        [MenuItem("World Wonders/Kaydı Sil (yerel ilerleme)")]
        public static void DeleteSave()
        {
            string dir = Path.Combine(Application.persistentDataPath, "save");
            if (!EditorUtility.DisplayDialog("Kaydı sil", "Bu bilgisayardaki World Wonders ilerlemesi silinecek. Yedek dosyaları (.wwbak) korunur.", "Sil", "Vazgeç")) return;
            foreach (var f in new[] { "worldwonders.save", "worldwonders.save.tmp", "worldwonders.save.bak" })
            {
                var p = Path.Combine(dir, f);
                if (File.Exists(p)) File.Delete(p);
            }
            Debug.Log("[World Wonders] Kayıt silindi: " + dir);
        }

        [MenuItem("World Wonders/Geçici Varlıkları Listele (Play modunda)")]
        public static void ListPlaceholders()
        {
            var all = Object.FindObjectsByType<WorldWonders.Game.PlaceholderAsset>(FindObjectsSortMode.None);
            foreach (var p in all) Debug.Log(p.name + ": " + p.Note, p);
            Debug.Log("[World Wonders] Sahnedeki geçici varlık kökü: " + all.Length + (Application.isPlaying ? "" : " (Play modunda çalıştırın)"));
        }
    }
}
