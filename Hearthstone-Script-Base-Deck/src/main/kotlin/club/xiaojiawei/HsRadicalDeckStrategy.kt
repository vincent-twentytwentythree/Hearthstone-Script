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
    }

    // how to clean MYWEN
    private val minionNeededToBurst: ArrayList<Card> = arrayListOf()
    private val playedActions: ArrayList<ArrayList<Card>> = arrayListOf()

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
        return PredictActionResponse("fail", arrayListOf(), "fail")
    }

    // MYWEN
    override fun executeOutCard() {
        if (War.me.isValid()){
            if (War.me.playArea.isFull) {
                log.info { "playArea is full, clean it first" }
                attackForAllCardsInPlayArea()
            }

            val me = War.me
            val rival = War.rival
            var plays = me.playArea.cards.toList()
            var toRivalList = War.rival.playArea.cards.toList().filter { it.canBeAttacked() }
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
                position = "pk_dp"
            }
            var predictActionRequest = PredictActionRequest(position,
                                                            me.usableResource,
                                                            hands.map { it.cardId } ,
                                                            arrayListOf(),
                                                            playedActions.map { it.map{ card -> card.cardId } },
                                                            toRivalList.map { it.cardId },
                                                            plays.map { it.cardId },
                                                            minionNeededToBurst.map { it.cardId }
                                                            )
            var predictActionResponse = sendPostRequest(predictActionRequest)
            val playedCard: ArrayList<Card> = arrayListOf()
            if (predictActionResponse.status == "succ") {

                log.info { "待出牌：${predictActionResponse.action}" }
                for (cardId in predictActionResponse.action) {
                    val card = hands.filter { it.cardId == cardId }.firstOrNull()
                    if (card == null) {
                        continue
                    }
                    log.info { "usableResource: ${me.usableResource}, cost: ${card.cost}, card: $card"  }
                    if (me.usableResource >= card.cost){
                        var succ = playCard(card)
                        if (succ == true) {
                            playedCard.add(card)
                            if (card.cardId == "CS3_034") {
                                // Sleep for 8 seconds
                                Thread.sleep(8000)
                            }
                            else if (card.cardId == "GDB_445") { // 陨石风暴
                                // Sleep for 2 seconds
                                Thread.sleep(2000)
                            }
                            else {
                                Thread.sleep(200)
                            }
                            if (card.cardId == "GDB_434" // 流彩巨岩
                                || card.cardId == "GDB_310" // 虚灵神谕者
                            ) {
                                minionNeededToBurst.add(card)
                            }
                            if (card.cardType == CardTypeEnum.SPELL ) {
                                minionNeededToBurst.clear()
                            }
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
                            var succ = playCard(card)
                            if (succ == true) {
                                playedCard.add(card)
                                if (card.cardId == "CS3_034") {
                                    // Sleep for 8 seconds
                                    Thread.sleep(8000)
                                }
                                else if (card.cardId == "GDB_445") { // 陨石风暴
                                    // Sleep for 2 seconds
                                    Thread.sleep(2000)
                                }
                                else {
                                    Thread.sleep(200)
                                }
                                if (card.cardId == "GDB_434" // 流彩巨岩
                                    || card.cardId == "GDB_310" // 虚灵神谕者
                                ) {
                                    minionNeededToBurst.add(card)
                                }
                                if (card.cardType == CardTypeEnum.SPELL ) {
                                    minionNeededToBurst.clear()
                                }
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

    private fun checkWhetherCanBeUsedThisTurn (card: Card): Boolean {
        val me = War.me
        val rival = War.rival
        var plays = me.playArea.cards.toList()
        var toRivalList = War.rival.playArea.cards.toList().filter { it.canBeAttacked() }
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
            else if (card.cardId == "CS3_034") {
                if (hands.size >= 5) { // 织法者玛里苟斯
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
        var toRivalList = War.rival.playArea.cards.toList().filter { it.canBeAttacked() }
        if (card.cardType === CardTypeEnum.SPELL) {
            if (card.cardId == "GDB_445") { // 陨石风暴
                log.info { "start storm, ${plays}" }
                attackForAllCardsInPlayArea()
                card.action.power()
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
                card.action.power(rival.playArea.hero)
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
                else {
                    card.action.power()
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
                else {
                    card.action.power(highCost)
                }
            }
            else if (card.cardId.startsWith("VAC_916")) { // 神圣佳酿
                var highCost = me.playArea.cards.sortedBy { playCard -> playCard.cost }.lastOrNull()
                if (highCost == null) {
                    card.action.power(me.playArea.hero)
                }
                else {
                    card.action.power(highCost)
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
                    card.action.power(highCost)
                }
            }
            else {
                card.action.power()
            }
        }
        else { //////////////////////////////////////////////////////////////////////////////////////非法术牌
            if (me.playArea.isFull) return false;
            // card.isBattlecry.isTrue {
            if (card.cardId == "GDB_901") { // 极紫外破坏者
                var tauntCard = rival.playArea.cards.find { card-> card.isTaunt }
                var canBeAttacked = rival.playArea.cards.filter { card-> card.canBeAttacked() }.sortedBy { card.cost }.lastOrNull()
                var firstCard = rival.playArea.cards.firstOrNull()
                tauntCard?.let {
                    card.action.power(it)
                }?:let {
                    canBeAttacked?.let {
                        card.action.power(it)
                    }?:let {
                        firstCard?.let {
                            card.action.power(it)
                        }?:let {
                            card.action.power()
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
                    card.action.power(highCost)
                }
            }
            else {
                card.action.power()
            }
        }
        return true
    }
}