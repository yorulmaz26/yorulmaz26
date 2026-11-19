using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Match3;

namespace WorldWonders.Game
{
    /// <summary>
    /// Match-3 tahtasının 3D stilize görünümü. Oyun kuralları Core'daki Match3Game/Match3Board'dadır;
    /// bu sınıf yalnız MoveResult verilerini canlandırır, girdiyi (kaydırma/dokunma) toplar ve sonunda
    /// tahtayla birebir senkronlar. Taş modelleri GEÇİCİ VARLIK'tır (primitiflerden).
    /// </summary>
    public sealed class Match3BoardView : ViewBase
    {
        public const float Cell = 1f;
        private const float TileHeight = 0.32f;
        private const float CameraPitch = 72f;

        private sealed class TileVisual
        {
            public int Id;
            public GameObject Go;
            public TileColor Color;
            public SpecialType Special;
            public Pos Cell;
        }

        private sealed class ObstacleVisual
        {
            public GameObject Go;
            public int State;
        }

        private Match3Game _game;
        private Transform _boardRoot, _tilesRoot, _fxRoot, _obstacleRoot;
        private readonly Dictionary<int, TileVisual> _tiles = new Dictionary<int, TileVisual>();
        private readonly Dictionary<Pos, ObstacleVisual> _sand = new Dictionary<Pos, ObstacleVisual>();
        private readonly Dictionary<Pos, ObstacleVisual> _crates = new Dictionary<Pos, ObstacleVisual>();
        private readonly Dictionary<Pos, ObstacleVisual> _chains = new Dictionary<Pos, ObstacleVisual>();
        private GameObject _selectMarker, _targetMarker;

        public Match3Game Game3 { get { return _game; } }
        public bool IsBusy { get; private set; }
        public bool InputEnabled = true;
        /// <summary>Güçlendirici hedefleme modu: dokunulan hücre CellTapped ile bildirilir.</summary>
        public bool Targeting;

        public event Action<Pos, Pos> SwapRequested;
        public event Action<Pos> CellTapped;

        private bool _hasDrag; private Pos _dragCell; private Vector2 _dragOrigin;
        private bool _hasSelection; private Pos _selected;
        private float _idle;
        private readonly List<TileVisual> _hint = new List<TileVisual>();
        private float _hintT;

        private Match3Board B { get { return _game.Board; } }

        protected override void Build()
        {
            _boardRoot = Prim.Group("Board", transform);
            PlaceholderAsset.Mark(gameObject, "Match-3 tahta, taş ve engel modelleri primitiflerden üretilmiştir; final stilize 3D taş modelleriyle değiştirilecek.");
            // Geniş açık zemin (arka plan bütünlüğü için)
            Prim.Box(transform, new Vector3(0, -0.45f, 0), new Vector3(60, 0.2f, 60), Palette.Cream);
        }

        // ------------------------------------------------------------------ kurulum
        public void Setup(Match3Game game)
        {
            _game = game;
            Prim.ClearChildren(_boardRoot);
            _tiles.Clear(); _sand.Clear(); _crates.Clear(); _chains.Clear(); _hint.Clear();
            _hasSelection = false; _hasDrag = false; Targeting = false; IsBusy = false; _idle = 0;

            var theme = Palette.Hex(WonderCatalog.Get(game.Level.Wonder).ThemeColor);
            var cellsRoot = Prim.Group("Cells", _boardRoot);
            _obstacleRoot = Prim.Group("Obstacles", _boardRoot);
            _tilesRoot = Prim.Group("Tiles", _boardRoot);
            _fxRoot = Prim.Group("Fx", _boardRoot);

            // Tepsi çerçevesi
            Prim.Box(cellsRoot, new Vector3(0, -0.2f, 0), new Vector3(B.Width * Cell + 0.5f, 0.2f, B.Height * Cell + 0.5f), Palette.Lighten(theme, 0.35f));
            var light = Palette.Marble;
            var dark = Palette.Lighten(Palette.Stone, 0.35f);
            for (int y = 0; y < B.Height; y++)
                for (int x = 0; x < B.Width; x++)
                {
                    if (!B.Cells[x, y].Playable) continue;
                    Prim.Box(cellsRoot, CellPos(x, y) + new Vector3(0, -0.06f, 0), new Vector3(Cell * 0.96f, 0.12f, Cell * 0.96f), (x + y) % 2 == 0 ? light : dark);
                }

            _selectMarker = Prim.FromMesh(MeshFactory.Ring(0.42f, 0.5f), _boardRoot, Vector3.zero, new Vector3(1, 0.08f, 1), Palette.Gold, "select");
            _selectMarker.GetComponent<Renderer>().sharedMaterial = MaterialLibrary.Get(Palette.Gold, 0.6f);
            _selectMarker.SetActive(false);
            _targetMarker = Prim.FromMesh(MeshFactory.Ring(0.4f, 0.52f), _boardRoot, Vector3.zero, new Vector3(1, 0.08f, 1), Palette.Coral, "target");
            _targetMarker.GetComponent<Renderer>().sharedMaterial = MaterialLibrary.Get(Palette.Coral, 0.6f);
            _targetMarker.SetActive(false);

            Sync();
            if (isActiveAndEnabled) ConfigureCamera();
        }

