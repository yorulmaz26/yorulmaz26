using UnityEngine;

namespace WorldWonders.Game
{
    /// <summary>
    /// Profil ve özelleştirme ekranları için kâşif önizlemesi: dünyadan uzakta (y=-500) duran karakteri
    /// kendi kamerasıyla bir RenderTexture'a çizer. Gizliyken kamera kapanır (pil tasarrufu).
    /// </summary>
    public sealed class CharacterPreview : MonoBehaviour
    {
        private static CharacterPreview _inst;
        private ExplorerCharacter _explorer;
        private Transform _turntable;
        private Camera _cam;
        private Light _fill;
        private RenderTexture _rt;
        private float _yaw = 160f, _targetYaw = 160f;

        public RenderTexture Texture { get { return _rt; } }

        public static CharacterPreview Get()
        {
            if (_inst != null) return _inst;
            var go = new GameObject("[CharacterPreview]");
            DontDestroyOnLoad(go);
            go.transform.position = new Vector3(0f, -500f, 0f);
            _inst = go.AddComponent<CharacterPreview>();
            _inst.Setup();
            return _inst;
        }

        private void Setup()
        {
            _rt = new RenderTexture(768, 1024, 24, RenderTextureFormat.ARGB32) { name = "CharacterPreviewRT", antiAliasing = 2 };
            _rt.Create();

            var stage = Prim.Cyl(transform, new Vector3(0, -0.06f, 0), 2.2f, 0.12f, Palette.Marble);
            stage.name = "Stage";
            Prim.Cyl(transform, new Vector3(0, -0.13f, 0), 2.45f, 0.06f, Palette.Gold);

            _turntable = Prim.Group("Turntable", transform);
            _explorer = ExplorerCharacter.Create(_turntable);

            var camGo = new GameObject("PreviewCamera");
            camGo.transform.SetParent(transform, false);
            camGo.transform.localPosition = new Vector3(0f, 1.05f, 4.2f);
            camGo.transform.LookAt(transform.position + new Vector3(0f, 0.75f, 0f));
            _cam = camGo.AddComponent<Camera>();
            _cam.targetTexture = _rt;
            _cam.clearFlags = CameraClearFlags.SolidColor;
            _cam.backgroundColor = Palette.Lighten(Palette.Sky, 0.55f);
            _cam.fieldOfView = 30f;
            _cam.nearClipPlane = 0.1f;
            _cam.farClipPlane = 15f;
            _cam.depth = -10;

            var lightGo = new GameObject("PreviewFill");
            lightGo.transform.SetParent(transform, false);
            lightGo.transform.localPosition = new Vector3(-1.5f, 2.5f, 2.5f);
            _fill = lightGo.AddComponent<Light>();
            _fill.type = LightType.Point;
            _fill.range = 7f;
            _fill.intensity = 1.2f;
            _fill.color = new Color(1f, 0.95f, 0.88f);

            PlaceholderAsset.Mark(gameObject, "Karakter önizleme sahnesi primitiflerden kurulu.");
            Refresh();
            SetVisible(false);
        }

        public void Refresh()
        {
            var gm = GameManager.I;
            if (gm != null && gm.Session != null && _explorer != null) _explorer.ApplyLoadout(gm.Session.Save.Equipped);
        }

        public void SetYaw(float yaw) { _targetYaw = yaw; }
        public void AddYaw(float delta) { _targetYaw += delta; }

        public void Play(ExplorerAnim a) { if (_explorer != null) _explorer.Play(a); }

        public void SetVisible(bool v)
        {
            if (_cam != null) _cam.enabled = v;
            if (_fill != null) _fill.enabled = v;
            if (_turntable != null) _turntable.gameObject.SetActive(v);
            if (v) Refresh();
        }

        private void Update()
        {
            _yaw = Mathf.LerpAngle(_yaw, _targetYaw, 1f - Mathf.Exp(-Time.unscaledDeltaTime * 8f));
            if (_turntable != null) _turntable.localRotation = Quaternion.Euler(0f, _yaw, 0f);
        }

        private void OnDestroy()
        {
            if (_rt != null) { _rt.Release(); Destroy(_rt); }
            if (_inst == this) _inst = null;
        }
    }
}
