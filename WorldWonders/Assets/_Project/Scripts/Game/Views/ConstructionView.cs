using System.Collections.Generic;
using UnityEngine;
using WorldWonders.Core;

namespace WorldWonders.Game
{
    /// <summary>
    /// Seçili harikanın 3D inşa sahnesi: küçük stilize kaide üzerinde anıt, yavaş dönen kamera.
    /// Jigsaw ekranı arka planında kullanılır; doğru yerleştirmede AnimatePlace ilgili grubu yükseltir.
    /// GEÇİCİ VARLIK: prosedürel kaide ve anıt.
    /// </summary>
    [DefaultExecutionOrder(1000)]
    public sealed class ConstructionView : ViewBase
    {
        private readonly Dictionary<int, MonumentModel> _models = new Dictionary<int, MonumentModel>();
        private Transform _stage, _plinth;
        private float _yaw = 20f;
        private float _dist = 20f;
        private Vector3 _pivot;
        private float _idleSpin = 1f;

        public MonumentModel Current { get; private set; }
        public int Wonder { get; private set; }

        protected override void Build()
        {
            PlaceholderAsset.Mark(gameObject, "prosedürel inşa sahnesi; final sahne sanatıyla değiştirilecek.");
            _stage = Prim.Group("Stage", transform);
            _plinth = Prim.Group("Plinth", _stage);
            Prim.Cyl(_plinth, new Vector3(0, -0.45f, 0), 1f, 0.5f, Palette.Grass);
            Prim.Cyl(_plinth, new Vector3(0, -0.9f, 0), 1.06f, 0.5f, Palette.Sand);
            Prim.Cyl(_plinth, new Vector3(0, -1.5f, 0), 1.0f, 0.7f, Palette.Stone);
            Prim.FromMesh(MeshFactory.Ring(0.5f, 0.53f, 360f, 64), _plinth, new Vector3(0, -0.22f, 0), new Vector3(1f, 0.04f, 1f), Palette.Gold, "trim");
            Wonder = -1;
        }

        public override void Enter()
        {
            if (Wonder >= 0) ApplyCamera(0.01f);
        }

        public override void Exit()
        {
            var rig = Game.Rig;
            rig.AllowPan = true;
            rig.AllowZoom = true;
        }

        /// <summary>Harikanın modelini (gerekirse kurup) gösterir ve kayıtla eşler.</summary>
        public void Show(int wonder)
        {
            EnsureBuilt();
            MonumentModel m;
            if (!_models.TryGetValue(wonder, out m) || m == null)
            {
                m = MonumentBuilder.Build(wonder, _stage);
                _models[wonder] = m;
            }
            foreach (var kv in _models) if (kv.Value != null) kv.Value.gameObject.SetActive(kv.Key == wonder);
            Current = m;
            Wonder = wonder;
            m.ShowGhosts = true;
            m.SyncFrom(Session);
            float d = (m.Radius + 1.6f) * 2f;
            _plinth.localScale = new Vector3(d, 1f, d);
            ApplyCamera(0.6f);
        }

        private void ApplyCamera(float duration)
        {
            if (Current == null) return;
            var rig = Game.Rig;
            float size = Mathf.Max(Current.Height, Current.Radius * 1.3f);
            _dist = size * 2.4f + 6f;
            _pivot = new Vector3(0, Current.Height * 0.38f, 0);
            rig.AllowPan = false;
            rig.AllowZoom = true;
            rig.Configure(new Rect(-2f, -2f, 4f, 4f), _dist * 1.15f, _dist * 0.55f, _dist * 1.7f, 26f, _yaw, _pivot);
            rig.FocusOn(_pivot, _dist, duration);
        }

        /// <summary>Doğru Jigsaw yerleştirmesinden sonra grubu yükseltir, ışıltı ve kamera dürtmesi oynatır.</summary>
        public void AnimatePlace(int group)
        {
            if (Current == null) return;
            Current.SetBuilt(group, true, true);
            var center = Current.GroupCenterWorld(group);
            Sparkle(center);
            var rig = Game.Rig;
            float back = _dist;
            Vector3 pivot = _pivot;
            rig.FocusOn(Vector3.Lerp(pivot, center, 0.6f), back * 0.82f, 0.5f);
            _idleSpin = 0f;
            Tween.Delay(1.5f, () => { if (this != null && Game.CurrentView == this) { rig.FocusOn(pivot, back, 0.8f); _idleSpin = 1f; } });
        }

        private void Sparkle(Vector3 worldCenter)
        {
            var root = Prim.Group("Sparkle", transform);
            root.position = worldCenter;
            var gold = MaterialLibrary.Get(Palette.Gold, 0.7f);
            for (int k = 0; k < 16; k++)
            {
                var s = Prim.Ball(root, Vector3.zero, 0.28f, Palette.Gold);
                s.GetComponent<MeshRenderer>().sharedMaterial = gold;
                float a = k / 16f * Mathf.PI * 2f;
                var dir = new Vector3(Mathf.Cos(a), 0.6f + (k % 3) * 0.35f, Mathf.Sin(a));
                var t = s.transform;
                Tween.Move(t, dir * (2.2f + (k % 4) * 0.4f), 0.7f, Ease.OutCubic);
                Tween.Scale(t, Vector3.zero, 0.4f, Ease.InCubic, null, 0.55f);
            }
            Tween.Delay(1.1f, () => { if (root != null) Destroy(root.gameObject); });
        }

        private void LateUpdate()
        {
            if (Current == null) return;
            var rig = Game.Rig;
            if (InputAdapter.Held && InputAdapter.Dragging && InputAdapter.WorldGesture)
                _yaw += InputAdapter.Delta.x * 0.25f;
            else
                _yaw += Time.deltaTime * 6f * _idleSpin;
            rig.Yaw = _yaw;
            rig.Apply();
        }
    }
}
