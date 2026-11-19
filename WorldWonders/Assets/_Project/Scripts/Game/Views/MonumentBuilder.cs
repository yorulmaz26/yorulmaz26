using UnityEngine;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    // İSKELET (B değiştirecek)
    public sealed class MonumentModel : MonoBehaviour
    {
        public int Wonder;
        public float Height = 6f;
        public void SetBuilt(int group, bool built, bool animate) { }
        public void SyncFrom(GameSession s) { }
    }
    public static class MonumentBuilder
    {
        public static MonumentModel Build(int wonder, Transform parent) { var go = new GameObject("Monument"); go.transform.SetParent(parent, false); var m = go.AddComponent<MonumentModel>(); m.Wonder = wonder; return m; }
    }
}
