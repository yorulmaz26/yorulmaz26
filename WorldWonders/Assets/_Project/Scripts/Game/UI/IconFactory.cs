using System;
using System.Collections.Generic;
using UnityEngine;

namespace WorldWonders.Game
{
    public enum Icon { Star, StarOutline, Heart, Coin, Crystal, Lock, Back, Close, Check, Play, Gear, Chest, Map, Museum, User, Shop, Magnifier, Brush, Trowel, Trophy, Puzzle, Calendar, Shirt, Plus, Hammer, Arrow, Bomb, Shuffle, Hint, Home }

    /// <summary>
    /// Yazı tipine bağımlı olmayan, çalışma anında üretilen beyaz ikon dokuları (renk UI'da tint ile verilir).
    /// GEÇİCİ VARLIK: final ikon seti hazır olduğunda Sprite/SVG ikonlarla değiştirilecek.
    /// </summary>
    public static class IconFactory
    {
        private const int Size = 96;
        private static readonly Dictionary<Icon, Texture2D> Cache = new Dictionary<Icon, Texture2D>();

        public static Texture2D Get(Icon icon)
        {
            Texture2D t;
            if (Cache.TryGetValue(icon, out t) && t != null) return t;
            t = Render(Shape(icon));
            t.name = "icon_" + icon;
            Cache[icon] = t;
            return t;
        }

        private static Texture2D Render(Func<Vector2, bool> inside)
        {
            var tex = new Texture2D(Size, Size, TextureFormat.RGBA32, false) { wrapMode = TextureWrapMode.Clamp, filterMode = FilterMode.Bilinear };
            var px = new Color32[Size * Size];
            const int ss = 3;
            for (int y = 0; y < Size; y++)
                for (int x = 0; x < Size; x++)
                {
                    int hit = 0;
                    for (int sy = 0; sy < ss; sy++)
                        for (int sx = 0; sx < ss; sx++)
                        {
                            float u = ((x + (sx + 0.5f) / ss) / Size) * 2f - 1f;
                            float v = ((y + (sy + 0.5f) / ss) / Size) * 2f - 1f;
                            if (inside(new Vector2(u, v))) hit++;
                        }
                    byte a = (byte)(255 * hit / (ss * ss));
                    px[y * Size + x] = new Color32(255, 255, 255, a);
                }
            tex.SetPixels32(px);
            tex.Apply(false, true);
            return tex;
        }

        // ---- Şekil ilkelleri ([-1,1] alanı, y yukarı) ----
        private static bool Circle(Vector2 p, Vector2 c, float r) { return (p - c).sqrMagnitude <= r * r; }
        private static bool Ring(Vector2 p, Vector2 c, float r, float w) { float d = (p - c).magnitude; return d <= r && d >= r - w; }
        private static bool Rect(Vector2 p, float x0, float y0, float x1, float y1) { return p.x >= x0 && p.x <= x1 && p.y >= y0 && p.y <= y1; }
        private static bool RoundRect(Vector2 p, float x0, float y0, float x1, float y1, float r)
        {
            float cx = Mathf.Clamp(p.x, x0 + r, x1 - r), cy = Mathf.Clamp(p.y, y0 + r, y1 - r);
            return Rect(p, x0, y0, x1, y1) && (p - new Vector2(cx, cy)).sqrMagnitude <= r * r;
        }
        private static bool Segment(Vector2 p, Vector2 a, Vector2 b, float w)
        {
            var ab = b - a; float t = Mathf.Clamp01(Vector2.Dot(p - a, ab) / ab.sqrMagnitude);
            return (p - (a + ab * t)).sqrMagnitude <= w * w;
        }
        private static bool Poly(Vector2 p, Vector2[] v)
        {
            bool inside = false;
            for (int i = 0, j = v.Length - 1; i < v.Length; j = i++)
                if (((v[i].y > p.y) != (v[j].y > p.y)) && (p.x < (v[j].x - v[i].x) * (p.y - v[i].y) / (v[j].y - v[i].y) + v[i].x)) inside = !inside;
            return inside;
        }
        private static Vector2[] StarPts(float r0, float r1)
        {
            var pts = new Vector2[10];
            for (int i = 0; i < 10; i++)
            {
                float a = Mathf.PI / 2 + i * Mathf.PI / 5;
                float r = i % 2 == 0 ? r0 : r1;
                pts[i] = new Vector2(Mathf.Cos(a) * r, Mathf.Sin(a) * r - 0.05f);
            }
            return pts;
        }

        private static readonly Vector2[] StarBig = StarPts(0.95f, 0.42f);
        private static readonly Vector2[] StarInner = StarPts(0.62f, 0.22f);

