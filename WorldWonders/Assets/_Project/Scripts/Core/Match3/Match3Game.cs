using System;
using System.Collections.Generic;
using WorldWonders.Core.Services;

namespace WorldWonders.Core.Match3
{
    public enum Match3State { Playing, Won, Lost }

    public sealed class GoalProgress
    {
        public GoalDefinition Def;
        public int Current;
        public bool Done { get { return Current >= Def.Count; } }
    }

    /// <summary>Bir bölüm oturumu: hamle sayacı, hedefler, kazanma/kaybetme ve yıldızlar.</summary>
    public sealed class Match3Game
    {
        public readonly LevelDefinition Level;
        public readonly Match3Board Board;
        public int MovesLeft { get; private set; }
        public Match3State State { get; private set; }
        public readonly List<GoalProgress> Goals = new List<GoalProgress>();
        public int MovesMade { get; private set; }

        public Match3Game(LevelDefinition level, ulong? seed = null)
        {
            Level = level;
            Board = Match3Board.FromDefinition(level, seed);
            MovesLeft = level.Moves;
            foreach (var g in level.Goals) Goals.Add(new GoalProgress { Def = g });
            State = Match3State.Playing;
        }

        public MoveResult Swap(Pos a, Pos b)
        {
            if (State != Match3State.Playing) return MoveResult.Invalid;
            var r = Board.TrySwap(a, b);
            if (!r.Valid) return r;
            MovesLeft--;
            MovesMade++;
            Evaluate();
            return r;
        }

        /// <summary>Oyun içi güçlendirici. Hamle harcamaz. Envanter düşümü çağıran tarafından yapılır.</summary>
        public MoveResult UseBooster(BoosterType booster, Pos target)
        {
            if (State != Match3State.Playing) return MoveResult.Invalid;
            MoveResult r;
            switch (booster)
            {
                case BoosterType.Hammer: r = Board.ApplyHammer(target); break;
                case BoosterType.AncientArrow: r = Board.ApplyPlacedSpecial(target, SpecialType.ArrowRow); break;
                case BoosterType.AncientBomb: r = Board.ApplyPlacedSpecial(target, SpecialType.Bomb); break;
                default: r = Board.ApplyShuffleBooster(); break;
            }
            if (r.Valid) Evaluate();
            return r;
        }

        /// <summary>Hamle bittiğinde ama bölüm kaybedildiğinde ek hamle verilmez (tasarımda yok); yalnız durum hesaplanır.</summary>
        private void Evaluate()
        {
            UpdateGoals();
            if (AllGoalsDone) State = Match3State.Won;
            else if (MovesLeft <= 0) State = Match3State.Lost;
        }

        public void UpdateGoals()
        {
            foreach (var g in Goals)
            {
                switch (g.Def.Type)
                {
                    case GoalType.Collect: g.Current = Board.ClearedByColor[(int)g.Def.Color]; break;
                    case GoalType.ClearSand: g.Current = Board.SandCleared; break;
                    case GoalType.BreakCrates: g.Current = Board.CratesBroken; break;
                    case GoalType.ReleaseChains: g.Current = Board.ChainsReleased; break;
                    case GoalType.Score: g.Current = Board.Score; break;
                }
                g.Current = Math.Min(g.Current, g.Def.Count);
            }
        }

        public bool AllGoalsDone
        {
            get { foreach (var g in Goals) if (!g.Done) return false; return true; }
        }

        public int Stars
        {
            get
            {
                if (State != Match3State.Won) return 0;
                return StarService.StarsFor(MovesLeft, Level.Star2MovesLeft, Level.Star3MovesLeft);
            }
        }
    }
}
