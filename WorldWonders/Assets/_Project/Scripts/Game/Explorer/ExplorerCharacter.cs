using System.Collections.Generic;
using System.Text;
using UnityEngine;
using WorldWonders.Core;
using WorldWonders.Core.Content;

namespace WorldWonders.Game
{
    public enum ExplorerAnim { Idle, Walk, Turn, Interact, Discover, Celebrate }

    /// <summary>
    /// Stilize (chibi) 3D kâşif. Primitiflerden kurulur (GEÇİCİ VARLIK), kozmetik yuvalarına göre yeniden giydirilir.
    /// Animasyonlar Animator gerektirmeyen prosedürel bir durum makinesiyle oynatılır.
    /// </summary>
    public sealed class ExplorerCharacter : MonoBehaviour
    {
        private Transform _shadow, _model, _body, _hipL, _hipR, _shL, _shR, _head, _found;
        private float _hipY, _bodyBaseY;
        private ExplorerAnim _anim = ExplorerAnim.Idle;
        private float _animT, _walkPhase, _speed, _leanSign;
        private Vector3 _lastPos;
        private string _signature = "";
        private readonly Dictionary<CosmeticSlot, string> _loadout = new Dictionary<CosmeticSlot, string>();

        public ExplorerAnim Current { get { return _anim; } }
        /// <summary>Animasyon için yatay hız (birim/sn). Hareket denetleyicisi verir; verilmezse konumdan ölçülür.</summary>
        public float SmoothedSpeed { get { return _speed; } }

        public static ExplorerCharacter Create(Transform parent)
        {
            var go = new GameObject("Explorer");
            go.transform.SetParent(parent, false);
            var c = go.AddComponent<ExplorerCharacter>();
            foreach (var id in CosmeticCatalog.DefaultLoadout)
            {
                var info = CosmeticCatalog.Resolve(id);
                if (info != null) c._loadout[info.Slot] = id;
            }
            c.Rebuild();
            PlaceholderAsset.Mark(go, "Kâşif karakteri primitiflerden kurulu; final riglenmiş stilize model ve animasyonlarla değiştirilecek.");
            return c;
        }

        public void ApplyLoadout(IDictionary<CosmeticSlot, string> equipped)
        {
            if (equipped == null) return;
            var sb = new StringBuilder();
            foreach (var kv in equipped) sb.Append((int)kv.Key).Append('=').Append(kv.Value).Append(';');
            string sig = sb.ToString();
            if (sig == _signature && _model != null) return;
            _signature = sig;
            _loadout.Clear();
            foreach (var kv in equipped) _loadout[kv.Key] = kv.Value;
            Rebuild();
        }

        public void Play(ExplorerAnim anim)
        {
            bool looping = anim == ExplorerAnim.Idle || anim == ExplorerAnim.Walk;
            if (looping && _anim == anim) return;
            _anim = anim;
            _animT = 0f;
            _leanSign = Random.value < 0.5f ? -1f : 1f;
            if (_found != null) _found.gameObject.SetActive(anim == ExplorerAnim.Discover);
        }

        private static bool IsOneShot(ExplorerAnim a) { return a == ExplorerAnim.Turn || a == ExplorerAnim.Interact || a == ExplorerAnim.Discover || a == ExplorerAnim.Celebrate; }

        private static float Duration(ExplorerAnim a)
        {
            switch (a)
            {
                case ExplorerAnim.Turn: return 0.3f;
                case ExplorerAnim.Interact: return 0.9f;
                case ExplorerAnim.Discover: return 1.6f;
                case ExplorerAnim.Celebrate: return 1.4f;
                default: return 0f;
            }
        }

        // ------------------------------------------------------------------ model
        private string Id(CosmeticSlot slot, string def)
        {
            string id;
            return _loadout.TryGetValue(slot, out id) && !string.IsNullOrEmpty(id) ? id : def;
        }

