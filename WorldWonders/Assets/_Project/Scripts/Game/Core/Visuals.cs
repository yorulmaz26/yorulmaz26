using System.Collections.Generic;
using UnityEngine;

namespace WorldWonders.Game
{
    /// <summary>Tasarım belgesindeki ana renkler ve yardımcı tonlar. Koyu tema yoktur.</summary>
    public static class Palette
    {
        public static readonly Color Cream = Hex("#F8F5ED");
        public static readonly Color Turquoise = Hex("#55C5D3");
        public static readonly Color Gold = Hex("#D5A447");
        public static readonly Color Green = Hex("#8FCB79");
        public static readonly Color Text = Hex("#25384A");

        public static readonly Color TextSoft = Hex("#5B6E80");
        public static readonly Color White = Color.white;
        public static readonly Color CardBorder = Hex("#E7DFCC");
        public static readonly Color TurquoiseDeep = Hex("#3AA9B8");
        public static readonly Color GoldDeep = Hex("#B98A30");
        public static readonly Color Coral = Hex("#E8846B");
        public static readonly Color Lilac = Hex("#A99BE0");
        public static readonly Color Sky = Hex("#BFE7F0");
        public static readonly Color Sand = Hex("#E9D3A6");
        public static readonly Color Stone = Hex("#CFC6B4");
        public static readonly Color Marble = Hex("#F4F1EA");
        public static readonly Color Terracotta = Hex("#D08A5E");
        public static readonly Color Grass = Hex("#A9D98F");
        public static readonly Color Water = Hex("#8FD3E0");
        public static readonly Color Locked = Hex("#C9CDD2");
        public static readonly Color Fog = new Color(1f, 1f, 1f, 0.55f);

        private static readonly Dictionary<string, Color> Cache = new Dictionary<string, Color>();

        public static Color Hex(string hex)
        {
            if (string.IsNullOrEmpty(hex)) return Color.magenta;
            Color c;
            if (Cache.TryGetValue(hex, out c)) return c;
            if (!ColorUtility.TryParseHtmlString(hex, out c)) c = Color.magenta;
            Cache[hex] = c;
            return c;
        }

        public static Color WithAlpha(Color c, float a) { c.a = a; return c; }
        public static Color Lighten(Color c, float t) { return Color.Lerp(c, Color.white, t); }
        public static Color Darken(Color c, float t) { return Color.Lerp(c, Text, t); }

        /// <summary>Match-3 taş renkleri (Altın Para, Parşömen, Amfora, Skarabe, Pusula, Taç).</summary>
        public static readonly Color[] TileColors =
        {
            Hex("#F2C14E"), Hex("#F3E3C0"), Hex("#D9805A"), Hex("#4FB89A"), Hex("#5AA6E0"), Hex("#B07BD8")
        };
    }

    /// <summary>
    /// Stilize şader malzemeleri (önbellekli, SRP Batcher dostu: renk başına bir malzeme).
    /// Şaderler Resources/Shaders altındadır; böylece yapıya her zaman dahil edilirler.
    /// </summary>
    public static class MaterialLibrary
    {
        private static Shader _opaque, _transparent;
        private static readonly Dictionary<long, Material> Cache = new Dictionary<long, Material>();

        private static Shader Opaque
        {
            get
            {
                if (_opaque == null) _opaque = Shader.Find("WorldWonders/Stylized");
                if (_opaque == null) _opaque = Shader.Find("Universal Render Pipeline/Lit");
                if (_opaque == null) _opaque = Shader.Find("Standard");
                return _opaque;
            }
        }

        private static Shader Transparent
        {
            get
            {
                if (_transparent == null) _transparent = Shader.Find("WorldWonders/StylizedTransparent");
                if (_transparent == null) _transparent = Opaque;
                return _transparent;
            }
        }

        private static long Key(Color c, float emission, bool transparent)
        {
            Color32 k = c;
            return ((long)k.r << 32) | ((long)k.g << 24) | ((long)k.b << 16) | ((long)k.a << 8) | ((long)(Mathf.Clamp01(emission) * 15) << 1) | (transparent ? 1L : 0L);
        }

        public static Material Get(Color c, float emission = 0f)
        {
            bool tr = c.a < 0.999f;
            long key = Key(c, emission, tr);
            Material m;
            if (Cache.TryGetValue(key, out m) && m != null) return m;
            m = new Material(tr ? Transparent : Opaque) { name = "WW_" + ColorUtility.ToHtmlStringRGBA(c) };
            m.SetColor("_BaseColor", c);
            m.SetColor("_Color", c);
            m.SetFloat("_Emission", emission);
            m.enableInstancing = true;
            if (tr) m.renderQueue = 3000;
            Cache[key] = m;
            return m;
        }

        public static Material Get(string hex, float emission = 0f) { return Get(Palette.Hex(hex), emission); }
    }

