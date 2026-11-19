using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;
using WorldWonders.Core.Content;

namespace WorldWonders.Game
{
    /// <summary>
    /// Jigsaw alanı: üstte 3D anıt (ConstructionView), altta 12 yuvalı tahta ve kazanılmış ama yerleştirilmemiş parçalar.
    /// Parça yalnız oyuncu doğru yuvaya sürükleyip bıraktığında yerleşir; inşa asla otomatik ilerlemez.
    /// </summary>
    public sealed class JigsawScreen : UIScreen
    {
        private readonly int _wonder;
        private readonly int _highlight;
        private readonly VisualElement[] _slots = new VisualElement[GameConfig.PiecesPerWonder];
        private VisualElement _tray, _root, _ghost;
        private Label _progress, _trayHint;
        private VisualElement _dragSource;
        private int _dragPiece = -1, _dragPointer = -1;
        private Vector2 _grabOffset;
        private bool _celebrated;

        public JigsawScreen(int wonder, int highlightPiece)
        {
            _wonder = wonder;
            _highlight = highlightPiece;
        }

        protected override void Build(VisualElement root)
        {
            _root = root;
            var info = WonderCatalog.Get(_wonder);
            // Kutlama yalnız bu ekranda tamamlanırsa gösterilir.
            _celebrated = Session.Jigsaw.PlacedCount(_wonder) >= GameConfig.PiecesPerWonder;
            var page = UIKit.Column().Grow();
            page.Ignore();
            page.Add(UIKit.Spacer(UIKit.HudHeight));

            var header = UIKit.Row().Pad(UIKit.Gutter, 12);
            header.Add(UIKit.IconButton(Icon.Back, Nav.Back, Palette.White, Palette.Text, 96));
            var titleCol = UIKit.Column(); titleCol.style.marginLeft = 20; titleCol.style.flexGrow = 1;
            titleCol.Add(UIKit.H2(info.Name + " İnşası"));
            _progress = UIKit.Small("");
            titleCol.Add(_progress);
            header.Add(titleCol);
            page.Add(header);

            // Üst yarı: 3D anıt arkada görünür (boş, tıklanamaz alan).
            page.Add(UIKit.Flex());

            var board = UIKit.Column();
            board.Bg(Palette.WithAlpha(Palette.Cream, 0.97f)).Radius(48).Pad(32, 28).Border(2, Palette.CardBorder);
            board.style.borderBottomLeftRadius = 0; board.style.borderBottomRightRadius = 0;
            board.style.height = Length.Percent(58);
            board.style.flexShrink = 0;

            board.Add(UIKit.Text("Parçayı sürükleyip anıttaki doğru yerine bırak", UIKit.FontSmall, Palette.Text, true));
            board.Add(UIKit.Spacer(14));

            var grid = UIKit.Column();
            for (int r = 0; r < 3; r++)
            {
                var row = UIKit.Row();
                row.style.justifyContent = Justify.SpaceBetween;
                row.style.marginBottom = 12;
                for (int c = 0; c < 4; c++)
                {
                    int slot = r * 4 + c;
                    var s = new VisualElement();
                    s.style.width = Length.Percent(23.5f); s.style.height = 150;
                    s.Radius(24).Center();
                    s.style.flexDirection = FlexDirection.Column;
                    s.pickingMode = PickingMode.Ignore;
                    _slots[slot] = s;
                    row.Add(s);
                }
                grid.Add(row);
            }
            board.Add(grid);

            board.Add(UIKit.Spacer(10));
            var trayHead = UIKit.Row();
            trayHead.Add(UIKit.IconImage(Icon.Puzzle, Palette.Gold, 44));
            var th = UIKit.Text("Kazanılan parçalar", UIKit.FontSmall, Palette.Text, true); th.style.marginLeft = 10;
            trayHead.Add(th);
            board.Add(trayHead);
            _tray = UIKit.Row();
            _tray.style.flexWrap = Wrap.Wrap;
            _tray.style.minHeight = 150;
            _tray.style.marginTop = 10;
            board.Add(_tray);
            _trayHint = UIKit.Small("");
            board.Add(_trayHint);

            page.Add(board);
            root.Add(page);

        }

        public override void OnShow()
        {
            var view = Game.ShowView<ConstructionView>();
            view.Show(_wonder);
            Refresh();
        }

