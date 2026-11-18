package com.golgen.oyun.core;

/** Dünya olaylarını (ses, titreşim, parçacık) arayüze iletir. Tahsis yapmaz. */
public interface GameEvents {
    int DIAMOND = 1;
    int HIT = 2;
    int SHADOW_SPAWN = 3;
    int DODGE = 4;
    int DEATH = 5;
    int WIN = 6;

    void onEvent(int type, float x, float y);
}
