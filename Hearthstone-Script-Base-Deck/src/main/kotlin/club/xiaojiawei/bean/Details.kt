package club.xiaojiawei.bean

import club.xiaojiawei.enums.CardRaceEnum
import club.xiaojiawei.enums.CardTypeEnum
import club.xiaojiawei.bean.Card
import com.google.gson.annotations.Expose

open class Details(
    @Expose val cardId: String,
    @Expose val attack: Int,
    @Expose val hp: Int,
    @Expose val entityId: String,
    @Expose val cost: Int,
    @Expose val playedRound: Int,
    @Expose val isActive: Boolean = false,
    @Expose val card: BaseCard,
)