        private void Refresh()
        {
            var j = Session.Jigsaw;
            var info = WonderCatalog.Get(_wonder);
            int placed = j.PlacedCount(_wonder);
            _progress.text = placed + "/12 inşa aşaması tamamlandı";

            for (int i = 0; i < _slots.Length; i++)
            {
                var s = _slots[i];
                s.Clear();
                bool isPlaced = j.IsPlaced(_wonder, i);
                if (isPlaced)
                {
                    s.Bg(Palette.Lighten(Palette.Gold, 0.55f)).Border(3, Palette.Gold);
                    s.Add(UIKit.IconImage(Icon.Check, Palette.GoldDeep, 46));
                }
                else
                {
                    s.Bg(Palette.WithAlpha(Palette.White, 0.6f)).Border(3, Palette.Locked);
                    var num = UIKit.Text((i + 1).ToString(), UIKit.FontH2, Palette.Locked, true);
                    s.Add(num);
                }
                var name = UIKit.Text(info.ConstructionStages[i], UIKit.FontTiny - 4, isPlaced ? Palette.Text : Palette.TextSoft, isPlaced);
                name.style.unityTextAlign = TextAnchor.MiddleCenter;
                name.style.marginTop = 4;
                s.Add(name);
            }

            _tray.Clear();
            var pieces = j.UnplacedPieces(_wonder);
            foreach (var p in pieces) _tray.Add(MakePiece(p));
            if (pieces.Count == 0)
            {
                if (placed >= GameConfig.PiecesPerWonder) _trayHint.text = "Bütün parçalar yerleştirildi.";
                else
                {
                    int next = (j.EarnedCount(_wonder) + 1) * 5;
                    _trayHint.text = "Yerleştirilecek parça yok. Parçalar yerel 5, 10, 15 … 60. bölümler ilk kez tamamlandığında kazanılır (sıradaki: " + next + ". bölüm). Parçalar satın alınamaz.";
                }
            }
            else _trayHint.text = "Parçalar yerel 5, 10 … 60. bölümlerden kazanılır ve satın alınamaz.";

            CheckCelebration();
        }

        private VisualElement MakePiece(int piece)
        {
            var info = WonderCatalog.Get(_wonder);
            var e = new VisualElement();
            e.Size(150, 150).Bg(Palette.Gold).Radius(28).Center();
            e.style.flexDirection = FlexDirection.Column;
            e.style.marginRight = 14; e.style.marginBottom = 10;
            e.style.borderBottomWidth = 8; e.style.borderBottomColor = Palette.GoldDeep;
            e.Add(UIKit.IconImage(Icon.Puzzle, Palette.White, 64));
            var l = UIKit.Text(info.ConstructionStages[piece], UIKit.FontTiny - 4, Palette.White, true);
            l.style.unityTextAlign = TextAnchor.MiddleCenter;
            e.Add(l);
            e.userData = piece;
            if (piece == _highlight)
            {
                Tween.Run(0.6f, k => e.style.scale = new Scale(Vector3.one * (1f + 0.12f * Mathf.Sin(k * Mathf.PI))), null, Ease.Linear, 0.3f);
            }
            e.RegisterCallback<PointerDownEvent>(evt => BeginDrag(e, piece, evt));
            e.RegisterCallback<PointerMoveEvent>(OnMove);
            e.RegisterCallback<PointerUpEvent>(OnUp);
            return e;
        }

        private void BeginDrag(VisualElement src, int piece, PointerDownEvent evt)
        {
            if (_dragPiece >= 0) return;
            _dragPiece = piece; _dragSource = src; _dragPointer = evt.pointerId;
            Vector2 pos = evt.position;
            _grabOffset = pos - src.worldBound.position;
            src.style.opacity = 0.3f;
            _ghost = new VisualElement();
            _ghost.Size(src.worldBound.width, src.worldBound.height).Bg(Palette.Gold).Radius(28).Center();
            _ghost.Add(UIKit.IconImage(Icon.Puzzle, Palette.White, 72));
            _ghost.style.position = Position.Absolute;
            _ghost.pickingMode = PickingMode.Ignore;
            _ghost.style.scale = new Scale(new Vector3(1.1f, 1.1f, 1f));
            _root.Add(_ghost);
            MoveGhost(pos);
            src.CapturePointer(evt.pointerId);
            evt.StopPropagation();
        }

        private void MoveGhost(Vector2 panelPos)
        {
            if (_ghost == null) return;
            var local = _root.WorldToLocal(panelPos - _grabOffset);
            _ghost.style.left = local.x; _ghost.style.top = local.y;
            for (int i = 0; i < _slots.Length; i++)
            {
                if (Session.Jigsaw.IsPlaced(_wonder, i)) continue;
                bool over = _slots[i].worldBound.Contains(panelPos);
                _slots[i].Border(3, over ? Palette.Turquoise : Palette.Locked);
            }
        }

        private void OnMove(PointerMoveEvent evt)
        {
            if (_dragPiece < 0 || evt.pointerId != _dragPointer) return;
            MoveGhost(evt.position);
        }