        private static Color ColorOf(string id, string fallbackHex)
        {
            var info = id != null ? CosmeticCatalog.Resolve(id) : null;
            return Palette.Hex(info != null && !string.IsNullOrEmpty(info.ColorHex) ? info.ColorHex : fallbackHex);
        }

        private void Rebuild()
        {
            if (_model != null) { _model.gameObject.SetActive(false); Destroy(_model.gameObject); }
            _model = Prim.Group("Model", transform);

            bool bodyB = Id(CosmeticSlot.Body, "body_a") == "body_b";
            float legLen = bodyB ? 0.44f : 0.38f;
            float torsoH = bodyB ? 0.6f : 0.52f;
            float torsoW = bodyB ? 0.44f : 0.48f;
            float headD = bodyB ? 0.52f : 0.56f;

            Color skin = ColorOf(Id(CosmeticSlot.SkinTone, "skin_2"), "#EBC0A0");
            Color hair = ColorOf(Id(CosmeticSlot.Hair, "hair_1"), "#3B2A20");
            string outfitId = Id(CosmeticSlot.Outfit, "outfit_safari");
            var outfitInfo = CosmeticCatalog.Resolve(outfitId);
            Color outfit = ColorOf(outfitId, "#C9B37E");
            string outfitColorOverride;
            if (_loadout.TryGetValue(CosmeticSlot.OutfitColor, out outfitColorOverride) && !string.IsNullOrEmpty(outfitColorOverride)) outfit = ColorOf(outfitColorOverride, "#C9B37E");
            Color pants = Color.Lerp(outfit, Palette.Hex("#6E5B45"), 0.55f);
            Color shoes = ColorOf(Id(CosmeticSlot.Shoes, "shoes_boots"), "#7A4B2A");

            // Blob gölge (zeminde kalır, zıplamaz)
            var shadow = Prim.Cyl(_model, new Vector3(0, 0.01f, 0), 0.7f, 0.01f, new Color(0.15f, 0.22f, 0.29f, 0.22f));
            shadow.name = "BlobShadow";
            _shadow = shadow.transform;

            _body = Prim.Group("Body", _model);
            _hipY = legLen + 0.06f;
            _bodyBaseY = 0f;

            // Bacaklar (kalça pivotundan sarkar)
            _hipL = Prim.Group("HipL", _body, new Vector3(-0.12f, _hipY, 0));
            _hipR = Prim.Group("HipR", _body, new Vector3(0.12f, _hipY, 0));
            foreach (var hip in new[] { _hipL, _hipR })
            {
                Prim.Capsule(hip, new Vector3(0, -legLen * 0.5f, 0), 0.17f, legLen, pants);
                Prim.Box(hip, new Vector3(0, -legLen + 0.02f, 0.05f), new Vector3(0.17f, 0.1f, 0.26f), shoes);
            }

            // Gövde
            float torsoY = _hipY + torsoH * 0.5f - 0.04f;
            Prim.Capsule(_body, new Vector3(0, torsoY, 0), torsoW, torsoH + 0.1f, outfit);
            Prim.Box(_body, new Vector3(0, _hipY + 0.02f, 0), new Vector3(torsoW * 0.95f, 0.07f, torsoW * 0.9f), Palette.Hex("#6E5B45")); // kemer
            Prim.Box(_body, new Vector3(0, _hipY + 0.02f, torsoW * 0.45f), new Vector3(0.09f, 0.08f, 0.03f), Palette.Gold); // toka

            bool special = outfitInfo != null && (outfitInfo.Source == CosmeticSource.WonderLevel || outfitInfo.Source == CosmeticSource.Mastery);
            if (special)
            {
                // Harika/ustalık kıyafetleri: pelerin + altın yaka
                var cape = Prim.Box(_body, new Vector3(0, torsoY - 0.02f, -torsoW * 0.5f), new Vector3(torsoW * 1.05f, torsoH + 0.1f, 0.04f), Palette.Darken(outfit, 0.15f));
                cape.transform.localRotation = Quaternion.Euler(-8f, 0, 0);
                Prim.Cyl(_body, new Vector3(0, torsoY + torsoH * 0.5f + 0.02f, 0), torsoW * 0.8f, 0.06f, Palette.Gold);
            }

            // Kollar (omuz pivotundan sarkar)
            float shY = torsoY + torsoH * 0.38f;
            _shL = Prim.Group("ShoulderL", _body, new Vector3(-torsoW * 0.55f - 0.04f, shY, 0));
            _shR = Prim.Group("ShoulderR", _body, new Vector3(torsoW * 0.55f + 0.04f, shY, 0));
            foreach (var sh in new[] { _shL, _shR })
            {
                Prim.Capsule(sh, new Vector3(0, -0.18f, 0), 0.13f, 0.38f, outfit);
                Prim.Ball(sh, new Vector3(0, -0.4f, 0), 0.12f, skin);
            }
            _found = Prim.Group("FoundItem", _shR, new Vector3(0, -0.5f, 0));
            Prim.Ball(_found, Vector3.zero, 0.16f, Palette.Gold).GetComponent<Renderer>().sharedMaterial = MaterialLibrary.Get(Palette.Gold, 0.5f);
            _found.gameObject.SetActive(_anim == ExplorerAnim.Discover);

            // Baş
            float headY = torsoY + torsoH * 0.5f + headD * 0.48f;
            _head = Prim.Group("Head", _body, new Vector3(0, headY, 0));
            Prim.Ball(_head, Vector3.zero, headD, skin);
            Prim.Ball(_head, new Vector3(-0.1f, 0.02f, headD * 0.46f), 0.07f, Palette.Text);
            Prim.Ball(_head, new Vector3(0.1f, 0.02f, headD * 0.46f), 0.07f, Palette.Text);
            Prim.Ball(_head, new Vector3(-0.17f, -0.07f, headD * 0.42f), 0.07f, Palette.WithAlpha(Palette.Coral, 0.6f));
            Prim.Ball(_head, new Vector3(0.17f, -0.07f, headD * 0.42f), 0.07f, Palette.WithAlpha(Palette.Coral, 0.6f));

            // Saç: A = kâküllü kısa saç, B = at kuyruklu
            var cap = Prim.Ball(_head, new Vector3(0, 0.06f, -0.04f), headD * 1.06f, hair);
            cap.transform.localScale = new Vector3(headD * 1.08f, headD * 0.9f, headD * 1.06f);
            if (!bodyB)
            {
                var fringe = Prim.Box(_head, new Vector3(0, headD * 0.3f, headD * 0.34f), new Vector3(headD * 0.8f, 0.09f, 0.12f), hair);
                fringe.transform.localRotation = Quaternion.Euler(20f, 0, 0);
            }
            else
            {
                Prim.Ball(_head, new Vector3(0, 0.02f, -headD * 0.62f), 0.22f, hair);
                Prim.Capsule(_head, new Vector3(0, -0.18f, -headD * 0.66f), 0.13f, 0.3f, hair);
            }

            BuildHat(Id(CosmeticSlot.Hat, "hat_none"), headD);
            BuildBag(Id(CosmeticSlot.Bag, "bag_satchel"), torsoW, torsoY);
            BuildAccessory(Id(CosmeticSlot.Accessory, "acc_none"), torsoY + torsoH * 0.5f, torsoW);
        }

