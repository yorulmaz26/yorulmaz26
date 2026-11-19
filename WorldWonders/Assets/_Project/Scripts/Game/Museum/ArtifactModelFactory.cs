using UnityEngine;
using WorldWonders.Core.Content;

namespace WorldWonders.Game
{
    /// <summary>
    /// 42 tarihî eserin stilize primitif modelleri (~1 birim boy, tabanı orijinde).
    /// Toplanmamış eser: açık gri, yarı saydam siluet (aydınlık tema).
    /// GEÇİCİ VARLIK: final eser modelleriyle değiştirilecek.
    /// </summary>
    public static class ArtifactModelFactory
    {
        private static readonly Color Bronze = Palette.Hex("#B8834A");
        private static readonly Color BronzeDark = Palette.Hex("#8E6235");
        private static readonly Color GoldC = Palette.Hex("#E2B44F");
        private static readonly Color Silver = Palette.Hex("#C9CED6");
        private static readonly Color Bone = Palette.Hex("#EDE2C8");
        private static readonly Color MarbleC = Palette.Hex("#F3EFE6");
        private static readonly Color TerraC = Palette.Hex("#C9774E");
        private static readonly Color SandStone = Palette.Hex("#D99A7C");
        private static readonly Color Wood = Palette.Hex("#A9744B");
        private static readonly Color StoneGrey = Palette.Hex("#B9B4A8");
        private static readonly Color Jade = Palette.Hex("#5DB38A");
        private static readonly Color Brick = Palette.Hex("#9A9690");
        private static readonly Color Soap = Palette.Hex("#A8B7A0");
        private static readonly Color SilhouetteC = new Color(0.86f, 0.88f, 0.91f, 0.62f);

        public static GameObject Build(ArtifactInfo a, Transform parent, bool collected)
        {
            var root = new GameObject(a.Id);
            root.transform.SetParent(parent, false);
            var t = root.transform;
            switch (a.Id)
            {
                // ---------------- Kolezyum
                case "colosseum_art1": Helmet(t); break;
                case "colosseum_art2": CoinOnStand(t, Bronze, BronzeDark, true); break;
                case "colosseum_art3": Ticket(t, Bone, TerraC, false); break;
                case "colosseum_art4": VelariumRing(t); break;
                case "colosseum_art5": Capital(t); break;
                case "colosseum_art6": Pulley(t); break;
                // ---------------- Petra
                case "petra_art1": Lamp(t); break;
                case "petra_art2": Bowl(t); break;
                case "petra_art3": CoinOnStand(t, Silver, Palette.Hex("#A3A9B2"), false); break;
                case "petra_art4": WaterChannel(t); break;
                case "petra_art5": Betyl(t); break;
                case "petra_art6": Urn(t); break;
                // ---------------- Tac Mahal
                case "tajmahal_art1": PietraDura(t); break;
                case "tajmahal_art2": Plaque(t); break;
                case "tajmahal_art3": SealRing(t); break;
                case "tajmahal_art4": Jali(t); break;
                case "tajmahal_art5": GardenPlan(t); break;
                case "tajmahal_art6": Finial(t); break;
                // ---------------- Çin Seddi
                case "greatwall_art1": StampedBrick(t); break;
                case "greatwall_art2": Beacon(t); break;
                case "greatwall_art3": CrossbowTrigger(t); break;
                case "greatwall_art4": PassTablet(t); break;
                case "greatwall_art5": EarthMold(t); break;
                case "greatwall_art6": DoorKnocker(t); break;
                // ---------------- Machu Picchu
                case "machupicchu_art1": Quipu(t); break;
                case "machupicchu_art2": Intihuatana(t); break;
                case "machupicchu_art3": Tumi(t); break;
                case "machupicchu_art4": Aryballo(t); break;
                case "machupicchu_art5": Chakitaqlla(t); break;
                case "machupicchu_art6": Llama(t); break;
                // ---------------- Chichén Itzá
                case "chichenitza_art1": ChacMool(t); break;
                case "chichenitza_art2": SerpentHead(t); break;
                case "chichenitza_art3": Glyph(t); break;
                case "chichenitza_art4": BallRing(t); break;
                case "chichenitza_art5": JadeNecklace(t); break;
                case "chichenitza_art6": Censer(t); break;
                // ---------------- Kurtarıcı İsa
                case "christredeemer_art1": MosaicTile(t); break;
                case "christredeemer_art2": PlasterHand(t); break;
                case "christredeemer_art3": Ticket(t, Palette.Hex("#F6EBC8"), Palette.Hex("#D4574A"), true); break;
                case "christredeemer_art4": Drawing(t); break;
                case "christredeemer_art5": LightningRod(t); break;
                case "christredeemer_art6": ConcreteSample(t); break;
                default: Prim.Ball(t, new Vector3(0, 0.4f, 0), 0.7f, StoneGrey); break;
            }
            if (!collected) Prim.SetColor(root, SilhouetteC);
            PlaceholderAsset.Mark(root, "prosedürel eser modeli (" + a.Name + ")");
            return root;
        }

