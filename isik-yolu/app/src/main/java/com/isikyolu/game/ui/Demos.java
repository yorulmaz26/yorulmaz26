package com.isikyolu.game.ui;

import com.isikyolu.engine.Dir;
import com.isikyolu.engine.Level;
import com.isikyolu.engine.LightColor;
import com.isikyolu.engine.Piece;

/** Öğretici kartlarındaki küçük, çözülmüş örnek tahtalar. */
public final class Demos {
    private Demos() {}

    public static Level intro() {
        return new Level(5, 3).add(Piece.source(0, 1, Dir.RIGHT, LightColor.WHITE)).add(Piece.target(4, 1, LightColor.WHITE))
                .add(Piece.wall(2, 0)).add(Piece.wall(2, 2));
    }

    public static Level chapter(int ch) {
        final int W = LightColor.WHITE;
        switch (ch) {
            case 1:
                return new Level(5, 4).add(Piece.source(0, 3, Dir.RIGHT, W)).add(Piece.mirror(3, 3, 0, true)).add(Piece.target(3, 0, W));
            case 2:
                return new Level(5, 4).add(Piece.source(0, 3, Dir.RIGHT, W)).add(Piece.mirror(2, 3, 0, true))
                        .add(Piece.mirror(2, 1, 0, true)).add(Piece.target(4, 1, W)).add(Piece.mirror(4, 3, 1, true));
            case 3:
                return new Level(5, 4).add(Piece.source(0, 0, Dir.RIGHT, W)).add(Piece.mirror(3, 0, 1, false))
                        .add(Piece.target(3, 3, W)).add(Piece.wall(1, 2)).add(Piece.wall(2, 2)).add(Piece.wall(4, 1)).add(Piece.wall(0, 3));
            case 4:
                return new Level(5, 5).add(Piece.source(0, 2, Dir.RIGHT, LightColor.RED)).add(Piece.source(2, 4, Dir.UP, LightColor.GREEN))
                        .add(Piece.target(2, 2, LightColor.YELLOW)).add(Piece.source(4, 0, Dir.LEFT, LightColor.BLUE)).add(Piece.target(0, 0, LightColor.BLUE));
            case 5:
                return new Level(5, 3).add(Piece.source(0, 1, Dir.RIGHT, W)).add(Piece.filter(2, 1, LightColor.RED)).add(Piece.target(4, 1, LightColor.RED))
                        .add(Piece.filter(2, 0, LightColor.BLUE)).add(Piece.filter(2, 2, LightColor.GREEN));
            case 6:
                return new Level(5, 5).add(Piece.source(0, 2, Dir.RIGHT, W)).add(Piece.prism(2, 2))
                        .add(Piece.target(2, 0, LightColor.RED)).add(Piece.target(4, 2, LightColor.GREEN)).add(Piece.target(2, 4, LightColor.BLUE));
            case 7:
                return new Level(5, 4).add(Piece.source(0, 0, Dir.RIGHT, W)).add(Piece.target(4, 0, W))
                        .add(Piece.source(0, 3, Dir.RIGHT, W)).add(Piece.mirror(3, 3, 0, true)).add(Piece.target(3, 1, W));
            case 8:
                return new Level(5, 4).add(Piece.source(0, 1, Dir.RIGHT, W)).add(Piece.target(4, 1, W)).add(Piece.block(2, 2))
                        .add(Piece.wall(2, 0));
            case 9:
                return new Level(5, 4).add(Piece.source(0, 0, Dir.RIGHT, W)).add(Piece.key(2, 0, 0)).add(Piece.target(4, 0, W))
                        .add(Piece.source(0, 2, Dir.RIGHT, W)).add(Piece.door(2, 2, 0)).add(Piece.target(4, 2, W));
            default:
                return new Level(6, 5).add(Piece.source(0, 2, Dir.RIGHT, W)).add(Piece.filter(1, 2, LightColor.YELLOW)).add(Piece.prism(3, 2))
                        .add(Piece.target(3, 0, LightColor.RED)).add(Piece.target(5, 2, LightColor.GREEN)).add(Piece.block(4, 4)).add(Piece.wall(3, 4));
        }
    }
}
