package club.xiaojiawei

import club.xiaojiawei.bean.Card
import club.xiaojiawei.bean.Details
import club.xiaojiawei.bean.PredictActionRequest
import club.xiaojiawei.bean.PredictActionResponse
import club.xiaojiawei.bean.isValid
import club.xiaojiawei.config.log
import club.xiaojiawei.enums.CardTypeEnum
import club.xiaojiawei.enums.RunModeEnum
import club.xiaojiawei.status.War
import club.xiaojiawei.util.DeckStrategyUtil
import club.xiaojiawei.util.isFalse
import club.xiaojiawei.util.isTrue

import club.xiaojiawei.bean.area.PlayArea
import club.xiaojiawei.bean.area.HandArea
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStream
import java.io.InputStreamReader
import java.io.BufferedReader

import java.net.SocketException

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose

import club.xiaojiawei.status.War.firstPlayerGameId
import club.xiaojiawei.status.War.myGameId
import kotlin.collections.indexOfLast

/**
 * @author 肖嘉威
 * @date 2024/10/17 17:58
 */
class HsRadicalDeckStrategy : DeckStrategy() {

    private val commonDeckStrategy = HsCommonDeckStrategy()

    override fun name(): String {
        return "激进策略"
    }

    override fun getRunMode(): Array<RunModeEnum> {
        return arrayOf(RunModeEnum.CASUAL, RunModeEnum.STANDARD, RunModeEnum.WILD, RunModeEnum.PRACTICE)
    }

    override fun deckCode(): String {
        return ""
    }

    override fun id(): String {
        return "e71234fa-radical-deck-97e9-1f4e126cd33b"
    }

    override fun executeChangeCard(cards: HashSet<Card>) {
        commonDeckStrategy.executeChangeCard(cards)
    }

    override fun reset() {
        super.reset()
        minionNeededToBurst.clear()
        playedActions.clear()
        companionDied.clear()
        minionOnBattle.clear()
        activateLocaton.clear()
        cardToRound.clear() // MYWEN todo
        rivalMinionToRound.clear()
        rivalDetailsMap.clear()
        companionDetailsMap.clear()
        rivalHero = null
        meHero = null
        rivalHealth = 40
        meHealth = 30
        round = 0
    }

    private val minionNeededToBurst: ArrayList<Card> = arrayListOf()
    private val playedActions: ArrayList<MutableList<Card>> = arrayListOf()
    private val cardToRound: MutableMap<Card, Int> = mutableMapOf()
    private val rivalMinionToRound: MutableMap<Card, Int> = mutableMapOf()
    private val companionDied: ArrayList<Card> = arrayListOf()
    private val minionOnBattle: ArrayList<Card> = arrayListOf()
    private val activateLocaton: MutableMap<Card, Int> = mutableMapOf()
    private val rivalDetailsMap: MutableMap<String, Card> = mutableMapOf()
    private val companionDetailsMap: MutableMap<String, Card> = mutableMapOf()
    private var round: Int = 0
    private var rivalHealth: Int = 40
    private var meHealth: Int = 30
    private var rivalHero: Card? = null
    private var meHero: Card? = null
    private val gson = GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .create()

    fun convertToJson(predictActionRequest: PredictActionRequest): String {
        return gson.toJson(predictActionRequest)
    }

    fun loadJson(jsonString: String): PredictActionResponse {
        return gson.fromJson(jsonString, PredictActionResponse::class.java)
    }

    fun sendPostRequest(predictActionRequest: PredictActionRequest): PredictActionResponse {
        try {
            var jsonData = convertToJson(predictActionRequest)
            log.debug { "request: ${jsonData}" }
            val url = URL("http://localhost:8000")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
        
            // Write JSON data to the output stream
            connection.outputStream.use { os ->
                val input = jsonData.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
        
            // Read the response
            val responseCode = connection.responseCode
            println("Response Code: $responseCode")
        
            val response = StringBuilder()
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
            }
            val responseJson = response.toString()
            log.debug { "response: ${responseJson}" }
            return loadJson(responseJson)
        } catch (e: SocketException) {
            // Handle the specific SocketException
            log.error { "SocketException occurred: ${e.message}" }
        } catch (e: Exception) {
            // Handle other exceptions
            log.error { "An error occurred: ${e.message}" }
        }
        return PredictActionResponse("fail", arrayListOf(), arrayListOf(), "fail", 0, 0, 0, mutableMapOf(), 0, arrayListOf(), false)
    }

    private fun convertCardToDetails(card:Card, playedRound: Int = -1, isActive: Boolean = false): Details {
        return Details(
                card.cardId,
                card.atc,
                card.blood(),
                card.entityId,
                card.cost,
                playedRound,
                isActive,
                card,
            )
    }