    /// <summary>
    /// Geçici (placeholder) varlık işareti. Prosedürel primitif modellerin kök nesnesine eklenir;
    /// final 3D modeller geldiğinde bu bileşeni taşıyan her şey değiştirilmelidir.
    /// Editörde "World Wonders/Geçici Varlıkları Listele" menüsüyle listelenir.
    /// </summary>
    public sealed class PlaceholderAsset : MonoBehaviour
    {
        [TextArea] public string Note = "GEÇİCİ VARLIK: prosedürel primitif model; final stilize 3D model ile değiştirilecek.";
        public static void Mark(GameObject go, string note = null)
        {
            var p = go.GetComponent<PlaceholderAsset>();
            if (p == null) p = go.AddComponent<PlaceholderAsset>();
            if (note != null) p.Note = "GEÇİCİ VARLIK: " + note;
        }
    }

    /// <summary>Prosedürel mesh üretimi (koni, halka/yay, kubbe, prizma). Mesh'ler önbelleklenir.</summary>
    public static class MeshFactory
    {
        private static readonly Dictionary<string, Mesh> Cache = new Dictionary<string, Mesh>();

        public static Mesh Cone(int segments = 16)
        {
            string key = "cone" + segments;
            Mesh m; if (Cache.TryGetValue(key, out m)) return m;
            var v = new List<Vector3>(); var t = new List<int>();
            for (int i = 0; i < segments; i++)
            {
                float a0 = i * Mathf.PI * 2 / segments, a1 = (i + 1) * Mathf.PI * 2 / segments;
                var p0 = new Vector3(Mathf.Cos(a0) * 0.5f, 0, Mathf.Sin(a0) * 0.5f);
                var p1 = new Vector3(Mathf.Cos(a1) * 0.5f, 0, Mathf.Sin(a1) * 0.5f);
                int b = v.Count;
                v.Add(p0); v.Add(new Vector3(0, 1, 0)); v.Add(p1);
                t.Add(b); t.Add(b + 1); t.Add(b + 2);
                b = v.Count;
                v.Add(p1); v.Add(Vector3.zero); v.Add(p0);
                t.Add(b); t.Add(b + 1); t.Add(b + 2);
            }
            return Store(key, v, t);
        }

        /// <summary>Yatay halka/yay (Kolezyum duvarları, kemer sıraları). Yükseklik 1, merkez tabanda.</summary>
        public static Mesh Ring(float inner, float outer, float arcDegrees = 360f, int segments = 48)
        {
            string key = "ring" + inner + "_" + outer + "_" + arcDegrees + "_" + segments;
            Mesh m; if (Cache.TryGetValue(key, out m)) return m;
            var v = new List<Vector3>(); var t = new List<int>();
            float arc = arcDegrees * Mathf.Deg2Rad;
            for (int i = 0; i < segments; i++)
            {
                float a0 = arc * i / segments, a1 = arc * (i + 1) / segments;
                Vector3 d0 = new Vector3(Mathf.Cos(a0), 0, Mathf.Sin(a0)), d1 = new Vector3(Mathf.Cos(a1), 0, Mathf.Sin(a1));
                Vector3 up = Vector3.up;
                Quad(v, t, d0 * outer, d1 * outer, d1 * outer + up, d0 * outer + up);           // dış yüz
                Quad(v, t, d1 * inner, d0 * inner, d0 * inner + up, d1 * inner + up);           // iç yüz
                Quad(v, t, d0 * inner + up, d0 * outer + up, d1 * outer + up, d1 * inner + up); // üst
                Quad(v, t, d1 * inner, d1 * outer, d0 * outer, d0 * inner);                     // alt
            }
            if (arcDegrees < 359.9f)
            {
                Vector3 s = new Vector3(1, 0, 0), e = new Vector3(Mathf.Cos(arc), 0, Mathf.Sin(arc));
                Quad(v, t, s * inner, s * outer, s * outer + Vector3.up, s * inner + Vector3.up);
                Quad(v, t, e * outer, e * inner, e * inner + Vector3.up, e * outer + Vector3.up);
            }
            return Store(key, v, t);
        }

        /// <summary>Yarım küre kubbe (taban yarıçapı 0.5, yükseklik 0.5 × heightScale).</summary>
        public static Mesh Dome(int segments = 20, int rings = 8, float onionPoint = 0f)
        {
            string key = "dome" + segments + "_" + rings + "_" + onionPoint;
            Mesh m; if (Cache.TryGetValue(key, out m)) return m;
            var v = new List<Vector3>(); var t = new List<int>();
            for (int r = 0; r <= rings; r++)
            {
                float phi = r / (float)rings * Mathf.PI * 0.5f;
                float rad = Mathf.Cos(phi) * 0.5f;
                if (onionPoint > 0) rad *= 1f + onionPoint * Mathf.Sin(phi * 2f); // soğan kubbe
                float y = Mathf.Sin(phi) * 0.5f + (onionPoint > 0 ? Mathf.Pow(r / (float)rings, 4) * onionPoint * 0.6f : 0);
                for (int s = 0; s <= segments; s++)
                {
                    float th = s / (float)segments * Mathf.PI * 2;
                    v.Add(new Vector3(Mathf.Cos(th) * rad, y, Mathf.Sin(th) * rad));
                }
            }
            int row = segments + 1;
            for (int r = 0; r < rings; r++)
                for (int s = 0; s < segments; s++)
                {
                    int a = r * row + s, b = a + 1, c = a + row, d = c + 1;
                    t.Add(a); t.Add(c); t.Add(b); t.Add(b); t.Add(c); t.Add(d);
                }
            return Store(key, v, t, smooth: true);
        }

