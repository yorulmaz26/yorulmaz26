using System;
using UnityEngine;
using UnityEngine.AI;

namespace WorldWonders.Game
{
    /// <summary>
    /// Dokunarak hareket: NavMeshAgent ile hedefe yürür, varınca Idle oynatıp geri çağırır.
    /// NavMesh yoksa doğrudan doğrusal yürüyüşe düşer.
    /// </summary>
    public sealed class ExplorerMovementController : MonoBehaviour
    {
        public float Speed = 3.2f;
        private ExplorerCharacter _c;
        private NavMeshAgent _agent;
        private Action _onArrive;
        private bool _moving, _useAgent;
        private Vector3 _target;
        private float _moveTime;
        private Quaternion? _faceTo;

        public bool IsMoving { get { return _moving; } }
        public Vector3 Destination { get { return _target; } }

        public static ExplorerMovementController Attach(ExplorerCharacter c)
        {
            var m = c.gameObject.GetComponent<ExplorerMovementController>();
            if (m == null) m = c.gameObject.AddComponent<ExplorerMovementController>();
            m._c = c;
            if (m._agent == null)
            {
                m._agent = c.gameObject.AddComponent<NavMeshAgent>();
                m._agent.enabled = false;
                m._agent.speed = m.Speed;
                m._agent.acceleration = 14f;
                m._agent.angularSpeed = 0f;
                m._agent.radius = 0.3f;
                m._agent.height = 1.4f;
                m._agent.stoppingDistance = 0.12f;
                m._agent.updateRotation = false;
                m._agent.autoBraking = true;
                m._agent.obstacleAvoidanceType = ObstacleAvoidanceType.NoObstacleAvoidance;
            }
            return m;
        }

        private bool EnsureAgent()
        {
            if (_agent == null) return false;
            if (_agent.enabled && _agent.isOnNavMesh) return true;
            NavMeshHit hit;
            if (NavMesh.SamplePosition(transform.position, out hit, 3f, NavMesh.AllAreas))
            {
                _agent.enabled = true;
                _agent.Warp(hit.position);
                return _agent.isOnNavMesh;
            }
            _agent.enabled = false;
            return false;
        }

        public void WalkTo(Vector3 point, Action onArrive)
        {
            _onArrive = onArrive;
            _faceTo = null;
            Vector3 dest = point;
            NavMeshHit hit;
            _useAgent = false;
            if (EnsureAgent() && NavMesh.SamplePosition(point, out hit, 3f, NavMesh.AllAreas))
            {
                dest = hit.position;
                _agent.isStopped = false;
                _useAgent = _agent.SetDestination(dest);
            }
            _target = dest;
            _moving = true;
            _moveTime = 0f;

            Vector3 dir = dest - transform.position; dir.y = 0;
            if (_c != null)
            {
                if (dir.sqrMagnitude > 0.01f && Vector3.Angle(transform.forward, dir) > 70f) _c.Play(ExplorerAnim.Turn);
                else _c.Play(ExplorerAnim.Walk);
            }
        }

        public void Warp(Vector3 p)
        {
            _moving = false; _onArrive = null;
            if (_agent != null && _agent.enabled && _agent.isOnNavMesh) _agent.Warp(p);
            else transform.position = p;
            if (_c != null) _c.Play(ExplorerAnim.Idle);
        }

        public void Stop()
        {
            _moving = false; _onArrive = null;
            if (_agent != null && _agent.enabled && _agent.isOnNavMesh) _agent.ResetPath();
            if (_c != null) _c.Play(ExplorerAnim.Idle);
        }

        public void FaceTowards(Vector3 p)
        {
            Vector3 d = p - transform.position; d.y = 0;
            if (d.sqrMagnitude > 0.0001f) _faceTo = Quaternion.LookRotation(d);
        }

        private void Update()
        {
            float dt = Time.deltaTime;
            if (_faceTo.HasValue && !_moving)
            {
                transform.rotation = Quaternion.Slerp(transform.rotation, _faceTo.Value, 1f - Mathf.Exp(-dt * 10f));
                if (Quaternion.Angle(transform.rotation, _faceTo.Value) < 1f) _faceTo = null;
            }
            if (!_moving) return;
            _moveTime += dt;
            Vector3 vel;
            if (_useAgent && _agent.enabled && _agent.isOnNavMesh)
            {
                vel = _agent.velocity;
                bool reached = !_agent.pathPending && _agent.remainingDistance <= _agent.stoppingDistance + 0.05f;
                bool stuck = !_agent.pathPending && _moveTime > 0.6f && _agent.velocity.sqrMagnitude < 0.0004f;
                if (reached || stuck) { Arrive(); return; }
            }
            else
            {
                Vector3 to = _target - transform.position; to.y = 0;
                float dist = to.magnitude;
                if (dist < 0.06f) { Arrive(); return; }
                vel = to / dist * Speed;
                transform.position += Vector3.ClampMagnitude(vel * dt, dist);
            }
            vel.y = 0;
            if (vel.sqrMagnitude > 0.01f)
                transform.rotation = Quaternion.Slerp(transform.rotation, Quaternion.LookRotation(vel), 1f - Mathf.Exp(-dt * 12f));
            if (_c != null && _c.Current == ExplorerAnim.Idle) _c.Play(ExplorerAnim.Walk);
        }

        private void Arrive()
        {
            _moving = false;
            if (_agent != null && _agent.enabled && _agent.isOnNavMesh) _agent.ResetPath();
            if (_c != null && (_c.Current == ExplorerAnim.Walk || _c.Current == ExplorerAnim.Turn)) _c.Play(ExplorerAnim.Idle);
            var cb = _onArrive;
            _onArrive = null;
            if (cb != null) cb();
        }
    }
}
