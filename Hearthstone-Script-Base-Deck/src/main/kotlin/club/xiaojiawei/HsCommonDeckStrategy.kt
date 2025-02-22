package club.xiaojiawei

import club.xiaojiawei.bean.Card
import club.xiaojiawei.bean.Player
import club.xiaojiawei.bean.SimulateWeightCard
import club.xiaojiawei.config.log
import club.xiaojiawei.enums.CardTypeEnum
import club.xiaojiawei.enums.RunModeEnum
import club.xiaojiawei.status.War
import club.xiaojiawei.status.War.me
import club.xiaojiawei.util.DeckStrategyUtil
import club.xiaojiawei.util.DeckStrategyUtil.sortCard

/**
 * @author 肖嘉威
 * @date 2024/9/8 14:56
 */
class HsCommonDeckStrategy : DeckStrategy() {

    override fun name(): String {
        return "基础策略"
    }

    override fun getRunMode(): Array<RunModeEnum> {
        return arrayOf(RunModeEnum.CASUAL, RunModeEnum.STANDARD, RunModeEnum.WILD, RunModeEnum.PRACTICE)
    }

    override fun deckCode(): String {
        return ""
    }

    override fun id(): String {
        return "e71234fa-base-deck-97e9-1f4e126cd33b"
    }

    override fun executeChangeCard(cards: HashSet<Card>) { // 起手换牌 MYWEN
        val pirate_count = cards.count {
            it.cardId == "TOY_518" || it.cardId == "VAC_512" || it.cardId == "CORE_WON_065"
        }
        val set = setOf("TOY_518", "VAC_512", "CORE_WON_065", "DRG_056")
        if (pirate_count == 0) {
            cards.clear()
        }
        else {
            cards.removeIf { 
                card -> card.cost > 2
                || card.cardType != CardTypeEnum.MINION
                || card.cardId == "CFM_637" // 
                || card.cardId == "DEEP_008" // 针岩图腾
                || card.cardId == "GDB_451" // 三角测量
                || !set.contains(card.cardId)
            }
        }

    }

    override fun executeOutCard() { // MYWEN
        val me = War.me
        val rival = War.rival

        powerCard(me, rival)

        DeckStrategyUtil.cleanPlay()

        powerCard(me, rival)

//        使用技能
        me.playArea.power?.let {
            if (me.usableResource >= it.cost || it.cost == 0) {
                it.action.power()
            }
        }
        DeckStrategyUtil.cleanPlay()
    }

    private fun powerCard(me: Player, rival: Player) {
        if (me.playArea.isFull) return

        val myHandCards = me.handArea.cards.toList()
        val myHandCardsCopy = myHandCards.toMutableList()
        myHandCardsCopy.removeAll { card -> card.cardType != CardTypeEnum.MINION }

        val (_, resultCards) = DeckStrategyUtil.calcPowerOrderConvert(
            myHandCardsCopy, me.usableResource
        )

        // val coinCard = findCoin(myHandCards)
        // if (coinCard != null) {
        //     val (num1, resultCards1) = DeckStrategyUtil.calcPowerOrderConvert(
        //         myHandCardsCopy, me.usableResource + 1
        //     )
        //     if (num1 > me.usableResource) {
        //         coinCard.action.power()
        //         outCard(resultCards1)
        //         return
        //     }
        // }
        outCard(resultCards)
    }

    fun outCard(cards: List<SimulateWeightCard>) {
        if (cards.isNotEmpty()) {
            var sortCard = sortCard(cards)
            log.debug { "待出牌：$sortCard" }
            for (simulateWeightCard in sortCard) {
                if (me.playArea.isFull) break
                simulateWeightCard.card.action.power()
            }
        }
    }

    private fun findCoin(cards: List<Card>): Card? {
        return cards.find { it.isCoinCard }
    }

    override fun executeDiscoverChooseCard(vararg cards: Card): Int { // MYWEN 发现
        var rivalCount = War.rival.playArea.cards.count { it.canAttack(true) }
        var count323 = War.me.handArea.cards.count { it.cardId.startsWith("VAC_323") }
        var count445 = War.me.handArea.cards.count { it.cardId == "GDB_445" }
        var highCost = -1;
        var highIndex = 0;
        Thread.sleep(300)
        for ((index, card) in cards.withIndex()) {
            log.debug { "card：${card.toSimpleString()}, index: ${index}, zonePos: ${card.zonePos}, rivalCount: ${rivalCount}" }
            if (card.cardId == "VAC_321t") { // 爆发
                return index
            }
            else if (card.cardId == "GDB_430") { // 小行星
                if (highCost < 100) {
                    highCost = 100
                    highIndex = index
                }
            }
            else if (card.cardId == "VAC_323" && rivalCount >= 4 && count323 == 0) { // 麦芽岩浆
                if (highCost < 200) {
                    highCost = 200
                    highIndex = index
                }
            }
            else if (card.cardId == "GDB_445" && rivalCount >= 4 && count445 == 0) { // 陨石风暴
                if (highCost < 150) {
                    highCost = 150
                    highIndex = index
                }
            }
            else if (card.cost > highCost) {
                highCost = card.cost
                highIndex = index
            }
        }
        return highIndex
    }
}