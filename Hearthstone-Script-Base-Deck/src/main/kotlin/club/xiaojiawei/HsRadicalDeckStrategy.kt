package club.xiaojiawei

import club.xiaojiawei.bean.Card
import club.xiaojiawei.bean.isValid
import club.xiaojiawei.config.log
import club.xiaojiawei.enums.CardTypeEnum
import club.xiaojiawei.enums.RunModeEnum
import club.xiaojiawei.status.War
import club.xiaojiawei.util.DeckStrategyUtil
import club.xiaojiawei.util.isFalse
import club.xiaojiawei.util.isTrue

// import club.xiaojiawei.hsscript.data.GAME_RECT
// import club.xiaojiawei.hsscript.data.GameRationConst

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

    override fun executeOutCard() {
        // val realH: Int = GAME_RECT.bottom - GAME_RECT.top
        // val usableH = realH
        // val realW: Int = GAME_RECT.right - GAME_RECT.left
        // val usableW = (realH * GameRationConst.GAME_WINDOW_ASPECT_TO_HEIGHT_RATIO).toInt()
        // val middleX = realW shr 1
        // val middleY = realH shr 1
        // log.info {"GAME_RECT.bottom:${GAME_RECT.bottom}, GAME_RECT.top:${GAME_RECT.top}, GAME_RECT.right:${GAME_RECT.right}, GAME_RECT.left:${GAME_RECT.left}"}
        // log.info {"realH: $realH, realW: $realW, usableW: $usableW, middleX:$middleX, middleY:$middleY"}
        if (War.me.isValid()){
            val me = War.me
            // MYWEN
            val rival = War.rival
            var plays = me.playArea.cards.toList()
            var toRivalList = War.rival.playArea.cards.toList()
            var toMeList = War.me.playArea.cards.toList()
            log.info { "rival: $toRivalList" }
            log.info { "me: $toMeList" }
//            使用地标
            plays.forEach {card->
                if (card.cardType === CardTypeEnum.LOCATION && !card.isLocationActionCooldown){
                    card.action.lClick()
                }
            }
            var hands = me.handArea.cards.toList()
            val (_, resultCards) = DeckStrategyUtil.calcPowerOrderConvert(hands, me.usableResource)
            if (resultCards.isNotEmpty()) {
                val sortCard = DeckStrategyUtil.sortCard(resultCards)
                log.info { "待出牌：$sortCard" }
                for (simulateWeightCard in sortCard) {
                    val card = simulateWeightCard.card
                    log.info { "usableResource: ${me.usableResource}, cost: ${card.cost}, card: $card"  }
                    if (me.usableResource >= card.cost){
                        if (card.cardType === CardTypeEnum.SPELL){
                            // rival.playArea.cards.find { card-> card.canBeTargetedByMe() }?.let {
                            //     card.action.power(it)
                            // }?:let {
                            //     card.action.power()
                            // }
                            if (card.cardId == "GDB_445") { // 陨石风暴
                                var highCostCount = me.playArea.cards.filter { playCard -> playCard.cost >= 3}.count()
                                var highCostCountRival = rival.playArea.cards.filter { playCard -> playCard.cost >= 3}.count()
                                if (highCostCount >= 3 && highCostCountRival <= 2) {
                                    log.info { "too much high cost cards" }
                                    continue;
                                }
                                log.info { "start storm, ${plays}" }
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
                            else if (
                                card.cardId == "GDB_456" || //自燃
                                card.cardId == "YOG_526" || //触须缠握
                                card.cardId == "TOY_508" || //立体书
                                card.cardId == "TTN_454" || //  殉船
                                card.cardId == "CORE_AT_064" || // 怒袭
                                card.cardId == "MIS_709" // 圣光荧光棒
                            ) {
                                card.action.power(rival.playArea.hero)
                            }
                            else if (
                                card.cardId.startsWith("GDB_305") || // 阳炎耀斑
                                card.cardId.startsWith("CORE_EX1_129") || // 刀扇
                                card.cardId.startsWith("VAC_323") || // 麦芽岩浆
                                card.cardId == "CORE_CS2_093" || // 奉献
                                card.cardId == "VAC_414" || // 炽热火炭
                                card.cardId == "ETC_069") { // 渐强声浪
                                if (toRivalList.size <= 2) {
                                    continue;
                                }
                                else if (playCard.size <= 2 && card.cardId.startsWith("GDB_305")) {
                                    continue;
                                }
                            }
                            else if (
                            card.cardId == "CORE_SW_085" // 暗巷契约
                            && hands.size <= 6) {
                                continue;
                            }
                            else if (
                            card.cardId.startsWith("VAC_951") // “健康”饮品
                            ) { 
                                var highCost = rival.playArea.cards.sortedBy { playCard -> playCard.cost }.lastOrNull()
                                if (highCost == null) {
                                    continue;
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
                                    continue;
                                }
                                else if (card.cardId.startsWith("ETC_076") && highCost.cost <= 4) { // 街舞起跳
                                    continue;
                                }
                                else {
                                    card.action.power(highCost)
                                }
                            }
                            else {
                                card.action.power()
                            }
                        }
                        else {
                            if (me.playArea.isFull) break
                            // card.isBattlecry.isTrue {
                            if (card.cardId == "GDB_901") { // 极紫外破坏者
                                var tauntCard = rival.playArea.cards.find { card-> card.isTaunt }
                                var canAttackCard = rival.playArea.cards.filter { card-> card.canAttack() }.sortedBy { card.cost }.lastOrNull()
                                var firstCard = rival.playArea.cards.firstOrNull()
                                tauntCard?.let {
                                    card.action.power(it)
                                }?:let {
                                    canAttackCard?.let {
                                        card.action.power(it)
                                    }?:let {
                                        firstCard?.let {
                                            card.action.power(it)
                                        }
                                        ?:let{
                                            card.action.power()
                                        }
                                    }
                                }
                                log.info { "tauntCard: $tauntCard, canAttackCard: $canAttackCard, firstCard: $firstCard"}
                            }
                            else if (card.cardId == "ETC_332" && plays.size <= 2 ) { // 梦中男神
                                continue
                            }
                            else if (card.cardId == "CS3_034" && hands.size >= 5) { // 织法者玛里苟斯
                                continue
                            }
                            else if (card.cardId.startsWith("TTN_087") || // 吸附寄生体
                            card.cardId.startsWith("WORK_009") // 月度魔范员工
                            ) { 
                                var highCost = me.playArea.cards.sortedBy { playCard -> playCard.cost }.lastOrNull()
                                if (highCost == null) {
                                    continue;
                                }
                                else {
                                    card.action.power(highCost)
                                }
                            }
                            else {
                                card.action.power()
                            }

                            // }.isFalse {
                            //     card.action.power()
                            // }
                        }
                    }
                }
            }
            plays = me.playArea.cards.toList()
//            使用地标
            plays.forEach {card->
                if (card.cardType === CardTypeEnum.LOCATION && !card.isLocationActionCooldown){
                    card.action.lClick()
                }
            }
            commonDeckStrategy.executeOutCard()

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
    }

    override fun executeDiscoverChooseCard(vararg cards: Card): Int {
        return commonDeckStrategy.executeDiscoverChooseCard(*cards)
    }
}