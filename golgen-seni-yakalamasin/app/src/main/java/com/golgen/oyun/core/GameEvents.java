package com.golgen.oyun.core;

/** Dünya olaylarını (ses, titreşim, parçacık) arayüze iletir. Tahsis yapmaz. */
public interface GameEvents {
    int DIAMOND = 1;
    int HIT = 2;
    int SHADOW_SPAWN = 3;
    int DODGE = 4;
    int DEATH = 5;
    int WIN = 6;
    int POWERUP = 7;       // GameWorld.lastPowerup türü
    int SHIELD_BREAK = 8;
    int STAGE = 9;         // GameWorld.stage yeni aşama
    int COMBO = 10;        // çarpan arttı

    void onEvent(int type, float x, float y);
}
