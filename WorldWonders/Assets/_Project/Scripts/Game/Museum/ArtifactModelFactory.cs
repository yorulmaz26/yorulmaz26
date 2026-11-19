using UnityEngine;
using WorldWonders.Core.Content;

namespace WorldWonders.Game
{
    // İSKELET (C2 değiştirecek)
    public static class ArtifactModelFactory
    {
        public static GameObject Build(ArtifactInfo a, Transform parent, bool collected) { var go = new GameObject(a.Id); go.transform.SetParent(parent, false); return go; }
    }
}
