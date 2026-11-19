using UnityEngine;

namespace WorldWonders.Game
{
    // İSKELET (C1 değiştirecek)
    public sealed class CharacterPreview : MonoBehaviour
    {
        public static CharacterPreview Get() { var go = new GameObject("CharacterPreview"); return go.AddComponent<CharacterPreview>(); }
        public RenderTexture Texture { get { return null; } }
        public void Refresh() { }
        public void SetYaw(float yaw) { }
        public void Play(ExplorerAnim a) { }
        public void SetVisible(bool v) { }
    }
}