        public override void Enter() { ConfigureCamera(); }

        public override void Exit()
        {
            StopAllCoroutines();
            IsBusy = false;
            var rig = Game.Rig;
            rig.AllowPan = true; rig.AllowZoom = true;
        }

        private void ConfigureCamera()
        {
            if (_game == null) return;
            var rig = Game.Rig;
            var cam = Game.Cam;
            float aspect = Mathf.Max(0.3f, cam.aspect);
            float tanV = Mathf.Tan(cam.fieldOfView * 0.5f * Mathf.Deg2Rad);
            float tanH = tanV * aspect;
            float halfW = B.Width * Cell * 0.5f + 0.45f;
            float halfH = B.Height * Cell * 0.5f + 0.45f;
            // Üstte hedef çubuğu, altta güçlendirici çubuğu var: tahta ekranın ~%58'ine sığmalı.
            float d = Mathf.Max(halfW / tanH, halfH / (tanV * 0.58f));
            var pivot = new Vector3(0, 0, 0.55f);
            rig.Configure(new Rect(pivot.x - 0.01f, pivot.z - 0.01f, 0.02f, 0.02f), d, d, d, CameraPitch, 0f, pivot);
            rig.AllowPan = false; rig.AllowZoom = false;
            rig.SetMode(CameraMode.FreePan);
        }

        public Vector3 CellPos(int x, int y)
        {
            return new Vector3((x - (B.Width - 1) * 0.5f) * Cell, 0f, ((B.Height - 1) * 0.5f - y) * Cell);
        }
        private Vector3 CellPos(Pos p) { return CellPos(p.X, p.Y); }
        private Vector3 TilePos(Pos p) { return CellPos(p) + Vector3.up * TileHeight; }

        // ------------------------------------------------------------------ taş modelleri
        private static Color Tc(TileColor c) { return Palette.TileColors[(int)c]; }

        private GameObject BuildTile(TileColor color, SpecialType special)
        {
            var root = new GameObject("tile");
            root.transform.SetParent(_tilesRoot, false);
            var m = Prim.Group("model", root.transform);
            m.localRotation = Quaternion.Euler(CameraPitch, 0, 0);
            if (special == SpecialType.TimeOrb)
            {
                Prim.Ball(m, Vector3.zero, 0.74f, Palette.Lighten(Palette.Turquoise, 0.35f)).GetComponent<Renderer>().sharedMaterial = MaterialLibrary.Get(Palette.Lighten(Palette.Turquoise, 0.35f), 0.55f);
                Prim.Ball(m, new Vector3(0, 0, -0.18f), 0.36f, Palette.Gold).GetComponent<Renderer>().sharedMaterial = MaterialLibrary.Get(Palette.Gold, 0.7f);
                var ring = Prim.FromMesh(MeshFactory.Ring(0.4f, 0.47f), m, Vector3.zero, new Vector3(1, 0.06f, 1), Palette.White, "orbring");
                ring.transform.localRotation = Quaternion.Euler(90, 0, 25);
                ring.GetComponent<Renderer>().sharedMaterial = MaterialLibrary.Get(Palette.White, 0.8f);
                return root;
            }
            var body = Prim.Group("body", m);
            BuildBody(color, body);
            if (special == SpecialType.Bomb)
            {
                body.localScale = Vector3.one * 0.78f;
                var ring = Prim.FromMesh(MeshFactory.Ring(0.4f, 0.5f), m, new Vector3(0, 0, 0.02f), new Vector3(1, 0.12f, 1), Palette.GoldDeep, "bombring");
                ring.transform.localRotation = Quaternion.Euler(90, 0, 0);
                ring.GetComponent<Renderer>().sharedMaterial = MaterialLibrary.Get(Palette.Gold, 0.35f);
                Prim.Cyl(m, new Vector3(0.28f, 0.36f, -0.05f), 0.07f, 0.2f, Palette.Text).transform.localRotation = Quaternion.Euler(0, 0, -35);
                Prim.Ball(m, new Vector3(0.36f, 0.46f, -0.05f), 0.12f, Palette.Coral).GetComponent<Renderer>().sharedMaterial = MaterialLibrary.Get(Palette.Coral, 0.8f);
            }
            else if (special == SpecialType.ArrowRow || special == SpecialType.ArrowColumn)
            {
                var a = Prim.Group("arrow", m, new Vector3(0, 0, -0.22f));
                if (special == SpecialType.ArrowColumn) a.localRotation = Quaternion.Euler(0, 0, 90);
                var bright = MaterialLibrary.Get(Palette.White, 0.8f);
                var bar = Prim.Box(a, Vector3.zero, new Vector3(0.62f, 0.09f, 0.04f), Palette.White);
                bar.GetComponent<Renderer>().sharedMaterial = bright;
                var h1 = Prim.Cone(a, new Vector3(0.31f, 0, 0), 0.24f, 0.18f, Palette.White);
                h1.transform.localRotation = Quaternion.Euler(0, 0, -90); h1.GetComponent<Renderer>().sharedMaterial = bright;
                var h2 = Prim.Cone(a, new Vector3(-0.31f, 0, 0), 0.24f, 0.18f, Palette.White);
                h2.transform.localRotation = Quaternion.Euler(0, 0, 90); h2.GetComponent<Renderer>().sharedMaterial = bright;
            }
            return root;
        }