        private void OnUp(PointerUpEvent evt)
        {
            if (_dragPiece < 0 || evt.pointerId != _dragPointer) return;
            if (_dragSource != null && _dragSource.HasPointerCapture(evt.pointerId)) _dragSource.ReleasePointer(evt.pointerId);
            Vector2 pos = evt.position;
            int target = -1;
            for (int i = 0; i < _slots.Length; i++) if (_slots[i].worldBound.Contains(pos)) { target = i; break; }
            int piece = _dragPiece;
            var ghost = _ghost; var src = _dragSource;
            _dragPiece = -1; _ghost = null; _dragSource = null; _dragPointer = -1;

            if (target >= 0 && Session.Jigsaw.TryPlace(_wonder, piece, target))
            {
                var slotRect = _slots[target].worldBound;
                var to = _root.WorldToLocal(slotRect.position);
                float fx = ghost.resolvedStyle.left, fy = ghost.resolvedStyle.top;
                Tween.Run(0.22f, k =>
                {
                    ghost.style.left = Mathf.Lerp(fx, to.x, k); ghost.style.top = Mathf.Lerp(fy, to.y, k);
                    ghost.style.scale = new Scale(Vector3.one * Mathf.Lerp(1.1f, 0.9f, k));
                }, () =>
                {
                    ghost.RemoveFromHierarchy();
                    Refresh();
                    var s = _slots[target];
                    Tween.Run(0.35f, k => s.style.scale = new Scale(Vector3.one * (1f + 0.15f * Mathf.Sin(k * Mathf.PI))), null, Ease.Linear);
                });
                var view = Game.GetView<ConstructionView>();
                view.AnimatePlace(piece);
                Haptics.Strong();
                UI.Toast(WonderCatalog.Get(_wonder).ConstructionStages[piece] + " inşa edildi! +" + GameConfig.XpJigsawPlace + " XP", Palette.Gold);
                UI.RefreshHud();
                Game.SaveNow();
            }
            else
            {
                // Yanlış yer: parça titreyerek tepsiye döner.
                var home = _root.WorldToLocal(src.worldBound.position);
                float fx = ghost.resolvedStyle.left, fy = ghost.resolvedStyle.top;
                for (int i = 0; i < _slots.Length; i++) if (!Session.Jigsaw.IsPlaced(_wonder, i)) _slots[i].Border(3, Palette.Locked);
                if (target >= 0)
                {
                    var bad = _slots[target];
                    bad.Border(3, Palette.Coral);
                    Tween.Run(0.4f, k => bad.style.translate = new Translate(Mathf.Sin(k * Mathf.PI * 6) * 12f * (1 - k), 0, 0), () =>
                    {
                        bad.style.translate = new Translate(0, 0, 0);
                        if (!Session.Jigsaw.IsPlaced(_wonder, target)) bad.Border(3, Palette.Locked);
                    }, Ease.Linear);
                    UI.Toast("Bu parça buraya ait değil. Aşama adına dikkat et!", Palette.Coral);
                }
                Tween.Run(0.28f, k =>
                {
                    ghost.style.left = Mathf.Lerp(fx, home.x, k); ghost.style.top = Mathf.Lerp(fy, home.y, k);
                }, () => { ghost.RemoveFromHierarchy(); src.style.opacity = 1f; });
            }
        }

        private void CheckCelebration()
        {
            if (_celebrated) return;
            if (Session.Jigsaw.PlacedCount(_wonder) < GameConfig.PiecesPerWonder) return;
            if (Session.Levels.CompletedCountInWonder(_wonder) < GameConfig.LevelsPerWonder) return;
            _celebrated = true;
            var info = WonderCatalog.Get(_wonder);
            string next = _wonder + 1 < GameConfig.WonderCount ? "Sıradaki durak: " + WonderCatalog.Get(_wonder + 1).Name + " açıldı!" : "Yedi harikanın hepsini tamamladın!";
            var card = UIKit.Card();
            card.style.alignItems = Align.Center;
            card.Add(UIKit.IconImage(Icon.Trophy, Palette.Gold, 140));
            card.Add(UIKit.Spacer(12));
            card.Add(UIKit.Title(info.Name + " tamamlandı!"));
            card.Add(UIKit.Spacer(8));
            card.Add(UIKit.Body("60 bölüm ve 12 inşa aşaması bitti. " + next));
            card.Add(UIKit.Spacer(20));
            VisualElement popup = null;
            var b = UIKit.Gold("Dünya Haritasına Dön", () => { UI.ClosePopup(popup); Nav.WorldMap(); });
            b.style.alignSelf = Align.Stretch;
            card.Add(b);
            var stay = UIKit.Secondary("Anıtı İncele", () => UI.ClosePopup(popup));
            stay.style.alignSelf = Align.Stretch;
            card.Add(stay);
            Tween.Delay(0.6f, () => { popup = UI.ShowPopup(card); Haptics.Strong(); });
        }

        public override bool OnBack()
        {
            if (_dragPiece >= 0) return true;
            return false;
        }
    }
}
