using System.Collections.Generic;
using UnityEngine;
using WorldWonders.Core;

namespace WorldWonders.Game
{
    // İSKELET (C1 değiştirecek)
    public enum ExplorerAnim { Idle, Walk, Turn, Interact, Discover, Celebrate }
    public sealed class ExplorerCharacter : MonoBehaviour
    {
        public static ExplorerCharacter Create(Transform parent) { var go = new GameObject("Explorer"); go.transform.SetParent(parent, false); return go.AddComponent<ExplorerCharacter>(); }
        public void ApplyLoadout(IDictionary<CosmeticSlot, string> equipped) { }
        public void Play(ExplorerAnim anim) { }
    }
}
