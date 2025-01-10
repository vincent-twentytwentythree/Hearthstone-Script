package club.xiaojiawei

import club.xiaojiawei.bean.Card
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

import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStream
import java.io.InputStreamReader
import java.io.BufferedReader

import java.net.SocketException

import com.google.gson.Gson

import club.xiaojiawei.status.War.firstPlayerGameId

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
        round = 0
    }

    private val minionNeededToBurst: ArrayList<Card> = arrayListOf()
    private val playedActions: ArrayList<ArrayList<Card>> = arrayListOf()
    private var round: Int = 0

    fun convertToJson(predictActionRequest: PredictActionRequest): String {
        val gson = Gson()
        return gson.toJson(predictActionRequest)
    }

    fun loadJson(jsonString: String): PredictActionResponse {
        val gson = Gson()
        return gson.fromJson(jsonString, PredictActionResponse::class.java)
    }

    fun sendPostRequest(predictActionRequest: PredictActionRequest): PredictActionResponse {
        try {
            var jsonData = convertToJson(predictActionRequest)
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
            return loadJson(response.toString())
        } catch (e: SocketException) {
            // Handle the specific SocketException
            log.info { "SocketException occurred: ${e.message}" }
        } catch (e: Exception) {
            // Handle other exceptions
            log.info { "An error occurred: ${e.message}" }
        }
        return PredictActionResponse("fail", arrayListOf(), arrayListOf(), "fail", 0, 0, 0, mutableMapOf(), 0, true)
    }

    override fun executeOutCard() { // MYWEN
        if (War.me.isValid()){
            round += 1
            // 使用地标
            War.me.playArea.cards.toList().forEach {card->
                if (card.cardType === CardTypeEnum.LOCATION && !card.isLocationActionCooldown){
                    card.action.lClick()
                }
            }
            val playedCard: ArrayList<Card> = arrayListOf()
            var retry: Int = 0
            var heroPlay = false
            var overload = 0
            if (playedActions.size > 0) {
                var lastAction = playedActions.lastOrNull()
                if (lastAction != null) {
                    overload = lastAction.count { it.cardId == "CS3_007" }
                }
            }
            log.info { "resource: ${War.me.resources}, usableResource: ${War.me.usableResource}, overload: ${overload}, War.me.overloadLocked: ${War.me.overloadLocked}" }

            while (retry <= 5) {    
                val me = War.me
                val rival = War.rival
                var plays = me.playArea.cards.toList()
                var toRivalList = War.rival.playArea.cards.toList().filter { it.canBeAttacked() }

                log.info { "before filter minionNeededToBurst: $minionNeededToBurst" }
                minionNeededToBurst.removeIf { !plays.contains(it) } // remove died minion
                log.info { "after filter minionNeededToBurst: $minionNeededToBurst" }
                log.info { "rival: $toRivalList" }
                log.info { "me: $plays" }

                var position = "landlord"
                if (firstPlayerGameId.endsWith("#51434") || firstPlayerGameId.endsWith("#5694") || firstPlayerGameId.endsWith("#5381") ||
                firstPlayerGameId.endsWith("#21836")
                ) { // 先手
                    position = "landlord"
                }
                else {
                    position = "second_hand"
                }
                var predictActionRequest = PredictActionRequest(position,
                                                                round,
                                                                me.usableResource + (War.me.overloadLocked - overload),
                                                                me.handArea.cards.map { it.cardId } ,
                                                                arrayListOf(),
                                                                playedActions.map { it.map{ card -> card.cardId } },
                                                                toRivalList.map { it.cardId },
                                                                plays.map { it.cardId },
                                                                minionNeededToBurst.map { it.cardId }
                                                                )
                var predictActionResponse = sendPostRequest(predictActionRequest)

                val mutableMap: MutableMap<Card, Double> = mutableMapOf()
                if (predictActionResponse.status == "succ") {
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
                }

                var playCardSucc = true
                if (predictActionResponse.status == "succ" && predictActionResponse.action.size != 0) {
                    log.info { "待出牌：${predictActionResponse}" }

                    for (cardId in predictActionResponse.action) {
                        var findCard: Int = 0
                        var card: Card? = null
                        while (findCard < 3) {
                            card = me.handArea.cards.filter { (it.cardId == cardId) || (it.cardId.startsWith("VAC_323") && cardId.startsWith("VAC_323"))}.firstOrNull()
                            findCard += 1
                            if (card == null) {
                                Thread.sleep(1000)
                            }
                        }
                        if (card == null) {
                            playCardSucc = false
                            break
                        }
                        if (predictActionResponse.cost <= predictActionResponse.crystal - 2
                            && (cardId == "TOY_508" || cardId.startsWith("VAC_323")) // 打之前，加法强
                            && minionNeededToBurst.count { it.cardId == "GDB_310" } == 0 // 虚灵神谕者
                            && heroPlay == false
                            && me.playArea.isFull == false
                        ) {
                            log.info { "usableResource: ${me.usableResource}, cost: 2, hero play"  }
                            me.playArea.power?.let {
                                if (me.usableResource >= it.cost || it.cost == 0) {
                                    it.action.power()
                                }
                            }
                            heroPlay = true
                        }
                        log.info { "usableResource: ${me.usableResource}, cost: ${card.cost}, card: $card"  }
                        if (me.usableResource >= card.cost){
                            var handSize = me.handArea.cards.size
                            var battleSize = me.playArea.cards.size
                            var succ = playCard(card, mutableMap, predictActionResponse)
                            if (succ == true) {
                                playedCard.add(card)
                                val getNewCard = waitForUI(card, handSize, battleSize)
                                if (getNewCard == true) { // 有新牌，重新跑模型
                                    break
                                }
                            }
                            else {
                                playCardSucc = false
                                break
                            }
                        }
                    }
                }
                
                if (predictActionResponse.status != "succ") {
                    Thread.sleep(2000)
                }
                else {
                    // 所有牌出完, 重新跑模型, 确认没有漏牌
                    // MYWEN todo mutableMap 没有本回合出的随从，主要影响到突袭和冲锋
                    DeckStrategyUtil.cleanPlay(1.2, 1.2,
                        mutableMap
                    )
                    attackForAllCardsInPlayArea(mutableMap)
                    retry += 1

                    if (playCardSucc == false) { // 出牌失败，重试
                        continue
                    }

                    var smallCard = me.handArea.cards.filter { it.cardId != "GAME_005" } .sortedBy { it.cost }.firstOrNull()
                    var coinCount = me.handArea.cards.count { it.cardId == "GAME_005" }
                    if (predictActionResponse.needSurrender == true) {
                        needSurrender = true;
                    }
                    if (smallCard == null || me.usableResource + coinCount < smallCard.cost || me.usableResource == 0) {
                        break
                    }
                }
            }

            playedActions.add(playedCard)

            // Sleep for 2 seconds
            Thread.sleep(2000)
            var plays = War.me.playArea.cards.toList()
            //            使用地标
            plays.forEach {card->
                if (card.cardType === CardTypeEnum.LOCATION && !card.isLocationActionCooldown){
                    card.action.lClick()
                }
            }

            // attackForAllCardsInPlayArea(mutableMap)
            // 确认没有漏牌
            commonDeckStrategy.executeOutCard()
        }
    }

    private fun waitForUI(card: Card, handSize: Int, battleSize: Int): Boolean {
        val me = War.me
        var hands = me.handArea.cards.toList()
        var maxWait = 10

        var getNewCard = false

        if (card.cardId == "CS3_034") { // 织法者玛里苟斯
            maxWait = 20
            while (me.handArea.cards.size < 10 && maxWait > 0) {
                log.info { "wait for card: ${me.handArea.cards.size}, expected: 10"}
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
                    log.info { "wait for card: ${me.handArea.cards.size}, expected: ${expectedSize} "}
                    Thread.sleep(2000L)
                    maxWait -= 1
                }
                getNewCard = true
            }
            else if (card.cardId == "GDB_451") { // 三角测量
                var expectedSize = handSize - 1 + 1
                maxWait = 5
                while (me.handArea.cards.size < expectedSize && maxWait > 0) {
                    log.info { "wait for card: ${me.handArea.cards.size}, expected: ${expectedSize} "}
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
                log.info { "wait for card: ${me.handArea.cards.size}, expected: ${expectedSize} "}
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

    private fun attackForAllCardsInPlayArea(mutableMap: MutableMap<Card, Double> = mutableMapOf()) {
        val me = War.me
        val rival = War.rival
        var plays = me.playArea.cards.toList()
        plays.filter{ playCard -> playCard.canAttack(false) }.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }) .forEach { playCard ->
            var toRivalList = War.rival.playArea.cards.toList().filter { it.canBeAttacked() }
            var tauntCard = toRivalList.filter { it.isTaunt } .sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }) .lastOrNull()
            tauntCard?.let {
                if (mutableMap.getOrDefault(playCard, 1.0) < 2.0 || mutableMap.getOrDefault(tauntCard, 1.0) >= 2.0) {
                    log.info { "card: $playCard, attack: $tauntCard" }
                    playCard.action.attack(tauntCard)
                    Thread.sleep(200L)
                }
                else {
                    log.info { "skip highcost card: $playCard, attack: $tauntCard" }
                }
            }?:let {
                if (playCard.isAttackableByRush || (playCard.isRush && playCard.numTurnsInPlay == 0)) {
                    var highCost = toRivalList.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).lastOrNull()
                    highCost?.let {
                        log.info { "card: $playCard, attack: $tauntCard" }
                        playCard.action.attack(highCost)
                        Thread.sleep(200L)
                    }
                }
                else {
                    log.info { "card: $playCard, attackHero" }
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
                    log.info { "too much high cost cards" }
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
                log.info { "tauntCard: $tauntCard, canBeAttacked: $canBeAttacked, cardSize: ${toRivalList.size}"}
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

    private fun playCard(card: Card, mutableMap: MutableMap<Card, Double> = mutableMapOf(), predictActionResponse: PredictActionResponse? = null): Boolean {
        val me = War.me
        val rival = War.rival
        var plays = me.playArea.cards.toList()
        var toRivalList = War.rival.playArea.cards.toList().filter { it.canBeAttacked() }
        if (card.cardType === CardTypeEnum.SPELL) {
            if (card.cardId == "GDB_445") { // 陨石风暴
                var powerPlus = predictActionResponse?.powerPlus ?: 0
                var ignoreRival: List<Card> = toRivalList.filter { !it.isTaunt && it.blood() <= 5 + powerPlus }
                var ignoreCompanion: List<Card> = plays.filter { it.blood() > 5 + powerPlus }
                log.info { "start storm, ${plays}, powerPlus: ${powerPlus}, ignoreRival: ${ignoreRival}, ignoreCompanion: ${ignoreCompanion}" }
                DeckStrategyUtil.cleanPlay(1.2, 1.2,
                    mutableMap,
                    ignoreRival,
                    ignoreCompanion
                )
                return runWithRetry(3, 200, card, null)
            }
            else if (
                card.cardId.contains("CS2_024") // 寒冰箭 CORE_CS2_024
                || card.cardId == "GDB_456" //自燃
                || card.cardId == "YOG_526" //触须缠握
                || card.cardId == "TOY_508" //立体书
                || card.cardId == "TTN_454" //  殉船
                || card.cardId == "CORE_AT_064" // 怒袭
                || card.cardId == "MIS_709" // 圣光荧光棒
                || card.cardId.contains("CS2_029") // 火球术 CORE_CS2_029
            ) {
                var tauntCard = toRivalList.filter { it.isTaunt } .sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }) .lastOrNull()
                
                var highCost = toRivalList.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }) .lastOrNull()
                if (tauntCard != null) {
                    var succ = runWithRetry(3, 200, card, tauntCard)
                    if (succ == false) {
                        return runWithRetry(3, 200, card, rival.playArea.hero)
                    }
                    else {
                        return succ
                    }
                }
                else if (highCost == null || mutableMap.getOrDefault(highCost, 1.0) < 2.0) { // 如果是普通牌，打英雄
                    return runWithRetry(3, 200, card, rival.playArea.hero)
                }
                else {
                    log.info { "toRivalList: ${toRivalList}, highCost: ${highCost} " }
                    var succ = runWithRetry(3, 200, card, highCost)
                    if (succ == false) {
                        return runWithRetry(3, 200, card, rival.playArea.hero)
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
                    return runWithRetry(3, 200, card, null)
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
                    return runWithRetry(3, 200, card, highCost)
                    
                }
            }
            else if (card.cardId.startsWith("VAC_916")) { // 神圣佳酿
                var highCost = me.playArea.cards.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).lastOrNull()
                if (highCost == null) {
                    return runWithRetry(3, 200, card, me.playArea.hero)
                }
                else {
                    return runWithRetry(3, 200, card, highCost)
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
                    return runWithRetry(3, 200, card, highCost)
                }
            }
            else {
                return runWithRetry(3, 200, card, null)
            }
        }
        else { //////////////////////////////////////////////////////////////////////////////////////非法术牌
            if (me.playArea.isFull) return false;
            // card.isBattlecry.isTrue {
            if (card.cardId == "GDB_901") { // 极紫外破坏者
                var tauntCard = toRivalList.filter { it.isTaunt } .sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }) .lastOrNull()
                var canBeAttacked = toRivalList.sortedWith(compareBy<Card> { mutableMap.getOrDefault(it, 1.0) }.thenBy { it.cost }).lastOrNull()
                var firstCard = toRivalList.firstOrNull()
                tauntCard?.let {
                    return runWithRetry(3, 200, card, it)
                }?:let {
                    canBeAttacked?.let {
                        return runWithRetry(3, 200, card, it)
                    }?:let {
                        firstCard?.let {
                            return runWithRetry(3, 200, card, it)
                        }?:let {
                            return runWithRetry(3, 200, card, null)
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
                    return runWithRetry(3, 200, card, highCost)
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
                return runWithRetry(3, 200, card, null)
            }
            else {
                return runWithRetry(3, 200, card, null)
            }
        }
        return true
    }

    fun runWithRetry(
        times: Int,            // Number of retry attempts
        delayMillis: Long,     // Delay between retries in milliseconds
        card: Card,
        target: Card?
    ): Boolean {
        val me = War.me
        var currentAttempt = 0

        while (currentAttempt < times) {
            target?.let {
                card.action.power(target)
            }?:let {
                card.action.power()
            }
            currentAttempt++
            Thread.sleep(delayMillis)
            if (me.handArea.cards.count { it.entityId == card.entityId } <= 0) {
                return true;
            }
        }
        return false
    }
}