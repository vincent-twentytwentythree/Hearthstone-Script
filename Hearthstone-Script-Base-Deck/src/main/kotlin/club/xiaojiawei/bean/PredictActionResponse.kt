package club.xiaojiawei.bean

import club.xiaojiawei.enums.CardTypeEnum
import kotlin.math.max

import club.xiaojiawei.bean.Details
import com.google.gson.annotations.Expose

data class PredictActionResponse (
    @Expose val status: String,
    @Expose val action: List<Details>,
    @Expose val cardName: List<String>,
    @Expose val message: String,
    @Expose val cost: Int,
    @Expose val score: Int,
    @Expose val crystal: Int,
    @Expose val coreCards: MutableMap<String, Double>,
    @Expose val powerPlus: Int,
    @Expose val force_actions: List<ForceAction>,
    @Expose val needSurrender: Boolean,
) {
    data class ForceAction(
        @Expose var companion: Details,
        @Expose var rival: Details? = null,
    )
}