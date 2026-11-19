using System.Collections.Generic;
using UnityEngine;
using UnityEngine.AI;

namespace WorldWonders.Game
{
    /// <summary>
    /// Çalışma anında NavMesh üretimi (motor API'si; ek paket gerektirmez).
    /// Yalnız WalkableGround altındaki collider'lar yürünebilir yüzey olarak alınır; Tappable collider'lar yok sayılır,
    /// diğer collider'lar "Not Walkable" (alan 1) engel olarak işlenir.
    /// </summary>
    public static class RuntimeNavMesh
    {
        private static NavMeshDataInstance _instance;
        private static NavMeshData _data;

        public static bool HasData { get { return _instance.valid; } }

        public static void Build(Transform root, Bounds bounds)
        {
            Clear();
            if (root == null) return;
            var sources = new List<NavMeshBuildSource>();
            var markups = new List<NavMeshBuildMarkup>();
            NavMeshBuilder.CollectSources(root, ~0, NavMeshCollectGeometry.PhysicsColliders, 0, markups, sources);
            for (int i = sources.Count - 1; i >= 0; i--)
            {
                var s = sources[i];
                var comp = s.component;
                if (comp == null) continue;
                var col = comp as Collider;
                if ((col != null && (col.isTrigger || !col.enabled)) || comp.GetComponentInParent<Tappable>() != null)
                {
                    sources.RemoveAt(i);
                    continue;
                }
                if (comp.GetComponentInParent<WalkableGround>() == null)
                {
                    s.area = 1; // Not Walkable
                    sources[i] = s;
                }
            }
            var settings = NavMesh.GetSettingsByID(0);
            settings.agentRadius = 0.3f;
            settings.agentHeight = 1.4f;
            settings.agentClimb = 0.3f;
            settings.agentSlope = 40f;
            _data = NavMeshBuilder.BuildNavMeshData(settings, sources, bounds, Vector3.zero, Quaternion.identity);
            if (_data != null) _instance = NavMesh.AddNavMeshData(_data);
        }

        public static void Clear()
        {
            if (_instance.valid) NavMesh.RemoveNavMeshData(_instance);
            _instance = default(NavMeshDataInstance);
            if (_data != null) { Object.Destroy(_data); _data = null; }
        }
    }
}