        private static Func<Vector2, bool> Shape(Icon i)
        {
            switch (i)
            {
                case Icon.Star: return p => Poly(p, StarBig);
                case Icon.StarOutline: return p => Poly(p, StarBig) && !Poly(p, StarInner);
                case Icon.Heart: return p => Circle(p, new Vector2(-0.38f, 0.25f), 0.42f) || Circle(p, new Vector2(0.38f, 0.25f), 0.42f) || Poly(p, new[] { new Vector2(-0.78f, 0.12f), new Vector2(0.78f, 0.12f), new Vector2(0, -0.85f) });
                case Icon.Coin: return p => Ring(p, Vector2.zero, 0.9f, 0.16f) || (Circle(p, Vector2.zero, 0.62f) && !Rect(p, -0.12f, -0.4f, 0.12f, 0.4f));
                case Icon.Crystal: return p => Poly(p, new[] { new Vector2(0, -0.92f), new Vector2(0.75f, 0.15f), new Vector2(0.45f, 0.7f), new Vector2(-0.45f, 0.7f), new Vector2(-0.75f, 0.15f) });
                case Icon.Lock: return p => RoundRect(p, -0.65f, -0.85f, 0.65f, 0.15f, 0.15f) || (Ring(p, new Vector2(0, 0.2f), 0.5f, 0.17f) && p.y > 0.1f);
                case Icon.Back: return p => Segment(p, new Vector2(0.35f, 0.7f), new Vector2(-0.35f, 0f), 0.16f) || Segment(p, new Vector2(-0.35f, 0f), new Vector2(0.35f, -0.7f), 0.16f);
                case Icon.Close: return p => Segment(p, new Vector2(-0.6f, -0.6f), new Vector2(0.6f, 0.6f), 0.15f) || Segment(p, new Vector2(-0.6f, 0.6f), new Vector2(0.6f, -0.6f), 0.15f);
                case Icon.Check: return p => Segment(p, new Vector2(-0.7f, 0f), new Vector2(-0.2f, -0.5f), 0.16f) || Segment(p, new Vector2(-0.2f, -0.5f), new Vector2(0.7f, 0.55f), 0.16f);
                case Icon.Play: return p => Poly(p, new[] { new Vector2(-0.5f, -0.75f), new Vector2(0.75f, 0f), new Vector2(-0.5f, 0.75f) });
                case Icon.Gear:
                    return p =>
                    {
                        float a = Mathf.Atan2(p.y, p.x); float r = p.magnitude;
                        float teeth = Mathf.Cos(a * 8f) > 0.3f ? 0.92f : 0.72f;
                        return r <= teeth && r >= 0.3f;
                    };
                case Icon.Chest: return p => (RoundRect(p, -0.85f, -0.75f, 0.85f, 0.15f, 0.08f) || (Circle(p, new Vector2(0, -0.05f), 0.85f) && p.y > 0.2f && p.y < 0.75f)) && !Rect(p, -0.85f, 0.12f, 0.85f, 0.2f) || Rect(p, -0.12f, -0.15f, 0.12f, 0.35f);
                case Icon.Map: return p => Poly(p, new[] { new Vector2(-0.9f, -0.7f), new Vector2(-0.3f, -0.5f), new Vector2(0.3f, -0.75f), new Vector2(0.9f, -0.55f), new Vector2(0.9f, 0.75f), new Vector2(0.3f, 0.55f), new Vector2(-0.3f, 0.8f), new Vector2(-0.9f, 0.6f) }) && !Segment(p, new Vector2(-0.3f, -0.5f), new Vector2(-0.3f, 0.8f), 0.05f) && !Segment(p, new Vector2(0.3f, -0.75f), new Vector2(0.3f, 0.55f), 0.05f);
                case Icon.Museum: return p => Poly(p, new[] { new Vector2(-0.9f, 0.4f), new Vector2(0f, 0.9f), new Vector2(0.9f, 0.4f) }) || Rect(p, -0.9f, -0.9f, 0.9f, -0.72f) || Rect(p, -0.7f, -0.7f, -0.45f, 0.35f) || Rect(p, -0.13f, -0.7f, 0.13f, 0.35f) || Rect(p, 0.45f, -0.7f, 0.7f, 0.35f);
                case Icon.User: return p => Circle(p, new Vector2(0, 0.4f), 0.4f) || (Circle(p, new Vector2(0, -0.85f), 0.85f) && p.y < -0.15f && p.y > -0.9f);
                case Icon.Shop: return p => RoundRect(p, -0.75f, -0.85f, 0.75f, 0.35f, 0.12f) || (Ring(p, new Vector2(0, 0.35f), 0.42f, 0.13f) && p.y > 0.35f);
                case Icon.Magnifier: return p => Ring(p, new Vector2(-0.18f, 0.18f), 0.62f, 0.17f) || Segment(p, new Vector2(0.3f, -0.3f), new Vector2(0.8f, -0.8f), 0.16f);
                case Icon.Brush: return p => Segment(p, new Vector2(0.75f, 0.75f), new Vector2(0.05f, 0.05f), 0.11f) || Poly(p, new[] { new Vector2(0.15f, -0.05f), new Vector2(-0.05f, 0.15f), new Vector2(-0.85f, -0.6f), new Vector2(-0.6f, -0.85f) });
                case Icon.Trowel: return p => Segment(p, new Vector2(0.8f, 0.8f), new Vector2(0.3f, 0.3f), 0.12f) || Poly(p, new[] { new Vector2(0.35f, 0.15f), new Vector2(0.15f, 0.35f), new Vector2(-0.85f, -0.85f) });
                case Icon.Trophy: return p => (Circle(p, new Vector2(0, 0.35f), 0.55f) && p.y < 0.9f && p.y > 0.0f) || Rect(p, -0.55f, 0.35f, 0.55f, 0.9f) || Rect(p, -0.1f, -0.5f, 0.1f, 0.0f) || RoundRect(p, -0.5f, -0.9f, 0.5f, -0.5f, 0.08f) || Ring(p, new Vector2(-0.62f, 0.5f), 0.3f, 0.1f) || Ring(p, new Vector2(0.62f, 0.5f), 0.3f, 0.1f);
                case Icon.Puzzle: return p => (Rect(p, -0.65f, -0.65f, 0.65f, 0.65f) || Circle(p, new Vector2(0, 0.78f), 0.22f) || Circle(p, new Vector2(0.78f, 0), 0.22f)) && !Circle(p, new Vector2(-0.65f, 0f), 0.22f);
                case Icon.Calendar: return p => (RoundRect(p, -0.8f, -0.85f, 0.8f, 0.7f, 0.12f) && !Rect(p, -0.65f, -0.7f, 0.65f, 0.3f)) || Rect(p, -0.5f, 0.6f, -0.3f, 0.95f) || Rect(p, 0.3f, 0.6f, 0.5f, 0.95f) || Rect(p, -0.45f, -0.45f, -0.1f, -0.1f);
                case Icon.Shirt: return p => Poly(p, new[] { new Vector2(-0.3f, 0.85f), new Vector2(-0.95f, 0.45f), new Vector2(-0.7f, 0.05f), new Vector2(-0.5f, 0.2f), new Vector2(-0.5f, -0.85f), new Vector2(0.5f, -0.85f), new Vector2(0.5f, 0.2f), new Vector2(0.7f, 0.05f), new Vector2(0.95f, 0.45f), new Vector2(0.3f, 0.85f), new Vector2(0f, 0.6f) });
                case Icon.Plus: return p => Rect(p, -0.15f, -0.75f, 0.15f, 0.75f) || Rect(p, -0.75f, -0.15f, 0.75f, 0.15f);
                case Icon.Hammer: return p => RoundRect(p, -0.8f, 0.3f, 0.6f, 0.8f, 0.1f) || Rect(p, -0.12f, -0.9f, 0.12f, 0.35f);
                case Icon.Arrow: return p => Poly(p, new[] { new Vector2(-0.9f, -0.15f), new Vector2(0.3f, -0.15f), new Vector2(0.3f, -0.5f), new Vector2(0.95f, 0f), new Vector2(0.3f, 0.5f), new Vector2(0.3f, 0.15f), new Vector2(-0.9f, 0.15f) });
                case Icon.Bomb: return p => Circle(p, new Vector2(-0.1f, -0.15f), 0.7f) || Segment(p, new Vector2(0.3f, 0.35f), new Vector2(0.65f, 0.75f), 0.1f) || Circle(p, new Vector2(0.75f, 0.85f), 0.14f);
                case Icon.Shuffle: return p => Segment(p, new Vector2(-0.8f, -0.5f), new Vector2(0.5f, 0.5f), 0.11f) || Segment(p, new Vector2(-0.8f, 0.5f), new Vector2(0.5f, -0.5f), 0.11f) || Poly(p, new[] { new Vector2(0.4f, 0.75f), new Vector2(0.9f, 0.5f), new Vector2(0.4f, 0.25f) }) || Poly(p, new[] { new Vector2(0.4f, -0.25f), new Vector2(0.9f, -0.5f), new Vector2(0.4f, -0.75f) });
                case Icon.Hint: return p => Circle(p, new Vector2(0, 0.2f), 0.6f) || Rect(p, -0.28f, -0.8f, 0.28f, -0.35f);
                case Icon.Home: return p => Poly(p, new[] { new Vector2(-0.9f, 0.05f), new Vector2(0f, 0.85f), new Vector2(0.9f, 0.05f) }) || (Rect(p, -0.65f, -0.85f, 0.65f, 0.1f) && !Rect(p, -0.18f, -0.85f, 0.18f, -0.25f));
                default: return p => Circle(p, Vector2.zero, 0.8f);
            }
        }
    }
}
