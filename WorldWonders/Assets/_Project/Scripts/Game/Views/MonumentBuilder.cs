using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;
using WorldWonders.Core;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>
    /// Bir dünya harikasının 3D modeli: 12 inşa grubu (WonderCatalog.ConstructionStages sırasıyla).
    /// Yerleştirilen her Jigsaw parçası ilgili grubu görünür yapar; yerleştirilmemiş gruplar soluk bir "hayalet" olarak görünür.
    /// GEÇİCİ VARLIK: prosedürel primitif model.
    /// </summary>
    public sealed class MonumentModel : MonoBehaviour
    {
        public int Wonder;
        public float Height = 6f;
        public float Radius = 6f;

        private Transform[] _groups;
        private GameObject[] _ghosts;
        private Bounds[] _bounds;
        private bool[] _built;
        private bool _showGhosts = true;

        public int GroupCount { get { return _groups == null ? 0 : _groups.Length; } }

        internal void Init(Transform[] groups, GameObject[] ghosts, Bounds[] bounds)
        {
            _groups = groups; _ghosts = ghosts; _bounds = bounds;
            _built = new bool[groups.Length];
            var all = new Bounds(Vector3.zero, Vector3.zero);
            bool first = true;
            foreach (var b in bounds)
            {
                if (b.size == Vector3.zero) continue;
                if (first) { all = b; first = false; } else all.Encapsulate(b);
            }
            Height = Mathf.Max(1f, all.max.y);
            Radius = Mathf.Max(1f, Mathf.Max(Mathf.Max(Mathf.Abs(all.min.x), Mathf.Abs(all.max.x)), Mathf.Max(Mathf.Abs(all.min.z), Mathf.Abs(all.max.z))));
            for (int i = 0; i < groups.Length; i++) { groups[i].gameObject.SetActive(false); ghosts[i].SetActive(_showGhosts); }
        }

        /// <summary>Yerleştirilmemiş grupların soluk silueti görünsün mü?</summary>
        public bool ShowGhosts
        {
            get { return _showGhosts; }
            set { _showGhosts = value; if (_ghosts == null) return; for (int i = 0; i < _ghosts.Length; i++) _ghosts[i].SetActive(value && !_built[i]); }
        }

        public bool IsBuilt(int group) { return _built != null && group >= 0 && group < _built.Length && _built[group]; }

        public int BuiltCount { get { int c = 0; if (_built != null) foreach (var b in _built) if (b) c++; return c; } }

        public Vector3 GroupCenterWorld(int group)
        {
            if (_bounds == null || group < 0 || group >= _bounds.Length) return transform.position;
            return transform.TransformPoint(_bounds[group].center);
        }

        public void SetBuilt(int group, bool built, bool animate)
        {
            if (_groups == null || group < 0 || group >= _groups.Length) return;
            var g = _groups[group];
            if (_built[group] == built && !(built && animate && !g.gameObject.activeSelf)) return;
            _built[group] = built;
            _ghosts[group].SetActive(!built && _showGhosts);
            g.gameObject.SetActive(built);
            g.localPosition = Vector3.zero;
            if (!built || !animate) return;

            var b = _bounds[group];
            float rise = Mathf.Min(b.size.y, 3f) + 0.6f;
            g.localPosition = new Vector3(0, -rise, 0);
            Tween.Move(g, Vector3.zero, 0.9f, Ease.OutBack);
            SpawnDust(new Vector3(b.center.x, b.min.y + 0.05f, b.center.z), Mathf.Max(b.size.x, b.size.z));
        }

        public void SyncFrom(GameSession s)
        {
            for (int i = 0; i < GroupCount; i++) SetBuilt(i, s.Construction.IsGroupBuilt(Wonder, i), false);
        }

        private void SpawnDust(Vector3 localPos, float width)
        {
            var ring = Prim.Cyl(transform, localPos, 0.2f, 0.06f, new Color(0.91f, 0.83f, 0.65f, 0.55f));
            ring.name = "dust";
            float target = Mathf.Max(1f, width * 1.35f);
            var t = ring.transform;
            Tween.Run(0.8f, k =>
            {
                if (t == null) return;
                float d = Mathf.Lerp(0.2f, target, k);
                float y = Mathf.Lerp(0.06f, 0.01f, k);
                t.localScale = new Vector3(d, y, d);
            }, () => { if (t != null) Destroy(t.gameObject); }, Ease.OutCubic);
        }
    }

    /// <summary>Yedi harikanın prosedürel stilize modellerini kurar. Her grup malzeme başına tek mesh'e birleştirilir.</summary>
    public static class MonumentBuilder
    {
        private static Transform G;
        private static readonly Color GhostColor = new Color(1f, 1f, 1f, 0.16f);

        public static MonumentModel Build(int wonder, Transform parent)
        {
            var root = new GameObject("Monument_" + WorldWonders.Core.Content.WonderCatalog.Get(wonder).Id);
            root.transform.SetParent(parent, false);
            PlaceholderAsset.Mark(root, "prosedürel primitif anıt modeli (" + WorldWonders.Core.Content.WonderCatalog.Get(wonder).Name + "); final stilize 3D model ile değiştirilecek.");
            var model = root.AddComponent<MonumentModel>();
            model.Wonder = wonder;

            var groups = new Transform[GameConfig.PiecesPerWonder];
            var ghosts = new GameObject[GameConfig.PiecesPerWonder];
            var bounds = new Bounds[GameConfig.PiecesPerWonder];
            var ghostRoot = Prim.Group("Ghosts", root.transform);
            for (int i = 0; i < groups.Length; i++)
            {
                G = Prim.Group("G" + (i + 1) + "_" + WorldWonders.Core.Content.WonderCatalog.Get(wonder).ConstructionStages[i], root.transform);
                BuildGroup(wonder, i);
                bounds[i] = CombineStatic(G);
                groups[i] = G;
                ghosts[i] = MakeGhost(G, ghostRoot, i);
            }
            G = null;
            model.Init(groups, ghosts, bounds);
            return model;
        }

        private static void BuildGroup(int w, int g)
        {
            switch (w)
            {
                case 0: Colosseum(g); break;
                case 1: Petra(g); break;
                case 2: TajMahal(g); break;
                case 3: GreatWall(g); break;
                case 4: MachuPicchu(g); break;
                case 5: ChichenItza(g); break;
                default: Redeemer(g); break;
            }
        }

        // ================================================================== birleştirme
        /// <summary>Bir grubun altındaki bütün mesh'leri malzeme başına tek mesh'e birleştirir; grup-yerel sınırları döndürür.</summary>
        public static Bounds CombineStatic(Transform group)
        {
            var filters = group.GetComponentsInChildren<MeshFilter>(true);
            var byMat = new Dictionary<Material, List<CombineInstance>>();
            var vcount = new Dictionary<Material, int>();
            Matrix4x4 inv = group.worldToLocalMatrix;
            foreach (var mf in filters)
            {
                var r = mf.GetComponent<MeshRenderer>();
                if (r == null || mf.sharedMesh == null) continue;
                var m = r.sharedMaterial;
                List<CombineInstance> list;
                if (!byMat.TryGetValue(m, out list)) { list = new List<CombineInstance>(); byMat[m] = list; vcount[m] = 0; }
                list.Add(new CombineInstance { mesh = mf.sharedMesh, transform = inv * mf.transform.localToWorldMatrix });
                vcount[m] += mf.sharedMesh.vertexCount;
            }
            var olds = new List<GameObject>();
            for (int i = 0; i < group.childCount; i++) olds.Add(group.GetChild(i).gameObject);
            foreach (var o in olds) { o.SetActive(false); Object.Destroy(o); }

            var bounds = new Bounds(Vector3.zero, Vector3.zero);
            bool first = true;
            foreach (var kv in byMat)
            {
                var mesh = new Mesh { name = group.name + "_" + kv.Key.name };
                if (vcount[kv.Key] > 65000) mesh.indexFormat = IndexFormat.UInt32;
                mesh.CombineMeshes(kv.Value.ToArray(), true, true);
                mesh.RecalculateBounds();
                var go = new GameObject("part");
                go.transform.SetParent(group, false);
                go.AddComponent<MeshFilter>().sharedMesh = mesh;
                var r = go.AddComponent<MeshRenderer>();
                r.sharedMaterial = kv.Key;
                r.shadowCastingMode = ShadowCastingMode.Off;
                r.receiveShadows = false;
                if (first) { bounds = mesh.bounds; first = false; } else bounds.Encapsulate(mesh.bounds);
            }
            return bounds;
        }

        private static GameObject MakeGhost(Transform group, Transform ghostRoot, int index)
        {
            var ghost = new GameObject("Ghost" + (index + 1));
            ghost.transform.SetParent(ghostRoot, false);
            var mat = MaterialLibrary.Get(GhostColor);
            foreach (var mf in group.GetComponentsInChildren<MeshFilter>(true))
            {
                if (mf.sharedMesh == null || !mf.gameObject.activeSelf) continue; // birleştirme öncesi (yok edilmeyi bekleyen) parçaları atla
                var go = new GameObject("ghostpart");
                go.transform.SetParent(ghost.transform, false);
                go.AddComponent<MeshFilter>().sharedMesh = mf.sharedMesh;
                var r = go.AddComponent<MeshRenderer>();
                r.sharedMaterial = mat;
                r.shadowCastingMode = ShadowCastingMode.Off;
                r.receiveShadows = false;
            }
            return ghost;
        }

        // ================================================================== çizim yardımcıları (y = taban)
        private static GameObject Bx(float x, float y, float z, float sx, float sy, float sz, Color c, float rotY = 0f)
        {
            var o = Prim.Box(G, new Vector3(x, y + sy * 0.5f, z), new Vector3(sx, sy, sz), c);
            if (rotY != 0f) o.transform.localRotation = Quaternion.Euler(0, rotY, 0);
            return o;
        }

        private static GameObject Cy(float x, float y, float z, float d, float h, Color c)
        {
            return Prim.Cyl(G, new Vector3(x, y + h * 0.5f, z), d, h, c);
        }

        /// <summary>Merkez tabanlı (elips) küre.</summary>
        private static GameObject Sp(float x, float y, float z, float sx, float sy, float sz, Color c)
        {
            return Prim.Create(PrimitiveType.Sphere, G, new Vector3(x, y, z), new Vector3(sx, sy, sz), c);
        }

        private static GameObject Cn(float x, float y, float z, float d, float h, Color c) { return Prim.Cone(G, new Vector3(x, y, z), d, h, c); }

        private static GameObject Dm(float x, float y, float z, float d, float h, float onion, Color c)
        {
            return Prim.FromMesh(MeshFactory.Dome(20, 8, onion), G, new Vector3(x, y, z), new Vector3(d, h * 2f, d), c, "dome");
        }

        private static GameObject Pr(float x, float y, float z, float sx, float sy, float sz, Color c, float rotY = 0f)
        {
            var o = Prim.FromMesh(MeshFactory.Prism(), G, new Vector3(x, y, z), new Vector3(sx, sy, sz), c, "prism");
            if (rotY != 0f) o.transform.localRotation = Quaternion.Euler(0, rotY, 0);
            return o;
        }

        private static GameObject Beam(Vector3 a, Vector3 b, float w, float h, Color c)
        {
            var dir = b - a;
            var o = Prim.Box(G, (a + b) * 0.5f, new Vector3(dir.magnitude, h, w), c);
            o.transform.localRotation = Quaternion.FromToRotation(Vector3.right, dir.normalized);
            return o;
        }

        private static GameObject Glow(GameObject o, Color c, float e)
        {
            o.GetComponent<MeshRenderer>().sharedMaterial = MaterialLibrary.Get(c, e);
            return o;
        }

        private static Color H(string hex) { return Palette.Hex(hex); }

        // ================================================================== KOLEZYUM
        private const int ColN = 36;
        private const float ColRx = 6f, ColRz = 4.8f;

        private static int ColMaxTier(int i)
        {
            float deg = (i + 0.5f) * 360f / ColN;
            float d = Mathf.Abs(Mathf.DeltaAngle(deg, 270f)); // güney tarafı çökmüş
            if (d < 38f) return 1;
            if (d < 52f) return 2;
            if (d < 64f) return 3;
            return 4;
        }

        private static void EllSeg(int i, float k, float along, float y, float w, float h, float d, Color c)
        {
            float a = (i + 0.5f) * Mathf.PI * 2f / ColN;
            var p = new Vector3(ColRx * k * Mathf.Cos(a), 0, ColRz * k * Mathf.Sin(a));
            var t = new Vector3(-ColRx * Mathf.Sin(a), 0, ColRz * Mathf.Cos(a)).normalized;
            float rot = -Mathf.Atan2(t.z, t.x) * Mathf.Rad2Deg;
            Bx(p.x + t.x * along, y, p.z + t.z * along, w, h, d, c, rot);
        }

        private static float SegLen(int i, float k)
        {
            float a = (i + 0.5f) * Mathf.PI * 2f / ColN;
            return new Vector2(-ColRx * Mathf.Sin(a), ColRz * Mathf.Cos(a)).magnitude * Mathf.PI * 2f / ColN * k;
        }

        private static void Arcade(int i, float k, float y, float h, Color c, Color band)
        {
            float len = SegLen(i, k) * 1.02f;
            EllSeg(i, k, -len * 0.36f, y, len * 0.28f, h - 0.26f, 0.55f, c);
            EllSeg(i, k, len * 0.36f, y, len * 0.28f, h - 0.26f, 0.55f, c);
            EllSeg(i, k, 0f, y + h - 0.26f, len, 0.26f, 0.6f, band);
        }

        private static void Colosseum(int g)
        {
            Color stone = H("#D9C3A0"), trav = H("#E8D9BC"), dark = H("#BFA27A"), brick = H("#B97E58"), sand = H("#E9D3A6");
            switch (g)
            {
                case 0: // temel halkası
                    Cy(0, -0.02f, 0, ColRx * 2.5f, 0.05f, H("#E3D4B0")).transform.localScale = new Vector3(ColRx * 2.5f, 0.025f, ColRz * 2.6f);
                    for (int i = 0; i < ColN; i++) EllSeg(i, 1.02f, 0, 0, SegLen(i, 1.02f) * 1.04f, 0.3f, 1.3f, dark);
                    break;
                case 1: // zemin kat kemerleri
                    for (int i = 0; i < ColN; i++) Arcade(i, 1f, 0.3f, 1.3f, stone, trav);
                    break;
                case 2: // hypogeum
                    for (float z = -2.0f; z <= 2.01f; z += 0.5f)
                    {
                        float half = 3.0f * Mathf.Sqrt(Mathf.Max(0f, 1f - (z / 2.4f) * (z / 2.4f)));
                        Bx(0, 0, z, half * 2f, 0.62f, 0.12f, brick);
                    }
                    for (float x = -2.4f; x <= 2.41f; x += 1.2f) Bx(x, 0, 0, 0.12f, 0.62f, 3.6f, brick);
                    break;
                case 3: // arena zemini (kısmen kapalı) + podium duvarı
                    Bx(-1.45f, 0.62f, 0, 2.9f, 0.1f, 3.9f, sand);
                    for (int i = 0; i < ColN; i++) EllSeg(i, 0.53f, 0, 0, SegLen(i, 0.53f) * 1.06f, 0.95f, 0.25f, trav);
                    break;
                case 4: // birinci kat kemerleri
                    for (int i = 0; i < ColN; i++) if (ColMaxTier(i) >= 2) Arcade(i, 1f, 1.6f, 1.2f, stone, trav);
                    break;
                case 5: // ikinci kat kemerleri
                    for (int i = 0; i < ColN; i++) if (ColMaxTier(i) >= 3) Arcade(i, 1f, 2.8f, 1.2f, stone, trav);
                    break;
                case 6: // oturma basamakları (cavea)
                    {
                        float[] ks = { 0.88f, 0.76f, 0.64f };
                        float[] hs = { 3.4f, 2.5f, 1.6f };
                        for (int r = 0; r < 3; r++)
                            for (int i = 0; i < ColN; i++)
                            {
                                float h = hs[r] * (ColMaxTier(i) == 1 ? 0.6f : 1f);
                                EllSeg(i, ks[r], 0, 0.3f, SegLen(i, ks[r]) * 1.08f, h, 0.75f, r % 2 == 0 ? trav : stone);
                            }
                        break;
                    }
                case 7: // imparator locası (kuzey)
                    Bx(0, 1.6f, ColRz * 0.62f, 1.4f, 0.9f, 0.7f, trav);
                    for (int k = 0; k < 4; k++) Cy(-0.55f + k * 0.37f, 2.5f, ColRz * 0.58f, 0.09f, 0.6f, Palette.Gold);
                    Bx(0, 3.1f, ColRz * 0.62f, 1.6f, 0.1f, 0.9f, H("#8C5BA8"));
                    break;
                case 8: // üçüncü kat (pencereli)
                    for (int i = 0; i < ColN; i++)
                    {
                        if (ColMaxTier(i) < 4) continue;
                        float len = SegLen(i, 1f) * 1.02f;
                        EllSeg(i, 1f, -len * 0.3f, 4.0f, len * 0.4f, 1.0f, 0.55f, stone);
                        EllSeg(i, 1f, len * 0.3f, 4.0f, len * 0.4f, 1.0f, 0.55f, stone);
                        EllSeg(i, 1f, 0, 4.55f, len * 0.22f, 0.45f, 0.55f, stone);
                    }
                    break;
                case 9: // attika
                    for (int i = 0; i < ColN; i++)
                        if (ColMaxTier(i) >= 4) { EllSeg(i, 1f, 0, 5.0f, SegLen(i, 1f) * 1.04f, 0.4f, 0.6f, trav); EllSeg(i, 1.03f, 0, 5.4f, SegLen(i, 1.03f) * 1.04f, 0.1f, 0.4f, dark); }
                    break;
                case 10: // velarium direkleri
                    for (int i = 0; i < ColN; i += 2)
                    {
                        if (ColMaxTier(i) < 4) continue;
                        float a = (i + 0.5f) * Mathf.PI * 2f / ColN;
                        var p = new Vector3(ColRx * 1.02f * Mathf.Cos(a), 0, ColRz * 1.02f * Mathf.Sin(a));
                        Cy(p.x, 5.5f, p.z, 0.08f, 1.3f, H("#8A6A4A"));
                        if (i % 4 == 0)
                        {
                            var inner = new Vector3(ColRx * 0.8f * Mathf.Cos(a), 6.2f, ColRz * 0.8f * Mathf.Sin(a));
                            Beam(new Vector3(p.x, 6.7f, p.z), inner, 0.5f, 0.03f, H("#F3EBDD"));
                        }
                    }
                    break;
                default: // harabe cephe + ışıklar
                    {
                        var rng = new Rng(77);
                        for (int i = 0; i < ColN; i++)
                        {
                            int mt = ColMaxTier(i);
                            if (mt >= 4) continue;
                            float top = mt == 1 ? 1.6f : mt == 2 ? 2.8f : 4.0f;
                            float hh = 0.2f + (float)rng.NextDouble() * 0.7f;
                            EllSeg(i, 1f, (float)(rng.NextDouble() - 0.5) * 0.3f, top, SegLen(i, 1f) * 0.6f, hh, 0.55f, stone);
                        }
                        for (int k = 0; k < 14; k++)
                        {
                            float a = Mathf.Lerp(220f, 320f, (float)rng.NextDouble()) * Mathf.Deg2Rad;
                            float rr = 1.12f + (float)rng.NextDouble() * 0.25f;
                            var o = Bx(ColRx * rr * Mathf.Cos(a), 0, ColRz * rr * Mathf.Sin(a), 0.3f + (float)rng.NextDouble() * 0.3f, 0.2f + (float)rng.NextDouble() * 0.2f, 0.3f, dark, (float)rng.NextDouble() * 90f);
                            o.name = "rubble";
                        }
                        for (int i = 0; i < ColN; i += 3)
                        {
                            float a = (i + 0.5f) * Mathf.PI * 2f / ColN;
                            Glow(Sp(ColRx * 1.12f * Mathf.Cos(a), 0.15f, ColRz * 1.12f * Mathf.Sin(a), 0.22f, 0.22f, 0.22f, H("#FFD98A")), H("#FFD98A"), 0.8f);
                        }
                        break;
                    }
            }
        }

        // ================================================================== PETRA (El Hazne)
        private static void Petra(int g)
        {
            Color rose = H("#D98F78"), light = H("#E8A893"), dark = H("#B86A57"), door = H("#7A4A3C"), sand = H("#E9C9A0");
            switch (g)
            {
                case 0: // kaya yüzeyi
                    Bx(0, 0, 2.3f, 14f, 11f, 4f, rose);
                    Sp(-3.5f, 11f, 2.4f, 7f, 2.6f, 4f, rose); Sp(3.2f, 10.8f, 2.5f, 8f, 2.2f, 4f, dark); Sp(0, 11.4f, 2.8f, 5f, 2f, 3.5f, rose);
                    var lw = Bx(-6.2f, 0, -2f, 3f, 9.5f, 6f, dark); lw.transform.localRotation = Quaternion.Euler(0, 8f, 0);
                    var rw = Bx(6.2f, 0, -2f, 3f, 9f, 6f, rose); rw.transform.localRotation = Quaternion.Euler(0, -8f, 0);
                    Sp(-6.4f, 9.4f, -2f, 3.2f, 1.6f, 6f, rose); Sp(6.4f, 9f, -2f, 3.2f, 1.6f, 6f, dark);
                    break;
                case 1: // alt cephe (oyulmuş panel)
                    Bx(0, 0, 0.15f, 7.8f, 10.2f, 0.4f, light);
                    break;
                case 2: // basamaklar
                    Bx(0, 0, -1.1f, 6.4f, 0.2f, 1.2f, light); Bx(0, 0.2f, -0.85f, 6.0f, 0.2f, 0.9f, light); Bx(0, 0.4f, -0.6f, 5.6f, 0.2f, 0.6f, light);
                    break;
                case 3: // giriş portali
                    Bx(0, 0.6f, -0.08f, 1.3f, 2.3f, 0.12f, door);
                    Bx(-0.75f, 0.6f, -0.12f, 0.18f, 2.5f, 0.15f, dark); Bx(0.75f, 0.6f, -0.12f, 0.18f, 2.5f, 0.15f, dark); Bx(0, 3.1f, -0.12f, 1.7f, 0.25f, 0.15f, dark);
                    break;
                case 4: // alt sütunlar
                    {
                        float[] xs = { -3.3f, -2.1f, -0.9f, 0.9f, 2.1f, 3.3f };
                        foreach (var x in xs)
                        {
                            Bx(x, 0.6f, -0.55f, 0.6f, 0.2f, 0.6f, dark);
                            Cy(x, 0.8f, -0.55f, 0.46f, 3.6f, light);
                            Bx(x, 4.4f, -0.55f, 0.62f, 0.35f, 0.62f, dark);
                        }
                        break;
                    }
                case 5: // alt alınlık
                    Bx(0, 4.75f, -0.5f, 7.6f, 0.55f, 0.75f, light);
                    Pr(0, 5.3f, -0.5f, 5.0f, 1.0f, 0.55f, light);
                    break;
                case 6: // orta friz
                    Bx(0, 6.3f, -0.4f, 7.8f, 0.6f, 0.55f, rose);
                    for (int k = 0; k < 7; k++) Sp(-3f + k, 6.6f, -0.7f, 0.28f, 0.28f, 0.14f, dark);
                    break;
                case 7: // üst kat kaidesi
                    Bx(0, 6.9f, -0.35f, 7.6f, 0.4f, 0.65f, light);
                    break;
                case 8: // tholos
                    Cy(0, 7.3f, -0.3f, 1.6f, 2.6f, light);
                    for (int k = 0; k < 5; k++)
                    {
                        float a = Mathf.Lerp(200f, 340f, k / 4f) * Mathf.Deg2Rad;
                        Cy(Mathf.Cos(a) * 1.0f, 7.3f, -0.3f + Mathf.Sin(a) * 1.0f, 0.2f, 2.6f, rose);
                    }
                    Cn(0, 9.9f, -0.3f, 2.3f, 0.9f, dark);
                    break;
                case 9: // yan köşkler + kırık alınlık
                    foreach (var sx in new[] { -1f, 1f })
                    {
                        Bx(2.9f * sx, 7.3f, -0.35f, 1.7f, 2.6f, 0.6f, light);
                        Cy(2.35f * sx, 7.3f, -0.75f, 0.22f, 2.6f, rose); Cy(3.45f * sx, 7.3f, -0.75f, 0.22f, 2.6f, rose);
                        Bx(2.9f * sx, 9.9f, -0.45f, 1.9f, 0.3f, 0.8f, dark);
                        var p = Pr(2.9f * sx, 10.2f, -0.45f, 1.7f, 0.7f, 0.6f, light);
                        p.transform.localScale = new Vector3(1.7f, 0.7f, 0.6f);
                    }
                    break;
                case 10: // urne ve tepelik
                    Cy(0, 10.75f, -0.3f, 0.35f, 0.4f, light);
                    Sp(0, 11.4f, -0.3f, 0.65f, 0.75f, 0.65f, light);
                    Cy(0, 11.75f, -0.3f, 0.18f, 0.25f, dark);
                    Bx(-2.5f, 6.2f, -0.5f, 0.3f, 0.35f, 0.3f, dark); Bx(2.5f, 6.2f, -0.5f, 0.3f, 0.35f, 0.3f, dark);
                    break;
                default: // kanyon ışıkları + zemin
                    Bx(0, -0.05f, -3.2f, 12f, 0.05f, 6.5f, sand);
                    for (int k = 0; k < 9; k++)
                    {
                        float x = -4f + k;
                        Glow(Sp(x, 0.12f, -2.2f - (k % 2) * 0.6f, 0.2f, 0.2f, 0.2f, H("#FFC86B")), H("#FFC86B"), 0.9f);
                    }
                    Glow(Sp(-3.6f, 2.0f, -0.9f, 0.35f, 0.35f, 0.35f, H("#FFD98A")), H("#FFD98A"), 0.8f);
                    Glow(Sp(3.6f, 2.0f, -0.9f, 0.35f, 0.35f, 0.35f, H("#FFD98A")), H("#FFD98A"), 0.8f);
                    break;
            }
        }

        // ================================================================== TAC MAHAL
        private static readonly Vector3[] Dirs4 = { Vector3.forward, Vector3.right, Vector3.back, Vector3.left };

        private static void TajMahal(int g)
        {
            Color marble = Palette.Marble, shade = H("#E3DCCF"), red = H("#C2694F"), gold = Palette.Gold, inlay = H("#9DB8C8");
            switch (g)
            {
                case 0: // kaide platformu
                    Bx(0, 0, 0, 11f, 0.3f, 11f, red);
                    Bx(0, 0.3f, 0, 8.6f, 0.8f, 8.6f, marble);
                    Bx(0, 0.3f, -4.4f, 2.2f, 0.4f, 0.4f, shade);
                    break;
                case 1: // kare (pahlı) gövde
                    Bx(0, 1.1f, 0, 3.6f, 3.8f, 5f, marble);
                    Bx(0, 1.1f, 0, 5f, 3.8f, 3.6f, marble);
                    for (int k = 0; k < 4; k++)
                    {
                        float sx = k % 2 == 0 ? 1 : -1, sz = k < 2 ? 1 : -1;
                        Bx(1.95f * sx, 1.1f, 1.95f * sz, 1.95f, 3.8f, 0.8f, shade, 45f * sx * sz);
                    }
                    break;
                case 2: // pishtak kemerleri
                    foreach (var d in Dirs4)
                    {
                        float rot = Mathf.Atan2(d.x, d.z) * Mathf.Rad2Deg;
                        var p = d * 2.55f;
                        Bx(p.x, 1.1f, p.z, 2.4f, 4.3f, 0.3f, marble, rot);
                        var r = d * 2.72f;
                        Bx(r.x, 1.1f, r.z, 1.5f, 2.6f, 0.06f, shade, rot);
                        var arch = Cy(r.x, 3.7f - 0.04f, r.z, 1.5f, 0.06f, shade);
                        arch.transform.localPosition = new Vector3(r.x, 3.7f, r.z);
                        arch.transform.localRotation = Quaternion.Euler(0, rot, 0) * Quaternion.Euler(90, 0, 0);
                        var fr = d * 2.71f;
                        Bx(fr.x, 4.9f, fr.z, 2.1f, 0.12f, 0.05f, inlay, rot);
                    }
                    break;
                case 3: // köşe nişleri
                    for (int k = 0; k < 4; k++)
                    {
                        float sx = k % 2 == 0 ? 1 : -1, sz = k < 2 ? 1 : -1;
                        float rot = Mathf.Atan2(sx, sz) * Mathf.Rad2Deg;
                        var n = new Vector3(sx, 0, sz).normalized * 2.95f;
                        Bx(n.x, 1.4f, n.z, 0.75f, 1.3f, 0.05f, shade, rot);
                        Bx(n.x, 3.1f, n.z, 0.75f, 1.3f, 0.05f, shade, rot);
                    }
                    break;
                case 4: // mermer kaplama süsleri
                    Bx(0, 4.9f, 0, 5.2f, 0.25f, 5.2f, marble);
                    foreach (var d in Dirs4) { var p = d * 2.55f + Vector3.Cross(d, Vector3.up) * 1.15f; Cn(p.x, 5.4f, p.z, 0.2f, 0.5f, gold); p = d * 2.55f - Vector3.Cross(d, Vector3.up) * 1.15f; Cn(p.x, 5.4f, p.z, 0.2f, 0.5f, gold); }
                    Bx(0, 1.1f, 0, 5.06f, 0.12f, 5.06f, inlay);
                    break;
                case 5: // kubbe kasnağı
                    Cy(0, 5.15f, 0, 3.0f, 1.0f, marble);
                    Cy(0, 6.05f, 0, 3.15f, 0.12f, shade);
                    break;
                case 6: // ana kubbe (soğan)
                    Dm(0, 6.15f, 0, 3.0f, 1.7f, 0.35f, marble);
                    break;
                case 7: // çatri köşkleri
                    for (int k = 0; k < 4; k++)
                    {
                        float sx = k % 2 == 0 ? 1 : -1, sz = k < 2 ? 1 : -1;
                        float x = 1.85f * sx, z = 1.85f * sz;
                        Cy(x, 5.15f, z, 1.0f, 0.15f, shade);
                        for (int c = 0; c < 4; c++) Cy(x + (c % 2 == 0 ? -0.32f : 0.32f), 5.3f, z + (c < 2 ? -0.32f : 0.32f), 0.08f, 0.6f, marble);
                        Cy(x, 5.9f, z, 0.95f, 0.1f, shade);
                        Dm(x, 6.0f, z, 0.85f, 0.5f, 0.3f, marble);
                        Cy(x, 6.45f, z, 0.05f, 0.3f, gold);
                    }
                    break;
                case 8: // alem
                    Cy(0, 8.3f, 0, 0.14f, 0.9f, gold);
                    Sp(0, 8.55f, 0, 0.24f, 0.24f, 0.24f, gold); Sp(0, 8.85f, 0, 0.2f, 0.2f, 0.2f, gold);
                    var cres = Prim.FromMesh(MeshFactory.Ring(0.12f, 0.18f, 200f, 12), G, new Vector3(0, 9.2f, 0), new Vector3(1f, 0.05f, 1f), gold, "crescent");
                    cres.transform.localRotation = Quaternion.Euler(-90, 0, 80);
                    break;
                case 9: Minarets(1f, marble, shade, gold); break;
                case 10: Minarets(-1f, marble, shade, gold); break;
                default: // bahçe ve yansıma havuzu
                    Bx(0, 0, -7.6f, 1.2f, 0.12f, 5.4f, H("#BFA98A"));
                    Bx(0, 0.06f, -7.6f, 0.9f, 0.08f, 5.2f, Palette.Water);
                    Bx(-2.6f, 0, -7.6f, 3.6f, 0.06f, 5.4f, Palette.Grass); Bx(2.6f, 0, -7.6f, 3.6f, 0.06f, 5.4f, Palette.Grass);
                    Bx(0, 0, -7.6f, 9f, 0.04f, 0.5f, H("#E8DCC6"));
                    for (int k = 0; k < 5; k++)
                    {
                        float z = -5.4f - k * 1.05f;
                        Cn(-0.95f, 0.05f, z, 0.45f, 1.5f, H("#4E8A5A")); Cn(0.95f, 0.05f, z, 0.45f, 1.5f, H("#4E8A5A"));
                    }
                    Cy(0, 0.1f, -7.6f, 0.7f, 0.15f, marble);
                    break;
            }
        }

        private static void Minarets(float sz, Color marble, Color shade, Color gold)
        {
            foreach (var sx in new[] { -1f, 1f })
            {
                float x = 3.9f * sx, z = 3.9f * sz, y = 1.1f;
                float[] ds = { 0.62f, 0.54f, 0.46f };
                for (int k = 0; k < 3; k++)
                {
                    Cy(x, y, z, ds[k], 1.8f, marble);
                    y += 1.8f;
                    Cy(x, y, z, ds[k] + 0.3f, 0.12f, shade);
                    y += 0.12f;
                }
                for (int c = 0; c < 4; c++) Cy(x + (c % 2 == 0 ? -0.18f : 0.18f), y, z + (c < 2 ? -0.18f : 0.18f), 0.06f, 0.45f, marble);
                Dm(x, y + 0.45f, z, 0.6f, 0.35f, 0.3f, marble);
                Cy(x, y + 0.75f, z, 0.04f, 0.25f, gold);
            }
        }

        // ================================================================== ÇİN SEDDİ
        private const int WallN = 15;

        private static Vector3 WallPt(int i)
        {
            float x = -7f + i;
            float z = 2.0f * Mathf.Sin(i * 0.55f);
            float y = 0.8f + 1.7f * (0.5f + 0.5f * Mathf.Sin(i * 0.45f - 0.8f));
            return new Vector3(x, y, z);
        }

        private static void WallSegments(float yOff, float h, float w, float side, Color c)
        {
            for (int i = 0; i < WallN - 1; i++)
            {
                Vector3 a = WallPt(i), b = WallPt(i + 1);
                var dir = b - a;
                var flat = new Vector3(dir.x, 0, dir.z).normalized;
                var lateral = Vector3.Cross(Vector3.up, flat) * side;
                var ext = dir.normalized * 0.08f;
                Beam(a - ext + lateral + Vector3.up * (yOff + h * 0.5f), b + ext + lateral + Vector3.up * (yOff + h * 0.5f), w, h, c);
            }
        }

        private static void Tower(Vector3 p, float h, Color body, Color top)
        {
            Bx(p.x, 0, p.z, 1.7f, p.y + h, 1.7f, body);
            Bx(p.x, p.y + h, p.z, 1.85f, 0.15f, 1.85f, top);
            for (int k = 0; k < 4; k++)
            {
                float o = -0.7f + k * 0.47f;
                Bx(p.x + o, p.y + h + 0.15f, p.z - 0.85f, 0.22f, 0.25f, 0.15f, top); Bx(p.x + o, p.y + h + 0.15f, p.z + 0.85f, 0.22f, 0.25f, 0.15f, top);
                Bx(p.x - 0.85f, p.y + h + 0.15f, p.z + o, 0.15f, 0.25f, 0.22f, top); Bx(p.x + 0.85f, p.y + h + 0.15f, p.z + o, 0.15f, 0.25f, 0.22f, top);
            }
            Bx(p.x, p.y + h * 0.45f, p.z - 0.86f, 0.35f, 0.5f, 0.04f, H("#6C675E"));
            Bx(p.x - 0.86f, p.y + h * 0.45f, p.z, 0.04f, 0.5f, 0.35f, H("#6C675E"));
        }

        private static void GreatWall(int g)
        {
            Color earth = H("#C9A878"), stone = H("#A9A59B"), brick = H("#B8B2A6"), brick2 = H("#C4BEB2"), walk = H("#D6D0C3");
            switch (g)
            {
                case 0: // sıkıştırılmış toprak çekirdek
                    for (int i = 0; i < WallN; i++) { var p = WallPt(i); Bx(p.x, 0, p.z, 1.15f, p.y, 1.15f, earth); }
                    WallSegments(0f, 0.5f, 1.0f, 0f, earth);
                    break;
                case 1: WallSegments(0f, 0.25f, 1.3f, 0f, stone); break;
                case 2: WallSegments(0.25f, 0.45f, 0.18f, 0.5f, brick); WallSegments(0.25f, 0.45f, 0.18f, -0.5f, brick); break;
                case 3: WallSegments(0.7f, 0.35f, 0.18f, 0.5f, brick2); WallSegments(0.7f, 0.35f, 0.18f, -0.5f, brick2); break;
                case 4: // mazgallar
                    for (int i = 0; i < WallN - 1; i++)
                    {
                        Vector3 a = WallPt(i), b = WallPt(i + 1);
                        var flat = new Vector3(b.x - a.x, 0, b.z - a.z).normalized;
                        var lat = Vector3.Cross(Vector3.up, flat) * 0.5f;
                        int n = Mathf.Max(2, Mathf.RoundToInt((b - a).magnitude / 0.36f));
                        for (int k = 0; k < n; k += 2)
                        {
                            var p = Vector3.Lerp(a, b, (k + 0.5f) / n) + lat;
                            Bx(p.x, p.y + 1.05f, p.z, 0.2f, 0.25f, 0.2f, brick2, -Mathf.Atan2(flat.z, flat.x) * Mathf.Rad2Deg);
                        }
                    }
                    break;
                case 5: WallSegments(0.62f, 0.08f, 0.84f, 0f, walk); break;
                case 6: // basamaklar
                    for (int i = 0; i < WallN - 1; i++)
                    {
                        Vector3 a = WallPt(i), b = WallPt(i + 1);
                        var d = b - a;
                        float slope = Mathf.Atan2(Mathf.Abs(d.y), new Vector2(d.x, d.z).magnitude) * Mathf.Rad2Deg;
                        if (slope < 14f) continue;
                        float rot = -Mathf.Atan2(d.z, d.x) * Mathf.Rad2Deg;
                        for (int k = 1; k < 5; k++) { var p = Vector3.Lerp(a, b, k / 5f); Bx(p.x, p.y + 0.66f, p.z, 0.06f, 0.07f, 0.8f, stone, rot); }
                    }
                    break;
                case 7: Tower(WallPt(3), 2.2f, brick, brick2); break;
                case 8: Tower(WallPt(10), 2.2f, brick, brick2); break;
                case 9: // işaret kulesi
                    {
                        int hi = 0; for (int i = 1; i < WallN; i++) if (WallPt(i).y > WallPt(hi).y) hi = i;
                        var p = WallPt(hi) + new Vector3(0.6f, 0, 2.8f);
                        Cn(p.x, 0, p.z, 3.4f, p.y + 0.6f, H("#8FB27A"));
                        Cy(p.x, p.y + 0.4f, p.z, 1.0f, 1.6f, brick);
                        Cy(p.x, p.y + 2.0f, p.z, 1.15f, 0.15f, brick2);
                        Glow(Sp(p.x, p.y + 2.35f, p.z, 0.5f, 0.6f, 0.5f, H("#FF9A3C")), H("#FF9A3C"), 1f);
                        Sp(p.x + 0.1f, p.y + 3.1f, p.z, 0.6f, 0.5f, 0.6f, new Color(0.85f, 0.85f, 0.85f, 0.6f));
                        Sp(p.x + 0.35f, p.y + 3.7f, p.z, 0.8f, 0.6f, 0.8f, new Color(0.9f, 0.9f, 0.9f, 0.45f));
                        break;
                    }
                case 10: // kale kapısı
                    {
                        var p = WallPt(0) + new Vector3(-0.6f, 0, 0);
                        Bx(p.x, 0, p.z, 2.0f, p.y + 1.6f, 2.4f, brick);
                        Bx(p.x - 1.01f, 0, p.z, 0.04f, 1.3f, 0.9f, H("#5E584F"));
                        Bx(p.x, p.y + 1.6f, p.z, 1.8f, 0.8f, 2.0f, H("#B5523B"));
                        var roof = Pr(p.x, p.y + 2.4f, p.z, 2.5f, 0.85f, 2.9f, H("#5C6B73"));
                        roof.transform.localRotation = Quaternion.Euler(0, 90, 0);
                        roof.transform.localScale = new Vector3(2.9f, 0.85f, 2.5f);
                        break;
                    }
                default: // dağ sırtı ve sis
                    {
                        var rng = new Rng(31);
                        for (int k = 0; k < 9; k++)
                        {
                            float x = -7f + k * 1.75f;
                            float z = 4.2f + (float)rng.NextDouble() * 2f;
                            Cn(x, -0.05f, z, 4f + (float)rng.NextDouble() * 2.5f, 2.5f + (float)rng.NextDouble() * 2.5f, k % 2 == 0 ? H("#8FB27A") : H("#7FA56E"));
                        }
                        for (int k = 0; k < 6; k++)
                            Cn(-6f + k * 2.4f, -0.05f, -3.6f - (float)rng.NextDouble(), 2.6f, 1.2f + (float)rng.NextDouble(), H("#9FBF86"));
                        for (int k = 0; k < 5; k++)
                            Sp(-6f + k * 3f, 1.0f + (float)rng.NextDouble(), 3.4f, 3.5f, 0.8f, 1.8f, new Color(1f, 1f, 1f, 0.45f));
                        break;
                    }
            }
        }

        // ================================================================== MACHU PICCHU
        private static void MachuPicchu(int g)
        {
            Color green = H("#8DBF6A"), grass = H("#A9D98F"), granite = H("#B9B4A8"), gdark = H("#9C978C"), thatch = H("#D8B36A"), rock = H("#8E8A80");
            switch (g)
            {
                case 0: // dağ sırtı
                    Sp(0, 0, 0, 13f, 4.4f, 9f, green);
                    Sp(-3.5f, 0.6f, 1.5f, 7f, 4f, 6f, green);
                    Bx(0.3f, 0, 0.3f, 8.5f, 2.0f, 4.6f, green);
                    Bx(0.3f, 1.95f, 0.3f, 8.5f, 0.06f, 4.6f, grass);
                    Sp(4.6f, 0, -3.2f, 3f, 3.4f, 2.6f, rock); Sp(-5.4f, 0, -2.6f, 2.6f, 2.6f, 2.4f, rock);
                    break;
                case 1: // alt teraslar
                    for (int k = 0; k < 4; k++)
                    {
                        float z = -2.4f - k * 0.65f, h = 1.75f - k * 0.42f, w = 8.2f - k * 0.9f;
                        Bx(0.2f, 0, z, w, h, 0.7f, granite);
                        Bx(0.2f, h, z - 0.05f, w, 0.05f, 0.6f, grass);
                    }
                    break;
                case 2: // üst teraslar
                    for (int k = 0; k < 3; k++)
                    {
                        float x = -4.6f - k * 0.0f, z = 0.6f + k * 0.65f, h = 2.3f + k * 0.35f;
                        Bx(x + k * 0.2f, 0, z, 3.0f, h, 0.7f, granite);
                        Bx(x + k * 0.2f, h, z, 3.0f, 0.05f, 0.65f, grass);
                    }
                    break;
                case 3: // merdivenler
                    for (int k = 0; k < 8; k++) Bx(-1.2f, k * 0.25f, -5.0f + k * 0.38f, 0.6f, 0.25f, 0.4f, gdark);
                    for (int k = 0; k < 4; k++) Bx(-3.2f, 2.0f + k * 0.2f, 0.2f + k * 0.35f, 0.5f, 0.2f, 0.35f, gdark);
                    break;
                case 4: // su kanalları
                    Bx(-0.2f, 2.0f, -1.6f, 0.15f, 0.04f, 1.6f, Palette.Water);
                    for (int k = 0; k < 4; k++) { Bx(-0.2f, 1.75f - k * 0.42f, -2.4f - k * 0.65f, 0.3f, 0.12f, 0.3f, granite); Bx(-0.2f, 1.87f - k * 0.42f, -2.4f - k * 0.65f, 0.15f, 0.03f, 0.15f, Palette.Water); }
                    break;
                case 5: // taş evler
                    for (int r = 0; r < 3; r++)
                        for (int c = 0; c < 4; c++)
                            Bx(1.4f + c * 0.85f, 2.0f, -1.0f + r * 1.0f, 0.7f, 0.6f, 0.6f, granite);
                    break;
                case 6: // güneş tapınağı (Torreón)
                    Sp(-1.4f, 2.0f, 0.4f, 1.6f, 0.6f, 1.6f, rock);
                    Cy(-1.4f, 2.1f, 0.4f, 1.2f, 1.2f, granite);
                    Bx(-1.4f, 2.6f, -0.21f, 0.25f, 0.35f, 0.04f, H("#6C675E"));
                    break;
                case 7: // üç pencere tapınağı
                    Bx(-0.4f, 2.0f, 1.9f, 2.2f, 1.1f, 0.35f, granite);
                    for (int k = 0; k < 3; k++) Bx(-1.1f + k * 0.7f, 2.4f, 1.71f, 0.3f, 0.45f, 0.03f, H("#6C675E"));
                    break;
                case 8: // ana meydan
                    Bx(0.0f, 2.0f, 0.2f, 2.6f, 0.06f, 2.0f, H("#B8E39C"));
                    break;
                case 9: // intihuatana
                    Sp(-3.7f, 2.4f, 2.4f, 2.2f, 1.6f, 2.2f, green);
                    Bx(-3.7f, 3.0f, 2.4f, 1.2f, 0.25f, 1.2f, granite); Bx(-3.7f, 3.25f, 2.4f, 0.8f, 0.25f, 0.8f, granite);
                    Bx(-3.7f, 3.5f, 2.4f, 0.25f, 0.55f, 0.25f, gdark);
                    break;
                case 10: // saz çatılar
                    for (int r = 0; r < 3; r++)
                        for (int c = 0; c < 4; c++)
                            Pr(1.4f + c * 0.85f, 2.6f, -1.0f + r * 1.0f, 0.8f, 0.5f, 0.7f, thatch, 90f);
                    Pr(-0.4f, 3.1f, 1.9f, 0.6f, 0.4f, 2.3f, thatch);
                    break;
                default: // bulutlar ve Huayna Picchu
                    Cn(0.6f, 0, 6.6f, 5.0f, 8.0f, H("#6FA35A"));
                    Cn(-2.6f, 0, 6.0f, 3.2f, 5.0f, H("#7DB066"));
                    Cn(0.6f, 5.5f, 6.6f, 1.7f, 2.5f, rock);
                    for (int k = 0; k < 4; k++) Sp(-3f + k * 2.2f, 4.5f + (k % 2) * 0.8f, 4.6f, 2.6f, 0.9f, 1.6f, new Color(1f, 1f, 1f, 0.6f));
                    break;
            }
        }

        // ================================================================== CHICHÉN ITZÁ (El Castillo)
        private static void ChichenItza(int g)
        {
            Color lime = H("#E3D8BF"), shade = H("#CDBF9F"), panel = H("#D2C39F"), temple = H("#E9DFC9"), dark = H("#6E6553");
            if (g <= 8)
            {
                float s = 8.6f - 0.62f * g, y = g * 0.55f;
                Bx(0, y, 0, s, 0.55f, s, lime);
                Bx(0, y + 0.08f, 0, s + 0.04f, 0.14f, s + 0.04f, panel);
                Bx(0, y + 0.42f, 0, s + 0.02f, 0.05f, s + 0.02f, shade);
                return;
            }
            float top = 9 * 0.55f; // 4.95
            float inner = (8.6f - 0.62f * 8) * 0.5f; // en üst teras kenarı
            switch (g)
            {
                case 9: // dört merdiven
                    for (int side = 0; side < 4; side++)
                    {
                        var sg = Prim.Group("stair" + side, G);
                        sg.localRotation = Quaternion.Euler(0, side * 90f, 0);
                        var keep = G; G = sg;
                        const int N = 18;
                        for (int k = 0; k < N; k++)
                        {
                            float d = 4.6f - (4.6f - inner) * k / N;
                            float depth = d - (inner - 0.2f);
                            Bx(0, k * top / N, -(inner - 0.2f) - depth * 0.5f, 1.6f, top / N, depth, k % 2 == 0 ? temple : lime);
                        }
                        var a = new Vector3(0, 0.15f, -4.6f); var b = new Vector3(0, top + 0.15f, -inner);
                        Beam(a + Vector3.right * 0.9f, b + Vector3.right * 0.9f, 0.25f, 0.3f, shade);
                        Beam(a - Vector3.right * 0.9f, b - Vector3.right * 0.9f, 0.25f, 0.3f, shade);
                        G = keep;
                    }
                    break;
                case 10: // yılan başları (kuzey merdiveni)
                    foreach (var sx in new[] { -0.9f, 0.9f })
                    {
                        Bx(sx, 0, -4.95f, 0.6f, 0.5f, 0.8f, lime);
                        Bx(sx, 0, -5.4f, 0.55f, 0.18f, 0.3f, shade);
                        Sp(sx - 0.2f, 0.42f, -5.1f, 0.12f, 0.12f, 0.12f, dark); Sp(sx + 0.2f, 0.42f, -5.1f, 0.12f, 0.12f, 0.12f, dark);
                        Bx(sx, 0.18f, -5.5f, 0.1f, 0.04f, 0.25f, H("#C0504D"));
                    }
                    break;
                default: // tepe tapınağı
                    Bx(0, top, 0, 3.0f, 1.6f, 3.0f, temple);
                    for (int k = -1; k <= 1; k++) Bx(k * 0.85f, top, -1.51f, 0.5f, 1.0f, 0.04f, dark);
                    Bx(0, top, 1.51f, 0.6f, 1.0f, 0.04f, dark); Bx(1.51f, top, 0, 0.04f, 1.0f, 0.6f, dark); Bx(-1.51f, top, 0, 0.04f, 1.0f, 0.6f, dark);
                    Bx(0, top + 1.6f, 0, 3.3f, 0.25f, 3.3f, shade);
                    for (int k = 0; k < 5; k++) Bx(-1.2f + k * 0.6f, top + 1.85f, 0, 0.3f, 0.3f, 0.3f, lime);
                    break;
            }
        }

        // ================================================================== KURTARICI İSA
        private static void StatueRobe(Color c, float f)
        {
            Cy(0, 7.3f, 0, 1.6f * f, 1.05f, c); Cy(0, 8.3f, 0, 1.38f * f, 1.3f, c);
        }
        private static void StatueTorso(Color c, float f)
        {
            Bx(0, 9.55f, 0, 1.25f * f, 1.75f, 0.78f * f, c);
            var sh = Cy(0, 0, 0, 0.75f * f, 1.5f, c); sh.transform.localPosition = new Vector3(0, 11.1f, 0); sh.transform.localRotation = Quaternion.Euler(0, 0, 90);
        }
        private static void StatueArm(float sx, Color c, float f)
        {
            Bx(1.85f * sx, 10.8f, 0, 2.4f, 0.55f * f, 0.55f * f, c);
            Bx(1.75f * sx, 10.45f, 0, 2.0f, 0.4f * f, 0.34f * f, c);
        }
        private static void StatueHands(Color c, float f)
        {
            Bx(-3.15f, 10.7f, 0, 0.32f * f, 0.55f * f, 0.28f * f, c); Bx(3.15f, 10.7f, 0, 0.32f * f, 0.55f * f, 0.28f * f, c);
        }
        private static void StatueHead(Color c, float f)
        {
            Cy(0, 11.45f, 0, 0.35f * f, 0.25f, c);
            var hd = Sp(0, 12.0f, -0.05f, 0.72f * f, 0.85f * f, 0.75f * f, c);
            hd.transform.localRotation = Quaternion.Euler(12, 0, 0);
            Sp(0, 11.95f, 0.15f, 0.72f * f, 0.8f * f, 0.55f * f, c);
        }

        private static void Redeemer(int g)
        {
            Color rock = H("#8E9586"), forest = H("#5E9B5A"), concrete = H("#B9B6AE"), soap = H("#E4E0D4"), ped = H("#D9D4C8");
            switch (g)
            {
                case 0: // zirve kayası + orman
                    Sp(0, 1.5f, 0, 7.5f, 6.4f, 6.5f, rock);
                    Sp(-1.4f, 3.0f, 0.6f, 4f, 3.4f, 4f, rock);
                    Bx(0, 3.9f, 0, 3.2f, 0.6f, 3.2f, rock);
                    var rng = new Rng(5);
                    for (int k = 0; k < 14; k++)
                    {
                        float a = k / 14f * Mathf.PI * 2f;
                        Sp(Mathf.Cos(a) * 4.0f, 0.5f + (float)rng.NextDouble() * 0.4f, Mathf.Sin(a) * 3.6f, 1.6f, 1.4f, 1.6f, k % 2 == 0 ? forest : H("#6FB06A"));
                    }
                    break;
                case 1: // kaide temeli
                    Bx(0, 4.5f, 0, 3.4f, 0.4f, 3.4f, ped);
                    Bx(0, 4.2f, -2.0f, 1.4f, 0.3f, 0.8f, ped);
                    break;
                case 2: // kaide ve şapel
                    Bx(0, 4.9f, 0, 1.8f, 2.4f, 1.8f, ped);
                    Bx(0, 4.9f, -0.91f, 0.5f, 0.9f, 0.04f, H("#6C675E"));
                    Bx(0, 7.2f, 0, 2.0f, 0.12f, 2.0f, concrete);
                    break;
                case 3: // betonarme iskelet
                    {
                        Color lattice = H("#9E9A90");
                        for (int k = 0; k < 4; k++) Bx(k % 2 == 0 ? -0.35f : 0.35f, 7.3f, k < 2 ? -0.2f : 0.2f, 0.1f, 4.8f, 0.1f, lattice);
                        Bx(0, 10.75f, 0, 6.2f, 0.12f, 0.12f, lattice);
                        for (int k = 0; k < 5; k++) Bx(0, 7.6f + k * 0.9f, 0, 0.85f, 0.08f, 0.5f, lattice);
                        break;
                    }
                case 4: StatueRobe(concrete, 1f); break;
                case 5: StatueTorso(concrete, 1f); break;
                case 6: StatueArm(-1f, concrete, 1f); break;
                case 7: StatueArm(1f, concrete, 1f); break;
                case 8: StatueHands(concrete, 1f); break;
                case 9: StatueHead(concrete, 1f); break;
                case 10: // sabuntaşı mozaik kaplama
                    StatueRobe(soap, 1.04f); StatueTorso(soap, 1.04f); StatueArm(-1f, soap, 1.05f); StatueArm(1f, soap, 1.05f); StatueHands(soap, 1.06f); StatueHead(soap, 1.04f);
                    break;
                default: // ışıklandırma
                    for (int k = 0; k < 4; k++)
                    {
                        float sx = k % 2 == 0 ? 1 : -1, sz = k < 2 ? 1 : -1;
                        Glow(Bx(1.45f * sx, 4.9f, 1.45f * sz, 0.25f, 0.25f, 0.25f, H("#FFE9A8")), H("#FFE9A8"), 1f);
                        // Koni tepesi ışıkta, geniş tabanı heykele doğru.
                        var lamp = new Vector3(1.45f * sx, 5.15f, 1.45f * sz);
                        var wide = new Vector3(0.5f * sx, 10.8f, 0.5f * sz);
                        var beam = Prim.FromMesh(MeshFactory.Cone(), G, wide, new Vector3(1.6f, (wide - lamp).magnitude, 1.6f), new Color(1f, 0.96f, 0.75f, 0.14f), "beam");
                        beam.transform.localRotation = Quaternion.FromToRotation(Vector3.up, (lamp - wide).normalized);
                    }
                    break;
            }
        }
    }
}