    override fun executeOutCard() { // MYWEN
        if (War.rival.playArea.hero == null) {
            Thread.sleep(2000)
        }
        if (War.me.playArea.hero == null) {
            Thread.sleep(2000)
        }

        if (War.rival.playArea.hero != null) {
            rivalHero =  War.rival.playArea.hero as Card
        }
        else {
            log.error { "rival is null" }
        }
        
        if (War.me.playArea.hero != null) {
            meHero = War.me.playArea.hero as Card
        }
        else {
            log.error { "me is null" }
        }

        rivalHealth = rivalHero?.blood()?:40
        meHealth = meHero?.blood()?:30

        if (War.me.isValid()){
            round += 1
            val playedCard: MutableList<Card> = mutableListOf()
            var retry: Double = 0.0
            var overload = 0
            val companionsBeforeBattle = War.me.playArea.cards.toMutableList()
            minionOnBattle.filter { it.cardType == CardTypeEnum.MINION && !companionsBeforeBattle.contains(it)}.forEach {
                companionDied.add(it)
            }
            if (playedActions.size > 0) {
                var lastAction = playedActions.lastOrNull()
                if (lastAction != null) {
                    overload = lastAction.count { it.cardId == "CS3_007" }
                }
            }

            while (retry <= 5) {
                log.info { "resource: ${War.me.resources}, usableResource: ${War.me.usableResource}, overload: ${overload}, War.me.overloadLocked: ${War.me.overloadLocked}" }
                val me = War.me
                val rival = War.rival
                var plays = me.playArea.cards.toList()
                var toRivalList = War.rival.playArea.cards.toList().filter { it.canBeAttacked() }

                log.debug { "${retry}" }
                log.debug { "before filter minionNeededToBurst: $minionNeededToBurst" }
                minionNeededToBurst.removeIf { !plays.contains(it) } // remove died minion
                log.debug { "after filter minionNeededToBurst: $minionNeededToBurst" }
                log.debug { "rival: $toRivalList" }
                log.debug { "me: $plays" }

                var position = "landlord"
                if (firstPlayerGameId == myGameId) // MYWEN
                { // 先手
                    position = "landlord"
                }
                else {
                    position = "second_hand"
                }
                toRivalList.filter { !rivalMinionToRound.contains(it) }.forEach {
                    if (position == "landlord") {
                        rivalMinionToRound[it] = round - 1
                    }
                    else {
                        rivalMinionToRound[it] = round
                    }
                }

                // DETAILS MAP
                // battle
                toRivalList.forEach {
                    rivalDetailsMap[it.entityId] = it as Card
                }
                // hero
                if (War.rival.playArea.hero != null) {
                    rivalHero = War.rival.playArea.hero as Card
                }
                var rivalHeroDetails: Details = convertCardToDetails(rivalHero as Card, -1, rivalHero!!.canBeAttacked())
                rivalDetailsMap[rivalHero!!.entityId] = rivalHero as Card

                // hand
                me.handArea.cards.forEach {
                    var isActive = false
                    if (it.cardType === CardTypeEnum.LOCATION || it.cardType === CardTypeEnum.SPELL) {
                        isActive = true
                    }
                    else if (it.cardType == CardTypeEnum.MINION) {
                        isActive = false
                    }
                    companionDetailsMap[it.entityId] = it as Card
                }
                // battle
                plays.forEach {
                    var isActive = false
                    if (it.cardType === CardTypeEnum.LOCATION && !it.isLocationActionCooldown && activateLocaton.getOrDefault(it, -1) <= round - 2) {
                        isActive = true
                    }
                    if (it.cardType == CardTypeEnum.MINION && cardToRound.getOrDefault(it, -1) < round && it.canAttack(false) == true) {
                        isActive = true
                    }
                    companionDetailsMap[it.entityId] = it as Card
                }
                // hero
                if (War.me.playArea.hero != null) {
                    meHero = War.me.playArea.hero as Card
                }
                var meHeroDetails: Details = convertCardToDetails(meHero as Card)
                companionDetailsMap[meHero!!.entityId] = meHero as Card

                var predictActionRequest = PredictActionRequest(position,
                                                                War.me.playArea.power!!.isExhausted,
                                                                round,
                                                                me.usableResource + (War.me.overloadLocked - overload),
                                                                me.handArea.cards.map { it ->
                                                                    var isActive = false
                                                                    if (it.cardType === CardTypeEnum.LOCATION || it.cardType === CardTypeEnum.SPELL) {
                                                                        isActive = true
                                                                    }
                                                                    else if (it.cardType == CardTypeEnum.MINION) {
                                                                        isActive = false
                                                                    }
                                                                    convertCardToDetails(it, round, isActive)
                                                                },
                                                                arrayListOf(),
                                                                playedActions.map { it.map{ card -> convertCardToDetails(card) } },
                                                                toRivalList.map { convertCardToDetails(it, rivalMinionToRound.getOrDefault(it, -1), it!!.canBeAttacked()) },
                                                                plays.map { it ->
                                                                    var isActive = false
                                                                    if (it.cardType === CardTypeEnum.LOCATION && !it.isLocationActionCooldown && activateLocaton.getOrDefault(it, -1) <= round - 2) {
                                                                        isActive = true
                                                                    }
                                                                    if (it.cardType == CardTypeEnum.MINION && cardToRound.getOrDefault(it, -1) < round && it.canAttack(false) == true) {
                                                                        isActive = true
                                                                    }
                                                                    convertCardToDetails(it, cardToRound.getOrDefault(it, -1), isActive)
                                                                },
                                                                companionDied.map { convertCardToDetails(it) },
                                                                minionNeededToBurst.map { convertCardToDetails(it) },
                                                                rivalHeroDetails,
                                                                meHeroDetails,
                                                                maxOf(0, rivalHealth - (War.rival.playArea.hero?.blood()?:40)),
                                                                maxOf(0, meHealth - (War.me.playArea.hero?.blood()?:30)),
                                                                War.rival.secretArea.cards.size,
                                                                meHeroPowerDetails = convertCardToDetails(War.me.playArea.power as Card),
                                                                )
                var predictActionResponse = sendPostRequest(predictActionRequest)

                var mutableMap: MutableMap<Card, Double> = mutableMapOf()
                if (predictActionResponse.status == "succ") {
                    mutableMap = generateCoreCardMap(predictActionResponse)
                }
                else {
                    Thread.sleep(2000)
                    retry += 0.5
                    continue;
                }

                val activeCount = plays.count { it ->
                    if (it.cardType === CardTypeEnum.LOCATION && !it.isLocationActionCooldown && activateLocaton.getOrDefault(it, -1) <= round - 2) {
                        true
                    }
                    else if (it.cardType == CardTypeEnum.MINION && cardToRound.getOrDefault(it, -1) < round && it.canAttack(false) == true) {
                        true
                    }
                    else {
                        false
                    }
                }
                if (predictActionResponse.status == "succ" && predictActionResponse.force_actions.size == 0 &&
                    predictActionResponse.action.size == 0 &&
                    activeCount == 0 &&
                    (War.me.playArea.power!!.isExhausted == true || me.playArea.cards.count { it.cardId == "TOY_381" } == 0 && me.usableResource < 2)
                    ) {
                    break;
                }

                var playCardSucc = true
                val power = me.playArea.power as Card
                if (predictActionResponse.status == "succ" && predictActionResponse.force_actions.size > 0) {
                    log.debug { "playForceAction：${predictActionResponse}" }
                    log.debug { "hero power, ${me.playArea.power}" }
                    log.debug { "rivalDetailsMap: ${rivalDetailsMap.keys}"}
                    log.debug { "companionDetailsMap: ${companionDetailsMap.keys}" }
                    playForceAction(predictActionResponse, playedCard, companionsBeforeBattle)
                    val companionsAfterBattle = War.me.playArea.cards.toList()
                    companionsBeforeBattle.filter { it.cardType == CardTypeEnum.MINION && !companionsAfterBattle.contains(it) && !companionDied.contains(it)}.forEach {
                        companionDied.add(it)
                    }
                    retry += 0.5
                    continue
                }
                else if (predictActionResponse.status == "succ" && predictActionResponse.action.size != 0) {
                    if (useLocation(mutableMap) > 0) { // 地标执行成功，重新跑模型
                        log.debug { "retry: location" }
                        retry += 0.5
                        continue;
                    }
                    log.debug { "待出牌：${predictActionResponse}" }
                    log.debug { "hero power, ${me.playArea.power}" }
                    var getNewCard = false
                    for (cardDetails in predictActionResponse.action) {
                        var cardId = cardDetails.cardId
                        var entityId = cardDetails.entityId
                        var card: Card? = null
                        if (cardId == power.cardId) { // 英雄技能 MYWEN
                            card = power
                        }
                        else {
                            card = me.handArea.cards.filter { it.entityId == entityId }.firstOrNull()
                            if (card == null) {
                                if (cardId == "CFM_637" || cardId == "DRG_056") {
                                    log.debug { "海盗帕奇斯 CFM_637 空降歹徒 DRG_056 continue" }
                                    card = me.playArea.cards.filter { it.entityId == entityId }.firstOrNull()
                                    if (card != null) {
                                        companionsBeforeBattle.add(card)
                                        cardToRound[card] = round
                                        playedCard.add(card)
                                    }
                                    continue;
                                }
                                playCardSucc = false
                                break
                            }
                        }
                        log.debug { "usableResource: ${me.usableResource}, cost: ${card.cost}, card: $card"  }
                        // if (me.usableResource >= card.cost){ MYWEN
                            var handSize = me.handArea.cards.size
                            var battleSize = me.playArea.cards.size
                            var succ = playCard(card, mutableMap, predictActionResponse)
                            if (succ == true) {
                                if (card.cardType == CardTypeEnum.MINION) {
                                    companionsBeforeBattle.add(card)
                                }
                                playedCard.add(card)
                                cardToRound[card] = round
                                getNewCard = waitForUI(card, handSize, battleSize)
                                if (getNewCard == true) { // 有新牌，重新跑模型
                                    break
                                }
                            }
                            else {
                                log.error { "play card ERROR: ${card}"}
                                playCardSucc = false
                                break
                            }
                        // }
                    }
                    val companionsAfterBattle = War.me.playArea.cards.toList()
                    companionsBeforeBattle.filter { it.cardType == CardTypeEnum.MINION && !companionsAfterBattle.contains(it) && !companionDied.contains(it)}.forEach {
                        companionDied.add(it)
                    }
                    if (getNewCard == true) { // 有新牌，重新跑模型
                        retry += 0.5
                        continue
                    }
                }

                // 所有牌出完, 重新跑模型, 确认没有漏牌
                if (useLocation(mutableMap) > 0) { // 地标执行成功，重新跑模型
                    log.debug { "retry: location" }
                    retry += 0.5
                    continue;
                }

                if (playCardSucc == false) { // 出牌失败，重试
                    log.debug { "retry for ${playCardSucc} "}
                    retry += 1
                    continue
                }
                // MYWEN todo mutableMap 没有本回合出的随从，主要影响到突袭和冲锋
                DeckStrategyUtil.cleanPlay(1.2, 1.2,
                    mutableMap
                )
                attackForAllCardsInPlayArea(mutableMap)
                val companionsAfterBattle = War.me.playArea.cards.toList()
                companionsBeforeBattle.filter { it.cardType == CardTypeEnum.MINION && !companionsAfterBattle.contains(it) && !companionDied.contains(it)}.forEach {
                    companionDied.add(it)
                }

                plays.forEach {
                    if (it.cardType == CardTypeEnum.MINION) {
                        cardToRound[it] = round
                    }
                }
                retry += 1

                var smallCard = me.handArea.cards.filter { it.cardId != "GAME_005" && it.cardId != "SCH_514" } .sortedBy { it.cost }.firstOrNull()
                var coinCount = me.handArea.cards.count { it.cardId == "GAME_005" }
                if (smallCard != null && smallCard.cost == 0 && smallCard.cardType == CardTypeEnum.MINION ) { // 0 费随从牌
                    log.debug { "retry for ${smallCard} "}
                    continue
                }
                if (War.me.playArea.power!!.isExhausted == false && (me.playArea.cards.count { it.cardId == "TOY_381" } > 0 || me.usableResource >= 2)) {
                    log.debug { "retry for TOY_381 纸艺天使"}
                    continue
                }
                if (me.handArea.cards.count { it.cardId == "YOD_032" } > 0 && rivalHealth - (War.rival.playArea.hero?.blood()?:40) >= 4) {
                    log.debug { "retry for YOD_032 艾狂暴邪翼"}
                    continue
                }
                if (companionDied.size >= 2 && me.handArea.cards.count { it.cardId == "SCH_514" } > 0) {
                    log.debug { "retry for SCH_514 亡者复生"}
                    continue
                }
                if (predictActionResponse.needSurrender == true) {
                    needSurrender = true
                }
                if (smallCard == null || me.usableResource + coinCount < smallCard.cost || me.usableResource == 0) {
                    break
                }
            }

            playedActions.add(playedCard)

            // Sleep for 2 seconds
            // Thread.sleep(2000)
            // var plays = War.me.playArea.cards.toList()
            // //            使用地标
            // plays.forEach {card->
            //     if (card.cardType === CardTypeEnum.LOCATION && !card.isLocationActionCooldown){
            //         card.action.lClick()
            //     }
            // }

            // attackForAllCardsInPlayArea(mutableMap)
            // 确认没有漏牌
            // commonDeckStrategy.executeOutCard()
            minionOnBattle.clear()
            val companionsAfterBattle = War.me.playArea.cards.toList()
            minionOnBattle.addAll(companionsAfterBattle)
        }
    }

