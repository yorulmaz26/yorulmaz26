using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.InputSystem;
using UnityEngine.InputSystem.EnhancedTouch;
using ETouch = UnityEngine.InputSystem.EnhancedTouch.Touch;

namespace WorldWonders.Game
{
    public enum Ease { Linear, OutCubic, InOutCubic, OutBack, OutBounce, InCubic }

    public sealed class TweenHandle
    {
        internal bool Cancelled;
        public bool Done { get; internal set; }
        public void Cancel() { Cancelled = true; }
    }

    /// <summary>Hafif tween sistemi (harici paket gerektirmez). Bütün animasyonlar buradan geçer.</summary>
    public sealed class Tween : MonoBehaviour
    {
        private struct Entry { public TweenHandle H; public float Delay, Duration, T; public Action<float> Step; public Action Done; public Ease Ease; public bool Unscaled; }
        private readonly List<Entry> _list = new List<Entry>();
        private readonly List<Entry> _pending = new List<Entry>();
        private static Tween _inst;

        private static Tween Inst
        {
            get
            {
                if (_inst == null)
                {
                    var go = new GameObject("[Tween]");
                    DontDestroyOnLoad(go);
                    _inst = go.AddComponent<Tween>();
                }
                return _inst;
            }
        }

        public static TweenHandle Run(float duration, Action<float> step, Action done = null, Ease ease = Ease.OutCubic, float delay = 0f)
        {
            var h = new TweenHandle();
            Inst._pending.Add(new Entry { H = h, Delay = delay, Duration = Mathf.Max(0.0001f, duration), Step = step, Done = done, Ease = ease });
            return h;
        }

        public static TweenHandle Delay(float seconds, Action done) { return Run(seconds, null, done, Ease.Linear); }

        public static TweenHandle Move(Transform t, Vector3 to, float duration, Ease ease = Ease.OutCubic, Action done = null, float delay = 0f)
        {
            Vector3 from = t.localPosition; bool started = false;
            return Run(duration, k => { if (t == null) return; if (!started) { from = t.localPosition; started = true; } t.localPosition = Vector3.LerpUnclamped(from, to, k); }, done, ease, delay);
        }

        public static TweenHandle Scale(Transform t, Vector3 to, float duration, Ease ease = Ease.OutBack, Action done = null, float delay = 0f)
        {
            Vector3 from = t.localScale; bool started = false;
            return Run(duration, k => { if (t == null) return; if (!started) { from = t.localScale; started = true; } t.localScale = Vector3.LerpUnclamped(from, to, k); }, done, ease, delay);
        }

        public static float Evaluate(Ease e, float x)
        {
            switch (e)
            {
                case Ease.OutCubic: return 1f - Mathf.Pow(1f - x, 3f);
                case Ease.InCubic: return x * x * x;
                case Ease.InOutCubic: return x < 0.5f ? 4f * x * x * x : 1f - Mathf.Pow(-2f * x + 2f, 3f) / 2f;
                case Ease.OutBack: { const float c1 = 1.70158f, c3 = c1 + 1f; return 1f + c3 * Mathf.Pow(x - 1f, 3f) + c1 * Mathf.Pow(x - 1f, 2f); }
                case Ease.OutBounce:
                    {
                        const float n1 = 7.5625f, d1 = 2.75f;
                        if (x < 1f / d1) return n1 * x * x;
                        if (x < 2f / d1) { x -= 1.5f / d1; return n1 * x * x + 0.75f; }
                        if (x < 2.5f / d1) { x -= 2.25f / d1; return n1 * x * x + 0.9375f; }
                        x -= 2.625f / d1; return n1 * x * x + 0.984375f;
                    }
                default: return x;
            }
        }

        private void Update()
        {
            if (_pending.Count > 0) { _list.AddRange(_pending); _pending.Clear(); }
            float dt = Time.unscaledDeltaTime;
            for (int i = _list.Count - 1; i >= 0; i--)
            {
                var e = _list[i];
                if (e.H.Cancelled) { _list.RemoveAt(i); continue; }
                if (e.Delay > 0) { e.Delay -= dt; _list[i] = e; continue; }
                e.T += dt;
                float k = Mathf.Clamp01(e.T / e.Duration);
                try { if (e.Step != null) e.Step(Evaluate(e.Ease, k)); }
                catch (Exception ex) { Debug.LogException(ex); e.H.Cancelled = true; }
                if (k >= 1f)
                {
                    _list.RemoveAt(i);
                    e.H.Done = true;
                    try { if (e.Done != null && !e.H.Cancelled) e.Done(); } catch (Exception ex) { Debug.LogException(ex); }
                }
                else _list[i] = e;
            }
        }
    }

