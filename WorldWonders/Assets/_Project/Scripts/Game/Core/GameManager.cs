using System;
using System.Collections.Generic;
using System.IO;
using Newtonsoft.Json;
using UnityEngine;
using UnityEngine.InputSystem;
using UnityEngine.Rendering;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Match3;
using WorldWonders.Core.Save;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>Herhangi bir sahne açıldığında oyunu başlatır (sahne dosyasına bağımlılık yoktur).</summary>
    public static class GameBootstrap
    {
        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void Boot()
        {
            if (GameManager.I != null) return;
            var go = new GameObject("[WorldWonders]");
            UnityEngine.Object.DontDestroyOnLoad(go);
            go.AddComponent<GameManager>();
        }
    }

    /// <summary>3D görünüm kökü. GameManager aynı anda yalnız bir görünümü etkin tutar.</summary>
    public abstract class ViewBase : MonoBehaviour
    {
        private bool _built;
        protected GameManager Game { get { return GameManager.I; } }
        protected GameSession Session { get { return GameManager.I.Session; } }
        internal void EnsureBuilt() { if (_built) return; _built = true; Build(); }
        /// <summary>Bir kez çağrılır: geometriyi oluşturur.</summary>
        protected abstract void Build();
        /// <summary>Görünüm etkinleşince (kamera ayarı, kayıttan senkron).</summary>
        public virtual void Enter() { }
        public virtual void Exit() { }
    }

    public sealed class SaveGameService
    {
        public readonly SaveStore Store;
        public readonly string BackupDir;

        public SaveGameService()
        {
            string dir = Path.Combine(Application.persistentDataPath, "save");
            Store = new SaveStore(dir);
            BackupDir = dir;
        }

        public LoadResult Load() { return Store.Load(DateTime.UtcNow); }

        public bool Save(GameSession s)
        {
            try { s.BeforeSave(); Store.Write(s.Save); return true; }
            catch (Exception e) { Debug.LogError("Kayıt yazılamadı: " + e.Message); return false; }
        }

        public string ExportBackup(GameSession s) { return Store.ExportBackup(s.Save); }
        public string[] ListBackups() { return Store.ListBackups(); }
        public SaveData ImportBackup(string path) { return Store.ImportBackup(path); }
    }

    /// <summary>420 bölümün veri dosyalarını (Resources/Levels/wonder_N.json) okur.</summary>
    public sealed class LevelRepository
    {
        private readonly Dictionary<int, LevelPack> _packs = new Dictionary<int, LevelPack>();

        public LevelDefinition Get(int globalLevel)
        {
            int w = GameConfig.WonderOfLevel(globalLevel);
            LevelPack pack;
            if (!_packs.TryGetValue(w, out pack))
            {
                var ta = Resources.Load<TextAsset>("Levels/wonder_" + w);
                if (ta == null) throw new InvalidOperationException("Bölüm verisi bulunamadı: wonder_" + w);
                pack = JsonConvert.DeserializeObject<LevelPack>(ta.text);
                _packs[w] = pack;
            }
            return pack.Levels[GameConfig.LocalLevel(globalLevel) - 1];
        }
    }

    public sealed class GameManager : MonoBehaviour
    {
        public static GameManager I { get; private set; }

        public GameSession Session { get; private set; }
        public SaveGameService Saves { get; private set; }
        public LevelRepository Levels { get; private set; }
        public UIFlowController UI { get; private set; }
        public Camera Cam { get; private set; }
        public CameraRig Rig { get; private set; }
        public Light Sun { get; private set; }

        private readonly Dictionary<Type, ViewBase> _views = new Dictionary<Type, ViewBase>();
        public ViewBase CurrentView { get; private set; }
        private bool _dirty;
        private float _lastSave;
        private float _tick;

        private void Awake()
        {
            if (I != null && I != this) { Destroy(gameObject); return; }
            I = this;
            Application.targetFrameRate = 60;
            Screen.sleepTimeout = SleepTimeout.SystemSetting;
            Screen.orientation = ScreenOrientation.Portrait;

            Saves = new SaveGameService();
            var load = Saves.Load();
            Session = new GameSession(load.Data, new DeviceClock());
            bool abandoned = Session.OnBoot();
            Levels = new LevelRepository();

            SetupScene();
            UI = gameObject.AddComponent<UIFlowController>();
            UI.Init();
            InputAdapter.IsOverUI = UI.IsOverUI;
            HookEvents();

            if (load.Warning != null) UI.Toast(load.Warning, Palette.Coral);
            if (abandoned) UI.Toast("Yarım bırakılan bölüm terk edildi: 1 can kaybedildi.", Palette.Coral);
            Session.Events.Dirty += () => _dirty = true;
            SaveNow();
            Nav.WorldMap();
        }

        private void SetupScene()
        {
            var camGo = new GameObject("Main Camera");
            camGo.tag = "MainCamera";
            DontDestroyOnLoad(camGo);
            Cam = camGo.AddComponent<Camera>();
            Cam.clearFlags = CameraClearFlags.SolidColor;
            Cam.backgroundColor = Palette.Lighten(Palette.Sky, 0.45f);
            Cam.fieldOfView = 35f;
            Cam.nearClipPlane = 0.3f; Cam.farClipPlane = 400f;
            camGo.AddComponent<AudioListener>();
            Rig = camGo.AddComponent<CameraRig>();
            Rig.Cam = Cam;

            // Kamera başka sahnelerden gelmişse kapat.
            foreach (var c in FindObjectsByType<Camera>(FindObjectsSortMode.None)) if (c != Cam && c.CompareTag("MainCamera")) c.gameObject.SetActive(false);

            var sunGo = new GameObject("Sun");
            DontDestroyOnLoad(sunGo);
            Sun = sunGo.AddComponent<Light>();
            Sun.type = LightType.Directional;
            Sun.color = new Color(1f, 0.96f, 0.88f);
            Sun.intensity = 1.15f;
            Sun.shadows = LightShadows.None;
            sunGo.transform.rotation = Quaternion.Euler(52f, -35f, 0f);

            RenderSettings.ambientMode = AmbientMode.Trilight;
            RenderSettings.ambientSkyColor = new Color(0.86f, 0.92f, 0.98f);
            RenderSettings.ambientEquatorColor = new Color(0.93f, 0.9f, 0.84f);
            RenderSettings.ambientGroundColor = new Color(0.78f, 0.74f, 0.66f);
            RenderSettings.fog = true;
            RenderSettings.fogMode = FogMode.Linear;
            RenderSettings.fogColor = Cam.backgroundColor;
            RenderSettings.fogStartDistance = 80f; RenderSettings.fogEndDistance = 220f;
        }

        private void HookEvents()
        {
            var ev = Session.Events;
            ev.ExplorerLevelUp += l => UI.Toast("Seviye " + l + "! " + Names.Title(l), Palette.Gold);
            ev.MasteryLevelUp += m => UI.Toast("Ustalık " + m + "!", Palette.Gold);
            ev.BadgeUnlocked += b => UI.Toast("Yeni rozet: " + BadgeCatalog.Resolve(b).Name, Palette.Turquoise);
            ev.CosmeticUnlocked += c => { var info = CosmeticCatalog.Resolve(c); UI.Toast("Yeni kozmetik: " + (info != null ? info.Name : c), Palette.Lilac); };
            ev.PieceEarned += (w, p) => UI.Toast(WonderCatalog.Get(w).Name + " için yeni Jigsaw parçası!", Palette.Gold);
            ev.WonderCompleted += w => UI.Toast(WonderCatalog.Get(w).Name + " tamamlandı!", Palette.Gold);
            ev.WonderUnlocked += w => UI.Toast("Yeni harika açıldı: " + WonderCatalog.Get(w).Name, Palette.Turquoise);
            ev.FeatureUnlocked += f =>
            {
                switch (f)
                {
                    case Features.DailyMissions: UI.Toast("Günlük keşif görevleri açıldı!", Palette.Green); break;
                    case Features.PersonalRoom: UI.Toast("Kişisel Kâşif Odası açıldı!", Palette.Green); break;
                    case Features.ToolUpgradeL2: UI.Toast("Araçlar 2. seviyeye yükseltilebilir.", Palette.Green); break;
                    case Features.ToolUpgradeL3: UI.Toast("Araçlar 3. seviyeye yükseltilebilir.", Palette.Green); break;
                    case Features.Mastery: UI.Toast("Ustalık sistemi açıldı!", Palette.Gold); break;
                }
            };
        }

        /// <summary>Görünümü (gerekirse oluşturup) etkinleştirir, diğerlerini kapatır.</summary>
        public T ShowView<T>() where T : ViewBase
        {
            var v = GetView<T>();
            if (CurrentView == v) { v.Enter(); return v; }
            if (CurrentView != null) { CurrentView.Exit(); CurrentView.gameObject.SetActive(false); }
            v.gameObject.SetActive(true);
            CurrentView = v;
            v.Enter();
            return v;
        }

        public T GetView<T>() where T : ViewBase
        {
            ViewBase v;
            if (!_views.TryGetValue(typeof(T), out v) || v == null)
            {
                var go = new GameObject(typeof(T).Name);
                DontDestroyOnLoad(go);
                go.SetActive(false);
                v = go.AddComponent<T>();
                _views[typeof(T)] = v;
            }
            v.EnsureBuilt();
            return (T)v;
        }

        public void SaveNow()
        {
            if (Saves.Save(Session)) { _dirty = false; _lastSave = Time.unscaledTime; }
        }

        /// <summary>Yedekten geri yükleme: oturum yeniden kurulur ve dünya haritasına dönülür.</summary>
        public void ReplaceSave(SaveData data)
        {
            Session = new GameSession(data, new DeviceClock());
            Session.OnBoot();
            HookEvents();
            Session.Events.Dirty += () => _dirty = true;
            foreach (var v in _views.Values) if (v != null) Destroy(v.gameObject);
            _views.Clear();
            CurrentView = null;
            SaveNow();
            UI.ClearStack();
            Nav.WorldMap();
        }

        private void Update()
        {
            InputAdapter.Update();
            _tick += Time.unscaledDeltaTime;
            if (_tick >= 1f) { _tick = 0f; Session.Lives.Tick(); }
            if (_dirty && Time.unscaledTime - _lastSave > 1.5f) SaveNow();
            var kb = Keyboard.current;
            if (kb != null && kb.escapeKey.wasPressedThisFrame) Nav.Back(); // Android geri tuşu
            UI.Tick(Time.unscaledDeltaTime);
        }

        private void OnApplicationPause(bool paused) { if (paused && Session != null) SaveNow(); }
        private void OnApplicationQuit() { if (Session != null) SaveNow(); }
    }
}
