package com.isikyolu.engine;

import java.util.ArrayDeque;
import java.util.Deque;

/** Bir bölümün oynanışı: hamleler, geri alma, sıfırlama, ipucu ve tamamlanma. */
public final class GameSession {
    public final Level level;
    private Board board;
    private final Deque<Move> history = new ArrayDeque<>();
    private BeamTracer.Result result;
    private int hintsUsed;
    /** Bu denemede yapılan toplam hamle (geri almalar iade edilmez). */
    private int actions;

    /** Hamle hakkı sınırı uygulanıyor mu (bölüm modu). */
    public final boolean limited;

    public GameSession(Level level) { this(level, false); }

    public GameSession(Level level, boolean limited) {
        this.level = level;
        this.limited = limited;
        reset();
    }

    public Board board() { return board; }
    public BeamTracer.Result result() { return result; }
    public int moves() { return history.size(); }
    public boolean canUndo() { return !history.isEmpty(); }
    public boolean isSolved() { return result.solved; }
    public int hintsUsed() { return hintsUsed; }
    public void markHintUsed() { hintsUsed++; }

    private int bonusBudget, continues;

    /** Kalan hamle hakkı. */
    public int budgetLeft() { return Lives.moveBudget(level.optimal) + bonusBudget - actions; }

    /** Marketten/biletle ek hak (bu denemede kaçıncı devam olduğu da sayılır). */
    public void addBudget(int n) { bonusBudget += n; continues++; }

    public int continues() { return continues; }
    public int actions() { return actions; }
    /** Hak bitti ve çözülmedi mi? */
    public boolean isOutOfMoves() { return limited && !result.solved && budgetLeft() <= 0; }

    public void reset() {
        board = new Board(level);
        history.clear();
        actions = 0;
        bonusBudget = 0;
        continues = 0;
        retrace();
    }

    private void retrace() { result = BeamTracer.trace(board); }

    /** Hücreye dokunma: döndürülebilir aynayı 90° çevirir. */
    public boolean tap(int x, int y) {
        if (!board.inside(x, y) || result.solved) return false;
        return play(Move.rotate(board.cell(x, y)));
    }

    /** Hareketli bloğu bir hücre iter. */
    public boolean drag(int x, int y, int dir) {
        if (!board.inside(x, y) || result.solved) return false;
        return play(Move.push(board.cell(x, y), dir));
    }

    public boolean play(Move m) {
        if (result.solved) return false;
        if (limited && budgetLeft() <= 0) return false;
        if (m.kind == Move.ROTATE && !board.isRotatable(m.cell)) return false;
        if (m.kind == Move.PUSH && !board.canPush(m.cell, m.dir)) return false;
        board.apply(m);
        history.push(m);
        actions++;
        retrace();
        return true;
    }

    public Move lastMove() { return history.peek(); }

    public boolean undo() {
        if (history.isEmpty() || result.solved) return false;
        board.revert(history.pop());
        retrace();
        return true;
    }

    public int stars() { return StarRating.stars(moves(), level.optimal); }

    /** Akıllı ipucu planı: kaç kez Geri Al (bedava) gerektiği, sonraki hamle ve kalan en az hamle. */
    public static final class Plan {
        /** Önce kaç kez Geri Al yapılmalı (0 = doğrudan hamle). */
        public int undos;
        /** undos == 0 iken yapılacak hamle. */
        public Move move;
        /** Geri almalardan sonra çözüme kalan en az hamle (bulunamazsa -1). */
        public int remaining = -1;
        /** Kalan hak bu planla bölümü bitirmeye yetiyor mu. */
        public boolean feasible;
    }

    /**
     * Geri Al hak harcamadığı için en iyi yol bazen önce geri almaktan geçer. Geçmişteki her ara
     * durumun çözüme uzaklığına bakılır; en kısa olan (eşitlikte en az geri alan) seçilir.
     */
    public Plan plan(int stateCap) {
        Plan pl = new Plan();
        java.util.Map<Long, Integer> dist = Solver.distancesToGoal(board, stateCap);
        if (dist == null) return pl;
        Board b = board.copy();
        Integer d0 = dist.get(b.stateKey());
        int best = d0 == null ? Integer.MAX_VALUE : d0, bestUndo = 0, i = 0;
        for (Move m : history) { // en son hamleden geriye
            b.revert(m);
            i++;
            Integer d = dist.get(b.stateKey());
            if (d != null && d < best) { best = d; bestUndo = i; }
        }
        if (best == Integer.MAX_VALUE) return pl;
        pl.undos = bestUndo;
        pl.remaining = best;
        pl.feasible = !limited || best <= budgetLeft();
        if (bestUndo == 0 && best > 0) {
            for (Move m : board.legalMoves()) {
                board.apply(m);
                Integer d = dist.get(board.stateKey());
                board.revert(m);
                if (d != null && d == best - 1) { pl.move = m; break; }
            }
        }
        return pl;
    }

    /** Mevcut durumdan en kısa çözümün ilk hamlesi (yoksa null). */
    public Move hint(int stateCap) {
        Solver.Solution s = Solver.solve(board, stateCap);
        return s.solvable && !s.moves.isEmpty() ? s.moves.get(0) : null;
    }

    /** Mevcut durumdan kalan en az hamle (çözülemezse -1). */
    public int remainingOptimal(int stateCap) {
        Solver.Solution s = Solver.solve(board, stateCap);
        return s.solvable ? s.optimal : -1;
    }
}