        /// <summary>Üçgen prizma (alınlık, çatı). Taban x:[-0.5,0.5], z:[-0.5,0.5], tepe y=1.</summary>
        public static Mesh Prism()
        {
            const string key = "prism";
            Mesh m; if (Cache.TryGetValue(key, out m)) return m;
            var v = new List<Vector3>(); var t = new List<int>();
            Vector3 a = new Vector3(-0.5f, 0, -0.5f), b = new Vector3(0.5f, 0, -0.5f), c = new Vector3(0, 1, -0.5f);
            Vector3 a2 = new Vector3(-0.5f, 0, 0.5f), b2 = new Vector3(0.5f, 0, 0.5f), c2 = new Vector3(0, 1, 0.5f);
            Tri(v, t, a, c, b); Tri(v, t, a2, b2, c2);
            Quad(v, t, a, a2, c2, c); Quad(v, t, b, c, c2, b2); Quad(v, t, a, b, b2, a2);
            return Store(key, v, t);
        }

        private static void Quad(List<Vector3> v, List<int> t, Vector3 a, Vector3 b, Vector3 c, Vector3 d)
        {
            int i = v.Count; v.Add(a); v.Add(b); v.Add(c); v.Add(d);
            t.Add(i); t.Add(i + 2); t.Add(i + 1); t.Add(i); t.Add(i + 3); t.Add(i + 2);
        }

        private static void Tri(List<Vector3> v, List<int> t, Vector3 a, Vector3 b, Vector3 c)
        {
            int i = v.Count; v.Add(a); v.Add(b); v.Add(c); t.Add(i); t.Add(i + 1); t.Add(i + 2);
        }

        private static Mesh Store(string key, List<Vector3> v, List<int> t, bool smooth = false)
        {
            var mesh = new Mesh { name = key };
            mesh.SetVertices(v);
            mesh.SetTriangles(t, 0);
            mesh.RecalculateNormals();
            mesh.RecalculateBounds();
            Cache[key] = mesh;
            return mesh;
        }
    }

    /// <summary>Primitif tabanlı geçici model yardımcıları.</summary>
    public static class Prim
    {
        public static Transform Group(string name, Transform parent, Vector3 localPos = default(Vector3))
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            return go.transform;
        }

        public static GameObject Create(PrimitiveType type, Transform parent, Vector3 localPos, Vector3 localScale, Color color, bool keepCollider = false, string name = null)
        {
            var go = GameObject.CreatePrimitive(type);
            if (name != null) go.name = name;
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            go.transform.localScale = localScale;
            var r = go.GetComponent<MeshRenderer>();
            r.sharedMaterial = MaterialLibrary.Get(color);
            r.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            r.receiveShadows = false;
            if (!keepCollider) Object.Destroy(go.GetComponent<Collider>());
            return go;
        }

        public static GameObject Box(Transform parent, Vector3 pos, Vector3 size, Color c, bool collider = false) { return Create(PrimitiveType.Cube, parent, pos, size, c, collider); }
        public static GameObject Cyl(Transform parent, Vector3 pos, float diameter, float height, Color c, bool collider = false) { return Create(PrimitiveType.Cylinder, parent, pos, new Vector3(diameter, height * 0.5f, diameter), c, collider); }
        public static GameObject Ball(Transform parent, Vector3 pos, float diameter, Color c, bool collider = false) { return Create(PrimitiveType.Sphere, parent, pos, Vector3.one * diameter, c, collider); }
        public static GameObject Capsule(Transform parent, Vector3 pos, float diameter, float height, Color c, bool collider = false) { return Create(PrimitiveType.Capsule, parent, pos, new Vector3(diameter, height * 0.5f, diameter), c, collider); }

        public static GameObject FromMesh(Mesh mesh, Transform parent, Vector3 pos, Vector3 scale, Color c, string name = "mesh", bool collider = false)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            go.transform.localScale = scale;
            go.AddComponent<MeshFilter>().sharedMesh = mesh;
            var r = go.AddComponent<MeshRenderer>();
            r.sharedMaterial = MaterialLibrary.Get(c);
            r.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            r.receiveShadows = false;
            if (collider) go.AddComponent<MeshCollider>().sharedMesh = mesh;
            return go;
        }

        public static GameObject Cone(Transform parent, Vector3 pos, float diameter, float height, Color c) { return FromMesh(MeshFactory.Cone(), parent, pos, new Vector3(diameter, height, diameter), c, "cone"); }

        public static void SetColor(GameObject go, Color c)
        {
            foreach (var r in go.GetComponentsInChildren<Renderer>(true)) r.sharedMaterial = MaterialLibrary.Get(c);
        }

        public static void ClearChildren(Transform t)
        {
            for (int i = t.childCount - 1; i >= 0; i--) Object.Destroy(t.GetChild(i).gameObject);
        }
    }
}