    /// <summary>
    /// Input System tabanlı tek işaretçi + iki parmak yakınlaştırma soyutlaması.
    /// GameManager her karede Update() çağırır. UI üzerinde başlayan dokunuşlar dünya etkileşimi başlatmaz.
    /// </summary>
    public static class InputAdapter
    {
        public const float TapMaxMove = 18f;
        public const float TapMaxTime = 0.35f;

        public static Vector2 Position { get; private set; }
        public static Vector2 Delta { get; private set; }
        public static Vector2 DownPosition { get; private set; }
        public static bool Down { get; private set; }
        public static bool Held { get; private set; }
        public static bool Up { get; private set; }
        public static bool Tap { get; private set; }
        public static bool Dragging { get; private set; }
        public static bool StartedOverUI { get; private set; }
        public static float Pinch { get; private set; }  // >0 yakınlaş, <0 uzaklaş (piksel)
        public static int TouchCount { get; private set; }
        /// <summary>Dünya (3D) etkileşimi için kullanılabilir mi: UI'da başlamamış tek parmak hareketi.</summary>
        public static bool WorldGesture { get { return !StartedOverUI && TouchCount <= 1; } }

        public static Func<Vector2, bool> IsOverUI = p => false;

        private static float _downTime, _lastPinchDist;
        private static float _maxMove;
        private static bool _enhanced;

        public static void Update()
        {
            if (!_enhanced) { EnhancedTouchSupport.Enable(); _enhanced = true; }
            Down = Up = Tap = false; Pinch = 0f; Delta = Vector2.zero;

            var touches = ETouch.activeTouches;
            TouchCount = touches.Count;
            bool pressed; Vector2 pos;
            if (touches.Count > 0)
            {
                pressed = true; pos = touches[0].screenPosition;
                if (touches.Count >= 2)
                {
                    float d = Vector2.Distance(touches[0].screenPosition, touches[1].screenPosition);
                    if (_lastPinchDist > 0) Pinch = d - _lastPinchDist;
                    _lastPinchDist = d;
                    _maxMove = 999f; // iki parmak: dokunuş sayılmaz
                }
                else _lastPinchDist = 0;
            }
            else
            {
                _lastPinchDist = 0;
                var mouse = Mouse.current;
                if (mouse != null)
                {
                    pressed = mouse.leftButton.isPressed; pos = mouse.position.ReadValue();
                    float scroll = mouse.scroll.ReadValue().y;
                    if (Mathf.Abs(scroll) > 0.01f && !IsOverUI(pos)) Pinch = Mathf.Sign(scroll) * 40f;
                }
                else { pressed = false; pos = Position; }
            }

            if (pressed && !Held)
            {
                Down = true; DownPosition = pos; _downTime = Time.unscaledTime; _maxMove = 0f; Dragging = false;
                StartedOverUI = IsOverUI(pos);
                Delta = Vector2.zero;
            }
            else if (pressed)
            {
                Delta = pos - Position;
                _maxMove = Mathf.Max(_maxMove, Vector2.Distance(pos, DownPosition));
                if (_maxMove > TapMaxMove) Dragging = true;
            }
            else if (Held)
            {
                Up = true;
                Tap = _maxMove <= TapMaxMove && Time.unscaledTime - _downTime <= TapMaxTime && !StartedOverUI;
                Dragging = false;
            }
            Held = pressed;
            Position = pos;
        }
    }

    public enum CameraMode { Follow, FreePan, Focus }

    /// <summary>İzometrik takip/serbest kaydırma/odak kamerası. Dokunarak kaydırma ve iki parmakla yakınlaştırma.</summary>
    public sealed class CameraRig : MonoBehaviour
    {
        public Camera Cam;
        public CameraMode Mode = CameraMode.FreePan;
        public Transform FollowTarget;
        public float Pitch = 50f, Yaw = 45f;
        public float Distance = 30f, MinDistance = 12f, MaxDistance = 60f;
        public Rect Bounds = new Rect(-50, -50, 100, 100);
        public bool AllowPan = true, AllowZoom = true;
        public Vector3 Pivot;

        private Vector3 _focusPivot; private float _focusDist; private float _focusT = 1f; private Vector3 _focusFromPivot; private float _focusFromDist; private float _focusDur;
        private Vector3 _velocity;

        public void Configure(Rect bounds, float distance, float minD, float maxD, float pitch, float yaw, Vector3 pivot)
        {
            Bounds = bounds; Distance = distance; MinDistance = minD; MaxDistance = maxD; Pitch = pitch; Yaw = yaw; Pivot = pivot;
            _velocity = Vector3.zero; _focusT = 1f;
            Apply();
        }

        public void SetMode(CameraMode m) { Mode = m; }

