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

    // how to clean MYWEN
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
        return PredictActionResponse("fail", arrayListOf(), arrayListOf(), "fail")
    }

    // MYWEN
    override fun executeOutCard() {
        if (War.me.isValid()){
            round += 1
            if (War.me.playArea.isFull) {
                log.info { "playArea is full, clean it first" }
                DeckStrategyUtil.cleanPlay()
            }

            val me = War.me
            val rival = War.rival
            var plays = me.playArea.cards.toList()
            var toRivalList = War.rival.playArea.cards.toList().filter { it.canBeAttacked() && !it.cardId.startsWith("GDB_100t") }
            var hands = me.handArea.cards.toList()
            log.info { "before filter minionNeededToBurst: $minionNeededToBurst" }
            minionNeededToBurst.removeIf { !plays.contains(it) } // remove died minion
            log.info { "after filter minionNeededToBurst: $minionNeededToBurst" }
            log.info { "rival: $toRivalList" }
            log.info { "me: $plays" }
//            使用地标
            plays.forEach {card->
                if (card.cardType === CardTypeEnum.LOCATION && !card.isLocationActionCooldown){
                    card.action.lClick()
                }
            }
            var position = "landlord"
            if (firstPlayerGameId == "firesnow#51434") { // 先手
                position = "landlord"
            }
            else {
                position = "second_hand"
            }
            var handsToPlaySimple = me.handArea.cards.filter { checkWhetherCanBeUsedThisTurnSimple(it) }.toMutableList()
            var predictActionRequest = PredictActionRequest(position,
                                                            round,
                                                            me.usableResource,
                                                            handsToPlaySimple.map { it.cardId } ,
                                                            arrayListOf(),
                                                            playedActions.map { it.map{ card -> card.cardId } },
                                                            toRivalList.map { it.cardId },
                                                            plays.map { it.cardId },
                                                            minionNeededToBurst.map { it.cardId }
                                                            )
            var predictActionResponse = sendPostRequest(predictActionRequest)
            val playedCard: ArrayList<Card> = arrayListOf()
            if (predictActionResponse.status == "succ") {

                log.info { "待出牌：${predictActionResponse}" }
                for (cardId in predictActionResponse.action) {
                    val card = me.handArea.cards.filter { (it.cardId == cardId) || (it.cardId.startsWith("VAC_323") && cardId.startsWith("VAC_323"))}.firstOrNull()
                    if (card == null) {
                        continue
                    }
                    log.info { "usableResource: ${me.usableResource}, cost: ${card.cost}, card: $card"  }
                    if (me.usableResource >= card.cost){
                        var handSize = me.handArea.cards.size
                        var succ = playCard(card)
                        if (succ == true) {
                            playedCard.add(card)
                            waitForUI(card, handSize)
                        }
                    }
                }
            }
            else {
                var handsToPlay = me.handArea.cards.filter { checkWhetherCanBeUsedThisTurn(it) }.toList()
                // val (_, resultCards) = DeckStrategyUtil.calcPowerOrderConvert(handsToPlay, me.usableResource)
                val (_, resultCards) = DeckStrategyUtil.calcPowerOrderConvert(handsToPlay, me.usableResource, toRivalList, plays, hands, minionNeededToBurst)
                if (resultCards.isNotEmpty()) {
                    val sortCard = DeckStrategyUtil.sortCard(resultCards)
                    log.info { "待出牌：$sortCard" }
                    for (simulateWeightCard in sortCard) {
                        val card = simulateWeightCard.card
                        log.info { "usableResource: ${me.usableResource}, cost: ${card.cost}, card: $card"  }
                        if (me.usableResource >= card.cost){
                            var handSize = me.handArea.cards.size
                            var succ = playCard(card)
                            if (succ == true) {
                                playedCard.add(card)
                                waitForUI(card, handSize)
                            }
                        }
                    }
                }
            }

            playedActions.add(playedCard)

            // Sleep for 2 seconds
            Thread.sleep(2000)
            plays = me.playArea.cards.toList()
//            使用地标
            plays.forEach {card->
                if (card.cardType === CardTypeEnum.LOCATION && !card.isLocationActionCooldown){
                    card.action.lClick()
                }
            }
            commonDeckStrategy.executeOutCard()

            attackForAllCardsInPlayArea()
        }
    }

    private fun waitForUI(card: Card, handSize: Int) {
        val me = War.me
        var hands = me.handArea.cards.toList()
        var maxWait = 10

        if (card.cardId == "CS3_034") { // 织法者玛里苟斯
            while (me.handArea.cards.size < 10 && maxWait > 0) {
                log.info { "wait for card: ${me.handArea.cards.size}, expected: 10"}
                Thread.sleep(2500L)
                maxWait -= 1
            }
        }
        else if (card.cardId == "MIS_307") { // 水宝宝鱼人
            while (me.handArea.cards.count { it.cardId == "MIS_307t1" } <= 0 && maxWait > 0) {
                Thread.sleep(500L)
                maxWait -= 1
            }
        }
        else if (card.cardId == "VAC_323") { // 麦芽岩浆
            while (me.handArea.cards.count { it.cardId == "VAC_323t" } <= 0 && maxWait > 0) {
                Thread.sleep(500L)
                maxWait -= 1
            }
        }
        else if (card.cardId == "VAC_323t") { // 麦芽岩浆
            while (me.handArea.cards.count { it.cardId == "VAC_323t2" } <= 0 && maxWait > 0) {
                Thread.sleep(500L)
                maxWait -= 1
            }
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

        // 法术迸发
        // 法术可能被反制 todo MYWEN
        if (card.cardType == CardTypeEnum.SPELL && card.cardId != "GDB_445" && minionNeededToBurst.size > 0) { // 陨石风暴 不能法术迸发
            var count1 = minionNeededToBurst.count { it.cardId == "GDB_434" }
            var count2 = minionNeededToBurst.count { it.cardId == "GDB_310" }
            if (count1 > 0) {
                Thread.sleep(3000L * count1)
            }
            if (count2 > 0) {
                var expectedSize = handSize - 1 + 2 * count2
                if (card.cardId == "VAC_323" || card.cardId == "VAC_323t") {
                    expectedSize = handSize + 2 * count2
                }
                if (card.cardId == "GDB_451" ) {
                    expectedSize = handSize - 1 + 2 * count2 + 1
                }
                maxWait = 5
                while (me.handArea.cards.size < expectedSize && maxWait > 0) {
                    log.info { "wait for card: ${me.handArea.cards.size}, expected: ${expectedSize} "}
                    Thread.sleep(2000L)
                    maxWait -= 1
                }
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
        }
    }

    private fun attackForAllCardsInPlayArea() {
        val me = War.me
        val rival = War.rival
        var plays = me.playArea.cards.toList()
        plays.filter{ playCard -> playCard.canAttack(false) }.forEach { playCard ->
            var tauntCard = rival.playArea.cards.find { card-> card.isTaunt }
            tauntCard?.let {
                log.info { "card: $playCard, attack: $tauntCard" }
                playCard.action.attack(tauntCard)
            }?:let {
                log.info { "card: $playCard, attackHero" }
                playCard.action.attackHero()
            }
        }
    }

    override fun executeDiscoverChooseCard(vararg cards: Card): Int {
        return commonDeckStrategy.executeDiscoverChooseCard(*cards)
    }

    private fun checkWhetherCanBeUsedThisTurnSimple (card: Card): Boolean {
        val me = War.me
        val rival = War.rival
        var plays = me.playArea.cards.toList()
        var toRivalList = War.rival.playArea.cards.toList().filter { it.canBeAttacked() && !it.cardId.startsWith("GDB_100t") }
        var hands = me.handArea.cards.toList()
        if (card.cardType === CardTypeEnum.SPELL){
            return true
        }
        else { //////////////////////////////////////////////////////////////////////////////////////非法术牌
            if (me.playArea.isFull) return false;
            if (card.cardId == "CS3_034") { // 织法者玛里苟斯
                if (hands.size >= 5) {
                    return false;
                }
            }
        }
        return true;
    }

    private fun checkWhetherCanBeUsedThisTurn (card: Card): Boolean {
        val me = War.me
        val rival = War.rival
        var plays = me.playArea.cards.toList()
        var toRivalList = War.rival.playArea.cards.toList().filter { it.canBeAttacked() && !it.cardId.startsWith("GDB_100t") }
        var hands = me.handArea.cards.toList()
        if (card.cardType === CardTypeEnum.SPELL){
            if (card.cardId == "GDB_445") { // 陨石风暴
                var highCostCount = me.playArea.cards.filter { playCard -> playCard.cost >= 3}.count()
                var highCostCountRival = rival.playArea.cards.filter { playCard -> playCard.cost >= 3}.count()
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
                var highCost = rival.playArea.cards.sortedBy { playCard -> playCard.cost }.lastOrNull()
                if (highCost == null) {
                    return false;
                }
            }
            else if (
                card.cardId.startsWith("ETC_076") || // 街舞起跳
                card.cardId.startsWith("TTN_079") || // 星轨晕环
                card.cardId.startsWith("GDB_439")) { // 虫外有虫
                var highCost = me.playArea.cards.sortedBy { playCard -> playCard.cost }.lastOrNull()
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
                var tauntCard = rival.playArea.cards.find { card-> card.isTaunt }
                var canBeAttacked = rival.playArea.cards.filter { card-> card.canBeAttacked() }.sortedBy { card.cost }.lastOrNull()
                log.info { "tauntCard: $tauntCard, canBeAttacked: $canBeAttacked, cardSize: ${rival.playArea.cards.size}"}
                if (tauntCard == null && canBeAttacked == null && rival.playArea.cards.size != 0) {
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
                var highCost = me.playArea.cards.sortedBy { playCard -> playCard.cost }.lastOrNull()
                if (highCost == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private fun playCard(card: Card): Boolean {
        val me = War.me
        val rival = War.rival
        var plays = me.playArea.cards.toList()
        var toRivalList = War.rival.playArea.cards.toList().filter { it.canBeAttacked() && !it.cardId.startsWith("GDB_100t") }
        if (card.cardType === CardTypeEnum.SPELL) {
            if (card.cardId == "GDB_445") { // 陨石风暴
                log.info { "start storm, ${plays}" }
                attackForAllCardsInPlayArea()
                return runWithRetry(3, 200, card, null)
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
                return runWithRetry(3, 200, card, rival.playArea.hero)
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
                var highCost = toRivalList.sortedBy { playCard -> playCard.cost }.lastOrNull()
                if (highCost == null) {
                    return false;
                }
                else {
                    return runWithRetry(3, 200, card, highCost)
                    
                }
            }
            else if (card.cardId.startsWith("VAC_916")) { // 神圣佳酿
                var highCost = me.playArea.cards.sortedBy { playCard -> playCard.cost }.lastOrNull()
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
                var highCost = me.playArea.cards.sortedBy { playCard -> playCard.cost }.lastOrNull()
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
                var tauntCard = toRivalList.find { card-> card.isTaunt }
                var canBeAttacked = toRivalList.sortedBy { card.cost }.lastOrNull()
                var firstCard = rival.playArea.cards.firstOrNull()
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
                var highCost = me.playArea.cards.sortedBy { playCard -> playCard.cost }.lastOrNull()
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