        private void BuildHat(string id, float headD)
        {
            if (id == "hat_none") return;
            Color c = ColorOf(id, "#C9B37E");
            var hat = Prim.Group("Hat", _head, new Vector3(0, headD * 0.36f, -0.02f));
            if (id == "hat_cap")
            {
                var dome = Prim.FromMesh(MeshFactory.Dome(), hat, new Vector3(0, -0.02f, 0), new Vector3(headD * 1.05f, headD * 0.8f, headD * 1.05f), c, "cap");
                dome.name = "Cap";
                Prim.Box(hat, new Vector3(0, -0.01f, headD * 0.5f), new Vector3(headD * 0.6f, 0.03f, headD * 0.35f), Palette.Darken(c, 0.15f));
            }
            else if (id == "hat_straw")
            {
                Prim.Cyl(hat, Vector3.zero, headD * 1.9f, 0.03f, c);
                Prim.Cyl(hat, new Vector3(0, 0.1f, 0), headD * 0.85f, 0.2f, c);
                Prim.Cyl(hat, new Vector3(0, 0.03f, 0), headD * 0.88f, 0.05f, Palette.Coral);
            }
            else if (id.StartsWith("lvl_hat_"))
            {
                Prim.Cyl(hat, new Vector3(0, 0.08f, 0), headD * 0.75f, 0.2f, c);
                for (int i = 0; i < 5; i++)
                {
                    float a = i * Mathf.PI * 2f / 5f;
                    Prim.Ball(hat, new Vector3(Mathf.Cos(a) * headD * 0.36f, 0.2f, Mathf.Sin(a) * headD * 0.36f), 0.07f, Palette.Gold);
                }
            }
            else // hat_explorer ve diğerleri
            {
                Prim.Cyl(hat, Vector3.zero, headD * 1.55f, 0.03f, c);
                Prim.Cyl(hat, new Vector3(0, 0.1f, 0), headD * 0.82f, 0.2f, c);
                Prim.Cyl(hat, new Vector3(0, 0.04f, 0), headD * 0.85f, 0.05f, Palette.Darken(c, 0.35f));
            }
        }

