package club.xiaojiawei.bean

import club.xiaojiawei.enums.CardTypeEnum
import kotlin.math.max
import club.xiaojiawei.bean.Details
import com.google.gson.annotations.Expose

data class PredictActionRequest (
    @Expose var position: String,
    @Expose val hero_play: Boolean,
    @Expose val round: Int,
    @Expose val crystal: Int,
    @Expose val player_hand_cards: List<Details>,
    @Expose val player_deck_cards: List<String>,
    @Expose val played_actions: List<List<Details>>,
    @Expose val rival_battle_cards: List<Details>,
    @Expose val companion_battle_cards: List<Details>,
    @Expose val companion_died: List<Details>,
    @Expose val companion_burst_cards: List<Details>,
    @Expose val rivalHeroDetails: Details,
    @Expose val meHeroDetails: Details,
    @Expose val attack_rival_hero: Int,
    @Expose val attack_me_hero: Int,
    @Expose val secret_size: Int,
    @Expose val meHeroPowerDetails: Details,
)