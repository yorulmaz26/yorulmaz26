using UnityEngine;
using UnityEngine.UIElements;

namespace WorldWonders.Game
{
    /// <summary>CharacterPreview dokusunu gösteren, sürükleyerek döndürülebilen panel (Profil ve Özelleştirme).</summary>
    public sealed class CharacterPreviewPanel : VisualElement
    {
        private readonly Image _img;
        private CharacterPreview _preview;
        private float _yaw = 180f;
        private bool _drag;
        private int _pointer = -1;
        private float _lastX;

        public CharacterPreviewPanel(float height)
        {
            style.height = height;
            style.flexShrink = 0;
            this.Bg(Palette.Lighten(Palette.Sky, 0.35f)).Radius(40).Border(2, Palette.CardBorder);
            style.overflow = Overflow.Hidden;
            style.alignItems = Align.Center; style.justifyContent = Justify.Center;
            var floor = new VisualElement().Ignore();
            floor.Absolute(0, null, 0, 0); floor.style.height = Length.Percent(22);
            floor.Bg(Palette.Lighten(Palette.Sand, 0.4f));
            Add(floor);
            _img = new Image { scaleMode = ScaleMode.ScaleAndCrop };
            _img.Fill().Ignore();
            Add(_img);
            var hint = UIKit.Small("Döndürmek için sürükle");
            hint.Absolute(null, null, 24, 16);
            Add(hint);
            RegisterCallback<PointerDownEvent>(e => { _drag = true; _pointer = e.pointerId; _lastX = e.position.x; this.CapturePointer(e.pointerId); });
            RegisterCallback<PointerMoveEvent>(e =>
            {
                if (!_drag || e.pointerId != _pointer) return;
                _yaw -= (e.position.x - _lastX) * 0.6f; _lastX = e.position.x;
                if (_preview != null) _preview.SetYaw(_yaw);
            });
            RegisterCallback<PointerUpEvent>(e => { _drag = false; if (this.HasPointerCapture(e.pointerId)) this.ReleasePointer(e.pointerId); });
        }

        public void Show()
        {
            _preview = CharacterPreview.Get();
            _preview.SetVisible(true);
            _preview.Refresh();
            _preview.SetYaw(_yaw);
            _preview.Play(ExplorerAnim.Idle);
            _img.image = _preview.Texture;
        }

        public void Refresh(ExplorerAnim anim = ExplorerAnim.Idle)
        {
            if (_preview == null) return;
            _preview.Refresh();
            _preview.Play(anim);
            _img.image = _preview.Texture;
        }

        public void Hide()
        {
            if (_preview != null) _preview.SetVisible(false);
        }
    }
}
