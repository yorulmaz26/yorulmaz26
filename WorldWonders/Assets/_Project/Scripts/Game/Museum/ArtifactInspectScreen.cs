using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core.Content;

namespace WorldWonders.Game
{
    /// <summary>Eser inceleme: döner tablada 3D model; sürükleyerek döndür, iki parmakla yakınlaştır; kısa tarihî bilgi kartı.</summary>
    public sealed class ArtifactInspectScreen : UIScreen
    {
        private readonly ArtifactInfo _a;
        private MuseumView _view;
        private Transform _turntable;
        private float _idle;
        private float _tilt;

        public ArtifactInspectScreen(ArtifactInfo artifact) { _a = artifact; }

        public override bool ShowHud { get { return false; } }

        protected override void Build(VisualElement root)
        {
            var col = UIKit.Column().Ignore();
            col.style.flexGrow = 1;
            root.Add(col);
            col.Add(UIKit.Spacer(40));
            var header = UIKit.Row().Ignore();
            header.Pad(UIKit.Gutter, 12);
            header.Add(UIKit.IconButton(Icon.Back, Nav.Back, Palette.White, Palette.Text, 96));
            header.Add(UIKit.Flex());
            header.Add(UIKit.IconButton(Icon.Shuffle, ResetPose, Palette.White, Palette.Text, 96));
            col.Add(header);
            col.Add(UIKit.Flex());

            var w = WonderCatalog.Get(_a.Wonder);
            var card = UIKit.Card();
            card.style.marginLeft = UIKit.Gutter; card.style.marginRight = UIKit.Gutter; card.style.marginBottom = 50;
            card.Add(UIKit.Pill(w.Name + " · " + w.AreaNames[_a.Area - 1] + " (alan " + _a.Area + ")", Palette.Hex(w.ThemeColor)));
            card.Add(UIKit.Spacer(14));
            card.Add(UIKit.Title(_a.Name));
            card.Add(UIKit.Spacer(10));
            card.Add(UIKit.Body(_a.History));
            card.Add(UIKit.Spacer(14));
            card.Add(UIKit.Small("Sürükle: döndür · İki parmak: yakınlaştır"));
            col.Add(card);
        }

        public override void OnShow()
        {
            _view = Game.ShowView<MuseumView>();
            _turntable = _view.ShowInspect(_a);
            _tilt = 0f;
        }

        public override void OnHide()
        {
            if (_view != null) _view.HideInspect();
        }

        private void ResetPose()
        {
            if (_turntable == null) return;
            _turntable.localRotation = Quaternion.identity;
            _tilt = 0f;
        }

        public override void Tick(float dt)
        {
            if (_turntable == null) return;
            if (InputAdapter.Held && InputAdapter.Dragging && !InputAdapter.StartedOverUI && InputAdapter.TouchCount <= 1)
            {
                _idle = 0f;
                _turntable.Rotate(0f, -InputAdapter.Delta.x * 0.35f, 0f, Space.World);
                _tilt = Mathf.Clamp(_tilt + InputAdapter.Delta.y * 0.2f, -35f, 35f);
            }
            else
            {
                _idle += dt;
                if (_idle > 1.5f) _turntable.Rotate(0f, 18f * dt, 0f, Space.World);
            }
            var e = _turntable.localEulerAngles;
            _turntable.localRotation = Quaternion.Euler(_tilt, e.y, 0f);
        }
    }
}