        private static void BuildBody(TileColor color, Transform m)
        {
            Color c = Tc(color);
            switch (color)
            {
                case TileColor.GoldCoin:
                    Prim.Cyl(m, Vector3.zero, 0.78f, 0.16f, c).transform.localRotation = Quaternion.Euler(90, 0, 0);
                    Prim.Cyl(m, new Vector3(0, 0, -0.08f), 0.56f, 0.04f, Palette.Darken(c, 0.12f)).transform.localRotation = Quaternion.Euler(90, 0, 0);
                    Prim.Box(m, new Vector3(0, 0, -0.1f), new Vector3(0.12f, 0.34f, 0.04f), Palette.Lighten(c, 0.4f));
                    break;
                case TileColor.Scroll:
                    Prim.Cyl(m, Vector3.zero, 0.38f, 0.64f, c).transform.localRotation = Quaternion.Euler(0, 0, 90);
                    Prim.Cyl(m, new Vector3(0.35f, 0, 0), 0.44f, 0.08f, Palette.Terracotta).transform.localRotation = Quaternion.Euler(0, 0, 90);
                    Prim.Cyl(m, new Vector3(-0.35f, 0, 0), 0.44f, 0.08f, Palette.Terracotta).transform.localRotation = Quaternion.Euler(0, 0, 90);
                    Prim.Box(m, new Vector3(0, 0, -0.02f), new Vector3(0.09f, 0.42f, 0.42f), Palette.Coral);
                    break;
                case TileColor.Amphora:
                    Prim.Create(PrimitiveType.Sphere, m, new Vector3(0, -0.06f, 0), new Vector3(0.52f, 0.6f, 0.4f), c);
                    Prim.Cyl(m, new Vector3(0, 0.3f, 0), 0.2f, 0.22f, c);
                    Prim.Cyl(m, new Vector3(0, 0.42f, 0), 0.3f, 0.06f, Palette.Darken(c, 0.15f));
                    Prim.Cyl(m, new Vector3(0, -0.37f, 0), 0.22f, 0.08f, Palette.Darken(c, 0.15f));
                    Prim.Capsule(m, new Vector3(0.22f, 0.2f, 0), 0.07f, 0.3f, Palette.Darken(c, 0.1f)).transform.localRotation = Quaternion.Euler(0, 0, 25);
                    Prim.Capsule(m, new Vector3(-0.22f, 0.2f, 0), 0.07f, 0.3f, Palette.Darken(c, 0.1f)).transform.localRotation = Quaternion.Euler(0, 0, -25);
                    Prim.Box(m, new Vector3(0, -0.04f, -0.19f), new Vector3(0.44f, 0.06f, 0.04f), Palette.Text);
                    break;
                case TileColor.Scarab:
                    Prim.Create(PrimitiveType.Sphere, m, new Vector3(0, -0.06f, 0), new Vector3(0.5f, 0.6f, 0.3f), c);
                    Prim.Ball(m, new Vector3(0, 0.31f, 0), 0.26f, Palette.Darken(c, 0.2f));
                    Prim.Box(m, new Vector3(0, -0.06f, -0.15f), new Vector3(0.03f, 0.5f, 0.04f), Palette.Darken(c, 0.4f));
                    Prim.Ball(m, new Vector3(0, 0.08f, -0.13f), 0.11f, Palette.Gold);
                    for (int s = -1; s <= 1; s += 2)
                        for (int k = 0; k < 3; k++)
                            Prim.Box(m, new Vector3(s * 0.3f, 0.12f - k * 0.17f, 0), new Vector3(0.16f, 0.04f, 0.04f), Palette.Darken(c, 0.35f)).transform.localRotation = Quaternion.Euler(0, 0, s * (20 - k * 20));
                    break;
                case TileColor.Compass:
                    Prim.Cyl(m, Vector3.zero, 0.78f, 0.14f, c).transform.localRotation = Quaternion.Euler(90, 0, 0);
                    Prim.Cyl(m, new Vector3(0, 0, -0.07f), 0.6f, 0.04f, Palette.Cream).transform.localRotation = Quaternion.Euler(90, 0, 0);
                    var n = Prim.Group("needle", m, new Vector3(0, 0, -0.1f));
                    n.localRotation = Quaternion.Euler(0, 0, 30);
                    Prim.Box(n, new Vector3(0, 0.12f, 0), new Vector3(0.09f, 0.26f, 0.03f), Palette.Coral);
                    Prim.Box(n, new Vector3(0, -0.12f, 0), new Vector3(0.09f, 0.26f, 0.03f), Palette.Stone);
                    Prim.Ball(m, new Vector3(0, 0, -0.12f), 0.1f, Palette.Gold);
                    break;
                default: // Taç
                    Prim.Box(m, new Vector3(0, -0.17f, 0), new Vector3(0.64f, 0.24f, 0.22f), c);
                    for (int k = -1; k <= 1; k++)
                    {
                        Prim.Cone(m, new Vector3(k * 0.22f, -0.06f, 0), 0.2f, k == 0 ? 0.36f : 0.28f, Palette.Gold);
                        Prim.Ball(m, new Vector3(k * 0.22f, k == 0 ? 0.31f : 0.23f, 0), 0.08f, Palette.Gold);
                    }
                    Prim.Ball(m, new Vector3(0, -0.17f, -0.12f), 0.1f, Palette.Turquoise);
                    Prim.Ball(m, new Vector3(-0.2f, -0.17f, -0.11f), 0.07f, Palette.Coral);
                    Prim.Ball(m, new Vector3(0.2f, -0.17f, -0.11f), 0.07f, Palette.Coral);
                    break;
            }
        }

