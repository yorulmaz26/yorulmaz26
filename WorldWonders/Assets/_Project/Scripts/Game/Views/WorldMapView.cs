using System;
using System.Collections.Generic;
using UnityEngine;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>
    /// Ana 3D dünya haritası: küre değil, izometrik, kaydırılabilir ve yakınlaştırılabilir diorama.
    /// Yedi bölge bir patika boyunca dizilir; kilitli bölgeler hafif sisle örtülür; anıt maketleri gerçek Jigsaw ilerlemesini gösterir.
    /// Bu haritada yürüyen kâşif yoktur. GEÇİCİ VARLIK: prosedürel primitif diorama.
    /// </summary>
    [DefaultExecutionOrder(1000)]
    public sealed class WorldMapView : ViewBase
    {
        public const float Spacing = 18f;
        private static readonly Vector3 Up = new Vector3(0.7071f, 0f, 0.7071f);
        private static readonly Vector3 Side = new Vector3(0.7071f, 0f, -0.7071f);

        private sealed class Region
        {
            public int W;
            public Transform Root;
            public MonumentModel Model;
            public float ModelScale;
            public Transform Fog;
            public Transform Lock;
            public Transform Highlight;
            public Vector3 Center;
        }

        private Region[] _regions;
        private Transform _water;
        private readonly List<Transform> _clouds = new List<Transform>();
        private int _current = -1;

        /// <summary>Kamera son karede uygulandıktan sonra çağrılır (UI etiketlerini 3D konumlara hizalamak için).</summary>
        public event Action AfterCamera;

        public static Vector3 RegionCenter(int w) { return Up * (w * Spacing) + Side * (w % 2 == 0 ? -5.5f : 5.5f); }

        /// <summary>Bölge etiketinin dünya konumu (anıt tepesinin biraz üstü).</summary>
        public Vector3 LabelAnchor(int w)
        {
            if (_regions == null) return RegionCenter(w);
            var r = _regions[w];
            float h = r.Model != null ? r.Model.Height * r.ModelScale : 4f;
            return r.Center + Vector3.up * (h + 1.6f);
        }

        protected override void Build()
        {
            PlaceholderAsset.Mark(gameObject, "prosedürel dünya haritası dioraması; final sanat varlıklarıyla değiştirilecek.");
            _water = Prim.Box(transform, new Vector3(Up.x * 55f, -1.3f, Up.z * 55f), new Vector3(320f, 1f, 320f), Palette.Water).transform;
            _water.name = "Ocean";

            _regions = new Region[GameConfig.WonderCount];
            for (int w = 0; w < GameConfig.WonderCount; w++) _regions[w] = BuildRegion(w);
            for (int w = 0; w + 1 < GameConfig.WonderCount; w++) BuildBridge(w);

            var deco = Prim.Group("Islets", transform);
            var rng = new Rng(4242);
            for (int k = 0; k < 10; k++)
            {
                var c = Up * (k * 13f - 6f) + Side * ((k % 2 == 0 ? 1 : -1) * (16f + (float)rng.NextDouble() * 6f));
                Prim.Cyl(deco, c + Vector3.up * -0.55f, 3f + (float)rng.NextDouble() * 3f, 0.6f, Palette.Sand);
                Prim.Ball(deco, c + Vector3.up * 0.1f, 1.2f + (float)rng.NextDouble(), Palette.Grass).transform.localScale = new Vector3(2f, 0.8f, 2f);
            }
            MonumentBuilder.CombineStatic(deco);

            var cloudRoot = Prim.Group("Clouds", transform);
            for (int k = 0; k < 6; k++)
            {
                var cl = Prim.Group("Cloud" + k, cloudRoot, Up * (k * 22f) + Side * ((k % 2 == 0 ? -1 : 1) * 12f) + Vector3.up * (11f + k % 3));
                Prim.Ball(cl, Vector3.zero, 3f, Palette.White).transform.localScale = new Vector3(3.6f, 1.6f, 2.4f);
                Prim.Ball(cl, new Vector3(1.8f, 0.4f, 0.3f), 2.4f, Palette.White).transform.localScale = new Vector3(2.6f, 1.6f, 2.0f);
                Prim.Ball(cl, new Vector3(-1.7f, 0.1f, -0.2f), 2.2f, Palette.White).transform.localScale = new Vector3(2.4f, 1.2f, 1.8f);
                _clouds.Add(cl);
            }
        }

        // ------------------------------------------------------------------ bölgeler
        private Region BuildRegion(int w)
        {
            var info = WonderCatalog.Get(w);
            var r = new Region { W = w, Center = RegionCenter(w) };
            r.Root = Prim.Group("Region_" + info.Id, transform, r.Center);
            var col = r.Root.gameObject.AddComponent<BoxCollider>();
            col.center = new Vector3(0, 2.5f, 0); col.size = new Vector3(16f, 7f, 16f);
            var tap = r.Root.gameObject.AddComponent<Tappable>();
            int wonder = w;
            tap.OnTap = () => Nav.Destination(wonder);

            var decor = Prim.Group("Decor", r.Root);
            Color ground = GroundColor(w);
            Prim.Cyl(decor, new Vector3(0, -0.75f, 0), 18.8f, 0.9f, Palette.Sand);
            Prim.Cyl(decor, new Vector3(0, -0.35f, 0), 17.6f, 0.7f, ground);
            Prim.Cyl(decor, new Vector3(0, 0f, 0), 11f, 0.04f, Palette.Lighten(ground, 0.25f));
            Vegetation(w, decor, new Rng((ulong)(1000 + w * 17)));
            MonumentBuilder.CombineStatic(decor);

            r.Model = MonumentBuilder.Build(w, r.Root);
            r.Model.transform.localRotation = Quaternion.Euler(0, 45f, 0);
            r.ModelScale = Mathf.Min(5.2f / r.Model.Height, 5.4f / r.Model.Radius, 1f);
            r.Model.transform.localScale = Vector3.one * r.ModelScale;

            // Kilitli bölge sisi + kilit işareti
            r.Fog = Prim.Group("Fog", r.Root);
            var rng = new Rng((ulong)(77 + w));
            for (int k = 0; k < 5; k++)
            {
                float a = k / 5f * Mathf.PI * 2f;
                var p = new Vector3(Mathf.Cos(a) * 4.2f, 2.2f + (float)rng.NextDouble(), Mathf.Sin(a) * 4.2f);
                Prim.Ball(r.Fog, p, 1f, Palette.Fog).transform.localScale = new Vector3(8.5f, 4.5f, 8.5f);
            }
            Prim.Ball(r.Fog, new Vector3(0, 3.4f, 0), 1f, Palette.Fog).transform.localScale = new Vector3(10f, 5.5f, 10f);
            r.Lock = Prim.Group("Lock", r.Root, new Vector3(0, 8.2f, 0));
            Prim.Box(r.Lock, Vector3.zero, new Vector3(1.5f, 1.2f, 0.6f), Palette.Gold);
            var shackle = Prim.FromMesh(MeshFactory.Ring(0.4f, 0.6f, 180f, 16), r.Lock, new Vector3(0, 0.6f, 0), new Vector3(1f, 0.35f, 1f), Palette.GoldDeep, "shackle");
            shackle.transform.localRotation = Quaternion.Euler(-90f, 0, 0);
            shackle.transform.localPosition = new Vector3(0, 0.6f, 0.17f);
            Prim.Box(r.Lock, new Vector3(0, -0.05f, -0.31f), new Vector3(0.22f, 0.45f, 0.04f), Palette.Text);

            // Mevcut bölge vurgusu
            r.Highlight = Prim.FromMesh(MeshFactory.Ring(8.9f, 9.5f, 360f, 64), r.Root, new Vector3(0, -0.02f, 0), new Vector3(1f, 0.12f, 1f), Palette.Turquoise, "Highlight").transform;
            return r;
        }

        private void BuildBridge(int w)
        {
            Vector3 a = RegionCenter(w), b = RegionCenter(w + 1);
            var dir = (b - a); float len = dir.magnitude; dir /= len;
            var bridge = Prim.Group("Path" + w, transform);
            int n = 7;
            for (int k = 0; k < n; k++)
            {
                float t = Mathf.Lerp(8.4f, len - 8.4f, (k + 0.5f) / n);
                var p = a + dir * t;
                var s = Prim.Box(bridge, p + Vector3.up * -0.35f, new Vector3(1.6f, 0.5f, 1.25f), Palette.Sand);
                s.transform.localRotation = Quaternion.LookRotation(dir) * Quaternion.Euler(0, (k % 2 == 0 ? 6f : -6f), 0);
            }
            MonumentBuilder.CombineStatic(bridge);
        }

        private static Color GroundColor(int w)
        {
            switch (w)
            {
                case 0: return Palette.Hex("#CFDC94");
                case 1: return Palette.Hex("#EBC9A0");
                case 2: return Palette.Hex("#A9D98F");
                case 3: return Palette.Hex("#A3C48B");
                case 4: return Palette.Hex("#93C773");
                case 5: return Palette.Hex("#79BD72");
                default: return Palette.Hex("#9FD38E");
            }
        }

        private static Vector3 RingPos(Rng rng, float rMin, float rMax)
        {
            float a = (float)rng.NextDouble() * Mathf.PI * 2f;
            float r = Mathf.Lerp(rMin, rMax, (float)rng.NextDouble());
            return new Vector3(Mathf.Cos(a) * r, 0, Mathf.Sin(a) * r);
        }

        private static void Hill(Transform t, Vector3 p, float d, float h, Color c)
        {
            Prim.Ball(t, p, 1f, c).transform.localScale = new Vector3(d, h, d);
        }

        private static void Cypress(Transform t, Vector3 p, float h) { Prim.Cone(t, p, 0.55f, h, Palette.Hex("#4E8A5A")); }
        private static void Pine(Transform t, Vector3 p, float h)
        {
            Prim.Cyl(t, p + Vector3.up * 0.25f, 0.18f, 0.5f, Palette.Hex("#8A6A4A"));
            Prim.Cone(t, p + Vector3.up * 0.4f, 1.3f, h * 0.6f, Palette.Hex("#4F8F63"));
            Prim.Cone(t, p + Vector3.up * (0.4f + h * 0.35f), 0.95f, h * 0.55f, Palette.Hex("#5FA172"));
        }
        private static void UmbrellaPine(Transform t, Vector3 p)
        {
            Prim.Cyl(t, p + Vector3.up * 0.7f, 0.18f, 1.4f, Palette.Hex("#8A6A4A"));
            Prim.Ball(t, p + Vector3.up * 1.55f, 1f, Palette.Hex("#5E9B5A")).transform.localScale = new Vector3(1.9f, 0.7f, 1.9f);
        }
        private static void Palm(Transform t, Vector3 p, float lean)
        {
            var trunk = Prim.Cyl(t, p + Vector3.up * 1.0f, 0.18f, 2.0f, Palette.Hex("#A9845A"));
            trunk.transform.localRotation = Quaternion.Euler(0, 0, lean);
            var top = p + Quaternion.Euler(0, 0, lean) * Vector3.up * 2.0f;
            for (int k = 0; k < 5; k++)
            {
                var f = Prim.Box(t, top, new Vector3(1.4f, 0.06f, 0.35f), Palette.Hex("#5FAE5B"));
                f.transform.localRotation = Quaternion.Euler(0, k * 72f, -18f);
                f.transform.localPosition = top + f.transform.localRotation * new Vector3(0.6f, 0, 0);
            }
        }
        private static void JungleTree(Transform t, Vector3 p, float s)
        {
            Prim.Cyl(t, p + Vector3.up * 0.5f, 0.2f, 1.0f, Palette.Hex("#7A5A3A"));
            Prim.Ball(t, p + Vector3.up * 1.4f, 1.6f * s, Palette.Hex("#3F9A55"));
            Prim.Ball(t, p + new Vector3(0.5f, 1.8f, 0.3f) * s, 1.2f * s, Palette.Hex("#57B163"));
        }

        private static void Vegetation(int w, Transform t, Rng rng)
        {
            switch (w)
            {
                case 0: // Roma: selvi, fıstık çamı, sütun kalıntıları
                    for (int k = 0; k < 3; k++) Hill(t, RingPos(rng, 6.5f, 7.6f), 4.5f, 2.4f, Palette.Hex("#BCD083"));
                    for (int k = 0; k < 9; k++) Cypress(t, RingPos(rng, 5.8f, 7.8f), 2.2f + (float)rng.NextDouble());
                    for (int k = 0; k < 4; k++) UmbrellaPine(t, RingPos(rng, 6f, 7.8f));
                    for (int k = 0; k < 4; k++) { var p = RingPos(rng, 5.6f, 6.4f); Prim.Cyl(t, p + Vector3.up * (0.5f + k * 0.1f), 0.35f, 1f + k * 0.2f, Palette.Hex("#E8D9BC")); }
                    break;
                case 1: // Petra: kızıl kaya iğneleri, palmiyeler
                    for (int k = 0; k < 6; k++) Prim.Cone(t, RingPos(rng, 5.8f, 7.8f), 2.2f + (float)rng.NextDouble() * 1.5f, 2.5f + (float)rng.NextDouble() * 2.5f, k % 2 == 0 ? Palette.Hex("#D98F78") : Palette.Hex("#C77A63"));
                    for (int k = 0; k < 5; k++) Hill(t, RingPos(rng, 6f, 7.6f), 2.5f, 1.4f, Palette.Hex("#E2B48F"));
                    for (int k = 0; k < 3; k++) Palm(t, RingPos(rng, 6.2f, 7.4f), -10f + (float)rng.NextDouble() * 20f);
                    break;
                case 2: // Tac Mahal: Yamuna nehri, selviler, çiçekler
                    Prim.Box(t, new Vector3(0, 0.02f, 7.2f), new Vector3(16f, 0.08f, 1.6f), Palette.Water).transform.localRotation = Quaternion.Euler(0, 45f, 0);
                    for (int k = 0; k < 10; k++) Cypress(t, RingPos(rng, 6f, 7.8f), 1.8f + (float)rng.NextDouble() * 0.6f);
                    for (int k = 0; k < 8; k++) Prim.Ball(t, RingPos(rng, 5.6f, 7.6f) + Vector3.up * 0.25f, 0.6f, k % 2 == 0 ? Palette.Hex("#F2A7B8") : Palette.Hex("#F6D06A"));
                    break;
                case 3: // Çin Seddi: dağlar, çamlar
                    for (int k = 0; k < 6; k++) Prim.Cone(t, RingPos(rng, 6.2f, 7.8f), 4f + (float)rng.NextDouble() * 2f, 3f + (float)rng.NextDouble() * 2.5f, k % 2 == 0 ? Palette.Hex("#8FB27A") : Palette.Hex("#7FA56E"));
                    for (int k = 0; k < 7; k++) Pine(t, RingPos(rng, 5.6f, 7.6f), 2f + (float)rng.NextDouble());
                    break;
                case 4: // Machu Picchu: sivri doruklar, bulutlar
                    for (int k = 0; k < 5; k++) Prim.Cone(t, RingPos(rng, 6.4f, 7.8f), 3f + (float)rng.NextDouble() * 1.5f, 4.5f + (float)rng.NextDouble() * 3f, k % 2 == 0 ? Palette.Hex("#6FA35A") : Palette.Hex("#7DB066"));
                    for (int k = 0; k < 6; k++) JungleTree(t, RingPos(rng, 5.6f, 7.4f), 0.8f);
                    break;
                case 5: // Maya ormanı: sık ağaçlar, cenote
                    {
                        var c = RingPos(rng, 6.4f, 6.8f);
                        Prim.Cyl(t, c + Vector3.up * 0.02f, 2.6f, 0.08f, Palette.Hex("#5FBFD0"));
                        for (int k = 0; k < 14; k++) JungleTree(t, RingPos(rng, 5.6f, 7.9f), 0.8f + (float)rng.NextDouble() * 0.5f);
                        break;
                    }
                default: // Rio: kumsal, palmiyeler, Şeker Somunu
                    Prim.Ball(t, new Vector3(-6.2f, 0, 4.2f), 1f, Palette.Hex("#8E9586")).transform.localScale = new Vector3(2.6f, 6f, 2.6f);
                    Prim.Ball(t, new Vector3(-5.2f, 0, 5.6f), 1f, Palette.Hex("#6FB06A")).transform.localScale = new Vector3(2.8f, 2.4f, 2.8f);
                    for (int k = 0; k < 7; k++) Palm(t, RingPos(rng, 6f, 7.8f), -14f + (float)rng.NextDouble() * 28f);
                    for (int k = 0; k < 3; k++) Hill(t, RingPos(rng, 6.2f, 7.6f), 3.5f, 2.2f, Palette.Hex("#86C27A"));
                    break;
            }
        }

        // ------------------------------------------------------------------ durum
        public override void Enter()
        {
            Sync();
            var rig = Game.Rig;
            rig.AllowPan = true; rig.AllowZoom = true;
            Vector3 min = RegionCenter(0), max = RegionCenter(0);
            for (int w = 1; w < GameConfig.WonderCount; w++) { min = Vector3.Min(min, RegionCenter(w)); max = Vector3.Max(max, RegionCenter(w)); }
            var bounds = new Rect(min.x - 6f, min.z - 6f, (max.x - min.x) + 12f, (max.z - min.z) + 12f);
            rig.Configure(bounds, 44f, 20f, 85f, 48f, 45f, RegionCenter(Mathf.Max(0, _current)));
            rig.SetMode(CameraMode.FreePan);
        }

        /// <summary>Kayıttan bölge durumlarını, sisleri ve anıt maketlerini günceller.</summary>
        public void Sync()
        {
            var s = Session;
            _current = GameConfig.WonderOfLevel(s.Levels.CurrentLevel);
            for (int w = 0; w < _regions.Length; w++)
            {
                var r = _regions[w];
                var st = s.WorldMap.StateOf(w);
                bool locked = st == RegionState.Locked;
                r.Fog.gameObject.SetActive(locked);
                r.Lock.gameObject.SetActive(locked);
                r.Model.ShowGhosts = st == RegionState.InProgress;
                r.Model.gameObject.SetActive(!locked || r.Model.BuiltCount > 0);
                r.Model.SyncFrom(s);
                r.Highlight.gameObject.SetActive(w == _current && !locked);
            }
        }

        public RegionState StateOf(int w) { return Session.WorldMap.StateOf(w); }

        public void FocusRegion(int w) { Game.Rig.FocusOn(RegionCenter(w), Game.Rig.Distance, 0.6f); }

        private void Update()
        {
            float t = Time.time;
            if (_water != null) _water.localPosition = new Vector3(_water.localPosition.x, -1.3f + Mathf.Sin(t * 0.8f) * 0.06f, _water.localPosition.z);
            for (int i = 0; i < _clouds.Count; i++)
            {
                var c = _clouds[i];
                c.localPosition += Up * (Time.deltaTime * (0.6f + 0.15f * i));
                if (Vector3.Dot(c.localPosition, Up) > Spacing * 7f + 20f) c.localPosition -= Up * (Spacing * 7f + 40f);
            }
            if (_regions != null)
                for (int w = 0; w < _regions.Length; w++)
                {
                    var r = _regions[w];
                    if (r.Fog.gameObject.activeSelf) r.Fog.localPosition = new Vector3(0, Mathf.Sin(t * 0.6f + w) * 0.25f, 0);
                    if (r.Lock.gameObject.activeSelf)
                    {
                        r.Lock.localPosition = new Vector3(0, 8.2f + Mathf.Sin(t * 1.4f + w) * 0.3f, 0);
                        r.Lock.localRotation = Quaternion.Euler(0, 45f + Mathf.Sin(t * 0.7f + w) * 20f, 0);
                    }
                    if (r.Highlight.gameObject.activeSelf)
                    {
                        float s = 1f + Mathf.Sin(t * 2.2f) * 0.025f;
                        r.Highlight.localScale = new Vector3(s, 0.12f, s);
                    }
                }

            // Dünya dokunuşu: yalnız UI dışında başlayan dokunuş (Tap), dünya haritası ekranı üstteyken.
            if (InputAdapter.Tap && Game.UI.Current is WorldMapScreen && !Game.UI.HasPopup)
            {
                Tappable tap; Vector3 gp; bool hg;
                WorldPicker.Pick(Game.Cam, InputAdapter.Position, out tap, out gp, out hg);
                if (tap != null && tap.OnTap != null) tap.OnTap();
            }
        }

        private void LateUpdate()
        {
            var h = AfterCamera;
            if (h != null) h();
        }
    }
}
