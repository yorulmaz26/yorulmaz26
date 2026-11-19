using System;
using System.Collections.Generic;
using UnityEngine;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>
    /// Gezilebilir 3D Dünya Müzesi: mermer atrium (sütunlar, cam kubbe, bronz detaylar, çeşme),
    /// yedi galeri (her biri 6 vitrin) ve müzenin içindeki Kişisel Kâşif Odası kanadı.
    /// Bütün sergiler her Enter()'da kayıttan yeniden senkronlanır.
    /// GEÇİCİ VARLIK: prosedürel primitif mimari; final müze modeliyle değiştirilecek.
    /// </summary>
    public sealed class MuseumView : ViewBase
    {
        public const int RoomSlot = 7;
        private const float AtriumHalf = 9f;
        private const float GalleryDist = 22f;
        private const float GallerySize = 10f;
        private const float RoomSize = 12f;

        private static readonly Color MarbleFloor = Palette.Hex("#F1ECE2");
        private static readonly Color MarbleInlay = Palette.Hex("#E2D8C5");
        private static readonly Color WallC = Palette.Hex("#F7F3EB");
        private static readonly Color BronzeC = Palette.Hex("#B8834A");
        private static readonly Color GlassC = new Color(0.75f, 0.9f, 0.95f, 0.22f);

        private Transform _walk, _static, _galleriesRoot, _roomRoot, _roomContent, _inspectStage, _inspectHolder;
        private GameObject _roomFloor, _roomCorridor, _roomDoor;
        private ExplorerCharacter _explorer;
        private ExplorerMovementController _mover;
        private readonly Transform[,] _caseHolders = new Transform[GameConfig.WonderCount, GameConfig.ArtifactsPerWonder];
        private readonly GameObject[] _plaques = new GameObject[GameConfig.WonderCount];
        private readonly Dictionary<string, Transform> _exhibitAnchors = new Dictionary<string, Transform>();
        private bool _roomOpenBuilt;
        private bool _navBuilt;
        private bool _placed;
        private Font _font;

        /// <summary>Etkileşim kapalıyken (ör. eser inceleme) dünya dokunuşları yok sayılır.</summary>
        public bool InteractionEnabled = true;
        /// <summary>Odadaki bir sergiye varıldığında; null ise varsayılan bilgi kartı gösterilir.</summary>
        public Action<RoomExhibit> ExhibitTapped;

        public bool IsRoomUnlocked { get { return Session.Room.IsUnlocked; } }

        // =================================================================== kurulum
        protected override void Build()
        {
            _font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
            _walk = Prim.Group("Walkable", transform);
            _static = Prim.Group("Static", transform);
            _galleriesRoot = Prim.Group("Galleries", transform);
            PlaceholderAsset.Mark(gameObject, "prosedürel müze mimarisi (atrium, 7 galeri, kâşif odası)");

            BuildAtrium();
            for (int w = 0; w < GameConfig.WonderCount; w++) BuildGallery(w);
            BuildRoomShell();
            BuildInspectStage();

            _explorer = ExplorerCharacter.Create(transform);
            _mover = ExplorerMovementController.Attach(_explorer);
        }

        private static Vector3 SlotDir(int slot) { float a = slot * 45f * Mathf.Deg2Rad; return new Vector3(Mathf.Sin(a), 0, Mathf.Cos(a)); }
        private static Quaternion SlotRot(int slot) { return Quaternion.Euler(0, slot * 45f, 0); }

        private GameObject Floor(Transform parent, Vector3 localPos, Vector3 size, Color c, string name)
        {
            var f = Prim.Box(parent, localPos, size, c, true);
            f.name = name;
            f.AddComponent<WalkableGround>();
            return f;
        }

        private void BuildAtrium()
        {
            Floor(_walk, new Vector3(0, -0.1f, 0), new Vector3(AtriumHalf * 2, 0.2f, AtriumHalf * 2), MarbleFloor, "AtriumFloor");
            Prim.Cyl(_static, new Vector3(0, 0.01f, 0), 15f, 0.02f, MarbleInlay);
            Prim.Cyl(_static, new Vector3(0, 0.02f, 0), 13.4f, 0.02f, MarbleFloor);
            Prim.Cyl(_static, new Vector3(0, 0.03f, 0), 7f, 0.02f, Palette.Lighten(Palette.Gold, 0.55f));

            // Sütunlar (koridor açılarının arasında)
            for (int i = 0; i < 8; i++)
            {
                float a = (i * 45f + 22.5f) * Mathf.Deg2Rad;
                var p = new Vector3(Mathf.Sin(a), 0, Mathf.Cos(a)) * 8.2f;
                Prim.Cyl(_static, p + new Vector3(0, 0.15f, 0), 1.1f, 0.3f, MarbleInlay);
                Prim.Cyl(_static, p + new Vector3(0, 2.8f, 0), 0.75f, 5.2f, Palette.Marble);
                Prim.Box(_static, p + new Vector3(0, 5.5f, 0), new Vector3(1.1f, 0.3f, 1.1f), BronzeC);
            }
            // Bronz halka + cam kubbe
            Prim.FromMesh(MeshFactory.Ring(8.0f, 8.9f, 360, 48), _static, new Vector3(0, 5.6f, 0), new Vector3(1, 0.45f, 1), BronzeC, "DomeRing");
            Prim.FromMesh(MeshFactory.Dome(24, 8), _static, new Vector3(0, 6.05f, 0), new Vector3(17.6f, 9f, 17.6f), GlassC, "GlassDome");
            Prim.Ball(_static, new Vector3(0, 10.6f, 0), 0.7f, Palette.Gold);

            // Çeşme ve bronz küre
            Prim.Cyl(_static, new Vector3(0, 0.3f, 0), 4.2f, 0.6f, Palette.Marble);
            Prim.Cyl(_static, new Vector3(0, 0.58f, 0), 3.7f, 0.06f, Palette.Water);
            Prim.Cyl(_static, new Vector3(0, 1.3f, 0), 0.7f, 1.6f, Palette.Marble);
            Prim.Ball(_static, new Vector3(0, 2.6f, 0), 1.4f, BronzeC);
            Prim.FromMesh(MeshFactory.Ring(0.72f, 0.82f, 360, 32), _static, new Vector3(0, 2.55f, 0), new Vector3(1, 0.1f, 1), Palette.Gold, "Equator");
            Label3D(_static, "DÜNYA MÜZESİ", new Vector3(0, 0.62f, -2.3f), Quaternion.Euler(70, 0, 0), 0.07f, Palette.Text);
        }

        private void BuildGallery(int w)
        {
            var info = WonderCatalog.Get(w);
            var accent = Palette.Hex(info.ThemeColor);
            var dir = SlotDir(w);
            var rot = SlotRot(w);

            // Koridor
            var corr = Prim.Group("Corridor" + w, _walk);
            corr.localPosition = dir * ((AtriumHalf + GalleryDist - GallerySize * 0.5f) * 0.5f);
            corr.localRotation = rot;
            float corrLen = GalleryDist - GallerySize * 0.5f - AtriumHalf + 1f;
            Floor(corr, new Vector3(0, -0.1f, 0), new Vector3(3.2f, 0.2f, corrLen), MarbleFloor, "CorridorFloor");
            Prim.Box(corr, new Vector3(0, 0.005f, 0), new Vector3(1.2f, 0.02f, corrLen), Palette.Lighten(accent, 0.45f));

            var g = Prim.Group("Gallery_" + info.Id, _galleriesRoot);
            g.localPosition = dir * GalleryDist;
            g.localRotation = rot;
            var floorGo = Floor(g, new Vector3(0, -0.1f, 0), new Vector3(GallerySize, 0.2f, GallerySize), MarbleFloor, "GalleryFloor");
            floorGo.transform.SetParent(_walk, true); // yürünebilir kök altında
            Prim.Box(g, new Vector3(0, 0.005f, 0), new Vector3(GallerySize - 1.2f, 0.02f, GallerySize - 1.2f), Palette.Lighten(accent, 0.6f));

            // Alçak duvarlar (izometrik kamerada içerisi görünsün)
            float h = 1.4f, half = GallerySize * 0.5f;
            Prim.Box(g, new Vector3(0, h * 0.5f, half), new Vector3(GallerySize, h, 0.3f), WallC);
            Prim.Box(g, new Vector3(-half, h * 0.5f, 0), new Vector3(0.3f, h, GallerySize), WallC);
            Prim.Box(g, new Vector3(half, h * 0.5f, 0), new Vector3(0.3f, h, GallerySize), WallC);
            Prim.Box(g, new Vector3(-half + 1.6f, h * 0.5f, -half), new Vector3(3.2f, h, 0.3f), WallC);
            Prim.Box(g, new Vector3(half - 1.6f, h * 0.5f, -half), new Vector3(3.2f, h, 0.3f), WallC);
            Prim.Box(g, new Vector3(0, h + 0.05f, half), new Vector3(GallerySize + 0.2f, 0.1f, 0.4f), BronzeC);

            // Pankart ve galeri rozeti plaketi
            Prim.Box(g, new Vector3(0, 2.4f, half - 0.05f), new Vector3(5.6f, 1.1f, 0.15f), accent);
            Label3D(g, info.Name.ToUpperInvariant(), new Vector3(0, 2.4f, half - 0.15f), Quaternion.identity, 0.09f, Palette.White);
            Prim.Box(g, new Vector3(-3.2f, 1.6f, half - 0.05f), new Vector3(0.12f, 1.8f, 0.12f), BronzeC);
            Prim.Box(g, new Vector3(3.2f, 1.6f, half - 0.05f), new Vector3(0.12f, 1.8f, 0.12f), BronzeC);
            var plaque = Prim.Cyl(g, new Vector3(0, 0.9f, half - 0.2f), 0.9f, 0.08f, Palette.Locked);
            plaque.transform.localRotation = Quaternion.Euler(90, 0, 0);
            plaque.name = "GalleryBadge";
            _plaques[w] = plaque;

            // 6 vitrin
            for (int k = 0; k < GameConfig.ArtifactsPerWonder; k++)
            {
                var art = info.Artifacts[k];
                float x = -3f + (k % 3) * 3f, z = k < 3 ? 2.2f : -1.4f;
                var sc = Prim.Group("Showcase_" + art.Id, g, new Vector3(x, 0, z));
                var ped = Prim.Box(sc, new Vector3(0, 0.45f, 0), new Vector3(1.2f, 0.9f, 1.2f), Palette.Marble, true);
                Prim.Box(sc, new Vector3(0, 0.92f, 0), new Vector3(1.3f, 0.06f, 1.3f), BronzeC);
                Prim.Box(sc, new Vector3(0, 0.3f, -0.61f), new Vector3(0.9f, 0.18f, 0.02f), accent);
                Prim.Box(sc, new Vector3(0, 1.62f, 0), new Vector3(1.1f, 1.35f, 1.1f), GlassC);
                Prim.Box(sc, new Vector3(0, 2.32f, 0), new Vector3(1.16f, 0.06f, 1.16f), BronzeC);
                var holder = Prim.Group("Model", sc, new Vector3(0, 0.98f, 0));
                holder.localScale = Vector3.one * 0.85f;
                _caseHolders[w, k] = holder;

                var approach = Prim.Group("Approach", sc, new Vector3(0, 0, -1.4f));
                var tap = ped.AddComponent<Tappable>();
                tap.ApproachPoint = approach;
                tap.WalkToFirst = true;
                var captured = art;
                tap.OnArrive = () => OnArtifactArrive(captured);
            }
        }

        private void BuildRoomShell()
        {
            var dir = SlotDir(RoomSlot);
            var rot = SlotRot(RoomSlot);
            _roomRoot = Prim.Group("PersonalRoom", transform);
            _roomRoot.localPosition = dir * (GalleryDist + 1f);
            _roomRoot.localRotation = rot;

            float corrLen = GalleryDist + 1f - RoomSize * 0.5f - AtriumHalf + 1f;
            var corrGo = Prim.Box(_static, dir * ((AtriumHalf + GalleryDist + 1f - RoomSize * 0.5f) * 0.5f) + new Vector3(0, -0.1f, 0), new Vector3(3.2f, 0.2f, corrLen), MarbleFloor, true);
            corrGo.transform.localRotation = rot;
            corrGo.name = "RoomCorridorFloor";
            _roomCorridor = corrGo;
            _roomFloor = Prim.Box(_roomRoot, new Vector3(0, -0.1f, 0), new Vector3(RoomSize, 0.2f, RoomSize), Palette.Hex("#EFE4D0"), true);
            _roomFloor.name = "RoomFloor";
            _roomFloor.transform.SetParent(_static, true);

            // Halı ve duvarlar (sıcak ahşap/krem tonlar)
            Prim.Box(_roomRoot, new Vector3(0, 0.005f, 0), new Vector3(7f, 0.02f, 5f), Palette.Lighten(Palette.Turquoise, 0.55f));
            float h = 1.6f, half = RoomSize * 0.5f;
            var wood = Palette.Hex("#D7B48A");
            Prim.Box(_roomRoot, new Vector3(0, h * 0.5f, half), new Vector3(RoomSize, h, 0.3f), wood);
            Prim.Box(_roomRoot, new Vector3(-half, h * 0.5f, 0), new Vector3(0.3f, h, RoomSize), wood);
            Prim.Box(_roomRoot, new Vector3(half, h * 0.5f, 0), new Vector3(0.3f, h, RoomSize), wood);
            Prim.Box(_roomRoot, new Vector3(-half + 2.4f, h * 0.5f, -half), new Vector3(4.8f, h, 0.3f), wood);
            Prim.Box(_roomRoot, new Vector3(half - 2.4f, h * 0.5f, -half), new Vector3(4.8f, h, 0.3f), wood);
            Prim.Box(_roomRoot, new Vector3(0, 2.5f, half - 0.05f), new Vector3(5f, 0.9f, 0.15f), Palette.Turquoise);
            Label3D(_roomRoot, "KÂŞİF ODASI", new Vector3(0, 2.5f, half - 0.15f), Quaternion.identity, 0.09f, Palette.White);

            // Kilitli kapı
            _roomDoor = Prim.Box(_roomRoot, new Vector3(0, 1.1f, -half - 0.1f), new Vector3(2.4f, 2.2f, 0.25f), BronzeC, true);
            _roomDoor.name = "RoomDoor";
            Prim.Ball(_roomDoor.transform, new Vector3(0.3f, 0, -0.6f), 0.12f, Palette.Gold);
            var dt = _roomDoor.AddComponent<Tappable>();
            dt.OnTap = () => Game.UI.Info("Kâşif Odası kilitli", "Kişisel Kâşif Odası, Kolezyum 10. bölüm tamamlanınca açılır.");

            _roomContent = Prim.Group("RoomContent", _roomRoot);
        }

        private void BuildInspectStage()
        {
            _inspectStage = Prim.Group("InspectStage", transform, new Vector3(300f, 0f, 0f));
            Prim.Cyl(_inspectStage, new Vector3(0, -0.05f, 0), 9f, 0.1f, Palette.Cream);
            Prim.Box(_inspectStage, new Vector3(0, 2.5f, 3.5f), new Vector3(12f, 6f, 0.2f), Palette.Lighten(Palette.Sky, 0.5f));
            Prim.Cyl(_inspectStage, new Vector3(0, 0.15f, 0), 1.6f, 0.3f, Palette.Marble);
            Prim.Cyl(_inspectStage, new Vector3(0, 0.31f, 0), 1.7f, 0.04f, BronzeC);
            _inspectHolder = Prim.Group("Turntable", _inspectStage, new Vector3(0, 0.34f, 0));
        }

        private void Label3D(Transform parent, string text, Vector3 pos, Quaternion rot, float charSize, Color color)
        {
            var go = new GameObject("Label");
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            go.transform.localRotation = rot;
            var tm = go.AddComponent<TextMesh>();
            tm.text = text;
            tm.font = _font;
            tm.fontSize = 64;
            tm.characterSize = charSize;
            tm.anchor = TextAnchor.MiddleCenter;
            tm.alignment = TextAlignment.Center;
            tm.color = color;
            if (_font != null) go.GetComponent<MeshRenderer>().sharedMaterial = _font.material;
        }

        // =================================================================== senkron
        public override void Enter()
        {
            InteractionEnabled = true;
            HideInspect();
            RefreshGalleries();
            RefreshRoomLock();
            RefreshRoom();
            if (!_navBuilt) { RuntimeNavMesh.Build(_walk, new Bounds(Vector3.zero, new Vector3(80, 10, 80))); _navBuilt = true; }
            _explorer.ApplyLoadout(Session.Save.Equipped);
            _explorer.gameObject.SetActive(true);
            if (!_placed) { _mover.Warp(new Vector3(0, 0, -5f)); _placed = true; }
            _explorer.Play(ExplorerAnim.Idle);
            ConfigureCamera(_explorer.transform.position);
        }

        public override void Exit() { HideInspect(); }

        private void ConfigureCamera(Vector3 pivot)
        {
            var rig = Game.Rig;
            rig.AllowPan = true; rig.AllowZoom = true;
            rig.Configure(new Rect(-34, -34, 68, 68), 24f, 10f, 48f, 55f, 30f, pivot);
            rig.FollowTarget = _explorer.transform;
            rig.SetMode(CameraMode.Follow);
        }

        private void RefreshGalleries()
        {
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                var info = WonderCatalog.Get(w);
                for (int k = 0; k < GameConfig.ArtifactsPerWonder; k++)
                {
                    var holder = _caseHolders[w, k];
                    Prim.ClearChildren(holder);
                    ArtifactModelFactory.Build(info.Artifacts[k], holder, Session.Museum.IsCollected(info.Artifacts[k]));
                }
                bool done = Session.Museum.IsGalleryComplete(w);
                var r = _plaques[w].GetComponent<MeshRenderer>();
                r.sharedMaterial = done ? MaterialLibrary.Get(Palette.Gold, 0.6f) : MaterialLibrary.Get(Palette.Locked);
            }
        }

        private void RefreshRoomLock()
        {
            bool open = IsRoomUnlocked;
            _roomDoor.SetActive(!open);
            if (open == _roomOpenBuilt && _navBuilt) return;
            _roomOpenBuilt = open;
            // Kilitliyken oda yürünemez: zemin yürünebilir kök dışına alınır ve işaret kaldırılır.
            SetWalkable(_roomFloor, open);
            SetWalkable(_roomCorridor, open);
            if (_navBuilt) RuntimeNavMesh.Build(_walk, new Bounds(Vector3.zero, new Vector3(80, 10, 80)));
        }

        private void SetWalkable(GameObject go, bool walkable)
        {
            go.transform.SetParent(walkable ? _walk : _static, true);
            var wg = go.GetComponent<WalkableGround>();
            if (walkable && wg == null) go.AddComponent<WalkableGround>();
            if (!walkable && wg != null) Destroy(wg);
        }

        /// <summary>Oda sergilerini kayıttan yeniden kurar (maketler, rozetler, ekipman, harita, masa, ustalık, dekorasyon).</summary>
        public void RefreshRoom()
        {
            Prim.ClearChildren(_roomContent);
            _exhibitAnchors.Clear();
            if (!IsRoomUnlocked) return;
            var exhibits = Session.Room.Exhibits();
            int decoIndex = 0;
            foreach (var ex in exhibits)
            {
                switch (ex.Kind)
                {
                    case RoomExhibitKind.WonderModel: BuildMaquette(ex); break;
                    case RoomExhibitKind.BadgeCase: BuildBadgeCase(ex); break;
                    case RoomExhibitKind.ToolCase: BuildToolCase(ex); break;
                    case RoomExhibitKind.WorldMap: BuildMapTable(ex); break;
                    case RoomExhibitKind.StudyDesk: BuildDesk(ex); break;
                    case RoomExhibitKind.MasteryCorner: BuildMastery(ex); break;
                    case RoomExhibitKind.Decoration:
                        if (ex.Visible) BuildDecoration(ex, decoIndex);
                        decoIndex++;
                        break;
                }
            }
        }

        private Transform ExhibitRoot(RoomExhibit ex, Vector3 localPos, Vector3 approachLocal, GameObject colliderHost)
        {
            var t = colliderHost.transform.parent;
            var tap = colliderHost.AddComponent<Tappable>();
            var ap = Prim.Group("Approach", t, approachLocal);
            tap.ApproachPoint = ap;
            tap.WalkToFirst = true;
            var captured = ex;
            tap.OnArrive = () => OnExhibitArrive(captured);
            _exhibitAnchors[ex.Id] = t;
            return t;
        }

        private void BuildMaquette(RoomExhibit ex)
        {
            int w = -1;
            for (int i = 0; i < GameConfig.WonderCount; i++) if (ex.Id == "model_" + WonderCatalog.Get(i).Id) w = i;
            if (w < 0) return;
            var t = Prim.Group(ex.Id, _roomContent, new Vector3(-4.8f + w * 1.6f, 0, 4.6f));
            var plinth = Prim.Box(t, new Vector3(0, 0.4f, 0), new Vector3(1.2f, 0.8f, 1.2f), Palette.Marble, true);
            Prim.Box(t, new Vector3(0, 0.82f, 0), new Vector3(1.3f, 0.05f, 1.3f), Palette.Hex(WonderCatalog.Get(w).ThemeColor));
            var holder = Prim.Group("Maquette", t, new Vector3(0, 0.85f, 0));
            var m = MonumentBuilder.Build(w, holder);
            float s = 0.9f / Mathf.Max(1f, m.Height);
            holder.localScale = Vector3.one * s;
            m.SyncFrom(Session);
            if (ex.Progress <= 0f) Prim.Box(t, new Vector3(0, 0.95f, 0), new Vector3(0.6f, 0.2f, 0.6f), Palette.Locked);
            ExhibitRoot(ex, Vector3.zero, new Vector3(0, 0, -1.4f), plinth);
        }

        private void BuildBadgeCase(RoomExhibit ex)
        {
            var t = Prim.Group(ex.Id, _roomContent, new Vector3(-5.6f, 0, 0.5f));
            t.localRotation = Quaternion.Euler(0, 90, 0);
            var board = Prim.Box(t, new Vector3(0, 1.3f, 0), new Vector3(4.2f, 2.2f, 0.25f), Palette.Hex("#C59A6A"), true);
            Prim.Box(t, new Vector3(0, 1.3f, -0.14f), new Vector3(3.9f, 1.9f, 0.04f), Palette.Lighten(Palette.Sky, 0.6f));
            var badges = Session.Save.Badges;
            int n = Mathf.Min(badges.Count, 24);
            for (int i = 0; i < n; i++)
            {
                int col = i % 8, row = i / 8;
                var med = Prim.Cyl(t, new Vector3(-1.6f + col * 0.46f, 2.0f - row * 0.6f, -0.2f), 0.36f, 0.06f, BadgeColor(badges[i]));
                med.transform.localRotation = Quaternion.Euler(90, 0, 0);
                Prim.Ball(t, new Vector3(-1.6f + col * 0.46f, 2.0f - row * 0.6f, -0.24f), 0.14f, Palette.White);
            }
            if (n == 0) Prim.Box(t, new Vector3(0, 1.3f, -0.18f), new Vector3(1.4f, 0.1f, 0.02f), Palette.Locked);
            ExhibitRoot(ex, Vector3.zero, new Vector3(0, 0, -1.5f), board);
        }

        private static Color BadgeColor(string id)
        {
            if (id.StartsWith("badge_gallery_")) return Palette.Turquoise;
            if (id.StartsWith("badge_stars_")) return Palette.Gold;
            if (id.StartsWith("badge_complete_")) return Palette.Green;
            if (id.StartsWith("badge_streak_")) return Palette.Coral;
            return Palette.Lilac;
        }

        private void BuildToolCase(RoomExhibit ex)
        {
            var t = Prim.Group(ex.Id, _roomContent, new Vector3(-3.4f, 0, -2.2f));
            var table = Prim.Box(t, new Vector3(0, 0.45f, 0), new Vector3(2.6f, 0.9f, 1.1f), Palette.Hex("#C59A6A"), true);
            Prim.Box(t, new Vector3(0, 1.25f, 0), new Vector3(2.5f, 0.7f, 1.0f), GlassC);
            for (int i = 0; i < 3; i++)
            {
                var tool = (ToolType)i;
                int lvl = Session.Tools.Level(tool);
                var slot = Prim.Group("Tool" + i, t, new Vector3(-0.85f + i * 0.85f, 0.92f, 0));
                ToolModel(slot, tool, lvl);
                for (int p = 0; p < GameConfig.ToolMaxLevel; p++)
                    Prim.Ball(t, new Vector3(-0.85f + i * 0.85f - 0.15f + p * 0.15f, 0.92f, -0.42f), 0.1f, p < lvl ? Palette.Gold : Palette.Locked);
            }
            ExhibitRoot(ex, Vector3.zero, new Vector3(0, 0, -1.5f), table);
        }

        private static void ToolModel(Transform t, ToolType tool, int level)
        {
            var metal = level >= 3 ? Palette.Gold : level == 2 ? Palette.Hex("#C9CED6") : BronzeC;
            switch (tool)
            {
                case ToolType.Magnifier:
                    {
                        var ring = Prim.FromMesh(MeshFactory.Ring(0.16f, 0.21f, 360, 24), t, new Vector3(0, 0.05f, 0.05f), new Vector3(1, 0.05f, 1), metal, "lens");
                        Prim.Cyl(t, new Vector3(0, 0.06f, 0.05f), 0.32f, 0.02f, new Color(0.8f, 0.94f, 1f, 0.5f));
                        Prim.Box(t, new Vector3(0, 0.06f, -0.25f), new Vector3(0.07f, 0.06f, 0.28f), Palette.Hex("#8E6235"));
                        ring.name = "lens";
                        break;
                    }
                case ToolType.Brush:
                    Prim.Box(t, new Vector3(0, 0.05f, -0.12f), new Vector3(0.06f, 0.06f, 0.4f), Palette.Hex("#A9744B"));
                    Prim.Box(t, new Vector3(0, 0.05f, 0.1f), new Vector3(0.14f, 0.05f, 0.06f), metal);
                    Prim.Box(t, new Vector3(0, 0.06f, 0.2f), new Vector3(0.16f, 0.07f, 0.16f), Palette.Sand);
                    break;
                default:
                    Prim.Box(t, new Vector3(0, 0.05f, -0.2f), new Vector3(0.06f, 0.06f, 0.24f), Palette.Hex("#A9744B"));
                    Prim.Box(t, new Vector3(0, 0.03f, 0.12f), new Vector3(0.22f, 0.02f, 0.34f), metal);
                    break;
            }
        }

        private void BuildMapTable(RoomExhibit ex)
        {
            var t = Prim.Group(ex.Id, _roomContent, new Vector3(0, 0, 0.6f));
            var top = Prim.Box(t, new Vector3(0, 0.75f, 0), new Vector3(3f, 0.12f, 1.9f), Palette.Hex("#C59A6A"), true);
            for (int i = 0; i < 4; i++) Prim.Box(t, new Vector3(i % 2 == 0 ? -1.35f : 1.35f, 0.35f, i < 2 ? -0.8f : 0.8f), new Vector3(0.12f, 0.7f, 0.12f), Palette.Hex("#A9744B"));
            Prim.Box(t, new Vector3(0, 0.82f, 0), new Vector3(2.8f, 0.02f, 1.7f), Palette.Water);
            // Kıtalar (stilize)
            Prim.Box(t, new Vector3(-0.9f, 0.84f, 0.2f), new Vector3(0.7f, 0.02f, 0.9f), Palette.Grass);
            Prim.Box(t, new Vector3(0.1f, 0.84f, 0.35f), new Vector3(0.9f, 0.02f, 0.6f), Palette.Sand);
            Prim.Box(t, new Vector3(0.8f, 0.84f, 0.1f), new Vector3(0.7f, 0.02f, 0.7f), Palette.Grass);
            Vector3[] pins = { new Vector3(0.0f, 0, 0.45f), new Vector3(0.35f, 0, 0.2f), new Vector3(0.75f, 0, 0.15f), new Vector3(1.0f, 0, 0.4f), new Vector3(-0.85f, 0, -0.25f), new Vector3(-1.05f, 0, 0.35f), new Vector3(-0.7f, 0, -0.5f) };
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                var st = Session.WorldMap.StateOf(w);
                var c = st == RegionState.Completed ? Palette.Gold : st == RegionState.InProgress ? Palette.Turquoise : Palette.Locked;
                Prim.Cyl(t, pins[w] + new Vector3(0, 0.95f, 0), 0.04f, 0.22f, Palette.Text);
                Prim.Ball(t, pins[w] + new Vector3(0, 1.08f, 0), 0.14f, c);
            }
            ExhibitRoot(ex, Vector3.zero, new Vector3(0, 0, -1.5f), top);
        }

        private void BuildDesk(RoomExhibit ex)
        {
            var t = Prim.Group(ex.Id, _roomContent, new Vector3(3.4f, 0, -2.2f));
            var desk = Prim.Box(t, new Vector3(0, 0.75f, 0), new Vector3(2.4f, 0.1f, 1.1f), Palette.Hex("#B88658"), true);
            Prim.Box(t, new Vector3(-1f, 0.35f, 0), new Vector3(0.3f, 0.7f, 1f), Palette.Hex("#A9744B"));
            Prim.Box(t, new Vector3(1f, 0.35f, 0), new Vector3(0.3f, 0.7f, 1f), Palette.Hex("#A9744B"));
            Prim.Box(t, new Vector3(-0.3f, 0.81f, 0), new Vector3(0.8f, 0.02f, 0.6f), Palette.Hex("#F6F0E2"));
            Color[] books = { Palette.Coral, Palette.Turquoise, Palette.Gold, Palette.Green };
            for (int i = 0; i < 4; i++) Prim.Box(t, new Vector3(0.55f + i * 0.12f, 0.95f, 0.3f), new Vector3(0.1f, 0.32f, 0.36f), books[i]);
            Prim.Cyl(t, new Vector3(0.7f, 0.85f, -0.25f), 0.25f, 0.06f, BronzeC);
            Prim.Cyl(t, new Vector3(0.7f, 1.05f, -0.25f), 0.05f, 0.4f, BronzeC);
            Prim.Ball(t, new Vector3(0.7f, 1.3f, -0.25f), 0.25f, new Color(1f, 0.93f, 0.7f, 1f)).GetComponent<MeshRenderer>().sharedMaterial = MaterialLibrary.Get(new Color(1f, 0.93f, 0.7f), 0.7f);
            ExhibitRoot(ex, Vector3.zero, new Vector3(0, 0, -1.5f), desk);
        }

        private void BuildMastery(RoomExhibit ex)
        {
            var t = Prim.Group(ex.Id, _roomContent, new Vector3(4.6f, 0, 2.6f));
            bool active = Session.Xp.Current.Level >= GameConfig.MaxExplorerLevel;
            var ped = Prim.Cyl(t, new Vector3(0, 0.5f, 0), 1.0f, 1.0f, Palette.Marble, true);
            Prim.Cyl(t, new Vector3(0, 1.02f, 0), 1.1f, 0.06f, BronzeC);
            var star = Prim.Cone(t, new Vector3(0, 1.05f, 0), 0.6f, 0.9f, active ? Palette.Gold : Palette.Locked);
            if (active) star.GetComponent<MeshRenderer>().sharedMaterial = MaterialLibrary.Get(Palette.Gold, 0.5f);
            int m = Session.Xp.Current.Mastery;
            for (int i = 0; i < Mathf.Min(m, 10); i++)
            {
                float a = i * 36f * Mathf.Deg2Rad;
                Prim.Ball(t, new Vector3(Mathf.Cos(a) * 0.45f, 1.1f, Mathf.Sin(a) * 0.45f), 0.1f, Palette.Gold);
            }
            ExhibitRoot(ex, Vector3.zero, new Vector3(-1.0f, 0, -1.2f), ped);
        }

        private void BuildDecoration(RoomExhibit ex, int index)
        {
            Vector3[] spots = { new Vector3(-4.8f, 0, -4.6f), new Vector3(4.8f, 0, -4.6f), new Vector3(-4.8f, 0, 2.4f), new Vector3(5.2f, 0, 0.2f), new Vector3(-2.2f, 0, -4.8f), new Vector3(2.2f, 0, -4.8f) };
            var info = CosmeticCatalog.Resolve(ex.Id);
            var c = info != null && info.ColorHex != null ? Palette.Hex(info.ColorHex) : Palette.Lilac;
            var pos = index < spots.Length ? spots[index] : new Vector3(-4.6f + (index - spots.Length) * 0.9f % 9f, 0, -3.6f);
            var t = Prim.Group(ex.Id, _roomContent, pos);
            var basePed = Prim.Cyl(t, new Vector3(0, 0.25f, 0), 0.7f, 0.5f, Palette.Marble, true);
            switch (index % 3)
            {
                case 0: // vazo
                    Prim.Create(PrimitiveType.Sphere, t, new Vector3(0, 0.78f, 0), new Vector3(0.5f, 0.6f, 0.5f), c);
                    Prim.Cyl(t, new Vector3(0, 1.12f, 0), 0.22f, 0.2f, c);
                    break;
                case 1: // obelisk
                    Prim.Box(t, new Vector3(0, 1.0f, 0), new Vector3(0.3f, 1.0f, 0.3f), c);
                    Prim.Cone(t, new Vector3(0, 1.5f, 0), 0.42f, 0.3f, Palette.Gold);
                    break;
                default: // bitki saksısı
                    Prim.Cyl(t, new Vector3(0, 0.65f, 0), 0.5f, 0.3f, c);
                    Prim.Ball(t, new Vector3(0, 1.05f, 0), 0.7f, Palette.Green);
                    break;
            }
            ExhibitRoot(ex, Vector3.zero, new Vector3(0, 0, -1.0f), basePed);
        }

        // =================================================================== etkileşim
        private void Update()
        {
            if (!InteractionEnabled || Game == null || Game.CurrentView != this) return;
            if (Game.UI.HasPopup) return;
            if (!InputAdapter.Tap) return;
            Tappable tap; Vector3 ground; bool hitGround;
            if (!WorldPicker.Pick(Game.Cam, InputAdapter.Position, out tap, out ground, out hitGround)) return;
            if (tap != null)
            {
                if (tap.WalkToFirst)
                {
                    var target = tap.ApproachPoint != null ? tap.ApproachPoint.position : tap.transform.position;
                    var lookAt = tap.transform.position;
                    var captured = tap;
                    WalkTo(target, () =>
                    {
                        FaceTowards(lookAt);
                        _explorer.Play(ExplorerAnim.Interact);
                        if (captured.OnArrive != null) captured.OnArrive();
                    });
                }
                else if (tap.OnTap != null) tap.OnTap();
                return;
            }
            if (hitGround) WalkTo(ground, null);
        }

        private void WalkTo(Vector3 p, Action onArrive)
        {
            Game.Rig.FollowTarget = _explorer.transform;
            Game.Rig.SetMode(CameraMode.Follow);
            _mover.WalkTo(p, onArrive);
        }

        private void FaceTowards(Vector3 p)
        {
            var d = p - _explorer.transform.position; d.y = 0;
            if (d.sqrMagnitude > 0.001f) _explorer.transform.rotation = Quaternion.LookRotation(d);
        }

        private void OnArtifactArrive(ArtifactInfo a)
        {
            if (Session.Museum.IsCollected(a))
            {
                _explorer.Play(ExplorerAnim.Discover);
                Nav.ArtifactInspect(a);
            }
            else Game.UI.Toast("Henüz keşfedilmedi: " + WonderCatalog.Get(a.Wonder).Name + " alan " + a.Area, Palette.Turquoise);
        }

        private void OnExhibitArrive(RoomExhibit ex)
        {
            if (ExhibitTapped != null) ExhibitTapped(ex);
            else Game.UI.Info(ex.Title, string.IsNullOrEmpty(ex.Detail) ? "Kişisel koleksiyonunun bir parçası." : ex.Detail);
        }

        // =================================================================== gezinme yardımcıları
        public void EnterAtrium()
        {
            _mover.Warp(new Vector3(0, 0, -5f));
            ConfigureCamera(_explorer.transform.position);
        }

        public void EnterGallery(int w)
        {
            var dir = SlotDir(w);
            var p = dir * (GalleryDist - GallerySize * 0.5f + 1.2f);
            _mover.Warp(p);
            _explorer.transform.rotation = Quaternion.LookRotation(dir);
            ConfigureCamera(p);
            Game.Rig.FocusOn(dir * GalleryDist, 16f, 0.6f);
            Game.Rig.FollowTarget = _explorer.transform;
        }

        /// <summary>Odaya ışınlar; kilitliyse kapının önüne götürür ve false döner.</summary>
        public bool EnterPersonalRoom()
        {
            var dir = SlotDir(RoomSlot);
            if (!IsRoomUnlocked)
            {
                var p = dir * (AtriumHalf - 1.5f);
                _mover.Warp(p);
                ConfigureCamera(p);
                Game.Rig.FocusOn(dir * (GalleryDist + 1f - RoomSize * 0.5f), 16f, 0.6f);
                return false;
            }
            var inside = dir * (GalleryDist + 1f - RoomSize * 0.5f + 1.5f);
            _mover.Warp(inside);
            _explorer.transform.rotation = Quaternion.LookRotation(dir);
            ConfigureCamera(inside);
            Game.Rig.FocusOn(dir * (GalleryDist + 1f), 17f, 0.6f);
            return true;
        }

        /// <summary>Oda sergisine kamerayı odaklar.</summary>
        public void FocusExhibit(string id)
        {
            Transform t;
            if (_exhibitAnchors.TryGetValue(id, out t) && t != null) Game.Rig.FocusOn(t.position, 11f, 0.5f);
        }

        public Vector3 ExhibitPosition(string id)
        {
            Transform t;
            return _exhibitAnchors.TryGetValue(id, out t) && t != null ? t.position : Vector3.zero;
        }

        // =================================================================== eser inceleme sahnesi
        /// <summary>Eseri döner tablaya koyar ve kamerayı inceleme moduna alır. Modelin dönüş kökünü döndürür.</summary>
        public Transform ShowInspect(ArtifactInfo a)
        {
            InteractionEnabled = false;
            Prim.ClearChildren(_inspectHolder);
            _inspectHolder.localRotation = Quaternion.identity;
            var model = ArtifactModelFactory.Build(a, _inspectHolder, Session.Museum.IsCollected(a));
            model.transform.localScale = Vector3.one * 1.6f;
            var rig = Game.Rig;
            rig.FollowTarget = null;
            rig.AllowPan = false; rig.AllowZoom = true;
            var pivot = _inspectStage.position + new Vector3(0, 1.1f, 0);
            rig.Configure(new Rect(pivot.x - 0.5f, pivot.z - 0.5f, 1f, 1f), 5f, 2.5f, 8f, 14f, 0f, pivot);
            rig.SetMode(CameraMode.FreePan);
            return _inspectHolder;
        }

        public void HideInspect()
        {
            if (_inspectHolder != null) Prim.ClearChildren(_inspectHolder);
            InteractionEnabled = true;
        }
    }
}