        private void BuildBag(string id, float torsoW, float torsoY)
        {
            if (string.IsNullOrEmpty(id)) return;
            Color c = ColorOf(id, "#7A4B2A");
            string bagColor;
            if (_loadout.TryGetValue(CosmeticSlot.BagColor, out bagColor) && !string.IsNullOrEmpty(bagColor)) c = ColorOf(bagColor, "#7A4B2A");
            if (id == "bag_backpack")
            {
                Prim.Box(_body, new Vector3(0, torsoY + 0.02f, -torsoW * 0.55f), new Vector3(torsoW * 0.8f, 0.42f, 0.2f), c);
                Prim.Box(_body, new Vector3(0, torsoY - 0.08f, -torsoW * 0.68f), new Vector3(torsoW * 0.6f, 0.16f, 0.08f), Palette.Darken(c, 0.15f));
            }
            else
            {
                // Çapraz askılı çanta
                var strap = Prim.Box(_body, new Vector3(0, torsoY + 0.02f, torsoW * 0.48f), new Vector3(0.05f, 0.62f, 0.03f), Palette.Darken(c, 0.2f));
                strap.transform.localRotation = Quaternion.Euler(0, 0, 40f);
                Prim.Box(_body, new Vector3(torsoW * 0.55f, torsoY - 0.2f, 0.06f), new Vector3(0.12f, 0.24f, 0.28f), c);
            }
        }

        private void BuildAccessory(string id, float neckY, float torsoW)
        {
            if (string.IsNullOrEmpty(id) || id == "acc_none") return;
            Color c = ColorOf(id, "#D5A447");
            Prim.Cyl(_body, new Vector3(0, neckY, 0), torsoW * 0.72f, 0.07f, c);
            Prim.Ball(_body, new Vector3(0, neckY - 0.12f, torsoW * 0.45f), 0.1f, Palette.Lighten(c, 0.2f));
        }

        // ------------------------------------------------------------------ animasyon
        private void OnEnable() { _lastPos = transform.position; }

