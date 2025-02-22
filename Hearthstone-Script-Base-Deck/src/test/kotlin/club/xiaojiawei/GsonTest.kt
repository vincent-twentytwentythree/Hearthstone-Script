package club.xiaojiawei

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose

import club.xiaojiawei.bean.Card
import club.xiaojiawei.bean.BaseCard
import club.xiaojiawei.bean.Details
import club.xiaojiawei.bean.PredictActionRequest
import club.xiaojiawei.bean.PredictActionResponse

class GsonTest {
    val gson = GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .create()

    @Test
    fun `test serialization of PredictActionRequest`() {

        val card = BaseCard()
        val details = Details("123", 5, 10, "001", 3, 1, true, card)
    
        val predictActionRequest = PredictActionRequest(
            position = "test_position",
            hero_play = true,
            round = 1,
            crystal = 3,
            player_hand_cards = listOf(),
            player_deck_cards = listOf("deck1", "deck2"),
            played_actions = listOf(listOf()),
            rival_battle_cards = listOf(),
            companion_battle_cards = listOf(),
            companion_died = listOf(),
            companion_burst_cards = listOf(),
            rivalHeroDetails = details,
            meHeroDetails = details,
            attack_rival_hero = 1,
            attack_me_hero = 2,
            secret_size = 3,
            meHeroPowerDetails = details,
        )
    
        val json = gson.toJson(predictActionRequest)
        println(json)
        val newPredictActionRequest = gson.fromJson(json, PredictActionRequest::class.java)
    }


    @Test
    fun `test serialization of PredictActionResponse`() {
        val companionDetails = Details("cardId1", 5, 10, "entityId1", 3, 1, isActive = true, BaseCard())
        val rivalDetails = Details("cardId2", 4, 8, "entityId2", 2, 2, isActive = true, BaseCard())

        val forceAction = PredictActionResponse.ForceAction(companion = companionDetails, rival = rivalDetails)

        // Initialize coreCards map
        val coreCardsMap = mutableMapOf(
            "card1" to 1.0,
            "card2" to 2.5,
            "card3" to 3.0
        )

        val predictActionResponse = PredictActionResponse(
            status = "success",
            action = listOf(companionDetails, rivalDetails),
            cardName = listOf("Card1", "Card2"),
            message = "Action successfully predicted",
            cost = 10,
            score = 95,
            crystal = 5,
            coreCards = coreCardsMap,
            powerPlus = 7,
            force_actions = listOf(forceAction),
            needSurrender = true,
        )
    
        val json = gson.toJson(predictActionResponse)
        println(json)
        val newPredictActionRequest = gson.fromJson(json, PredictActionResponse::class.java)
    }
}