    private fun playForceAction(predictActionResponse: PredictActionResponse,
                                        playedCard: MutableList<Card> = mutableListOf(),
                                        companionsBeforeBattle: MutableList<Card> = mutableListOf(),
                                        ): Boolean {

        val power = War.me.playArea.power as Card
        val force_actions: List<PredictActionResponse.ForceAction> = predictActionResponse.force_actions
        for (it in force_actions) {
            log.debug { "companion: ${it.companion}, rival: ${it.rival}" }
            val itRival = if (it.rival != null) it.rival as Details else null
            if (it.companion != null && (it.companion.entityId.isNullOrEmpty() || companionDetailsMap.contains(it.companion.entityId)) && (itRival == null || rivalDetailsMap.contains(itRival.entityId)
            || companionDetailsMap.contains(itRival.entityId))) {
                var handSize = War.me.handArea.cards.size
                var battleSize = War.me.playArea.cards.size
                var rival = if (itRival != null && !itRival.entityId.isNullOrEmpty() && rivalDetailsMap.contains(itRival.entityId)) rivalDetailsMap[itRival.entityId] else null
                if (rival == null && itRival != null && !itRival.entityId.isNullOrEmpty() && companionDetailsMap.contains(itRival.entityId)) {
                    rival = companionDetailsMap[itRival.entityId]
                }
                var companion = if (it.companion != null && !it.companion.entityId.isNullOrEmpty()) companionDetailsMap[it.companion.entityId] else null
                if (companion == null) {
                    if (it.companion.cardId == power.cardId) {
                        var succ = playWithRetry(3, 200, power, rival)
                        if (succ == true) {
                            playedCard.add(power)
                            cardToRound[power] = round
                            val getNewCard = waitForUI(power, handSize, battleSize)
                            if (getNewCard) {
                                return true
                            }
                        }
                        else {
                            return false
                        }
                    }
                    else if (it.companion.cardId == "CFM_637") {
                        log.debug { "海盗帕奇斯 CFM_637 空降歹徒 DRG_056 continue" }
                        var card = War.me.playArea.cards.filter { it.cardId == "CFM_637" }.lastOrNull()
                        if (card == null) {
                            log.debug { "PlayArea ERROR companion: CFM_637" }
                            return false
                        }
                        companionsBeforeBattle.add(card)
                        cardToRound[card] = round
                        playedCard.add(card)
                    }
                    else {
                        return false
                    }
                }
                else if (companion.area is PlayArea) {
                    var card = War.me.playArea.cards.filter { it.entityId == companion.entityId }.lastOrNull()
                    if (card == null) {
                        log.error { "PlayArea ERROR companion: ${companion}" }
                        return false
                    }
                    else if (companion.cardType == CardTypeEnum.LOCATION) // 地标
                    {
                        if (execLocation(companion, rival) > 0) {
                            return true
                        }
                    }
                    else {
                        if (rival == null) {
                            if (companion.cardId == "CFM_637" || companion.cardId == "DRG_056") {
                                log.debug { "海盗帕奇斯 CFM_637 空降歹徒 DRG_056 continue" }
                                companionsBeforeBattle.add(companion)
                                cardToRound[companion] = round
                                playedCard.add(companion)
                            }
                            else {
                                log.error { "PlayArea ERROR ${companion}, rival no exist" }
                                return false
                            }
                        }
                        else {
                            var succ = attackWithRetry(3, 200, companion, rival as Card)
                            if (succ == true) {
                                cardToRound[companion] = round
                            }
                            else {
                                log.error { "PlayArea ERROR ${companion}, ${rival}" }
                                return false
                            }
                        }
                    }
                }
                else if (companion.area is HandArea) {
                    var card = War.me.handArea.cards.filter { it == companion }.lastOrNull()
                    if (card == null) {
                        if (companion.cardId == "CFM_637" || companion.cardId == "DRG_056") {
                            log.debug { "海盗帕奇斯 CFM_637 空降歹徒 DRG_056 continue" }
                            companionsBeforeBattle.add(companion)
                            cardToRound[companion] = round
                            playedCard.add(companion)
                        }
                        else {
                            log.error { "handArea ERROR companion: ${companion}" }
                            return false
                        }
                    }
                    else {
                        var succ = playWithRetry(3, 200, companion, rival)
                        if (succ == true) {
                            playedCard.add(companion)
                            cardToRound[companion] = round
                            if (companion.cardType == CardTypeEnum.MINION) {
                                companionsBeforeBattle.add(companion)
                            }
                            val getNewCard = waitForUI(companion, handSize, battleSize)
                            if (getNewCard == true) {
                                return true
                            }
                            if (companion.cardType == CardTypeEnum.LOCATION && rival != null) // 地标
                            {
                                if (execLocation(companion, rival) > 0) {
                                    return true
                                }
                            }
                        }
                        else {
                            return false
                        }
                    }
                }
            }
            else {
                log.debug { "failed to generate force action: compaion: ${it.companion}, rival: ${itRival}" }
                log.debug { "rivalDetailsMap: ${rivalDetailsMap.keys}"}
                log.debug { "companionDetailsMap: ${companionDetailsMap.keys}" }
                return false
            }
        }
        return true
    } // MYWEN TODO 不能用foreach
    private fun generateCoreCardMap(predictActionResponse: PredictActionResponse): MutableMap<Card, Double> {
        var mutableMap: MutableMap<Card, Double> = mutableMapOf()
        val coreCards: MutableMap<String, Double> = predictActionResponse.coreCards
        War.rival.playArea.cards.forEach {
            var value: Double = 1.0
            value += coreCards.getOrDefault(it.cardId, 0.0)
            mutableMap[it] = value
        }

        War.me.playArea.cards.forEach {
            var value: Double = 1.0
            value += coreCards.getOrDefault(it.cardId, 0.0)
            if (minionNeededToBurst.contains(it)) {
                value += 2.0
            }
            mutableMap[it] = value
        }
        return mutableMap
    }