        private void Update()
        {
            if (_body == null) return;
            float dt = Time.deltaTime;
            if (dt <= 0f) return;
            _animT += dt;

            Vector3 d = transform.position - _lastPos; d.y = 0f;
            _lastPos = transform.position;
            float measured = d.magnitude / dt;
            _speed = Mathf.Lerp(_speed, measured, 1f - Mathf.Exp(-dt * 10f));

            if (IsOneShot(_anim) && _animT >= Duration(_anim)) Play(_speed > 0.3f ? ExplorerAnim.Walk : ExplorerAnim.Idle);
            if (_anim == ExplorerAnim.Idle && _speed > 0.4f) Play(ExplorerAnim.Walk);
            else if (_anim == ExplorerAnim.Walk && _speed < 0.08f) Play(ExplorerAnim.Idle);

            // Temel poz
            float bodyY = _bodyBaseY, modelY = 0f, modelYaw = 0f, lean = 0f;
            float legL = 0f, legR = 0f, armLx = 0f, armRx = 0f, armLz = 8f, armRz = -8f, headX = 0f;
            float t = _animT;

            switch (_anim)
            {
                case ExplorerAnim.Idle:
                    bodyY += Mathf.Sin(t * 2.2f) * 0.012f;
                    armLz += Mathf.Sin(t * 2.2f) * 2f; armRz -= Mathf.Sin(t * 2.2f) * 2f;
                    headX = Mathf.Sin(t * 0.9f) * 3f;
                    break;
                case ExplorerAnim.Walk:
                    {
                        float k = Mathf.Clamp01(_speed / 3f);
                        _walkPhase += dt * (5f + _speed * 2.2f);
                        float s = Mathf.Sin(_walkPhase);
                        legL = s * 38f * k; legR = -s * 38f * k;
                        armLx = -s * 32f * k; armRx = s * 32f * k;
                        bodyY += Mathf.Abs(Mathf.Cos(_walkPhase)) * 0.045f * k;
                        lean = 6f * k;
                        break;
                    }
                case ExplorerAnim.Turn:
                    lean = 4f;
                    armLz += 10f * Mathf.Sin(t / 0.3f * Mathf.PI) * _leanSign;
                    armRz += 10f * Mathf.Sin(t / 0.3f * Mathf.PI) * _leanSign;
                    break;
                case ExplorerAnim.Interact:
                    {
                        float k = t < 0.3f ? t / 0.3f : t < 0.6f ? 1f : 1f - (t - 0.6f) / 0.3f;
                        k = Mathf.Clamp01(k);
                        armRx = -80f * k; lean = 12f * k; headX = 12f * k;
                        bodyY -= 0.04f * k;
                        break;
                    }
                case ExplorerAnim.Discover:
                    {
                        float crouch = t < 0.4f ? t / 0.4f : Mathf.Clamp01(1f - (t - 0.4f) / 0.25f);
                        bodyY -= 0.16f * crouch;
                        legL = legR = -25f * crouch;
                        float raise = Mathf.Clamp01((t - 0.55f) / 0.35f);
                        armRx = Mathf.Lerp(-40f * crouch, -165f, raise);
                        armLx = -60f * raise;
                        headX = -15f * raise;
                        break;
                    }
                case ExplorerAnim.Celebrate:
                    {
                        float hop = Mathf.Abs(Mathf.Sin(t / 0.7f * Mathf.PI));
                        modelY = hop * 0.42f;
                        modelYaw = Mathf.Clamp01(t / 1.2f) * 360f;
                        armLx = armRx = -160f; armLz = 25f; armRz = -25f;
                        legL = legR = -15f * hop;
                        break;
                    }
            }

            _body.localPosition = new Vector3(0, bodyY, 0);
            _body.localRotation = Quaternion.Euler(lean, 0, 0);
            var mp = _model.localPosition; mp.y = modelY; _model.localPosition = mp;
            _model.localRotation = Quaternion.Euler(0, modelYaw, 0);
            _hipL.localRotation = Quaternion.Euler(legL, 0, 0);
            _hipR.localRotation = Quaternion.Euler(legR, 0, 0);
            _shL.localRotation = Quaternion.Euler(armLx, 0, armLz);
            _shR.localRotation = Quaternion.Euler(armRx, 0, armRz);
            _head.localRotation = Quaternion.Euler(headX, 0, 0);

            // Gölge zıplamada zeminde kalsın
            if (_shadow != null) _shadow.localPosition = new Vector3(0, 0.01f - modelY, 0);
        }
    }
}
