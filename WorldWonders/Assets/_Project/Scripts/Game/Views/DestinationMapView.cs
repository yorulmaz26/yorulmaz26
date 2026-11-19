using System.Collections.Generic;
using UnityEngine;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>
    /// Harika başına 3D destinasyon haritası: kıvrımlı yol üzerinde 60 bölüm düğümü (12 alan × 5),
    /// 12 keşif noktası (tek alan = eser, çift alan = sandık), sisli kilitli alanlar ve NavMesh üzerinde yürüyen kâşif.
    /// GEÇİCİ VARLIK: zemin, dekor ve düğümler prosedürel primitiflerdir.
    /// </summary>
    public sealed class DestinationMapView : ViewBase
    {
        public const float NodeSpacing = 2.8f;
        private const float HalfWidth = 11f;

        private sealed class NodeRef { public int Local; public Vector3 Pos; public GameObject Disc; public Transform Visual; public NodeState State = (NodeState)(-1); public int Stars = -1; }
        private sealed class DiscRef { public int Area; public Vector3 Pos; public Transform Approach; public Transform Visual; public DiscoveryState State = (DiscoveryState)(-1); }

        private struct Theme
        {
            public Color Ground, Ground2, Path, Accent, Deco1, Deco2, Deco3;
        }

        private int _wonder = -1;
        private Transform _content, _fogRoot;
        private ExplorerCharacter _explorer;
        private ExplorerMovementController _mover;
        private readonly List<NodeRef> _nodes = new List<NodeRef>();
        private readonly List<DiscRef> _discs = new List<DiscRef>();
        private readonly List<GameObject> _areaFog = new List<GameObject>();
        private readonly List<Transform> _bobbers = new List<Transform>();
        private Transform _pulse;
        private int _navFrames = -1;
        private bool _placed;
        private int _lastDoneMask = -1;

        public int Wonder { get { return _wonder; } }

        public static Vector3 NodePos(int index)
        {
            float x = Mathf.Sin(index * 0.42f) * 3.4f + Mathf.Sin(index * 0.13f + 1f) * 1.2f;
            return new Vector3(x, 0f, index * NodeSpacing);
        }

        private static float PathX(float z)
        {
            float i = z / NodeSpacing;
            return Mathf.Sin(i * 0.42f) * 3.4f + Mathf.Sin(i * 0.13f + 1f) * 1.2f;
        }

        protected override void Build()
        {
            _content = Prim.Group("Content", transform);
        }

        public override void Enter()
        {
            if (_wonder < 0) return;
            RefreshState();
            ConfigureCamera();
        }

        public override void Exit()
        {
            if (Game != null && Game.Rig != null) Game.Rig.FollowTarget = null;
            if (_mover != null) _mover.Stop();
        }

        public void ShowWonder(int wonder)
        {
            if (wonder != _wonder)
            {
                _wonder = wonder;
                Rebuild();
            }
            RefreshState();
            ConfigureCamera();
        }

        /// <summary>Kameranın baktığı alan (1..12).</summary>
        public int AreaAtPivot()
        {
            if (Game == null || Game.Rig == null) return 1;
            int idx = Mathf.Clamp(Mathf.RoundToInt(Game.Rig.Pivot.z / NodeSpacing), 0, GameConfig.LevelsPerWonder - 1);
            return idx / GameConfig.LevelsPerArea + 1;
        }

        public int CurrentLocal()
        {
            if (_wonder < 0) return 1;
            var d = Session.Destinations;
            bool allDone = true;
            for (int l = 1; l <= GameConfig.LevelsPerWonder; l++)
            {
                var st = d.NodeStateOf(_wonder, l);
                if (st == NodeState.Current) return l;
                if (st != NodeState.Completed) allDone = false;
            }
            return allDone ? GameConfig.LevelsPerWonder : 1;
        }

        public void FocusLevel(int local)
        {
            var p = NodePos(Mathf.Clamp(local, 1, 60) - 1);
            Game.Rig.FocusOn(p, 20f, 0.6f);
        }

        // ------------------------------------------------------------------ kurulum
        private void ConfigureCamera()
        {
            var rig = Game.Rig;
            rig.AllowPan = true; rig.AllowZoom = true;
            float maxZ = (GameConfig.LevelsPerWonder - 1) * NodeSpacing;
            rig.Configure(new Rect(-8f, -2f, 16f, maxZ + 4f), 21f, 11f, 34f, 52f, 0f, NodePos(CurrentLocal() - 1));
            rig.FollowTarget = _explorer != null ? _explorer.transform : null;
            rig.SetMode(CameraMode.FreePan);
        }

        private static Theme ThemeOf(int w)
        {
            switch (w)
            {
                case 0: return new Theme { Ground = Palette.Hex("#CFE3A0"), Ground2 = Palette.Hex("#C4DA93"), Path = Palette.Hex("#E9D9B4"), Accent = Palette.Hex("#D9A066"), Deco1 = Palette.Hex("#EDE6D6"), Deco2 = Palette.Hex("#D7CDB8"), Deco3 = Palette.Hex("#7FB069") };
                case 1: return new Theme { Ground = Palette.Hex("#EBC8A2"), Ground2 = Palette.Hex("#E4BC92"), Path = Palette.Hex("#F4DFC0"), Accent = Palette.Hex("#D97A5B"), Deco1 = Palette.Hex("#D98A68"), Deco2 = Palette.Hex("#C9735A"), Deco3 = Palette.Hex("#B9C98A") };
                case 2: return new Theme { Ground = Palette.Hex("#BDE2A5"), Ground2 = Palette.Hex("#B1D997"), Path = Palette.Hex("#F4F1EA"), Accent = Palette.Hex("#E9E1D3"), Deco1 = Palette.Hex("#5E9C5A"), Deco2 = Palette.Hex("#8FD3E0"), Deco3 = Palette.Hex("#F2EDE3") };
                case 3: return new Theme { Ground = Palette.Hex("#BCD3A5"), Ground2 = Palette.Hex("#B0C997"), Path = Palette.Hex("#DCD6C8"), Accent = Palette.Hex("#8E9C78"), Deco1 = Palette.Hex("#9FB08A"), Deco2 = Palette.Hex("#CFC6B4"), Deco3 = Palette.Hex("#6E9460") };
                case 4: return new Theme { Ground = Palette.Hex("#ADDB93"), Ground2 = Palette.Hex("#A0D186"), Path = Palette.Hex("#DDD3BC"), Accent = Palette.Hex("#7FB77E"), Deco1 = Palette.Hex("#8CC47A"), Deco2 = Palette.Hex("#A9A08E"), Deco3 = Palette.Hex("#C9D9D2") };
                case 5: return new Theme { Ground = Palette.Hex("#96CF80"), Ground2 = Palette.Hex("#8AC474"), Path = Palette.Hex("#E6D6A8"), Accent = Palette.Hex("#5FA36B"), Deco1 = Palette.Hex("#4F9A5A"), Deco2 = Palette.Hex("#D8CFAE"), Deco3 = Palette.Hex("#7A5A3C") };
                default: return new Theme { Ground = Palette.Hex("#AEE0A5"), Ground2 = Palette.Hex("#A1D697"), Path = Palette.Hex("#F2E9D4"), Accent = Palette.Hex("#7EC8E3"), Deco1 = Palette.Hex("#79B86C"), Deco2 = Palette.Hex("#8FD3E0"), Deco3 = Palette.Hex("#8A6A48") };
            }
        }

        private void Rebuild()
        {
            Prim.ClearChildren(_content);
            _nodes.Clear(); _discs.Clear(); _areaFog.Clear(); _bobbers.Clear();
            _pulse = null; _placed = false; _lastDoneMask = -1;
            var th = ThemeOf(_wonder);
            var rng = new Rng(Rng.Hash("dest-" + _wonder));
            float areaLen = GameConfig.LevelsPerArea * NodeSpacing;

            // Alan zeminleri (yürünebilir) ve kilitli alan sisi
            var ground = Prim.Group("Ground", _content);
            ground.gameObject.AddComponent<WalkableGround>();
            _fogRoot = Prim.Group("Fog", _content);
            for (int a = 0; a < GameConfig.AreasPerWonder; a++)
            {
                float z0 = a * areaLen - NodeSpacing * 0.5f - (a == 0 ? 3f : 0f);
                float z1 = (a + 1) * areaLen - NodeSpacing * 0.5f + (a == GameConfig.AreasPerWonder - 1 ? 4f : 0f);
                var slab = Prim.Box(ground, new Vector3(0, -0.2f, (z0 + z1) * 0.5f), new Vector3(HalfWidth * 2f, 0.4f, z1 - z0), a % 2 == 0 ? th.Ground : th.Ground2, true);
                slab.name = "Area" + (a + 1);
                var fog = Prim.Box(_fogRoot, new Vector3(0, 1.4f, (z0 + z1) * 0.5f), new Vector3(HalfWidth * 2f + 0.2f, 2.8f, z1 - z0 - 0.05f), Palette.WithAlpha(Palette.White, 0.55f));
                fog.name = "FogArea" + (a + 1);
                fog.SetActive(false);
                _areaFog.Add(fog);
                // Alan girişindeki tabela direği (alan adı UI'da gösterilir)
                if (a > 0)
                {
                    var sp = NodePos(a * GameConfig.LevelsPerArea) + new Vector3(-1.6f, 0, -NodeSpacing * 0.5f);
                    var sign = Prim.Group("Sign" + (a + 1), _content, sp);
                    Prim.Cyl(sign, new Vector3(0, 0.6f, 0), 0.12f, 1.2f, Palette.Hex("#8A6A48"));
                    Prim.Box(sign, new Vector3(0, 1.15f, 0), new Vector3(0.9f, 0.4f, 0.08f), th.Accent);
                }
            }

            // Kıvrımlı yol
            var path = Prim.Group("Path", _content);
            for (int i = 0; i < GameConfig.LevelsPerWonder - 1; i++)
            {
                Vector3 a = NodePos(i), b = NodePos(i + 1);
                Vector3 mid = (a + b) * 0.5f; mid.y = 0.02f;
                var seg = Prim.Box(path, mid, new Vector3(1.0f, 0.04f, Vector3.Distance(a, b) + 0.2f), th.Path);
                seg.transform.localRotation = Quaternion.LookRotation(b - a);
            }

            // Bölüm düğümleri
            var nodesRoot = Prim.Group("Nodes", _content);
            for (int i = 0; i < GameConfig.LevelsPerWonder; i++)
            {
                int local = i + 1;
                var n = new NodeRef { Local = local, Pos = NodePos(i) };
                var root = Prim.Group("Node" + local, nodesRoot, n.Pos);
                n.Disc = Prim.Cyl(root, new Vector3(0, 0.09f, 0), 1.25f, 0.18f, Palette.Stone, true);
                var tap = n.Disc.AddComponent<Tappable>();
                int g = GameConfig.GlobalLevel(_wonder, local);
                Vector3 stand = n.Pos + new Vector3(0.9f, 0f, -0.3f);
                tap.OnTap = () =>
                {
                    Nav.LevelPreview(g); // ön izleme kartı hemen açılır
                    WalkExplorer(stand, null);
                };
                n.Visual = Prim.Group("Visual", root);
                _nodes.Add(n);
            }

            // Keşif noktaları
            var discRoot = Prim.Group("Discoveries", _content);
            for (int a = 1; a <= GameConfig.AreasPerWonder; a++)
            {
                int idx = (a - 1) * GameConfig.LevelsPerArea + 2;
                Vector3 np = NodePos(idx);
                float side = np.x > 0 ? -1f : 1f;
                var d = new DiscRef { Area = a, Pos = np + new Vector3(side * 3.3f, 0, 0.4f) };
                var root = Prim.Group("Discovery" + a, discRoot, d.Pos);
                var hit = new GameObject("Hit");
                hit.transform.SetParent(root, false);
                hit.transform.localPosition = new Vector3(0, 0.6f, 0);
                var bc = hit.AddComponent<BoxCollider>();
                bc.size = new Vector3(1.4f, 1.4f, 1.4f);
                d.Approach = Prim.Group("Approach", root, new Vector3(-side * 1.15f, 0, 0));
                var tap = hit.AddComponent<Tappable>();
                tap.ApproachPoint = d.Approach;
                int area = a;
                tap.OnTap = () => OnDiscoveryTapped(area);
                d.Visual = Prim.Group("Visual", root);
                _discs.Add(d);
            }

            BuildDecor(th, rng);

            _explorer = ExplorerCharacter.Create(_content);
            _mover = ExplorerMovementController.Attach(_explorer);

            PlaceholderAsset.Mark(_content.gameObject, "Destinasyon haritası (" + WonderCatalog.Get(_wonder).DestinationTheme + ") prosedürel primitiflerle kurulu.");
            _navFrames = 1; // collider'lar oturduktan sonra (bir kare sonra) NavMesh üret
        }

        // ------------------------------------------------------------------ dekor
        private void BuildDecor(Theme th, Rng rng)
        {
            var deco = Prim.Group("Decor", _content);
            float maxZ = (GameConfig.LevelsPerWonder - 1) * NodeSpacing;
            if (_wonder == 6)
            {
                // Rio: bir yanda deniz
                Prim.Box(deco, new Vector3(HalfWidth + 9f, -0.25f, maxZ * 0.5f), new Vector3(18f, 0.3f, maxZ + 30f), th.Deco2);
            }
            int count = 64;
            for (int k = 0; k < count; k++)
            {
                float z = -2f + (float)rng.NextDouble() * (maxZ + 4f);
                float sign = rng.Next(2) == 0 ? -1f : 1f;
                float x = sign * (5.6f + (float)rng.NextDouble() * 5f);
                if (Mathf.Abs(x - PathX(z)) < 2.6f) continue;
                if (_wonder == 6 && x > 0) x = -x; // deniz tarafını boş bırak
                bool far = Mathf.Abs(x) > 8.5f;
                var p = new Vector3(x, 0, z);
                DecoItem(deco, p, th, rng, far);
            }
            // Küçük çiçek/çalı serpintisi
            for (int k = 0; k < 40; k++)
            {
                float z = (float)rng.NextDouble() * maxZ;
                float x = (rng.Next(2) == 0 ? -1f : 1f) * (2.6f + (float)rng.NextDouble() * 7f);
                if (Mathf.Abs(x - PathX(z)) < 1.6f) continue;
                Color c = k % 3 == 0 ? Palette.Coral : k % 3 == 1 ? Palette.Hex("#F2C14E") : Palette.White;
                Prim.Ball(deco, new Vector3(x, 0.08f, z), 0.18f, k % 4 == 0 ? th.Deco3 : c);
            }
        }

        private void DecoItem(Transform parent, Vector3 p, Theme th, Rng rng, bool far)
        {
            var g = Prim.Group("deco", parent, p);
            float r = (float)rng.NextDouble();
            switch (_wonder)
            {
                case 0: // Roma kalıntıları
                    if (r < 0.45f)
                    {
                        float h = 0.8f + (float)rng.NextDouble() * 1.8f;
                        Prim.Cyl(g, new Vector3(0, h * 0.5f, 0), 0.45f, h, th.Deco1);
                        if (h > 1.8f) Prim.Box(g, new Vector3(0, h + 0.08f, 0), new Vector3(0.65f, 0.16f, 0.65f), th.Deco2);
                    }
                    else if (r < 0.65f)
                    {
                        Prim.Box(g, new Vector3(-0.7f, 0.9f, 0), new Vector3(0.4f, 1.8f, 0.5f), th.Deco1);
                        Prim.Box(g, new Vector3(0.7f, 0.9f, 0), new Vector3(0.4f, 1.8f, 0.5f), th.Deco1);
                        Prim.Box(g, new Vector3(0, 1.95f, 0), new Vector3(1.9f, 0.35f, 0.55f), th.Deco2);
                    }
                    else if (r < 0.85f) { Prim.Cone(g, Vector3.zero, 0.55f, 2.6f, th.Deco3); }
                    else Prim.Box(g, new Vector3(0, 0.2f, 0), new Vector3(1.2f, 0.4f, 0.6f), th.Deco2);
                    break;
                case 1: // Petra kanyonları
                    {
                        float h = far ? 4f + (float)rng.NextDouble() * 4f : 1.2f + (float)rng.NextDouble() * 2f;
                        var rock = Prim.Box(g, new Vector3(0, h * 0.5f, 0), new Vector3(1.8f + (float)rng.NextDouble() * 2f, h, 1.6f + (float)rng.NextDouble() * 2f), r < 0.5f ? th.Deco1 : th.Deco2);
                        rock.transform.localRotation = Quaternion.Euler(0, (float)rng.NextDouble() * 60f, 0);
                        if (r > 0.8f) Prim.Ball(g, new Vector3(0.9f, 0.3f, 0.6f), 0.6f, th.Deco3);
                        break;
                    }
                case 2: // Tac Mahal bahçeleri
                    if (r < 0.55f) Prim.Cone(g, Vector3.zero, 0.6f, 2.4f + (float)rng.NextDouble(), th.Deco1);
                    else if (r < 0.75f) Prim.Box(g, new Vector3(0, 0.02f, 0), new Vector3(0.9f, 0.05f, 3.5f), th.Deco2);
                    else
                    {
                        Prim.Box(g, new Vector3(0, 0.4f, 0), new Vector3(1f, 0.8f, 1f), th.Deco3);
                        Prim.FromMesh(MeshFactory.Dome(), g, new Vector3(0, 0.8f, 0), new Vector3(0.9f, 1.1f, 0.9f), th.Deco3, "dome");
                    }
                    break;
                case 3: // Çin Seddi dağları
                    if (far || r < 0.35f) Prim.Cone(g, Vector3.zero, 4f + (float)rng.NextDouble() * 4f, 3f + (float)rng.NextDouble() * 4f, r < 0.5f ? th.Deco1 : th.Deco3);
                    else if (r < 0.7f)
                    {
                        Prim.Box(g, new Vector3(0, 0.5f, 0), new Vector3(0.7f, 1f, 2.6f), th.Deco2);
                        for (int c = 0; c < 4; c++) Prim.Box(g, new Vector3(0.25f, 1.12f, -1.1f + c * 0.72f), new Vector3(0.2f, 0.24f, 0.3f), th.Deco2);
                    }
                    else Prim.Cone(g, Vector3.zero, 0.7f, 1.6f, th.Deco3);
                    break;
                case 4: // Machu Picchu terasları
                    if (far || r < 0.3f) Prim.Cone(g, Vector3.zero, 5f + (float)rng.NextDouble() * 3f, 5f + (float)rng.NextDouble() * 4f, th.Deco1);
                    else if (r < 0.7f)
                    {
                        for (int s = 0; s < 3; s++) Prim.Box(g, new Vector3(0, 0.25f + s * 0.5f, -s * 0.4f), new Vector3(2.6f - s * 0.5f, 0.5f, 1.4f), s % 2 == 0 ? th.Deco1 : Palette.Lighten(th.Deco1, 0.15f));
                    }
                    else
                    {
                        Prim.Box(g, new Vector3(0, 0.4f, 0), new Vector3(1f, 0.8f, 0.9f), th.Deco2);
                        var roof = Prim.FromMesh(MeshFactory.Prism(), g, new Vector3(0, 0.8f, 0), new Vector3(1.1f, 0.6f, 1f), Palette.Hex("#D9B36A"), "roof");
                        roof.name = "Roof";
                    }
                    break;
                case 5: // Maya ormanı
                    if (r < 0.75f)
                    {
                        float h = 1.2f + (float)rng.NextDouble() * 1.4f;
                        Prim.Cyl(g, new Vector3(0, h * 0.5f, 0), 0.25f, h, th.Deco3);
                        Prim.Ball(g, new Vector3(0, h + 0.3f, 0), 1.5f, th.Deco1);
                        Prim.Ball(g, new Vector3(0.5f, h, 0.2f), 1.0f, Palette.Lighten(th.Deco1, 0.1f));
                    }
                    else for (int s = 0; s < 4; s++) Prim.Box(g, new Vector3(0, 0.2f + s * 0.4f, 0), new Vector3(2.4f - s * 0.5f, 0.4f, 2.4f - s * 0.5f), th.Deco2);
                    break;
                default: // Rio tepeleri
                    if (far || r < 0.35f)
                    {
                        var hill = Prim.Ball(g, Vector3.zero, 5f + (float)rng.NextDouble() * 3f, th.Deco1);
                        hill.transform.localScale = new Vector3(hill.transform.localScale.x, 3.5f + (float)rng.NextDouble() * 3f, hill.transform.localScale.z);
                    }
                    else
                    {
                        float h = 1.8f + (float)rng.NextDouble();
                        var trunk = Prim.Cyl(g, new Vector3(0, h * 0.5f, 0), 0.18f, h, th.Deco3);
                        trunk.transform.localRotation = Quaternion.Euler(0, 0, 6f);
                        for (int l = 0; l < 4; l++)
                        {
                            var leaf = Prim.Box(g, new Vector3(0, h, 0), new Vector3(0.25f, 0.06f, 1.4f), Palette.Hex("#5FA36B"));
                            leaf.transform.localRotation = Quaternion.Euler(18f, l * 90f + 20f, 0);
                        }
                    }
                    break;
            }
        }

        // ------------------------------------------------------------------ durum
        public void RefreshState()
        {
            if (_wonder < 0) return;
            var s = Session;
            int cur = CurrentLocal();
            _pulse = null;

            foreach (var n in _nodes)
            {
                var st = s.Destinations.NodeStateOf(_wonder, n.Local);
                int stars = s.Destinations.NodeStars(_wonder, n.Local);
                if (st == n.State && stars == n.Stars && st != NodeState.Current) continue;
                n.State = st; n.Stars = stars;
                Prim.ClearChildren(n.Visual);
                switch (st)
                {
                    case NodeState.Completed:
                        Prim.SetColor(n.Disc, Palette.Gold);
                        for (int k = 0; k < 3; k++)
                        {
                            var pip = Prim.Ball(n.Visual, new Vector3(-0.32f + k * 0.32f, 0.32f + (k == 1 ? 0.08f : 0f), 0), 0.24f, k < stars ? Palette.Hex("#F2C14E") : Palette.Lighten(Palette.Stone, 0.4f));
                            if (k < stars) pip.GetComponent<Renderer>().sharedMaterial = MaterialLibrary.Get(Palette.Hex("#F2C14E"), 0.35f);
                        }
                        break;
                    case NodeState.Current:
                        Prim.SetColor(n.Disc, Palette.Turquoise);
                        var ring = Prim.Cyl(n.Visual, new Vector3(0, 0.05f, 0), 1.8f, 0.04f, Palette.WithAlpha(Palette.Turquoise, 0.45f));
                        _pulse = ring.transform;
                        Prim.Cyl(n.Visual, new Vector3(-0.45f, 0.75f, 0), 0.07f, 1.3f, Palette.Hex("#8A6A48"));
                        Prim.Box(n.Visual, new Vector3(-0.2f, 1.22f, 0), new Vector3(0.45f, 0.3f, 0.04f), Palette.Coral);
                        break;
                    default:
                        Prim.SetColor(n.Disc, Palette.Locked);
                        break;
                }
                // Jigsaw ödüllü düğüm: henüz tamamlanmadıysa yüzen parça işareti
                if (GameConfig.JigsawPieceForLocalLevel(n.Local) >= 0 && st != NodeState.Completed)
                {
                    var piece = Prim.Box(n.Visual, new Vector3(0, 0.75f, 0), new Vector3(0.32f, 0.32f, 0.08f), Palette.Gold);
                    piece.GetComponent<Renderer>().sharedMaterial = MaterialLibrary.Get(Palette.Gold, 0.3f);
                    _bobbers.Add(piece.transform);
                }
            }
            _bobbers.RemoveAll(t => t == null);

            for (int a = 1; a <= GameConfig.AreasPerWonder; a++)
                _areaFog[a - 1].SetActive(!s.Destinations.IsAreaUnlocked(_wonder, a));

            int doneMask = 0;
            foreach (var d in _discs)
            {
                var st = s.Discoveries.StateOf(_wonder, d.Area);
                if (st == DiscoveryState.Done) doneMask |= 1 << d.Area;
                if (st == d.State) continue;
                d.State = st;
                Prim.ClearChildren(d.Visual);
                BuildDiscoveryVisual(d, st);
            }
            if (_lastDoneMask >= 0 && (doneMask & ~_lastDoneMask) != 0 && _explorer != null) _explorer.Play(ExplorerAnim.Discover);
            _lastDoneMask = doneMask;

            if (_explorer != null)
            {
                _explorer.ApplyLoadout(s.Save.Equipped);
                if (!_placed)
                {
                    _placed = true;
                    Vector3 p = NodePos(cur - 1) + new Vector3(0.9f, 0f, -0.3f);
                    _mover.Warp(p);
                    _explorer.transform.rotation = Quaternion.LookRotation(Vector3.forward);
                }
            }
        }

        private void BuildDiscoveryVisual(DiscRef d, DiscoveryState st)
        {
            var kind = GameConfig.DiscoveryKindForArea(d.Area);
            var tool = GameConfig.ToolForArea(d.Area);
            bool locked = st == DiscoveryState.Locked;
            Color stone = locked ? Palette.Locked : Palette.Marble;
            if (kind == DiscoveryKind.Artifact)
            {
                Prim.Cyl(d.Visual, new Vector3(0, 0.35f, 0), 0.8f, 0.7f, stone);
                Prim.Cyl(d.Visual, new Vector3(0, 0.74f, 0), 0.95f, 0.08f, locked ? Palette.Locked : Palette.Gold);
                if (st == DiscoveryState.Available)
                {
                    var orb = Prim.Ball(d.Visual, new Vector3(0, 1.1f, 0), 0.42f, Palette.Gold);
                    orb.GetComponent<Renderer>().sharedMaterial = MaterialLibrary.Get(Palette.Hex("#F2C14E"), 0.6f);
                    Prim.Cyl(d.Visual, new Vector3(0, 0.02f, 0), 1.7f, 0.02f, Palette.WithAlpha(Palette.Gold, 0.35f));
                }
                else if (st == DiscoveryState.Done)
                    Prim.Box(d.Visual, new Vector3(0, 0.86f, 0), new Vector3(0.3f, 0.12f, 0.3f), Palette.Green);
            }
            else
            {
                Color wood = locked ? Palette.Locked : Palette.Terracotta;
                Prim.Box(d.Visual, new Vector3(0, 0.3f, 0), new Vector3(1.0f, 0.6f, 0.7f), wood);
                var lidPivot = Prim.Group("Lid", d.Visual, new Vector3(0, 0.6f, -0.35f));
                Prim.Box(lidPivot, new Vector3(0, 0.08f, 0.35f), new Vector3(1.05f, 0.18f, 0.72f), locked ? Palette.Locked : Palette.Gold);
                if (st == DiscoveryState.Done) lidPivot.localRotation = Quaternion.Euler(-65f, 0, 0);
                else Prim.Box(d.Visual, new Vector3(0, 0.45f, 0.36f), new Vector3(0.16f, 0.2f, 0.04f), Palette.GoldDeep);
            }
            if (st == DiscoveryState.Available)
            {
                Color tc = tool == ToolType.Magnifier ? Palette.Turquoise : tool == ToolType.Brush ? Palette.Gold : Palette.Terracotta;
                var marker = Prim.Box(d.Visual, new Vector3(0, 2.0f, 0), new Vector3(0.36f, 0.36f, 0.36f), tc);
                marker.transform.localRotation = Quaternion.Euler(45f, 0, 45f);
                marker.GetComponent<Renderer>().sharedMaterial = MaterialLibrary.Get(tc, 0.35f);
                _bobbers.Add(marker.transform);
            }
        }

        // ------------------------------------------------------------------ etkileşim
        private void WalkExplorer(Vector3 to, System.Action onArrive)
        {
            if (_mover == null) return;
            _mover.WalkTo(to, onArrive);
            Game.Rig.FollowTarget = _explorer.transform;
            Game.Rig.SetMode(CameraMode.Follow);
        }

        private void OnDiscoveryTapped(int area)
        {
            var st = Session.Discoveries.StateOf(_wonder, area);
            var p = DiscoveryManager.Point(_wonder, area);
            if (st == DiscoveryState.Locked)
            {
                Game.UI.Toast("Bu keşif noktası, " + WonderCatalog.Get(_wonder).AreaNames[area - 1] + " alanı açılınca erişilebilir.", Palette.Coral);
                return;
            }
            var d = _discs[area - 1];
            if (st == DiscoveryState.Done)
            {
                Game.UI.Toast(p.Kind == DiscoveryKind.Artifact ? "Bu eser müzende sergileniyor." : "Bu sandık zaten açıldı.", Palette.Turquoise);
                WalkExplorer(d.Approach.position, null);
                return;
            }
            Game.UI.Toast(Names.Tool(p.Tool) + " ile keşfe gidiliyor…", Palette.Gold);
            WalkExplorer(d.Approach.position, () =>
            {
                var mv = _mover;
                if (mv != null) mv.FaceTowards(d.Pos);
                _explorer.Play(ExplorerAnim.Interact);
                Tween.Delay(0.8f, () =>
                {
                    if (this == null || !isActiveAndEnabled) return;
                    if (Game.UI.Current is DestinationScreen) Nav.Discovery(_wonder, area);
                });
            });
        }

        private void Update()
        {
            if (_wonder < 0) return;
            if (_navFrames >= 0)
            {
                if (_navFrames-- == 0)
                {
                    float maxZ = (GameConfig.LevelsPerWonder - 1) * NodeSpacing;
                    RuntimeNavMesh.Build(_content, new Bounds(new Vector3(0, 0, maxZ * 0.5f), new Vector3(HalfWidth * 2f + 2f, 8f, maxZ + 16f)));
                    if (_mover != null && _explorer != null) _mover.Warp(_explorer.transform.position);
                }
            }

            float t = Time.time;
            if (_pulse != null)
            {
                float k = 1f + Mathf.Sin(t * 3f) * 0.12f;
                _pulse.localScale = new Vector3(1.8f * k, 0.02f, 1.8f * k);
            }
            for (int i = 0; i < _bobbers.Count; i++)
            {
                var b = _bobbers[i];
                if (b == null) continue;
                var lp = b.localPosition;
                lp.y = (lp.y > 1.5f ? 2.0f : 0.75f) + Mathf.Sin(t * 2f + i) * 0.08f;
                b.localPosition = lp;
                b.Rotate(0f, 60f * Time.deltaTime, 0f, Space.World);
            }

            if (_mover != null && !_mover.IsMoving && Game.Rig.Mode == CameraMode.Follow) Game.Rig.SetMode(CameraMode.FreePan);

            if (!InputAdapter.Tap || Game.UI.HasPopup || !(Game.UI.Current is DestinationScreen)) return;
            Tappable tap; Vector3 gp; bool hitGround;
            if (!WorldPicker.Pick(Game.Cam, InputAdapter.Position, out tap, out gp, out hitGround)) return;
            if (tap != null)
            {
                if (tap.OnTap != null) tap.OnTap();
                if (tap.WalkToFirst)
                {
                    Vector3 to = tap.ApproachPoint != null ? tap.ApproachPoint.position : tap.transform.position;
                    WalkExplorer(to, tap.OnArrive);
                }
            }
            else if (hitGround) WalkExplorer(gp, null);
        }

        private void OnDisable()
        {
            if (_mover != null) _mover.Stop();
        }
    }
}