    private fun useLocation(mutableMap: MutableMap<Card, Double> = mutableMapOf(), forceActionMap: MutableMap<Card, Card> = mutableMapOf()): Int {
        val playAreaOrdered = War.me.playArea.cards.filter { it.cardType == CardTypeEnum.MINION } .sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }
        .thenBy { -cardToRound.getOrDefault(it, -1) }
        .thenBy { it.cost }
        )
        var count = 0
        War.me.playArea.cards.toList().forEach {card->
            if (card.cardId == "REV_290") {
                var target = playAreaOrdered.lastOrNull()
                if (forceActionMap.contains(card)) {
                    target = forceActionMap[card]
                }
                if (target != null) {
                    count += execLocation(card, target)
                }
            }
            else {
                var target: Card? = null
                if (forceActionMap.contains(card)) {
                    target = forceActionMap[card]
                }
                count += execLocation(card, target)
            }
        }
        return count;
    }

    private fun execLocation(card: Card, target: Card?): Int {
        if (card.cardType === CardTypeEnum.LOCATION && !card.isLocationActionCooldown && activateLocaton.getOrDefault(card, -1) <= round - 2) {
            if (target != null) {
                var handSize = War.me.handArea.cards.size
                card.action.buff(target)
                if (card.cardId == "REV_290") {
                    val getNewCard = waitForNewCard(handSize + 1)
                }
                activateLocaton[card] = round
            }
            else {
                card.action.lClick()
                activateLocaton[card] = round
            }
            return 1
        }
        else {
            return 0
        }
    }

    private fun waitForNewCard(expectedSize: Int): Boolean {
        var maxWait = 5
        while (War.me.handArea.cards.size < expectedSize && maxWait > 0) {
            log.debug { "wait for card: ${War.me.handArea.cards.size}, expected: ${expectedSize} "}
            Thread.sleep(500L)
            maxWait -= 1
        }
        return War.me.handArea.cards.size >= expectedSize
    }

    private fun waitForUI(card: Card, handSize: Int, battleSize: Int): Boolean {
        val me = War.me
        var hands = me.handArea.cards.toList()
        var maxWait = 10

        var getNewCard = false

        if (card.cardId == "CS3_034") { // 织法者玛里苟斯
            maxWait = 20
            while (me.handArea.cards.size < 10 && maxWait > 0) {
                log.debug { "wait for card: ${me.handArea.cards.size}, expected: 10"}
                Thread.sleep(2500L)
                maxWait -= 1
            }
            Thread.sleep(500L)
            getNewCard = true
        }
        else if (card.cardId == "MIS_307") { // 水宝宝鱼人
            var expectedPlayAreaSize = battleSize + 1
            if (battleSize <= 5) {
                expectedPlayAreaSize = battleSize + 2
            }
            while ((me.handArea.cards.size < handSize || me.handArea.cards.count { it.cardId == "MIS_307t1" } <= 0
            || me.playArea.cards.size < expectedPlayAreaSize
            ) && maxWait > 0) {
                Thread.sleep(500L)
                maxWait -= 1
            }
            Thread.sleep(500L)
            getNewCard = true
        }
        else if (card.cardId == "VAC_323") { // 麦芽岩浆
            while ((me.handArea.cards.size < handSize || me.handArea.cards.count { it.cardId == "VAC_323t" } <= 0) && maxWait > 0) {
                Thread.sleep(500L)
                maxWait -= 1
            }
            Thread.sleep(500L)
            getNewCard = true
        }
        else if (card.cardId == "VAC_323t") { // 麦芽岩浆
            while ((me.handArea.cards.size < handSize || me.handArea.cards.count { it.cardId == "VAC_323t2" } <= 0) && maxWait > 0) {
                Thread.sleep(500L)
                maxWait -= 1
            }
            Thread.sleep(500L)
            getNewCard = true
        }
        else if (card.cardId == "SW_444") { // 暮光欺诈者
            while (me.handArea.cards.size < handSize && maxWait > 0) {
                Thread.sleep(500L)
                maxWait -= 1
            }
            Thread.sleep(500L)
            getNewCard = true
        }
        else if (card.cardId == "SCH_514") { // 亡者复生
            while (me.handArea.cards.size < handSize && maxWait > 0) {
                Thread.sleep(500L)
                maxWait -= 1
            }
            Thread.sleep(500L)
            getNewCard = true
        }
        else if (card.cardId == "GDB_445") { // 陨石风暴
            // Sleep for 2 seconds
            Thread.sleep(2500L)
        }
        else {
            Thread.sleep(500L)
        }

        if (card.cardId == "GDB_434" // 流彩巨岩
            || card.cardId == "GDB_310" // 虚灵神谕者
        ) {
            minionNeededToBurst.add(card)
        }

        // 法术迸发 法术可能被反制 todo MYWEN
        if (card.cardType == CardTypeEnum.SPELL && card.cardId != "GDB_445" && minionNeededToBurst.size > 0) { // 陨石风暴 不能法术迸发
            var count1 = minionNeededToBurst.count { it.cardId == "GDB_434" } // 流彩巨岩
            var count2 = minionNeededToBurst.count { it.cardId == "GDB_310" } // 虚灵神谕者
            if (count1 > 0) {
                Thread.sleep(3000L * count1)
            }
            if (count2 > 0) {
                var expectedSize = handSize - 1 + 2 * count2
                if (card.cardId == "VAC_323" || card.cardId == "VAC_323t") { // 麦芽岩浆
                    expectedSize = handSize + 2 * count2
                }
                if (card.cardId == "GDB_451" ) { // 三角测量
                    expectedSize = handSize - 1 + 2 * count2 + 1
                }
                maxWait = 5
                while (me.handArea.cards.size < expectedSize && maxWait > 0) {
                    log.debug { "wait for card: ${me.handArea.cards.size}, expected: ${expectedSize} "}
                    Thread.sleep(2000L)
                    maxWait -= 1
                }
                getNewCard = true
            }
            else if (card.cardId == "GDB_451") { // 三角测量
                var expectedSize = handSize - 1 + 1
                maxWait = 5
                while (me.handArea.cards.size < expectedSize && maxWait > 0) {
                    log.debug { "wait for card: ${me.handArea.cards.size}, expected: ${expectedSize} "}
                    Thread.sleep(2000L)
                    maxWait -= 1
                }
                getNewCard = true
            }
            minionNeededToBurst.clear()
        }
        else if (card.cardId == "GDB_451" ) { // 三角测量
            var expectedSize = handSize - 1 + 1
            maxWait = 5
            while (me.handArea.cards.size < expectedSize && maxWait > 0) {
                log.debug { "wait for card: ${me.handArea.cards.size}, expected: ${expectedSize} "}
                Thread.sleep(2000L)
                maxWait -= 1
            }
            minionNeededToBurst.clear()
            getNewCard = true
        }
        else if (card.cardId == "MIS_307t1") { // 水宝宝鱼人
            var expectedPlayAreaSize = battleSize + 1
            if (battleSize <= 5) {
                expectedPlayAreaSize = battleSize + 2
            }
            while (me.playArea.cards.size < expectedPlayAreaSize && maxWait > 0) {
                Thread.sleep(500L)
                maxWait -= 1
            }
            Thread.sleep(500L)
        }
        return getNewCard
    }

    private fun forceAttackInPlayArea(forceAttackMap: MutableMap<Card, Card> = mutableMapOf()) {
        val me = War.me
        var plays = me.playArea.cards.toList()
        plays.filter{ forceAttackMap.contains(it) }.forEach { playCard ->
            playCard.action.attack(forceAttackMap[playCard])
        }
    }

    private fun attackForAllCardsInPlayArea(mutableMap: MutableMap<Card, Double> = mutableMapOf()) {
        val me = War.me
        val rival = War.rival
        var plays = me.playArea.cards.toList()
        plays.filter{ playCard -> playCard.canAttack(false) }.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }) .forEach { playCard ->
            var toRivalList = War.rival.playArea.cards.toList().filter { it.canBeAttacked() }
            var tauntCard = toRivalList.filter { it.isTaunt } .sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }) .lastOrNull()
            tauntCard?.let {
                if (mutableMap.getOrDefault(playCard, 1.0) < 2.0 || mutableMap.getOrDefault(tauntCard, 1.0) >= 2.0) {
                    log.debug { "card: $playCard, attack: $tauntCard" }
                    playCard.action.attack(tauntCard)
                }
                else {
                    log.debug { "skip highcost card: $playCard, attack: $tauntCard" }
                }
            }?:let {
                if (playCard.isAttackableByRush || (playCard.isRush && playCard.numTurnsInPlay == 0)) {
                    var highCost = toRivalList.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).lastOrNull()
                    highCost?.let {
                        log.debug { "card: $playCard, attack: $tauntCard" }
                        playCard.action.attack(highCost)
                    }
                }
                else {
                    log.debug { "card: $playCard, attackHero" }
                    if (rivalHero!!.isImmune == true) {
                        return
                    }
                    playCard.action.attackHero()
                }
            }
        }
    }

    override fun executeDiscoverChooseCard(vararg cards: Card): Int {
        return commonDeckStrategy.executeDiscoverChooseCard(*cards)
    }

    private fun checkWhetherCanBeUsedThisTurn (card: Card, mutableMap: MutableMap<Card, Double> = mutableMapOf()): Boolean {
        val me = War.me
        val rival = War.rival
        var plays = me.playArea.cards.toList()
        var toRivalList = War.rival.playArea.cards.toList().filter { it.canBeAttacked() }
        var hands = me.handArea.cards.toList()
        if (card.cardType === CardTypeEnum.SPELL){
            if (card.cardId == "GDB_445") { // 陨石风暴
                var highCostCount = me.playArea.cards.filter { mutableMap.getOrDefault(it, 1.0) >= 2}.count()
                var highCostCountRival = toRivalList.filter { mutableMap.getOrDefault(it, 1.0) >= 2}.count()
                if (highCostCount >= 3 && highCostCountRival <= 2) {
                    log.debug { "too much high cost cards" }
                    return false;
                }
            }
            else if (
                card.cardId.startsWith("GDB_305") // 阳炎耀斑
                || card.cardId.startsWith("CORE_EX1_129") // 刀扇
                || card.cardId.startsWith("VAC_323") // 麦芽岩浆
                || card.cardId == "CORE_CS2_093" // 奉献
                || card.cardId == "VAC_414" // 炽热火炭
                || card.cardId == "ETC_069" // 渐强声浪
                || card.cardId == "CS2_032" // 烈焰风暴
            ) {
                if (toRivalList.size <= 0) {
                    return false;
                }
            }
            else if (
                card.cardId.startsWith("TOY_508") // 立体书
            ) {
                if (me.usableResource <= 2) {
                    return false;
                }
            }
            else if (
                card.cardId.startsWith("GDB_451") // 三角测量
            ) {
                if (me.usableResource <= 4) {
                    return false;
                }
            }
            else if (
            card.cardId == "CORE_SW_085" // 暗巷契约
            ) {
                if (hands.size <= 6) {
                    return false;
                }
            }
            else if (
                card.cardId == "EX1_179" //冰刺
                || card.cardId.startsWith("VAC_951") // “健康”饮品
                || card.cardId.startsWith("CS2_022") // 变形术
            ) { 
                var highCost = toRivalList.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).lastOrNull()
                if (highCost == null) {
                    return false;
                }
            }
            else if (
                card.cardId.startsWith("ETC_076") || // 街舞起跳
                card.cardId.startsWith("TTN_079") || // 星轨晕环
                card.cardId.startsWith("GDB_439")) { // 虫外有虫
                var highCost = me.playArea.cards.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).lastOrNull()
                if (highCost == null) {
                    return false;
                }
                else if (card.cardId.startsWith("ETC_076") && highCost.cost <= 4) { // 街舞起跳
                    return false;
                }
            }
        }
        else { //////////////////////////////////////////////////////////////////////////////////////非法术牌
            if (me.playArea.isFull) return false;
            // card.isBattlecry.isTrue {
            if (card.cardId == "GDB_901") { // 极紫外破坏者
                var tauntCard = toRivalList.filter { it.isTaunt } .sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }) .lastOrNull()
                var canBeAttacked = toRivalList.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).lastOrNull()
                log.debug { "tauntCard: $tauntCard, canBeAttacked: $canBeAttacked, cardSize: ${toRivalList.size}"}
                if (tauntCard == null && canBeAttacked == null && toRivalList.size != 0) {
                    return false;
                }
            }
            else if (card.cardId == "DEEP_008" // 针岩图腾
            ) {
                if (plays.size <= 0 ) { 
                    return false;
                }
            }
            else if (card.cardId == "ETC_332" // 梦中男神
            || card.cardId == "SCH_311" // 活化扫帚
            ) {
                if (plays.size <= 2 ) { 
                    return false;
                }
            }
            else if (card.cardId == "CS3_034") { // 织法者玛里苟斯
                if (hands.size >= 5) {
                    return false;
                }
            }
            else if (card.cardId.startsWith("TTN_087") || // 吸附寄生体
            card.cardId.startsWith("WORK_009") // 月度魔范员工
            ) { 
                var highCost = me.playArea.cards.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).lastOrNull()
                if (highCost == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private fun playCard(card: Card, mutableMap: MutableMap<Card, Double> = mutableMapOf(), predictActionResponse: PredictActionResponse? = null,
        forceActionMap: MutableMap<Card, Card> = mutableMapOf()
    ): Boolean {
        if (forceActionMap.contains(card)) {
            return playWithRetry(3, 200, card, forceActionMap[card])
        }

        val me = War.me
        val rival = War.rival
        var plays = me.playArea.cards.toList()
        var toRivalList = War.rival.playArea.cards.toList().filter { it.canBeAttacked() }
        if (card.cardType === CardTypeEnum.SPELL) {
            if (card.cardId == "GDB_445") { // 陨石风暴
                var powerPlus = predictActionResponse?.powerPlus ?: 0
                var ignoreRival: List<Card> = toRivalList.filter { !it.isTaunt && it.blood() <= 5 + powerPlus }
                var ignoreCompanion: List<Card> = plays.filter { it.blood() > 5 + powerPlus }
                log.debug { "start storm, ${plays}, powerPlus: ${powerPlus}, ignoreRival: ${ignoreRival}, ignoreCompanion: ${ignoreCompanion}" }
                DeckStrategyUtil.cleanPlay(1.2, 1.2,
                    mutableMap,
                    ignoreRival,
                    ignoreCompanion
                )
                return playWithRetry(3, 200, card, null)
            }
            else if (
                card.cardId == "CS2_024" // 寒冰箭
                || card.cardId == "GDB_456" //自燃
                || card.cardId == "YOG_526" //触须缠握
                || card.cardId == "TOY_508" //立体书
                || card.cardId == "TTN_454" //  殉船
                || card.cardId == "CORE_AT_064" // 怒袭
                || card.cardId == "MIS_709" // 圣光荧光棒
                || card.cardId == "CS2_029" // 火球术
            ) {
                var tauntCard = toRivalList.filter { it.isTaunt } .sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }) .lastOrNull()
                
                var highCost = toRivalList.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }) .lastOrNull()
                if (tauntCard != null) {
                    var succ = playWithRetry(3, 200, card, tauntCard)
                    if (succ == false) {
                        return playWithRetry(3, 200, card, rival.playArea.hero)
                    }
                    else {
                        return succ
                    }
                }
                else if (highCost == null || mutableMap.getOrDefault(highCost, 1.0) < 2.0) { // 如果是普通牌，打英雄
                    return playWithRetry(3, 200, card, rival.playArea.hero)
                }
                else {
                    log.debug { "toRivalList: ${toRivalList}, highCost: ${highCost} " }
                    var succ = playWithRetry(3, 200, card, highCost)
                    if (succ == false) {
                        return playWithRetry(3, 200, card, rival.playArea.hero)
                    }
                    else {
                        return succ
                    }
                }
            }
            else if (
                card.cardId.startsWith("GDB_305") // 阳炎耀斑
                || card.cardId.startsWith("CORE_EX1_129") // 刀扇
                || card.cardId.startsWith("VAC_323") // 麦芽岩浆
                || card.cardId == "CORE_CS2_093" // 奉献
                || card.cardId == "VAC_414" // 炽热火炭
                || card.cardId == "ETC_069" // 渐强声浪
                || card.cardId == "CS2_032" // 烈焰风暴
            ) {
                if (toRivalList.size <= 0 && minionNeededToBurst.size == 0) {
                    return false;
                }
                else {
                    return playWithRetry(3, 200, card, null)
                }
            }
            else if (
                card.cardId == "EX1_179" //冰刺
                || card.cardId.startsWith("VAC_951") // “健康”饮品
                || card.cardId.startsWith("CS2_022") // 变形术
            ) {
                var highCost = toRivalList.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).lastOrNull()
                if (highCost == null) {
                    return false;
                }
                else {
                    return playWithRetry(3, 200, card, highCost)
                    
                }
            }
            else if (
                card.cardId.startsWith("NX2_019") // 精神灼烧
            ) {
                var highRivalCost = toRivalList.filter { it.blood() <= 2 }.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).lastOrNull()
                var highCompanionCost = plays.filter { it.blood() <= 2 }.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).firstOrNull()
                if (highRivalCost != null) {
                    return playWithRetry(3, 200, card, highRivalCost)
                }
                else if (highCompanionCost != null) {
                    return playWithRetry(3, 200, card, highCompanionCost)
                }
                else {
                    return false;
                }
            }
            else if (card.cardId.startsWith("VAC_916")) { // 神圣佳酿
                var highCost = me.playArea.cards.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).lastOrNull()
                if (highCost == null) {
                    return playWithRetry(3, 200, card, me.playArea.hero)
                }
                else {
                    return playWithRetry(3, 200, card, highCost)
                }
            }
            else if (
                card.cardId.startsWith("ETC_076") || // 街舞起跳
                card.cardId.startsWith("TTN_079") || // 星轨晕环
                card.cardId.startsWith("GDB_439")) { // 虫外有虫
                var highCost = me.playArea.cards.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).lastOrNull()
                if (highCost == null) {
                    return false;
                }
                else {
                    return playWithRetry(3, 200, card, highCost)
                }
            }
            else {
                return playWithRetry(3, 200, card, null)
            }
        }
        else if (card.cardType === CardTypeEnum.HERO_POWER) {
            return playWithRetry(3, 200, card, null)
        }
        else { //////////////////////////////////////////////////////////////////////////////////////非法术牌
            if (me.playArea.isFull) return false;
            // card.isBattlecry.isTrue {
            if (card.cardId == "GDB_901") { // 极紫外破坏者
                var tauntCard = toRivalList.filter { it.isTaunt } .sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }) .lastOrNull()
                var canBeAttacked = toRivalList.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).lastOrNull()
                var firstCard = toRivalList.firstOrNull()
                tauntCard?.let {
                    return playWithRetry(3, 200, card, it)
                }?:let {
                    canBeAttacked?.let {
                        return playWithRetry(3, 200, card, it)
                    }?:let {
                        firstCard?.let {
                            return playWithRetry(3, 200, card, it)
                        }?:let {
                            return playWithRetry(3, 200, card, null)
                        }
                    }
                }
            }
            else if (card.cardId.startsWith("TTN_087") || // 吸附寄生体
            card.cardId.startsWith("WORK_009") // 月度魔范员工
            ) {
                var highCost = me.playArea.cards.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).lastOrNull()
                if (highCost == null) {
                    return false;
                }
                else {
                    return playWithRetry(3, 200, card, highCost)
                }
            }
            else if (card.cardId == "CS3_034") { // 织法者玛里苟斯
                var ignoreRival: List<Card> = emptyList<Card>()
                var ignoreCompanion: List<Card> = plays.filter { it.cardId == "CS3_007" || it.cardId == "GDB_310" }
                DeckStrategyUtil.cleanPlay(1.2, 1.2,
                    mutableMap,
                    ignoreRival,
                    ignoreCompanion
                )
                return playWithRetry(3, 200, card, null)
            }
            else {
                return playWithRetry(3, 200, card, null)
            }
        }

        return true
    }

    fun playWithRetry(
        times: Int,            // Number of retry attempts
        delayMillis: Long,     // Delay between retries in milliseconds
        card: Card,
        target: Card?
    ): Boolean {
        val me = War.me
        var currentAttempt = 0
        var targetHP = target?.blood()?:0
        val rivalHealth: Int = War.rival.playArea.hero?.blood()?:0

        if (target != null && target.cardType === CardTypeEnum.MINION && target.area != War.rival.playArea && target.area != War.me.playArea) {
            log.debug { "${target} died"}
            return false
        }
        if (target != null && target.canBeAttacked() == false) {
            log.debug { "${target} canBeAttacked false"}
            return false
        }

        while (currentAttempt < times) {
            if (target != null && target.cardType == CardTypeEnum.HERO && War.rival.playArea.hero != null && War.rival.playArea.hero!!.isImmune == true) {
                return true;
            }
            target?.let {
                card.action.power(target)
            }?:let {
                card.action.power()
            }
            currentAttempt++
            Thread.sleep(delayMillis)

            if (card.cardType != CardTypeEnum.HERO_POWER && me.handArea.cards.count { it.entityId == card.entityId } <= 0) {
                return true;
            }
            if (card.cardType == CardTypeEnum.HERO_POWER && War.me.playArea.power!!.isExhausted == true) {
                return true;
            }

            if (target != null && target.cardType === CardTypeEnum.MINION && target.area != War.rival.playArea && target.area != War.me.playArea) {
                log.debug { "${target} died"}
                return true
            }

            // MINION RIVAL
            if (target != null && target.cardType === CardTypeEnum.MINION && target.area == War.rival.playArea) {
                var find = War.rival.playArea.cards.filter { it.entityId == target!!.entityId }.lastOrNull()
                if (find == null || find.blood() != targetHP) {
                    return true;
                }
                if (find != null && find.canBeAttacked() == false) {
                    log.debug { "${find} canBeAttacked false"}
                    return false
                }
            }

            // MINION ME
            if (target != null && target.cardType === CardTypeEnum.MINION && target.area == War.me.playArea) {
                var find = War.me.playArea.cards.filter { it.entityId == target!!.entityId }.lastOrNull()
                if (find == null || find.blood() != targetHP) {
                    return true;
                }
                if (find != null && find.canBeAttacked() == false) {
                    log.debug { "${find} canBeAttacked false"}
                    return false
                }
            }

            // HERO
            val newRivalHealth: Int = War.rival.playArea.hero?.blood()?:0
            if (newRivalHealth != rivalHealth) {
                return true;
            }
        }
        return false
    }

    fun attackWithRetry(
        times: Int,            // Number of retry attempts
        delayMillis: Long,     // Delay between retries in milliseconds
        card: Card,
        target: Card
    ): Boolean {
        val me = War.me
        var currentAttempt = 0
        var targetHP = target?.blood()?:0
        val rivalHealth: Int = War.rival.playArea.hero?.blood()?:0

        if (target != null && target.cardType === CardTypeEnum.MINION && target.area != War.rival.playArea && target.area != War.me.playArea) {
            log.debug { "${target} died"}
            return false
        }

        if (target != null && target.canBeAttacked() == false) {
            log.debug { "${target} canBeAttacked false"}
            return false
        }

        while (currentAttempt < times) {
            card.action.attack(target)
            currentAttempt++
            Thread.sleep(delayMillis)

            // 随从可能过程中死亡 
            var find = War.me.playArea.cards.filter { it.entityId == card.entityId }.lastOrNull()
            if (find == null || !find.canAttack(false)) { // MYWEN 风怒
                return true;
            }

            // MINION RIVAL
            if (target != null && target.cardType == CardTypeEnum.MINION && target.area == War.rival.playArea) {
                var find = War.rival.playArea.cards.filter { it.entityId == target!!.entityId }.lastOrNull()
                if (find == null || find.blood() != targetHP) {
                    return true;
                }
                if (find != null && find.canBeAttacked() == false) {
                    log.debug { "${find} canBeAttacked false"}
                    return false
                }
            }

            // MINION ME
            if (target != null && target.cardType == CardTypeEnum.MINION && target.area == War.me.playArea) {
                var find = War.me.playArea.cards.filter { it.entityId == target!!.entityId }.lastOrNull()
                if (find == null || find.blood() != targetHP) {
                    return true;
                }
                if (find != null && find.canBeAttacked() == false) {
                    log.debug { "${find} canBeAttacked false"}
                    return false
                }
            }

            if (target != null && target.cardType === CardTypeEnum.MINION && target.area != War.rival.playArea && target.area != War.me.playArea) {
                log.debug { "${target} died"}
                return true
            }

            // HERO
            val newRivalHealth: Int = War.rival.playArea.hero?.blood()?:0
            if (newRivalHealth != rivalHealth) {
                return true;
            }
        }
        return false
    }
}