        // ================================================================ yardımcılar
        private static GameObject Rot(GameObject g, float x, float y, float z) { g.transform.localRotation = Quaternion.Euler(x, y, z); return g; }
        private static void Stand(Transform t, Color c) { Prim.Cyl(t, new Vector3(0, 0.03f, 0), 0.5f, 0.06f, c); }

        private static void Disc(Transform t, Vector3 pos, float d, float thick, Color c) { Rot(Prim.Cyl(t, pos, d, thick, c), 90, 0, 0); }

        private static void UprightRing(Transform t, Vector3 pos, float inner, float outer, float depth, Color c, float arc = 360f)
        {
            var g = Prim.FromMesh(MeshFactory.Ring(inner, outer, arc, 32), t, pos, new Vector3(1, depth, 1), c, "ring");
            g.transform.localRotation = Quaternion.Euler(90, 0, 0);
            g.transform.localPosition = pos + new Vector3(0, 0, depth * 0.5f);
        }

        // ================================================================ Kolezyum
        private static void Helmet(Transform t)
        {
            Prim.FromMesh(MeshFactory.Dome(), t, new Vector3(0, 0.35f, 0), new Vector3(0.62f, 0.9f, 0.62f), Bronze, "dome");
            Prim.FromMesh(MeshFactory.Ring(0.3f, 0.5f, 360, 32), t, new Vector3(0, 0.33f, 0), new Vector3(1, 0.04f, 1), BronzeDark, "brim");
            Prim.Box(t, new Vector3(0, 0.12f, 0.2f), new Vector3(0.36f, 0.3f, 0.12f), BronzeDark);
            for (int i = -2; i <= 2; i++) Prim.Box(t, new Vector3(i * 0.06f, 0.14f, 0.27f), new Vector3(0.02f, 0.24f, 0.02f), GoldC);
            Prim.Box(t, new Vector3(0, 0.88f, 0), new Vector3(0.06f, 0.18f, 0.55f), Palette.Coral);
        }

        private static void CoinOnStand(Transform t, Color face, Color rim, bool colosseumRelief)
        {
            Stand(t, Wood);
            Prim.Box(t, new Vector3(0, 0.12f, 0.06f), new Vector3(0.12f, 0.18f, 0.06f), Wood);
            Disc(t, new Vector3(0, 0.5f, 0), 0.78f, 0.08f, rim);
            Disc(t, new Vector3(0, 0.5f, -0.03f), 0.64f, 0.04f, face);
            if (colosseumRelief) UprightRing(t, new Vector3(0, 0.5f, -0.07f), 0.13f, 0.22f, 0.03f, rim);
            else Prim.Ball(t, new Vector3(0, 0.55f, -0.05f), 0.22f, rim);
        }

        private static void Ticket(Transform t, Color paper, Color stripe, bool hole)
        {
            Stand(t, Wood);
            var g = Rot(Prim.Box(t, new Vector3(0, 0.45f, 0), new Vector3(0.62f, 0.8f, 0.05f), paper), 0, 0, 8);
            Prim.Box(g.transform, new Vector3(0, 0.3f, -0.6f), new Vector3(1f, 0.12f, 0.4f), stripe);
            for (int i = 0; i < 3; i++) Prim.Box(g.transform, new Vector3(0, -0.05f - i * 0.15f, -0.6f), new Vector3(0.7f, 0.04f, 0.4f), Palette.Text * 0.3f + paper * 0.7f);
            if (hole) Disc(g.transform, new Vector3(0.3f, -0.35f, -0.6f), 0.12f, 0.4f, Palette.Cream);
        }

