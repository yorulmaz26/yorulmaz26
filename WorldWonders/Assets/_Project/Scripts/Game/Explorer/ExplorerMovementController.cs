using System;
using UnityEngine;

namespace WorldWonders.Game
{
    // İSKELET (C1 değiştirecek)
    public sealed class ExplorerMovementController : MonoBehaviour
    {
        public static ExplorerMovementController Attach(ExplorerCharacter c) { return c.gameObject.AddComponent<ExplorerMovementController>(); }
        public bool IsMoving { get { return false; } }
        public void WalkTo(Vector3 p, Action onArrive) { if (onArrive != null) onArrive(); }
        public void Warp(Vector3 p) { transform.position = p; }
    }
}