        private TileVisual CreateVisual(int id, Pos at, TileColor color, SpecialType special, Vector3? startPos = null, bool pop = false)
        {
            TileVisual old;
            if (_tiles.TryGetValue(id, out old) && old.Go != null) Destroy(old.Go);
            var v = new TileVisual { Id = id, Color = color, Special = special, Cell = at, Go = BuildTile(color, special) };
            v.Go.name = "tile_" + id;
            v.Go.transform.localPosition = startPos ?? TilePos(at);
            if (pop)
            {
                v.Go.transform.localScale = Vector3.one * 0.2f;
                Tween.Scale(v.Go.transform, Vector3.one, 0.25f, Ease.OutBack);
            }
            _tiles[id] = v;
            return v;
        }

        private TileVisual VisualAt(Pos p)
        {
            foreach (var v in _tiles.Values) if (v.Cell.Equals(p) && v.Go != null) return v;
            return null;
        }

        // ------------------------------------------------------------------ engeller
        private void SyncObstacles()
        {
            for (int y = 0; y < B.Height; y++)
                for (int x = 0; x < B.Width; x++)
                {
                    var p = new Pos(x, y);
                    var c = B.Cells[x, y];
                    SyncObstacle(_sand, p, c.Playable ? c.Sand : 0, BuildSand);
                    SyncObstacle(_crates, p, c.Playable ? c.Crate : 0, BuildCrate);
                    SyncObstacle(_chains, p, c.Playable && c.Chain ? 1 : 0, BuildChain);
                }
        }

        private void SyncObstacle(Dictionary<Pos, ObstacleVisual> map, Pos p, int state, Func<Pos, int, GameObject> build)
        {
            ObstacleVisual ov;
            map.TryGetValue(p, out ov);
            if (ov != null && ov.State == state) return;
            if (ov != null && ov.Go != null)
            {
                var go = ov.Go;
                Tween.Scale(go.transform, Vector3.zero, 0.18f, Ease.InCubic, () => { if (go != null) Destroy(go); });
            }
            map.Remove(p);
            if (state <= 0) return;
            var ng = build(p, state);
            map[p] = new ObstacleVisual { Go = ng, State = state };
        }

