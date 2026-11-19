package com.emirzinciri.robotkomutan.logic

import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.grid.Dir
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.level.LevelDef
import com.emirzinciri.robotkomutan.logic.level.ObjectiveDef
import com.emirzinciri.robotkomutan.logic.level.ObjectiveType
import com.emirzinciri.robotkomutan.logic.level.RobotDef
import com.emirzinciri.robotkomutan.logic.sim.Simulation

fun robot(id: String, x: Int, y: Int, dir: Dir = Dir.E, energy: Int = 10, max: Int = 10) =
    RobotDef(id, x, y, dir, energy = energy, maxEnergy = max)

fun level(
    vararg map: String,
    robots: List<RobotDef> = listOf(robot("A", 0, 0)),
    objectives: List<ObjectiveType> = listOf(ObjectiveType.REACH_EXIT),
    energy: Boolean = false,
    channels: Map<String, Int> = emptyMap(),
    values: Map<String, Int> = emptyMap(),
    maxTicks: Int = 100,
) = LevelData(
    LevelDef(
        id = 999, area = 1, name = "test", map = map.toList(), robots = robots,
        commands = CommandType.entries, maxCommands = 99, par = 99,
        objectives = objectives.map { ObjectiveDef(it) }, energy = energy,
        channels = channels, values = values, maxTicks = maxTicks,
    )
)

fun sim(level: LevelData, vararg programs: Pair<String, String>): Simulation =
    Simulation.create(level, programs.associate { (id, p) -> id to p.split(' ').filter { it.isNotBlank() } }).getOrThrow()
