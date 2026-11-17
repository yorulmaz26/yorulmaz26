package com.kuaforumcepte.app.feature.schedule

import com.kuaforumcepte.app.data.schedule.ScheduleConflict

/** Önizle → (çakışma varsa) onay → uygula akışının ekran durumu. */
sealed interface ChangeState {
    data object Idle : ChangeState
    data object Checking : ChangeState
    data class AwaitingConfirmation(val conflicts: List<ScheduleConflict>) : ChangeState
    data class Applying(val conflicts: List<ScheduleConflict>) : ChangeState
}