        private GameObject BuildSand(Pos p, int layers)
        {
            var color = layers >= 2 ? Palette.Darken(Palette.Sand, 0.12f) : Palette.Sand;
            float h = 0.05f + 0.05f * layers;
            var go = Prim.Box(_obstacleRoot, CellPos(p) + new Vector3(0, h * 0.5f, 0), new Vector3(Cell * 0.92f, h, Cell * 0.92f), color);
            go.name = "sand";
            return go;
        }

        private GameObject BuildCrate(Pos p, int hp)
        {
            var root = Prim.Group("crate", _obstacleRoot, CellPos(p));
            var wood = Palette.Hex(hp >= 2 ? "#A86E3E" : "#C99566");
            Prim.Box(root, new Vector3(0, 0.36f, 0), new Vector3(0.86f, 0.72f, 0.86f), wood);
            var band = Palette.Darken(wood, 0.3f);
            Prim.Box(root, new Vector3(0, 0.73f, 0), new Vector3(0.88f, 0.03f, 0.12f), band);
            Prim.Box(root, new Vector3(0, 0.73f, 0), new Vector3(0.12f, 0.03f, 0.88f), band);
            if (hp >= 2)
            {
                Prim.Box(root, new Vector3(0, 0.73f, 0.33f), new Vector3(0.88f, 0.03f, 0.08f), Palette.Stone);
                Prim.Box(root, new Vector3(0, 0.73f, -0.33f), new Vector3(0.88f, 0.03f, 0.08f), Palette.Stone);
            }
            else
            {
                Prim.Box(root, new Vector3(0.18f, 0.74f, 0.1f), new Vector3(0.3f, 0.02f, 0.04f), Palette.Text).transform.localRotation = Quaternion.Euler(0, 35, 0);
            }
            return root.gameObject;
        }

        private GameObject BuildChain(Pos p, int _)
        {
            var root = Prim.Group("chain", _obstacleRoot, CellPos(p) + new Vector3(0, 0.72f, 0));
            var steel = Palette.Hex("#8C96A0");
            Prim.Box(root, Vector3.zero, new Vector3(1.0f, 0.07f, 0.09f), steel).transform.localRotation = Quaternion.Euler(0, 45, 0);
            Prim.Box(root, Vector3.zero, new Vector3(1.0f, 0.07f, 0.09f), steel).transform.localRotation = Quaternion.Euler(0, -45, 0);
            Prim.Box(root, new Vector3(0, 0.05f, 0), new Vector3(0.22f, 0.18f, 0.12f), Palette.GoldDeep);
            return root.gameObject;
        }

        // ------------------------------------------------------------------ senkron
        /// <summary>Görseli tahtanın gerçek durumuyla birebir eşitler (animasyon sonrası güvenlik ağı).</summary>
        public void Sync()
        {
            if (_game == null) return;
            var alive = new HashSet<int>();
            for (int y = 0; y < B.Height; y++)
                for (int x = 0; x < B.Width; x++)
                {
                    var t = B.Tiles[x, y];
                    if (t == null) continue;
                    var p = new Pos(x, y);
                    alive.Add(t.Id);
                    TileVisual v;
                    if (!_tiles.TryGetValue(t.Id, out v) || v.Go == null || v.Color != t.Color || v.Special != t.Special)
                        v = CreateVisual(t.Id, p, t.Color, t.Special);
                    v.Cell = p;
                    v.Go.transform.localPosition = TilePos(p);
                    v.Go.transform.localScale = Vector3.one;
                }
            var dead = new List<int>();
            foreach (var kv in _tiles) if (!alive.Contains(kv.Key)) dead.Add(kv.Key);
            foreach (var id in dead) { if (_tiles[id].Go != null) Destroy(_tiles[id].Go); _tiles.Remove(id); }
            SyncObstacles();
        }

        // ------------------------------------------------------------------ animasyon
        public void AnimateInvalid(Pos a, Pos b, Action done)
        {
            StartCoroutine(InvalidRoutine(a, b, done));
        }

        private IEnumerator InvalidRoutine(Pos a, Pos b, Action done)
        {
            IsBusy = true; ClearHint();
            var va = VisualAt(a); var vb = VisualAt(b);
            Vector3 pa = TilePos(a), pb = TilePos(b);
            if (va != null) Tween.Move(va.Go.transform, Vector3.Lerp(pa, pb, 0.55f), 0.12f);
            if (vb != null) Tween.Move(vb.Go.transform, Vector3.Lerp(pb, pa, 0.55f), 0.12f);
            yield return new WaitForSeconds(0.13f);
            if (va != null) Tween.Move(va.Go.transform, pa, 0.18f, Ease.OutBack);
            if (vb != null) Tween.Move(vb.Go.transform, pb, 0.18f, Ease.OutBack);
            yield return new WaitForSeconds(0.2f);
            IsBusy = false;
            if (done != null) done();
        }

