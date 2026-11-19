// UYARI: Bu dosya Unity içinde derlenir; bu depodaki CI/test ortamında Unity olmadığı için derlenmedi.
// Çekirdek (Assets/Scripts/Core) ise Tests/ altında derlenip prototiple birebir karşılaştırılıyor.
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using WorldWonders.Core;

namespace WorldWonders.View
{
    /// <summary>
    /// En sade Match-3 görünümü: çekirdek Board'u çalıştırır, her hamleden sonra
    /// taş kimliklerine (Gem.Id) göre görünümü eşitler. Yeni taşlar yukarıdan düşer,
    /// silinenler küçülerek kaybolur. Efektler için board.Events okunabilir.
    /// </summary>
    public sealed class BoardView : MonoBehaviour
    {
        [SerializeField] int levelNumber = 1;
        [SerializeField] float cellSize = 1f;
        [SerializeField] SpriteRenderer piecePrefab;
        [SerializeField] Sprite[] stoneSprites = new Sprite[6];   // sikke, parşömen, amfora, skarabe, pusula, taç
        [SerializeField] Sprite diskSprite, relicSprite, blockSprite;
        [SerializeField] Color lineTint = new Color(1f, .95f, .7f), bombTint = new Color(1f, .8f, .55f);
        [SerializeField] float fallSpeed = 12f;

        public Board Board { get; private set; }
        readonly Dictionary<int, SpriteRenderer> gems = new Dictionary<int, SpriteRenderer>();
        readonly Dictionary<int, SpriteRenderer> blocks = new Dictionary<int, SpriteRenderer>();
        bool busy; Vector2Int? dragStart; Vector3 dragWorld;

        void Start() { Board = new Board(LevelGenerator.Make(levelNumber)); SyncView(instant: true); }

        Vector3 CellPos(int r, int c) => transform.position + new Vector3((c - (GameData.Cols - 1) / 2f) * cellSize, ((GameData.Rows - 1) / 2f - r) * cellSize, 0);

        Vector2Int? CellAt(Vector3 world)
        {
            var local = world - transform.position;
            int c = Mathf.FloorToInt(local.x / cellSize + GameData.Cols / 2f), r = Mathf.FloorToInt(GameData.Rows / 2f - local.y / cellSize);
            return r >= 0 && r < GameData.Rows && c >= 0 && c < GameData.Cols ? new Vector2Int(r, c) : (Vector2Int?)null;
        }

        void Update()
        {
            if (busy || Board.Over || Camera.main == null) return;
            var world = Camera.main.ScreenToWorldPoint(Input.mousePosition); world.z = 0;
            if (Input.GetMouseButtonDown(0)) { dragStart = CellAt(world); dragWorld = world; }
            else if (Input.GetMouseButton(0) && dragStart.HasValue)
            {
                var d = world - dragWorld; if (d.magnitude < cellSize * .4f) return;
                var a = dragStart.Value; var b = Mathf.Abs(d.x) > Mathf.Abs(d.y) ? a + new Vector2Int(0, d.x > 0 ? 1 : -1) : a + new Vector2Int(d.y > 0 ? -1 : 1, 0);
                dragStart = null;
                if (b.x >= 0 && b.x < GameData.Rows && b.y >= 0 && b.y < GameData.Cols) StartCoroutine(DoSwap(new Pos(a.x, a.y), new Pos(b.x, b.y)));
            }
            else if (Input.GetMouseButtonUp(0)) dragStart = null;
        }

        IEnumerator DoSwap(Pos a, Pos b)
        {
            busy = true; Board.Events.Clear();
            var ga = Board.Grid[a.R, a.C]; var gb = Board.Grid[b.R, b.C];
            bool ok = Board.TrySwap(a, b);
            if (ga != null && gb != null && gems.TryGetValue(ga.Id, out var va) && gems.TryGetValue(gb.Id, out var vb))
            {
                yield return Move2(va.transform, CellPos(b.R, b.C), vb.transform, CellPos(a.R, a.C), .15f);
                if (!ok) yield return Move2(va.transform, CellPos(a.R, a.C), vb.transform, CellPos(b.R, b.C), .15f);
            }
            if (ok) yield return SyncView(instant: false);
            busy = false;
            if (Board.Over) Debug.Log(Board.Won ? "Bölüm kazanıldı" : "Hamle bitti");
        }

        IEnumerator Move2(Transform t1, Vector3 p1, Transform t2, Vector3 p2, float dur)
        {
            Vector3 s1 = t1.position, s2 = t2.position;
            for (float t = 0; t < 1; t += Time.deltaTime / dur) { t1.position = Vector3.Lerp(s1, p1, t); t2.position = Vector3.Lerp(s2, p2, t); yield return null; }
            t1.position = p1; t2.position = p2;
        }

        Sprite SpriteFor(Gem g) => g.Kind == GemKind.Disk ? diskSprite : g.Kind == GemKind.Relic ? relicSprite : stoneSprites[g.Color];

        IEnumerator SyncView(bool instant)
        {
            var alive = new HashSet<int>(); var targets = new List<(Transform t, Vector3 to)>();
            for (int r = 0; r < GameData.Rows; r++) for (int c = 0; c < GameData.Cols; c++)
            {
                var g = Board.Grid[r, c]; if (g == null) continue; alive.Add(g.Id);
                if (!gems.TryGetValue(g.Id, out var v))
                {
                    v = Instantiate(piecePrefab, transform); gems[g.Id] = v;
                    v.transform.position = instant ? CellPos(r, c) : CellPos(-1, c);
                }
                v.sprite = SpriteFor(g);
                v.color = g.Sp == 'b' ? bombTint : (g.Sp == 'h' || g.Sp == 'v') ? lineTint : Color.white;
                targets.Add((v.transform, CellPos(r, c)));
            }
            // silinen taşlar
            var dead = new List<int>(); foreach (var kv in gems) if (!alive.Contains(kv.Key)) dead.Add(kv.Key);
            foreach (var id in dead) { var v = gems[id]; gems.Remove(id); if (instant) Destroy(v.gameObject); else StartCoroutine(PopOut(v)); }
            SyncBlocks();
            if (instant) { foreach (var (t, to) in targets) t.position = to; yield break; }
            bool moving = true;
            while (moving)
            {
                moving = false;
                foreach (var (t, to) in targets) { t.position = Vector3.MoveTowards(t.position, to, fallSpeed * cellSize * Time.deltaTime); if (t.position != to) moving = true; }
                yield return null;
            }
        }

        IEnumerator PopOut(SpriteRenderer v)
        {
            for (float t = 0; t < 1; t += Time.deltaTime / .18f) { v.transform.localScale = Vector3.one * (1 - t); yield return null; }
            Destroy(v.gameObject);
        }

        void SyncBlocks()
        {
            for (int r = 0; r < GameData.Rows; r++) for (int c = 0; c < GameData.Cols; c++)
            {
                int k = r * GameData.Cols + c; var b = Board.Blocks[r, c];
                if (b != null && !blocks.ContainsKey(k)) { var v = Instantiate(piecePrefab, transform); v.sprite = blockSprite; v.transform.position = CellPos(r, c); blocks[k] = v; }
                else if (b == null && blocks.TryGetValue(k, out var old)) { blocks.Remove(k); Destroy(old.gameObject); }
            }
        }
    }
}