        public void FocusOn(Vector3 point, float distance, float duration = 0.6f)
        {
            Mode = CameraMode.Focus;
            _focusFromPivot = Pivot; _focusFromDist = Distance;
            _focusPivot = point; _focusDist = Mathf.Clamp(distance, MinDistance, MaxDistance); _focusDur = Mathf.Max(0.01f, duration); _focusT = 0f;
        }

        private void LateUpdate()
        {
            if (Cam == null) return;
            float dt = Time.unscaledDeltaTime;
            if (AllowZoom && Mathf.Abs(InputAdapter.Pinch) > 0.01f)
                Distance = Mathf.Clamp(Distance - InputAdapter.Pinch * Distance * 0.004f, MinDistance, MaxDistance);

            if (AllowPan && InputAdapter.WorldGesture && InputAdapter.Held && InputAdapter.Dragging)
            {
                if (Mode == CameraMode.Follow || Mode == CameraMode.Focus) Mode = CameraMode.FreePan;
                Vector3 worldDelta = ScreenDeltaToWorld(InputAdapter.Delta);
                Pivot -= worldDelta;
                _velocity = -worldDelta / Mathf.Max(dt, 0.001f);
            }
            else if (Mode == CameraMode.FreePan)
            {
                Pivot += _velocity * dt;
                _velocity = Vector3.Lerp(_velocity, Vector3.zero, dt * 6f);
            }

            if (Mode == CameraMode.Follow && FollowTarget != null)
                Pivot = Vector3.Lerp(Pivot, FollowTarget.position, 1f - Mathf.Exp(-dt * 5f));
            else if (Mode == CameraMode.Focus && _focusT < 1f)
            {
                _focusT = Mathf.Min(1f, _focusT + dt / _focusDur);
                float k = Tween.Evaluate(Ease.InOutCubic, _focusT);
                Pivot = Vector3.Lerp(_focusFromPivot, _focusPivot, k);
                Distance = Mathf.Lerp(_focusFromDist, _focusDist, k);
            }

            Pivot.x = Mathf.Clamp(Pivot.x, Bounds.xMin, Bounds.xMax);
            Pivot.z = Mathf.Clamp(Pivot.z, Bounds.yMin, Bounds.yMax);
            Apply();
        }

        public void Apply()
        {
            if (Cam == null) return;
            var rot = Quaternion.Euler(Pitch, Yaw, 0f);
            Cam.transform.rotation = rot;
            Cam.transform.position = Pivot - rot * Vector3.forward * Distance;
        }

        private Vector3 ScreenDeltaToWorld(Vector2 d)
        {
            // Zemin düzlemindeki iki ışın kesişiminin farkı.
            var plane = new Plane(Vector3.up, new Vector3(0, Pivot.y, 0));
            Vector2 p1 = InputAdapter.Position, p0 = p1 - d;
            Ray r0 = Cam.ScreenPointToRay(p0), r1 = Cam.ScreenPointToRay(p1);
            float e0, e1;
            if (plane.Raycast(r0, out e0) && plane.Raycast(r1, out e1)) return r1.GetPoint(e1) - r0.GetPoint(e0);
            return Vector3.zero;
        }
    }

    /// <summary>Dokunulabilir 3D nesne. Collider'ı olan nesneye ya da üst nesnesine eklenir.</summary>
    public sealed class Tappable : MonoBehaviour
    {
        public Action OnTap;
        /// <summary>Kâşifin yürüyeceği nokta (yoksa nesnenin önü).</summary>
        public Transform ApproachPoint;
        /// <summary>True ise dokununca kâşif önce buraya yürür, varınca OnArrive çağrılır.</summary>
        public bool WalkToFirst;
        public Action OnArrive;
        public int Priority;
    }

    /// <summary>Kâşifin yürüyebileceği zemin işareti.</summary>
    public sealed class WalkableGround : MonoBehaviour { }

    public static class WorldPicker
    {
        /// <summary>Dokunma anında dünyada neye dokunulduğunu bulur (UI üzerinde değilse).</summary>
        public static bool Pick(Camera cam, Vector2 screenPos, out Tappable tappable, out Vector3 groundPoint, out bool hitGround)
        {
            tappable = null; groundPoint = Vector3.zero; hitGround = false;
            var ray = cam.ScreenPointToRay(screenPos);
            var hits = Physics.RaycastAll(ray, 500f, ~0, QueryTriggerInteraction.Collide);
            Array.Sort(hits, (a, b) => a.distance.CompareTo(b.distance));
            foreach (var h in hits)
            {
                var t = h.collider.GetComponentInParent<Tappable>();
                if (t != null && t.isActiveAndEnabled) { tappable = t; return true; }
                if (!hitGround && h.collider.GetComponentInParent<WalkableGround>() != null) { hitGround = true; groundPoint = h.point; }
            }
            return hitGround;
        }
    }
}