        /// <summary>Bir hamle/güçlendirici sonucunu adım adım canlandırır.</summary>
        public void Animate(MoveResult r, bool isSwap, Action done)
        {
            StartCoroutine(PlayRoutine(r, isSwap, done));
        }

        private IEnumerator PlayRoutine(MoveResult r, bool isSwap, Action done)
        {
            IsBusy = true; ClearHint();
            _selectMarker.SetActive(false); _hasSelection = false;
            if (isSwap)
            {
                var va = VisualAt(r.A); var vb = VisualAt(r.B);
                if (va != null) { va.Cell = r.B; Tween.Move(va.Go.transform, TilePos(r.B), 0.16f); }
                if (vb != null) { vb.Cell = r.A; Tween.Move(vb.Go.transform, TilePos(r.A), 0.16f); }
                yield return new WaitForSeconds(0.17f);
            }
            int depth = 0;
            foreach (var step in r.Steps)
            {
                depth++;
                if (step.Fired.Count > 0)
                {
                    foreach (var f in step.Fired) FireFx(f);
                    yield return new WaitForSeconds(0.12f);
                }
                foreach (var c in step.Cleared) ClearVisual(c);
                foreach (var o in step.Obstacles) ObstacleFx(o);
                if (step.Cleared.Count > 0 || step.Obstacles.Count > 0) yield return new WaitForSeconds(0.2f);
                foreach (var c in step.Created) CreateVisual(c.TileId, c.At, c.Color, c.Special, null, true);
                float maxDur = 0f;
                foreach (var m in step.Moves)
                {
                    TileVisual v;
                    if (!_tiles.TryGetValue(m.TileId, out v) || v.Go == null) continue;
                    float dur = 0.1f + 0.045f * Mathf.Abs(m.To.Y - m.From.Y);
                    maxDur = Mathf.Max(maxDur, dur);
                    v.Cell = m.To;
                    Tween.Move(v.Go.transform, TilePos(m.To), dur, Ease.OutCubic);
                }
                foreach (var s in step.Spawns)
                {
                    var start = TilePos(s.At) + Vector3.forward * (s.DropRows * Cell + 0.4f);
                    var v = CreateVisual(s.TileId, s.At, s.Color, s.Special, start);
                    float dur = 0.12f + 0.045f * s.DropRows;
                    maxDur = Mathf.Max(maxDur, dur);
                    Tween.Move(v.Go.transform, TilePos(s.At), dur, Ease.OutCubic);
                }
                yield return new WaitForSeconds(maxDur + 0.04f);
            }
            if (r.Shuffled && r.ShuffleMoves.Count > 0)
            {
                foreach (var m in r.ShuffleMoves)
                {
                    TileVisual v;
                    if (!_tiles.TryGetValue(m.TileId, out v) || v.Go == null) continue;
                    v.Cell = m.To;
                    var mid = Vector3.Lerp(TilePos(m.From), TilePos(m.To), 0.5f) + Vector3.up * 0.8f;
                    var tr = v.Go.transform; Vector3 from = tr.localPosition, to = TilePos(m.To);
                    Tween.Run(0.45f, k =>
                    {
                        if (tr == null) return;
                        tr.localPosition = Vector3.Lerp(Vector3.Lerp(from, mid, k), Vector3.Lerp(mid, to, k), k);
                    }, null, Ease.InOutCubic);
                }
                yield return new WaitForSeconds(0.5f);
            }
            Sync();
            IsBusy = false;
            _idle = 0;
            if (done != null) done();
        }

        private void ClearVisual(TileCleared c)
        {
            TileVisual v;
            if (!_tiles.TryGetValue(c.TileId, out v) || v.Go == null) return;
            _tiles.Remove(c.TileId);
            var go = v.Go;
            Burst(go.transform.localPosition, c.Color == TileColor.None ? Palette.White : Tc(c.Color), 5);
            Tween.Scale(go.transform, Vector3.zero, 0.18f, Ease.InCubic, () => { if (go != null) Destroy(go); });
        }