        private static void VelariumRing(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.08f, 0), new Vector3(0.5f, 0.16f, 0.3f), StoneGrey);
            UprightRing(t, new Vector3(0, 0.55f, 0), 0.26f, 0.38f, 0.1f, BronzeDark);
            var rope = Rot(Prim.Cyl(t, new Vector3(0.28f, 0.45f, 0), 0.06f, 0.9f, Palette.Sand), 0, 0, 55);
            rope.name = "rope";
        }

        private static void Capital(Transform t)
        {
            Prim.Cyl(t, new Vector3(0, 0.25f, 0), 0.42f, 0.5f, MarbleC);
            Prim.Cyl(t, new Vector3(0, 0.56f, 0), 0.52f, 0.12f, MarbleC);
            for (int i = 0; i < 8; i++)
            {
                float a = i * 45f * Mathf.Deg2Rad;
                Rot(Prim.Box(t, new Vector3(Mathf.Cos(a) * 0.22f, 0.66f, Mathf.Sin(a) * 0.22f), new Vector3(0.1f, 0.18f, 0.04f), Palette.Hex("#E6DFCF")), 0, -i * 45f + 90, 0);
            }
            Prim.Box(t, new Vector3(0, 0.85f, 0), new Vector3(0.78f, 0.12f, 0.78f), MarbleC);
            Prim.Ball(t, new Vector3(0.36f, 0.76f, 0), 0.18f, MarbleC);
            Prim.Ball(t, new Vector3(-0.36f, 0.76f, 0), 0.18f, MarbleC);
        }

        private static void Pulley(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.04f, 0), new Vector3(0.7f, 0.08f, 0.4f), Wood);
            Prim.Box(t, new Vector3(-0.28f, 0.45f, 0), new Vector3(0.08f, 0.82f, 0.12f), Wood);
            Prim.Box(t, new Vector3(0.28f, 0.45f, 0), new Vector3(0.08f, 0.82f, 0.12f), Wood);
            Rot(Prim.Cyl(t, new Vector3(0, 0.6f, 0), 0.62f, 0.14f, Palette.Hex("#C08A5A")), 0, 0, 90);
            Rot(Prim.Cyl(t, new Vector3(0, 0.6f, 0), 0.06f, 0.64f, BronzeDark), 0, 0, 90);
            Prim.Cyl(t, new Vector3(0.2f, 0.32f, 0), 0.03f, 0.55f, Palette.Sand);
        }

        // ================================================================ Petra
        private static void Lamp(Transform t)
        {
            Prim.Create(PrimitiveType.Sphere, t, new Vector3(0, 0.2f, 0), new Vector3(0.62f, 0.3f, 0.5f), TerraC);
            Rot(Prim.Cyl(t, new Vector3(0.36f, 0.2f, 0), 0.16f, 0.2f, TerraC), 0, 0, 90);
            Disc(t, new Vector3(0, 0.36f, 0), 0.14f, 0.02f, Palette.Darken(TerraC, 0.3f));
            Rot(Prim.Create(PrimitiveType.Capsule, t, new Vector3(-0.32f, 0.32f, 0), new Vector3(0.08f, 0.12f, 0.08f), TerraC), 0, 0, 40);
            Prim.Cone(t, new Vector3(0.46f, 0.24f, 0), 0.1f, 0.22f, Palette.Hex("#F2B544")).GetComponent<MeshRenderer>().sharedMaterial = MaterialLibrary.Get(Palette.Hex("#F2B544"), 0.8f);
        }

        private static void Bowl(Transform t)
        {
            Prim.Cyl(t, new Vector3(0, 0.04f, 0), 0.3f, 0.08f, TerraC);
            Rot(Prim.FromMesh(MeshFactory.Dome(), t, new Vector3(0, 0.46f, 0), new Vector3(0.95f, 0.8f, 0.95f), Palette.Hex("#E3A57C"), "bowl"), 180, 0, 0);
            Prim.Cyl(t, new Vector3(0, 0.42f, 0), 0.86f, 0.02f, Palette.Hex("#F1D7BF"));
            for (int i = 0; i < 6; i++)
            {
                float a = i * 60f * Mathf.Deg2Rad;
                Rot(Prim.Box(t, new Vector3(Mathf.Cos(a) * 0.25f, 0.44f, Mathf.Sin(a) * 0.25f), new Vector3(0.22f, 0.02f, 0.05f), Palette.Hex("#8E4B2E")), 0, -i * 60f, 0);
            }
        }

        private static void WaterChannel(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.12f, 0), new Vector3(0.9f, 0.08f, 0.42f), SandStone);
            Prim.Box(t, new Vector3(0, 0.24f, 0.17f), new Vector3(0.9f, 0.2f, 0.08f), SandStone);
            Prim.Box(t, new Vector3(0, 0.24f, -0.17f), new Vector3(0.9f, 0.2f, 0.08f), SandStone);
            Prim.Box(t, new Vector3(0, 0.2f, 0), new Vector3(0.88f, 0.04f, 0.26f), Palette.Water);
            Rot(Prim.Cyl(t, new Vector3(0, 0.55f, 0), 0.22f, 0.8f, TerraC), 0, 0, 90);
            Rot(Prim.Cyl(t, new Vector3(0.0f, 0.55f, 0), 0.26f, 0.08f, Palette.Darken(TerraC, 0.15f)), 0, 0, 90);
        }

        private static void Betyl(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.08f, 0), new Vector3(0.7f, 0.16f, 0.5f), Palette.Darken(SandStone, 0.1f));
            Prim.Box(t, new Vector3(0, 0.55f, 0), new Vector3(0.36f, 0.78f, 0.28f), SandStone);
            Prim.Box(t, new Vector3(0, 0.55f, -0.15f), new Vector3(0.12f, 0.12f, 0.02f), Palette.Darken(SandStone, 0.25f));
        }

        private static void Urn(Transform t)
        {
            Prim.Cyl(t, new Vector3(0, 0.08f, 0), 0.42f, 0.16f, SandStone);
            Prim.Cyl(t, new Vector3(0, 0.2f, 0), 0.22f, 0.1f, SandStone);
            Prim.Create(PrimitiveType.Sphere, t, new Vector3(0, 0.46f, 0), new Vector3(0.56f, 0.5f, 0.56f), SandStone);
            Prim.Cyl(t, new Vector3(0, 0.72f, 0), 0.3f, 0.08f, Palette.Darken(SandStone, 0.12f));
            Prim.Cone(t, new Vector3(0, 0.76f, 0), 0.3f, 0.22f, SandStone);
            for (int i = 0; i < 4; i++) Prim.Ball(t, new Vector3(Mathf.Cos(i * 1.7f) * 0.2f, 0.4f + i * 0.06f, -0.24f), 0.04f, Palette.Darken(SandStone, 0.35f));
        }

        // ================================================================ Tac Mahal
        private static void PietraDura(Transform t)
        {
            Stand(t, MarbleC);
            var p = Prim.Box(t, new Vector3(0, 0.48f, 0), new Vector3(0.75f, 0.85f, 0.08f), Palette.White).transform;
            Color[] gems = { Palette.Coral, Palette.Hex("#3E7CB1"), Jade, GoldC, Palette.Lilac };
            for (int i = 0; i < 9; i++)
            {
                float a = i / 9f * Mathf.PI * 2;
                Prim.Ball(t, new Vector3(Mathf.Cos(a) * 0.2f, 0.5f + Mathf.Sin(a) * 0.25f, -0.05f), 0.08f, gems[i % gems.Length]);
            }
            Prim.Ball(t, new Vector3(0, 0.5f, -0.05f), 0.1f, GoldC);
            Prim.Box(t, new Vector3(0, 0.2f, -0.05f), new Vector3(0.03f, 0.3f, 0.02f), Jade);
            p.name = "panel";
        }

        private static void Plaque(Transform t)
        {
            Stand(t, MarbleC);
            Prim.Box(t, new Vector3(0, 0.5f, 0), new Vector3(0.85f, 0.75f, 0.08f), Palette.White);
            for (int r = 0; r < 4; r++)
                for (int c = 0; c < 5; c++)
                {
                    float h = 0.05f + 0.01f * r; // yukarı satırlarda harfler büyür
                    Prim.Box(t, new Vector3(-0.3f + c * 0.15f, 0.27f + r * 0.15f, -0.05f), new Vector3(0.09f, h, 0.02f), Palette.Text);
                }
        }

        private static void SealRing(Transform t)
        {
            Prim.Cyl(t, new Vector3(0, 0.05f, 0), 0.5f, 0.1f, Palette.Hex("#7A4B6A"));
            UprightRing(t, new Vector3(0, 0.42f, 0), 0.22f, 0.3f, 0.12f, GoldC);
            Prim.Box(t, new Vector3(0, 0.75f, 0), new Vector3(0.26f, 0.12f, 0.2f), GoldC);
            Prim.Box(t, new Vector3(0, 0.82f, 0), new Vector3(0.2f, 0.05f, 0.15f), Palette.Coral);
        }

        private static void Jali(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.05f, 0), new Vector3(0.9f, 0.1f, 0.2f), MarbleC);
            Prim.Box(t, new Vector3(0, 0.94f, 0), new Vector3(0.9f, 0.08f, 0.14f), MarbleC);
            Prim.Box(t, new Vector3(-0.42f, 0.5f, 0), new Vector3(0.06f, 0.82f, 0.14f), MarbleC);
            Prim.Box(t, new Vector3(0.42f, 0.5f, 0), new Vector3(0.06f, 0.82f, 0.14f), MarbleC);
            for (int i = -3; i <= 3; i++)
            {
                Rot(Prim.Box(t, new Vector3(i * 0.11f, 0.5f, 0), new Vector3(0.025f, 0.95f, 0.06f), Palette.White), 0, 0, 40);
                Rot(Prim.Box(t, new Vector3(i * 0.11f, 0.5f, 0), new Vector3(0.025f, 0.95f, 0.06f), Palette.White), 0, 0, -40);
            }
        }

        private static void GardenPlan(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.06f, 0), new Vector3(0.95f, 0.12f, 0.95f), MarbleC);
            for (int q = 0; q < 4; q++)
            {
                float x = q % 2 == 0 ? -0.24f : 0.24f, z = q < 2 ? -0.24f : 0.24f;
                Prim.Box(t, new Vector3(x, 0.14f, z), new Vector3(0.38f, 0.06f, 0.38f), Palette.Grass);
                Prim.Cone(t, new Vector3(x, 0.16f, z), 0.12f, 0.3f, Palette.Hex("#4E9A5A"));
            }
            Prim.Box(t, new Vector3(0, 0.13f, 0), new Vector3(0.94f, 0.05f, 0.08f), Palette.Water);
            Prim.Box(t, new Vector3(0, 0.13f, 0), new Vector3(0.08f, 0.05f, 0.94f), Palette.Water);
            Prim.Cyl(t, new Vector3(0, 0.15f, 0), 0.2f, 0.04f, Palette.Water);
            Prim.Box(t, new Vector3(0, 0.3f, 0.44f), new Vector3(0.3f, 0.3f, 0.06f), Palette.White);
        }

        private static void Finial(Transform t)
        {
            Prim.Cyl(t, new Vector3(0, 0.05f, 0), 0.36f, 0.1f, MarbleC);
            Prim.Ball(t, new Vector3(0, 0.2f, 0), 0.24f, GoldC);
            Prim.Ball(t, new Vector3(0, 0.38f, 0), 0.18f, GoldC);
            Prim.Ball(t, new Vector3(0, 0.52f, 0), 0.13f, GoldC);
            Prim.Cyl(t, new Vector3(0, 0.68f, 0), 0.05f, 0.25f, GoldC);
            UprightRing(t, new Vector3(0, 0.88f, 0), 0.08f, 0.13f, 0.04f, GoldC, 200f);
        }

        // ================================================================ Çin Seddi
        private static void StampedBrick(Transform t)
        {
            Rot(Prim.Box(t, new Vector3(0, 0.25f, 0), new Vector3(0.8f, 0.36f, 0.4f), Brick), 0, 15, 0);
            Prim.Box(t, new Vector3(0, 0.25f, -0.2f), new Vector3(0.3f, 0.18f, 0.04f), Palette.Darken(Brick, 0.3f));
            Rot(Prim.Box(t, new Vector3(0.05f, 0.6f, 0.05f), new Vector3(0.7f, 0.3f, 0.36f), Palette.Lighten(Brick, 0.15f)), 0, -10, 0);
        }

        private static void Beacon(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.05f, 0), new Vector3(0.8f, 0.1f, 0.4f), StoneGrey);
            for (int i = -1; i <= 1; i++)
            {
                Prim.Cyl(t, new Vector3(i * 0.26f, 0.32f, 0), 0.18f, 0.45f, Brick);
                var f = Prim.Cone(t, new Vector3(i * 0.26f, 0.55f, 0), 0.16f, 0.3f, Palette.Hex("#F29A3E"));
                f.GetComponent<MeshRenderer>().sharedMaterial = MaterialLibrary.Get(Palette.Hex("#F29A3E"), 0.9f);
                Prim.Ball(t, new Vector3(i * 0.26f, 0.95f, 0), 0.18f, new Color(0.92f, 0.92f, 0.92f, 0.6f));
            }
        }

        private static void CrossbowTrigger(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.05f, 0), new Vector3(0.6f, 0.1f, 0.4f), Wood);
            Prim.Box(t, new Vector3(0, 0.38f, 0), new Vector3(0.18f, 0.55f, 0.32f), Bronze);
            Rot(Prim.Box(t, new Vector3(0.05f, 0.75f, 0), new Vector3(0.08f, 0.25f, 0.1f), BronzeDark), 0, 0, -20);
            Rot(Prim.Box(t, new Vector3(0, 0.1f + 0.02f, 0.05f), new Vector3(0.06f, 0.35f, 0.08f), BronzeDark), 0, 0, 25);
            Rot(Prim.Cyl(t, new Vector3(0, 0.45f, 0), 0.08f, 0.4f, GoldC), 90, 0, 0);
        }

        private static void PassTablet(Transform t)
        {
            Stand(t, Wood);
            Rot(Prim.Box(t, new Vector3(0, 0.48f, 0), new Vector3(0.38f, 0.85f, 0.06f), Palette.Hex("#C9A06C")), 0, 0, -6);
            for (int i = 0; i < 5; i++) Prim.Box(t, new Vector3(0, 0.75f - i * 0.11f, -0.04f), new Vector3(0.05f, 0.07f, 0.02f), Palette.Text);
            Disc(t, new Vector3(0, 0.2f, -0.05f), 0.16f, 0.03f, Palette.Hex("#C8453A"));
        }

        private static void EarthMold(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.3f, 0.3f), new Vector3(0.9f, 0.6f, 0.06f), Wood);
            Prim.Box(t, new Vector3(0, 0.3f, -0.3f), new Vector3(0.9f, 0.6f, 0.06f), Wood);
            Color[] layers = { Palette.Hex("#C4A27A"), Palette.Hex("#B38F67"), Palette.Hex("#CFB08A"), Palette.Hex("#A98458") };
            for (int i = 0; i < 4; i++) Prim.Box(t, new Vector3(0, 0.07f + i * 0.13f, 0), new Vector3(0.85f, 0.12f, 0.54f), layers[i]);
            Prim.Cyl(t, new Vector3(0.25f, 0.85f, 0), 0.06f, 0.5f, Wood);
            Prim.Cyl(t, new Vector3(0.25f, 0.6f, 0), 0.18f, 0.08f, Wood);
        }

        private static void DoorKnocker(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.5f, 0.08f), new Vector3(0.8f, 0.95f, 0.1f), Palette.Hex("#C8453A"));
            Disc(t, new Vector3(0, 0.6f, 0), 0.34f, 0.06f, Bronze);
            Prim.Ball(t, new Vector3(-0.07f, 0.65f, -0.05f), 0.06f, BronzeDark);
            Prim.Ball(t, new Vector3(0.07f, 0.65f, -0.05f), 0.06f, BronzeDark);
            UprightRing(t, new Vector3(0, 0.36f, -0.06f), 0.11f, 0.16f, 0.04f, GoldC);
            for (int i = 0; i < 3; i++) for (int j = 0; j < 2; j++) Prim.Ball(t, new Vector3(-0.28f + i * 0.28f, 0.2f + j * 0.7f, 0.02f), 0.07f, GoldC);
        }

        // ================================================================ Machu Picchu
        private static void Quipu(Transform t)
        {
            Prim.Box(t, new Vector3(-0.45f, 0.45f, 0), new Vector3(0.05f, 0.9f, 0.05f), Wood);
            Prim.Box(t, new Vector3(0.45f, 0.45f, 0), new Vector3(0.05f, 0.9f, 0.05f), Wood);
            Rot(Prim.Cyl(t, new Vector3(0, 0.88f, 0), 0.05f, 0.95f, Palette.Hex("#D9B98A")), 0, 0, 90);
            Color[] cords = { Palette.Coral, Palette.Gold, Palette.Turquoise, Palette.Green, Palette.Hex("#D9B98A"), Palette.Lilac, Palette.Coral };
            for (int i = 0; i < 7; i++)
            {
                float x = -0.33f + i * 0.11f; float len = 0.4f + (i * 37 % 5) * 0.06f;
                Prim.Cyl(t, new Vector3(x, 0.86f - len * 0.5f, 0), 0.025f, len, cords[i]);
                for (int k = 0; k < 1 + i % 3; k++) Prim.Ball(t, new Vector3(x, 0.8f - 0.1f - k * 0.1f, 0), 0.05f, Palette.Darken(cords[i], 0.2f));
            }
        }

        private static void Intihuatana(Transform t)
        {
            Prim.Create(PrimitiveType.Sphere, t, new Vector3(0, 0.15f, 0), new Vector3(0.95f, 0.35f, 0.8f), StoneGrey);
            Prim.Box(t, new Vector3(0, 0.36f, 0), new Vector3(0.7f, 0.18f, 0.5f), StoneGrey);
            Prim.Box(t, new Vector3(0.1f, 0.5f, 0), new Vector3(0.35f, 0.12f, 0.35f), Palette.Lighten(StoneGrey, 0.1f));
            Rot(Prim.Box(t, new Vector3(0.1f, 0.75f, 0), new Vector3(0.16f, 0.42f, 0.16f), Palette.Lighten(StoneGrey, 0.15f)), 0, 30, 0);
        }

        private static void Tumi(Transform t)
        {
            Stand(t, Wood);
            var blade = Prim.FromMesh(MeshFactory.Ring(0.0f, 0.32f, 180, 20), t, new Vector3(0, 0.12f, 0.03f), new Vector3(1, 0.05f, 1), GoldC, "blade");
            blade.transform.localRotation = Quaternion.Euler(90, 0, 180);
            blade.transform.localPosition = new Vector3(0, 0.36f, 0.025f);
            Prim.Box(t, new Vector3(0, 0.55f, 0), new Vector3(0.1f, 0.36f, 0.06f), GoldC);
            Prim.Box(t, new Vector3(0, 0.78f, 0), new Vector3(0.3f, 0.16f, 0.08f), GoldC);
            Prim.Ball(t, new Vector3(0, 0.92f, 0), 0.16f, GoldC);
            Prim.Ball(t, new Vector3(-0.05f, 0.94f, -0.07f), 0.04f, Palette.Turquoise);
            Prim.Ball(t, new Vector3(0.05f, 0.94f, -0.07f), 0.04f, Palette.Turquoise);
        }

        private static void Aryballo(Transform t)
        {
            var c = Prim.Cone(t, new Vector3(0, 0.25f, 0), 0.32f, 0.25f, TerraC);
            c.transform.localRotation = Quaternion.Euler(180, 0, 0);
            Prim.Create(PrimitiveType.Sphere, t, new Vector3(0, 0.42f, 0), new Vector3(0.62f, 0.55f, 0.62f), TerraC);
            Prim.Cyl(t, new Vector3(0, 0.38f, 0), 0.63f, 0.06f, Palette.Hex("#F1DDC4"));
            Prim.Cyl(t, new Vector3(0, 0.5f, 0), 0.6f, 0.04f, Palette.Text);
            Prim.Cyl(t, new Vector3(0, 0.78f, 0), 0.16f, 0.2f, TerraC);
            Prim.Cyl(t, new Vector3(0, 0.9f, 0), 0.28f, 0.05f, TerraC);
            Prim.Ball(t, new Vector3(0.3f, 0.45f, 0), 0.08f, TerraC);
            Prim.Ball(t, new Vector3(-0.3f, 0.45f, 0), 0.08f, TerraC);
        }

        private static void Chakitaqlla(Transform t)
        {
            Stand(t, StoneGrey);
            Rot(Prim.Cyl(t, new Vector3(0, 0.5f, 0), 0.06f, 0.95f, Wood), 0, 0, 8);
            Prim.Box(t, new Vector3(0.1f, 0.3f, 0), new Vector3(0.2f, 0.04f, 0.06f), Wood);
            Prim.Box(t, new Vector3(-0.02f, 0.1f, 0), new Vector3(0.08f, 0.16f, 0.12f), Bronze);
            Prim.Cyl(t, new Vector3(-0.05f, 0.88f, 0), 0.1f, 0.1f, Palette.Coral);
        }

        private static void Llama(Transform t)
        {
            Stand(t, StoneGrey);
            Rot(Prim.Capsule(t, new Vector3(0, 0.42f, 0), 0.26f, 0.55f, Silver), 0, 0, 90);
            Rot(Prim.Cyl(t, new Vector3(0.22f, 0.62f, 0), 0.1f, 0.35f, Silver), 0, 0, -15);
            Prim.Box(t, new Vector3(0.27f, 0.82f, 0), new Vector3(0.18f, 0.1f, 0.1f), Silver);
            Prim.Box(t, new Vector3(0.24f, 0.9f, 0.03f), new Vector3(0.03f, 0.08f, 0.03f), Silver);
            Prim.Box(t, new Vector3(0.24f, 0.9f, -0.03f), new Vector3(0.03f, 0.08f, 0.03f), Silver);
            for (int i = 0; i < 4; i++) Prim.Cyl(t, new Vector3(i < 2 ? 0.15f : -0.15f, 0.2f, i % 2 == 0 ? 0.07f : -0.07f), 0.06f, 0.3f, Silver);
            Prim.Ball(t, new Vector3(-0.3f, 0.48f, 0), 0.1f, Silver);
        }

        // ================================================================ Chichén Itzá
        private static void ChacMool(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.06f, 0), new Vector3(0.95f, 0.12f, 0.45f), StoneGrey);
            Rot(Prim.Box(t, new Vector3(-0.2f, 0.3f, 0), new Vector3(0.45f, 0.18f, 0.32f), StoneGrey), 0, 0, 25);
            Prim.Ball(t, new Vector3(-0.42f, 0.55f, -0.05f), 0.22f, StoneGrey);
            Rot(Prim.Box(t, new Vector3(0.2f, 0.32f, 0), new Vector3(0.12f, 0.4f, 0.3f), StoneGrey), 0, 0, -30);
            Prim.Box(t, new Vector3(0.38f, 0.2f, 0), new Vector3(0.12f, 0.3f, 0.3f), StoneGrey);
            Prim.Cyl(t, new Vector3(-0.05f, 0.45f, 0), 0.22f, 0.04f, Palette.Hex("#C9B28A"));
        }

        private static void SerpentHead(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.35f, 0), new Vector3(0.75f, 0.45f, 0.8f), StoneGrey);
            Prim.Box(t, new Vector3(0, 0.12f, -0.1f), new Vector3(0.7f, 0.16f, 0.65f), Palette.Darken(StoneGrey, 0.1f));
            Prim.Ball(t, new Vector3(-0.2f, 0.55f, -0.36f), 0.16f, Palette.Lighten(StoneGrey, 0.2f));
            Prim.Ball(t, new Vector3(0.2f, 0.55f, -0.36f), 0.16f, Palette.Lighten(StoneGrey, 0.2f));
            for (int i = -1; i <= 1; i += 2)
            {
                var f = Prim.Cone(t, new Vector3(i * 0.18f, 0.24f, -0.38f), 0.08f, 0.16f, Palette.White);
                f.transform.localRotation = Quaternion.Euler(180, 0, 0);
            }
            Prim.Box(t, new Vector3(0, 0.62f, 0.1f), new Vector3(0.6f, 0.08f, 0.5f), Palette.Lighten(StoneGrey, 0.1f));
        }

        private static void Glyph(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.42f, 0), new Vector3(0.8f, 0.8f, 0.24f), StoneGrey);
            Prim.Box(t, new Vector3(0, 0.42f, -0.13f), new Vector3(0.62f, 0.62f, 0.04f), Palette.Lighten(StoneGrey, 0.12f));
            Prim.Ball(t, new Vector3(-0.12f, 0.52f, -0.16f), 0.2f, Palette.Darken(StoneGrey, 0.1f));
            Prim.Box(t, new Vector3(0.14f, 0.36f, -0.16f), new Vector3(0.22f, 0.3f, 0.04f), Palette.Darken(StoneGrey, 0.1f));
            for (int i = 0; i < 4; i++) Prim.Ball(t, new Vector3(-0.21f + i * 0.14f, 0.18f, -0.16f), 0.07f, Palette.Darken(StoneGrey, 0.15f));
        }

        private static void BallRing(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.15f, 0.1f), new Vector3(0.6f, 0.3f, 0.3f), StoneGrey);
            UprightRing(t, new Vector3(0, 0.6f, 0), 0.17f, 0.36f, 0.12f, Palette.Lighten(StoneGrey, 0.08f));
            Prim.Ball(t, new Vector3(0.0f, 0.3f, -0.35f), 0.2f, Palette.Hex("#3D3A38"));
        }

        private static void JadeNecklace(Transform t)
        {
            Stand(t, Wood);
            Prim.Box(t, new Vector3(0, 0.45f, 0.05f), new Vector3(0.08f, 0.8f, 0.08f), Wood);
            for (int i = 0; i <= 10; i++)
            {
                float a = Mathf.PI * (i / 10f);
                Prim.Ball(t, new Vector3(Mathf.Cos(a) * 0.3f, 0.85f - Mathf.Sin(a) * 0.35f, 0), 0.09f, Jade);
            }
            Prim.Box(t, new Vector3(0, 0.38f, -0.03f), new Vector3(0.16f, 0.2f, 0.06f), Palette.Darken(Jade, 0.15f));
        }

        private static void Censer(Transform t)
        {
            for (int i = 0; i < 3; i++)
            {
                float a = i * 120f * Mathf.Deg2Rad;
                Prim.Cyl(t, new Vector3(Mathf.Cos(a) * 0.18f, 0.12f, Mathf.Sin(a) * 0.18f), 0.07f, 0.24f, TerraC);
            }
            Prim.Cyl(t, new Vector3(0, 0.36f, 0), 0.55f, 0.26f, TerraC);
            Prim.Cyl(t, new Vector3(0, 0.5f, 0), 0.5f, 0.02f, Palette.Hex("#F4E9C9"));
            Prim.Ball(t, new Vector3(0, 0.66f, 0), 0.25f, new Color(0.95f, 0.95f, 0.95f, 0.55f));
            Prim.Ball(t, new Vector3(0.06f, 0.88f, 0), 0.18f, new Color(0.95f, 0.95f, 0.95f, 0.45f));
        }

        // ================================================================ Kurtarıcı İsa
        private static void MosaicTile(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.05f, 0), new Vector3(0.9f, 0.1f, 0.9f), Palette.Lighten(Soap, 0.3f));
            for (int r = 0; r < 4; r++)
                for (int c = 0; c < 4; c++)
                {
                    var p = Prim.FromMesh(MeshFactory.Prism(), t, new Vector3(-0.32f + c * 0.21f, 0.1f, -0.32f + r * 0.21f), new Vector3(0.18f, 0.06f, 0.18f), Palette.Lighten(Soap, (r + c) % 3 * 0.1f), "tri");
                    p.transform.localRotation = Quaternion.Euler(-90, (r + c) % 2 == 0 ? 0 : 180, 0);
                }
        }

        private static void PlasterHand(Transform t)
        {
            Prim.Box(t, new Vector3(0, 0.06f, 0), new Vector3(0.6f, 0.12f, 0.4f), Wood);
            Prim.Box(t, new Vector3(0, 0.4f, 0), new Vector3(0.36f, 0.5f, 0.12f), Palette.White);
            for (int i = 0; i < 4; i++) Prim.Capsule(t, new Vector3(-0.13f + i * 0.087f, 0.8f, 0), 0.08f, 0.32f - Mathf.Abs(i - 1.5f) * 0.05f, Palette.White);
            Rot(Prim.Capsule(t, new Vector3(0.24f, 0.5f, 0), 0.09f, 0.28f, Palette.White), 0, 0, -40);
        }

        private static void Drawing(Transform t)
        {
            Prim.Box(t, new Vector3(-0.25f, 0.45f, 0.1f), new Vector3(0.05f, 0.9f, 0.05f), Wood);
            Prim.Box(t, new Vector3(0.25f, 0.45f, 0.1f), new Vector3(0.05f, 0.9f, 0.05f), Wood);
            Rot(Prim.Box(t, new Vector3(0, 0.55f, 0.05f), new Vector3(0.7f, 0.65f, 0.03f), Palette.Hex("#F6F0E2")), -8, 0, 0);
            var ink = Palette.Hex("#3E6FA8");
            Prim.Box(t, new Vector3(0, 0.55f, 0.02f), new Vector3(0.04f, 0.45f, 0.02f), ink);
            Prim.Box(t, new Vector3(0, 0.66f, 0.02f), new Vector3(0.5f, 0.04f, 0.02f), ink);
            Prim.Box(t, new Vector3(0, 0.3f, 0.02f), new Vector3(0.16f, 0.06f, 0.02f), ink);
        }

        private static void LightningRod(Transform t)
        {
            var copper = Palette.Hex("#C27A4A");
            Prim.Cyl(t, new Vector3(0, 0.05f, 0), 0.4f, 0.1f, StoneGrey);
            Prim.Cyl(t, new Vector3(0, 0.5f, 0), 0.05f, 0.85f, copper);
            Prim.Cone(t, new Vector3(0, 0.92f, 0), 0.08f, 0.12f, copper);
            for (int i = 0; i < 4; i++)
            {
                var c = Prim.Cone(t, new Vector3(0, 0.75f, 0), 0.05f, 0.18f, copper);
                c.transform.localRotation = Quaternion.Euler(0, i * 90, 50);
            }
            Rot(Prim.Box(t, new Vector3(0.12f, 0.45f, 0), new Vector3(0.03f, 0.2f, 0.03f), Palette.Gold), 0, 0, 30);
        }

        private static void ConcreteSample(Transform t)
        {
            var conc = Palette.Hex("#C7C3BB");
            Prim.Cyl(t, new Vector3(0, 0.3f, 0), 0.5f, 0.6f, conc);
            Prim.Cyl(t, new Vector3(0, 0.62f, 0), 0.5f, 0.04f, Palette.Lighten(conc, 0.15f));
            for (int i = 0; i < 4; i++)
            {
                float a = (i * 90 + 45) * Mathf.Deg2Rad;
                Prim.Cyl(t, new Vector3(Mathf.Cos(a) * 0.12f, 0.75f, Mathf.Sin(a) * 0.12f), 0.04f, 0.35f, Palette.Hex("#8A6A55"));
            }
        }
    }
}