        private void ObstacleFx(ObstacleHit o)
        {
            var pos = CellPos(o.At) + Vector3.up * 0.4f;
            switch (o.Kind)
            {
                case ObstacleKind.Sand:
                    Burst(pos, Palette.Sand, 4);
                    SyncObstacle(_sand, o.At, B.Cells[o.At.X, o.At.Y].Sand, BuildSand);
                    break;
                case ObstacleKind.Crate:
                    {
                        Burst(pos + Vector3.up * 0.3f, Palette.Hex("#C99566"), 6);
                        ObstacleVisual ov;
                        if (_crates.TryGetValue(o.At, out ov) && ov.Go != null && o.Remaining > 0)
                        {
                            var t = ov.Go.transform;
                            Tween.Run(0.25f, k => { if (t != null) t.localScale = Vector3.one * (1f + 0.15f * Mathf.Sin(k * Mathf.PI * 3) * (1 - k)); });
                        }
                        SyncObstacle(_crates, o.At, B.Cells[o.At.X, o.At.Y].Crate, BuildCrate);
                        break;
                    }
                default:
                    Burst(pos + Vector3.up * 0.3f, Palette.Hex("#8C96A0"), 6);
                    SyncObstacle(_chains, o.At, B.Cells[o.At.X, o.At.Y].Chain ? 1 : 0, BuildChain);
                    break;
            }
        }

        private void FireFx(SpecialFired f)
        {
            var c = MaterialLibrary.Get(new Color(1f, 0.98f, 0.9f, 0.75f), 0.9f);
            var center = CellPos(f.At) + Vector3.up * 0.6f;
            switch (f.Special)
            {
                case SpecialType.ArrowRow:
                    Flash(Prim.Box(_fxRoot, new Vector3(0, 0.6f, center.z), new Vector3(B.Width * Cell + 0.4f, 0.3f, 0.5f), Color.white), c, new Vector3(1f, 0.2f, 0.05f));
                    break;
                case SpecialType.ArrowColumn:
                    Flash(Prim.Box(_fxRoot, new Vector3(center.x, 0.6f, 0), new Vector3(0.5f, 0.3f, B.Height * Cell + 0.4f), Color.white), c, new Vector3(0.05f, 0.2f, 1f));
                    break;
                case SpecialType.Bomb:
                    {
                        var s = Prim.Ball(_fxRoot, center, 0.5f, Color.white);
                        s.GetComponent<Renderer>().sharedMaterial = MaterialLibrary.Get(new Color(1f, 0.85f, 0.55f, 0.6f), 0.9f);
                        var t = s.transform;
                        Tween.Run(0.3f, k => { if (t != null) t.localScale = Vector3.one * Mathf.Lerp(0.5f, 3.4f, k); }, () => { if (s != null) Destroy(s); });
                        break;
                    }
                case SpecialType.TimeOrb:
                    {
                        var s = Prim.Ball(_fxRoot, center, 0.6f, Color.white);
                        s.GetComponent<Renderer>().sharedMaterial = MaterialLibrary.Get(Palette.WithAlpha(Palette.Turquoise, 0.55f), 0.9f);
                        var t = s.transform;
                        float size = Mathf.Max(B.Width, B.Height) * 1.4f;
                        Tween.Run(0.4f, k => { if (t != null) t.localScale = Vector3.one * Mathf.Lerp(0.6f, size, k); }, () => { if (s != null) Destroy(s); });
                        break;
                    }
            }
        }

        private void Flash(GameObject go, Material m, Vector3 endScaleFactor)
        {
            go.GetComponent<Renderer>().sharedMaterial = m;
            var t = go.transform; Vector3 s0 = t.localScale;
            Tween.Run(0.28f, k => { if (t != null) t.localScale = Vector3.Scale(s0, Vector3.Lerp(Vector3.one, endScaleFactor, k)); }, () => { if (go != null) Destroy(go); }, Ease.InCubic);
        }

        private void Burst(Vector3 pos, Color color, int count)
        {
            for (int i = 0; i < count; i++)
            {
                float a = (i / (float)count) * Mathf.PI * 2f + UnityEngine.Random.value;
                var dir = new Vector3(Mathf.Cos(a), 0.6f, Mathf.Sin(a)) * (0.6f + UnityEngine.Random.value * 0.4f);
                var p = Prim.Ball(_fxRoot, pos, 0.16f, color);
                var t = p.transform;
                Tween.Run(0.35f, k =>
                {
                    if (t == null) return;
                    t.localPosition = pos + dir * k;
                    t.localScale = Vector3.one * 0.16f * (1f - k);
                }, () => { if (p != null) Destroy(p); });
            }
        }

        // ------------------------------------------------------------------ ipucu
        private void ClearHint()
        {
            foreach (var h in _hint) if (h.Go != null) h.Go.transform.localScale = Vector3.one;
            _hint.Clear();
        }

        private void ShowHint()
        {
            Pos a, b;
            if (!B.FindHint(out a, out b)) return;
            var va = VisualAt(a); var vb = VisualAt(b);
            if (va != null) _hint.Add(va);
            if (vb != null) _hint.Add(vb);
            _hintT = 0;
        }

        // ------------------------------------------------------------------ girdi
        private bool CellAt(Vector2 screen, out Pos p)
        {
            p = default(Pos);
            var cam = Game.Cam;
            var plane = new Plane(Vector3.up, new Vector3(0, TileHeight, 0));
            var ray = cam.ScreenPointToRay(screen);
            float e;
            if (!plane.Raycast(ray, out e)) return false;
            var w = transform.InverseTransformPoint(ray.GetPoint(e));
            int x = Mathf.RoundToInt(w.x / Cell + (B.Width - 1) * 0.5f);
            int y = Mathf.RoundToInt((B.Height - 1) * 0.5f - w.z / Cell);
            p = new Pos(x, y);
            return B.InBounds(p) && B.Cells[x, y].Playable;
        }

        public void SetTargeting(bool on)
        {
            Targeting = on;
            _hasSelection = false;
            if (_selectMarker != null) _selectMarker.SetActive(false);
            if (_targetMarker != null) _targetMarker.SetActive(false);
        }

        private void Update()
        {
            if (_game == null) return;
            float dt = Time.deltaTime;

            if (_hint.Count > 0)
            {
                _hintT += dt;
                float s = 1f + 0.1f * Mathf.Sin(_hintT * 7f);
                foreach (var h in _hint) if (h.Go != null) h.Go.transform.localScale = Vector3.one * s;
            }
            if (_selectMarker != null && _selectMarker.activeSelf) _selectMarker.transform.Rotate(0, 90f * dt, 0);

            if (IsBusy || !InputEnabled || _game.State != Match3State.Playing) return;
            if (Game.UI.HasPopup) { _hasDrag = false; return; }

            _idle += dt;
            if (_idle > 6f && _hint.Count == 0 && !Targeting) ShowHint();

            if (InputAdapter.Down)
            {
                _hasDrag = false;
                Pos p;
                if (!InputAdapter.StartedOverUI && CellAt(InputAdapter.Position, out p))
                {
                    _hasDrag = true; _dragCell = p; _dragOrigin = InputAdapter.Position;
                    _idle = 0; ClearHint();
                }
            }

            if (InputAdapter.Held && _hasDrag && !Targeting && InputAdapter.TouchCount <= 1)
            {
                Vector2 d = InputAdapter.Position - _dragOrigin;
                float threshold = Mathf.Max(30f, Screen.width / (B.Width + 1f) * 0.32f);
                if (d.magnitude > threshold)
                {
                    _hasDrag = false;
                    Pos n = Mathf.Abs(d.x) > Mathf.Abs(d.y)
                        ? new Pos(_dragCell.X + (d.x > 0 ? 1 : -1), _dragCell.Y)
                        : new Pos(_dragCell.X, _dragCell.Y + (d.y > 0 ? -1 : 1));
                    _hasSelection = false; _selectMarker.SetActive(false);
                    if (B.InBounds(n) && SwapRequested != null) SwapRequested(_dragCell, n);
                    return;
                }
            }

            if (InputAdapter.Tap)
            {
                Pos p;
                if (!CellAt(InputAdapter.Position, out p)) { _hasSelection = false; _selectMarker.SetActive(false); }
                else if (Targeting)
                {
                    _targetMarker.SetActive(true);
                    _targetMarker.transform.localPosition = CellPos(p) + Vector3.up * 0.05f;
                    if (CellTapped != null) CellTapped(p);
                }
                else if (_hasSelection && _selected.IsAdjacent(p))
                {
                    _hasSelection = false; _selectMarker.SetActive(false);
                    if (SwapRequested != null) SwapRequested(_selected, p);
                }
                else if (_hasSelection && _selected.Equals(p))
                {
                    _hasSelection = false; _selectMarker.SetActive(false);
                }
                else if (B.CanSwapCell(p))
                {
                    _hasSelection = true; _selected = p;
                    _selectMarker.SetActive(true);
                    _selectMarker.transform.localPosition = CellPos(p) + Vector3.up * 0.04f;
                }
            }
            if (InputAdapter.Up) _hasDrag = false;
        }

        public void HideTargetMarker() { if (_targetMarker != null) _targetMarker.SetActive(false); }
    }
}
