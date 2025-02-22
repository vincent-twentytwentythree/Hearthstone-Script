package club.xiaojiawei.util

import club.xiaojiawei.bean.Card
import club.xiaojiawei.bean.Player
import club.xiaojiawei.bean.SimulateCard
import club.xiaojiawei.bean.SimulateCard.Companion.TAUNT_EXTRA_WEIGHT
import club.xiaojiawei.bean.SimulateWeightCard
import club.xiaojiawei.bean.area.PlayArea
import club.xiaojiawei.config.CALC_THREAD_POOL
import club.xiaojiawei.config.log
import club.xiaojiawei.data.CARD_WEIGHT_TRIE
import club.xiaojiawei.enums.CardTypeEnum
import club.xiaojiawei.status.War
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import kotlin.math.max
import kotlin.math.min

/**
 * @author 肖嘉威 xjw580@qq.com
 * @date 2024/9/13 17:39
 */
private const val EXEC_ACTION: Boolean = true

private val MAX_INVERSION_CALC_COUNT = min(12, Runtime.getRuntime().availableProcessors())

object DeckStrategyUtil {

    private var me: Player? = null
    private lateinit var myPlayArea: PlayArea
    private lateinit var myHandCards: List<Card>
    private lateinit var myPlayCards: List<Card>

    private var rival: Player? = null
    private lateinit var rivalPlayArea: PlayArea
    private lateinit var rivalHandCards: List<Card>
    private lateinit var rivalPlayCards: List<Card>

    private fun assign() {
        me = War.me
        me?.let {
            myPlayArea = it.playArea
            myPlayCards = myPlayArea.cards
            myHandCards = it.handArea.cards
        }
        rival = War.rival
        rival?.let {
            rivalPlayArea = it.playArea
            rivalPlayCards = rivalPlayArea.cards
            rivalHandCards = it.handArea.cards
        }
    }

    private data class Action(
        val deathCard: Card?,
        val runnable: Runnable,
    )

    private class Result(
        @Volatile
        var allWeight: Double = Int.MIN_VALUE.toDouble(),
    ) {

        private var actions: List<Action> = emptyList()

        var myCard: MutableList<SimulateCard>? = null

        var rivalCard: MutableList<SimulateCard>? = null

        fun execAction(): Int {
            val text = "权重: ${allWeight}, 步骤↓"
            log.info { text }
            if (!EXEC_ACTION) {
                println(text)
            }
            var deathCount = 0
            actions.forEach { action ->
                deathCount += if (action.deathCard == null) 0 else 1
                action.runnable.run()
            }
            return deathCount
        }

        fun setNewResult(result: Result) {
            setNewResult(result.allWeight, result.actions, result.myCard, result.rivalCard)
        }

        fun setNewResult(
            newWeight: Double,
            newActions: List<Action>,
            myCards: List<SimulateCard>?,
            rivalCards: List<SimulateCard>?
        ) {
            if (newWeight >= allWeight) {
                synchronized(DeckStrategyUtil::javaClass) {
                    if (newWeight >= allWeight) {
                        allWeight = newWeight
                        actions = newActions.toList()
                        myCards?.let {
                            this.myCard = SimulateCard.copySimulateList(it)
                        }
                        rivalCards?.let {
                            this.rivalCard = SimulateCard.copySimulateList(it)
                        }
                    }
                }
            }
        }

        fun isValid(result: Result): Boolean {
            return result.actions !== emptyList<Action>()
        }
    }

    private fun findTauntCardCount(rivalPlayCards: List<Card>): Int {
        return rivalPlayCards.sumOf { if (it.isTaunt && it.canBeAttacked()) 1 else 0 as Int }
    }

    private fun calcBlood(card: Card): Int {
        return card.health + card.armor - card.damage
    }

    /**
     * @param myAtcWeight 我方随从攻击力权重，大于1表示攻击力比生命值重要
     * @param rivalAtcWeight 敌方随从攻击力权重，rivalAtcWeight/myAtcWeight小于1时认为我方随从更加厉害，防止出现我方2-2解对方2-2的情况
     */
    private fun clean( // MYWEN 随从交换
        myAtcWeight: Double,
        rivalAtcWeight: Double,
        mutableMap: MutableMap<Card, Double> = mutableMapOf(),
        ignoreRival: List<Card> = emptyList<Card>(),
        ignoreCompanion: List<Card> = emptyList<Card>(),
    ) {
        val myCardWeightCalc: Function<Card, Double> = Function {
            var value = mutableMap.getOrDefault(it, 1.0)
            if (it.isDeathRattle) {
                value -= 0.3
            }
            if (it.isTaunt) {
                value += 0.1
            }
            if (it.isAdjacentBuff) {
                value += 0.3
            }
            if (it.isAura) {
                value += 0.3
            }
            if (it.isWindFury) {
                value += 0.15
            }
            if (it.isTitan) {
                value += 0.5
            }
            if (it.isTriggerVisual) {
                value += 0.1
            }
            value += it.spellPower * 0.1
            if (it.cardType === CardTypeEnum.HERO) {
                value += 0.01
            }
            value
        }
        val rivalCardWeightCalc: Function<Card, Double> = myCardWeightCalc

        val myAtcWeightCalc: Function<Card, Double> = Function {
            var value = myAtcWeight
            if (it.isLifesteal) {
                value += 0.1
            }
            if (it.isReborn) {
                value -= max((5 - it.atc) / 10.0, 0.1)
            }
            value
        }
        val rivalAtcWeightCalc: Function<Card, Double> = Function {
            var value = rivalAtcWeight
            if (it.isLifesteal) {
                value += 0.1
            }
            if (it.isReborn) {
                value -= max((5 - it.atc) / 10.0, 0.1)
            }
            value
        }

        var myPlayCards = this.myPlayCards.filter { !ignoreCompanion.contains(it) } .toMutableList()
        var rivalPlayCards = this.rivalPlayCards.filter { !ignoreRival.contains(it) } .toMutableList()
        this.myPlayArea.hero?.let { myPlayCards.add(it) }
        this.rivalPlayArea.hero?.let { 
            if (it.isImmune == false) {
                rivalPlayCards.add(it)
            }
        }

        var firstMyCards: MutableList<SimulateCard>? = null
        var text: String

        //  清理嘲讽
        val myAttackCountCalc: Function<Card, Int> = Function<Card, Int> {
            if (it.canAttack()) {
                if (it.isWindFury) 2 else 1
            } else 0
        }
        var myInversionAttackCountCalc: Function<Card, Int>
        var rivalAttackCountCalc: Function<Card, Int>
        var rivalInversionAttackCountCalc: Function<Card, Int>
        val findTauntCardCount = findTauntCardCount(this.rivalPlayCards)
        if (findTauntCardCount > 0) {
            myInversionAttackCountCalc = Function<Card, Int> { 0 }
            rivalAttackCountCalc = Function<Card, Int> {
                if (it.isTaunt && it.canBeAttacked()) {
                    1
                } else 0
            }
            rivalInversionAttackCountCalc = Function<Card, Int> { 0 }
            val (myCards, rivalCards) = getCleanData(
                myPlayCards = myPlayCards,
                rivalPlayCards = rivalPlayCards,
                myAtcWeightCalc = myAtcWeightCalc,
                rivalAtcWeightCalc = rivalAtcWeightCalc,
                myAttackCountCalc = myAttackCountCalc,
                myInversionAttackCountCalc = myInversionAttackCountCalc,
                rivalAttackCountCalc = rivalAttackCountCalc,
                rivalInversionAttackCountCalc = rivalInversionAttackCountCalc,
                myCardWeightCalc = myCardWeightCalc,
                rivalCardWeightCalc = rivalCardWeightCalc,
            )
            text = "开始思考清理嘲讽"
            log.info { text }
            if (!EXEC_ACTION) {
                println(text)
            }
            val result = calcClean(myCards, rivalCards, true)
            val deathCount = result.execAction()
            if (deathCount < findTauntCardCount) {
                return
            }
            if (EXEC_ACTION) {
                Thread.sleep(3500)
                if (findTauntCardCount(this.rivalPlayCards) > 0) {
                    return
                }
                myPlayCards = this.myPlayCards.toMutableList()
                rivalPlayCards = this.rivalPlayCards.toMutableList()
                this.myPlayArea.hero?.let { myPlayCards.add(it) }
                this.rivalPlayArea.hero?.let { rivalPlayCards.add(it) }
            } else {
                firstMyCards = result.myCard
                rivalPlayCards.removeIf { it.isTaunt && it.canBeAttacked() }
            }
        }

//        普通清理
        myInversionAttackCountCalc = Function<Card, Int> {
            if (it.canBeAttacked()) 1 else 0
        }
        rivalAttackCountCalc = myInversionAttackCountCalc
        rivalInversionAttackCountCalc = Function<Card, Int> {
            if (it.canAttack(true)) {
                if (it.isWindFury) 2 else 1
            } else 0
        }

        val (myNormalCards, rivalNormalCards) = getCleanData(
            myPlayCards = myPlayCards,
            rivalPlayCards = rivalPlayCards,
            myAtcWeightCalc = myAtcWeightCalc,
            rivalAtcWeightCalc = rivalAtcWeightCalc,
            myAttackCountCalc = myAttackCountCalc,
            myInversionAttackCountCalc = myInversionAttackCountCalc,
            rivalAttackCountCalc = rivalAttackCountCalc,
            rivalInversionAttackCountCalc = rivalInversionAttackCountCalc,
            myCardWeightCalc = myCardWeightCalc,
            rivalCardWeightCalc = rivalCardWeightCalc,
        )
        text = "开始思考清理万物"
        log.info { text }
        if (!EXEC_ACTION) {
            println(text)
        }
        if (firstMyCards == null) {
            calcClean(myNormalCards, rivalNormalCards).execAction()
        } else {
            calcClean(firstMyCards, rivalNormalCards).execAction()
        }
    }

    private fun getCleanData(
        myPlayCards: MutableList<Card>, rivalPlayCards: MutableList<Card>,
        myAtcWeightCalc: Function<Card, Double>, rivalAtcWeightCalc: Function<Card, Double>,
        myAttackCountCalc: Function<Card, Int>, myInversionAttackCountCalc: Function<Card, Int>,
        rivalAttackCountCalc: Function<Card, Int>, rivalInversionAttackCountCalc: Function<Card, Int>,
        myCardWeightCalc: Function<Card, Double>, rivalCardWeightCalc: Function<Card, Double>,
    ): Pair<MutableList<SimulateCard>, MutableList<SimulateCard>> {
        val myCards = mutableListOf<SimulateCard>()
        val rivalCards = mutableListOf<SimulateCard>()
        for (myPlayCard in myPlayCards) {
            val simulateCard = SimulateCard(
                card = myPlayCard,
                attackCount = myAttackCountCalc.apply(myPlayCard),
                inversionAttackCount = myInversionAttackCountCalc.apply(myPlayCard),
                atcWeight = myAtcWeightCalc.apply(myPlayCard),
                inversionAtcWeight = rivalAtcWeightCalc.apply(myPlayCard),
                blood = calcBlood(myPlayCard),
//                对末日预言者特殊处理
                cardWeight = if (myPlayCard.cardId.contains("NEW1_021")) 15.0 else myCardWeightCalc.apply(myPlayCard),
                inversionCardWeight = rivalCardWeightCalc.apply(myPlayCard),
                isDivineShield = myPlayCard.isDivineShield,
            )
            myCards.add(simulateCard)
        }
        for (rivalCard in rivalPlayCards) {
            val simulateCard = SimulateCard(
                card = rivalCard,
                attackCount = rivalAttackCountCalc.apply(rivalCard),
                inversionAttackCount = rivalInversionAttackCountCalc.apply(rivalCard),
                atcWeight = rivalAtcWeightCalc.apply(rivalCard),
                inversionAtcWeight = myAtcWeightCalc.apply(rivalCard),
                blood = calcBlood(rivalCard),
                cardWeight = rivalCardWeightCalc.apply(rivalCard),
                inversionCardWeight = myCardWeightCalc.apply(rivalCard),
                isDivineShield = rivalCard.isDivineShield,
            )
            rivalCards.add(simulateCard)
        }
        return Pair(myCards, rivalCards)
    }

    private fun calcClean(
        myCards: MutableList<SimulateCard>, rivalCards: MutableList<SimulateCard>, disableInversion: Boolean = false
    ): Result {
        val start = System.currentTimeMillis()
        val finalResult = Result()
        val task = mutableListOf<CompletableFuture<Void>>()
        var text: String
        var realDisableInversion = disableInversion
        if (!realDisableInversion) {
            realDisableInversion =
                rivalCards.sumOf { it.inversionAttackCount } + myCards.sumOf { it.attackCount } > MAX_INVERSION_CALC_COUNT
                        || rivalCards.sumOf { it.attackCount } + myCards.sumOf { it.inversionAttackCount } > MAX_INVERSION_CALC_COUNT
        }
        text = if (realDisableInversion) {
            "禁用反演"
        } else {
            "启用反演"
        }
        log.info { text }
        if (!EXEC_ACTION) {
            println(text)
        }
        for (index in myCards.indices) {
            if (myCards[0].canAttack(false)) {
                val tempMyCards = SimulateCard.copySimulateList(myCards)
                val tempRivalCards = SimulateCard.copySimulateList(rivalCards)
                task.add(CompletableFuture.runAsync({
                    val initWeight = calcStateWeight(tempMyCards, tempRivalCards, false)
                    val result: Result
                    if (realDisableInversion) {
                        result = Result(initWeight)
                    } else {
                        val inversionMyCards = SimulateCard.copySimulateList(tempMyCards)
                        val inversionRivalCards = SimulateCard.copySimulateList(tempRivalCards)
                        val inversionResult = Result()
                        recursionCalcClean(
                            inversionRivalCards,
                            inversionMyCards,
                            0,
                            mutableListOf(),
                            inversionResult,
                            inversion = true, disableInversion = false
                        )
                        result = Result(calcAllWeight(initWeight, inversionResult.allWeight))
                    }
                    recursionCalcClean(
                        tempMyCards, tempRivalCards,
                        0,
                        mutableListOf(), result, inversion = false, disableInversion = realDisableInversion
                    )
                    finalResult.setNewResult(result)
                }, CALC_THREAD_POOL))
            }
            myCards.add(myCards.removeFirst())
        }
        CompletableFuture.allOf(*task.toTypedArray()).get()
        text = "思考耗时：" + (System.currentTimeMillis() - start) + "ms"
        log.info { text }
        if (!EXEC_ACTION) {
            println(text)
        }
        return finalResult
    }

    private fun recursionCalcClean(
        myCards: List<SimulateCard>, rivalCards: List<SimulateCard>,
        myIndex: Int,
        actions: MutableList<Action>, result: Result,
        inversion: Boolean, disableInversion: Boolean
    ) {
        if (myIndex == myCards.size) {
            val weight = calcStateWeight(myCards, rivalCards, inversion)
            if (disableInversion || inversion) {
                result.setNewResult(weight, actions, myCards, rivalCards)
            } else {
                val inversionResult = Result()

                recursionCalcClean(
                    SimulateCard.copySimulateList(rivalCards),
                    SimulateCard.copySimulateList(myCards),
                    0,
                    mutableListOf(),
                    inversionResult,
                    true, disableInversion = false
                )
                result.setNewResult(calcAllWeight(weight, inversionResult.allWeight), actions, myCards, rivalCards)
            }
            return
        }

        val myCard = myCards[myIndex]
        val task = mutableListOf<CompletableFuture<Void>>()
        if (myCard.canAttack(inversion)) { // MYWEN
            for (rivalCard in rivalCards) {
//                敌方随从能被攻击，突袭无法攻击英雄
                if (rivalCard.canBeAttacked(inversion)
                    && !(rivalCard.card.cardType === CardTypeEnum.HERO && (myCard.card.isAttackableByRush || (myCard.card.isRush && myCard.card.numTurnsInPlay == 0)))
                ) {
                    attack(
                        myCards,
                        rivalCards,
                        myIndex,
                        actions,
                        result,
                        myCard,
                        rivalCard,
                        inversion,
                        disableInversion
                    )
                }
            }
        }
        recursionCalcClean(
            myCards,
            rivalCards,
            myIndex + 1,
            actions,
            result,
            inversion,
            disableInversion
        )
        CompletableFuture.allOf(*task.toTypedArray()).get()
    }

    private fun attack(
        myCards: List<SimulateCard>, rivalCards: List<SimulateCard>,
        myIndex: Int,
        actions: MutableList<Action>, result: Result,
        myCard: SimulateCard, rivalCard: SimulateCard,
        inversion: Boolean, disableInversion: Boolean
    ) {
        val myDivineShield = myCard.isDivineShield
        val rivalDivineShield = rivalCard.isDivineShield

        val index = actions.size
        val myCardBlood = myCard.blood
        val rivalCardBlood = rivalCard.blood

        if (inversion) {
            myCard.inversionAttackCount--
        } else {
            myCard.attackCount--
        }

        if (myCard.card.isImmuneWhileAttacking || myCard.card.isImmune) {
        } else if (myDivineShield) {
            if (rivalCard.card.atc > 0) {
                myCard.isDivineShield = false
            }
        } else if (rivalCard.card.isPoisonous) {
            myCard.blood = -myCard.blood
        } else {
            myCard.blood -= rivalCard.card.atc
        }

        if (rivalDivineShield) {
            rivalCard.isDivineShield = false
        } else if (myCard.card.isPoisonous) {
            rivalCard.blood = -rivalCard.blood
        } else {
            rivalCard.blood -= myCard.card.atc
        }

        val deathCard = if (rivalCard.isAlive()) null else rivalCard.card
        actions.add(Action(deathCard) {
            val myC = myCard.card
            val rivalC = rivalCard.card
            val text =
                "【${myC.entityName}: ${myC.atc}-${myCardBlood}】攻击【${rivalC.entityName}: ${rivalC.atc}-${rivalCardBlood}】"
            log.info { text }
            if (EXEC_ACTION) {
                myCard.card.action.attack(rivalCard.card)
            } else {
                println("$text -> 死亡: ${deathCard?.entityName}")
            }
        })

        val nextIndex = if (myCard.attackCount > 0) myIndex else myIndex + 1
        recursionCalcClean(
            myCards,
            rivalCards,
            nextIndex,
            actions,
            result,
            inversion,
            disableInversion
        )

        if (inversion) {
            myCard.inversionAttackCount++
        } else {
            myCard.attackCount++
        }

        if (myCard.card.isImmuneWhileAttacking || myCard.card.isImmune) {
        } else if (myDivineShield) {
            myCard.isDivineShield = true
        } else if (rivalCard.card.isPoisonous) {
            myCard.blood = -myCard.blood
        } else {
            myCard.blood += rivalCard.card.atc
        }

        if (rivalDivineShield) {
            rivalCard.isDivineShield = true
        } else if (myCard.card.isPoisonous) {
            rivalCard.blood = -rivalCard.blood
        } else {
            rivalCard.blood += myCard.card.atc
        }
        actions.removeAt(index)
    }

    private fun calcAllWeight(weight: Double, inversionWeight: Double): Double {
        return 0.6 * weight - 0.4 * inversionWeight
    }

    private fun calcStateWeight(
        myCards: List<SimulateCard>, rivalCards: List<SimulateCard>, inversion: Boolean
    ): Double {
        val myWeight = calcSelfWeight(myCards, inversion)
        val rivalWeight = calcSelfWeight(rivalCards, inversion)
        return myWeight.first - rivalWeight.first - if (rivalWeight.second > 0) TAUNT_EXTRA_WEIGHT else 0
    }

    private fun calcSelfWeight(simulateCards: List<SimulateCard>, inversion: Boolean): Pair<Double, Int> {
        var tauntCount = 0
        var weight = 0.0
        for (simulateCard in simulateCards) {
            weight += simulateCard.calcSelfWeight(inversion)
            if (simulateCard.card.isTaunt && simulateCard.card.canBeAttacked() && simulateCard.isAlive()) {
                tauntCount++
            }
        }
        return Pair(weight, tauntCount)
    }

    fun cleanPlay(
        myAtcWeight: Double = 1.2,
        rivalAtcWeight: Double = 1.2,
        mutableMap: MutableMap<Card, Double> = mutableMapOf(),
        ignoreRival: List<Card> = emptyList<Card>(),
        ignoreCompanion: List<Card> = emptyList<Card>(),
    ) {
        assign()
        clean(myAtcWeight, rivalAtcWeight, mutableMap, ignoreRival, ignoreCompanion)
    }

    fun calcPowerOrderConvert(cards: List<Card>, target: Int,
        toRivalList: List<Card>, 
        plays: List<Card>, 
        hards: List<Card>,
        minionNeededToBurst: ArrayList<Card>
    ): Pair<Double, List<SimulateWeightCard>> {
        return calcPowerOrderBluteForce(cards, target,
            toRivalList,
            plays,
            hards,
            minionNeededToBurst
        )
    }

    fun calcPowerOrderConvert(cards: List<Card>, target: Int): Pair<Double, List<SimulateWeightCard>> {
        return calcPowerOrder(convertToSimulateWeightCard(cards), target)
    }

    fun calcPowerOrder(cards: List<SimulateWeightCard>, oriTarget: Int): Pair<Double, List<SimulateWeightCard>> { // MYWEN dp
        // dp[j] 表示总 cost 为 j 时的最高 (cost + weight) 值
        var luckCoin = cards.count { it.card.cardId == "GAME_005" || it.card.cardId == "CORE_EX1_169" }
        var luckCoinList: MutableList<SimulateWeightCard> = cards.filter { it.card.cardId == "GAME_005" || it.card.cardId == "CORE_EX1_169" }.toMutableList()
        var target = oriTarget + luckCoin
        val dp = DoubleArray(target + 1)
        val chosenCards = Array(target + 1) { mutableListOf<SimulateWeightCard>() }

        for (card in cards) {
            for (j in target downTo card.card.cost) {
                val newTotal = dp[j - card.card.cost] + card.card.cost + card.weight
                if (newTotal > dp[j] ||
                    (newTotal == dp[j] && chosenCards[j - card.card.cost].sumOf { it.card.cost } < chosenCards[j].sumOf { it.card.cost })
                ) {
                    dp[j] = newTotal
                    chosenCards[j] = chosenCards[j - card.card.cost].toMutableList().apply { add(card) }
                }
            }
        }

        // 处理 cost 为 0 的 Card
        if (target == 0) {
            chosenCards[0] = cards.filter { it.card.cost == 0 && it.card.cardId != "GAME_005" && it.card.cardId != "CORE_EX1_169" }
                .sortedByDescending { it.weight }
                .toMutableList()
        } else {
            for (it in cards) {
                if (it.card.cost == 0 && it.card.cardId != "GAME_005" && it.card.cardId != "CORE_EX1_169") {
                    for (j in target downTo 0) {
                        if (dp[j] > 0) { // 当前总 cost 大于 0 时才选择 cost 为 0 的 Card
                            chosenCards[j].add(it)
                        }
                    }
                }
            }
        }

        var choosenTarget = oriTarget
        for (coin in luckCoin downTo 0) {
            if (dp[oriTarget + coin] > dp[choosenTarget]) {
                choosenTarget = oriTarget + coin
                chosenCards[choosenTarget].addAll(luckCoinList.subList(0, coin))
            }
        }

        return Pair(dp[choosenTarget], chosenCards[choosenTarget])
    }

    fun calcPowerOrderBluteForce(cards: List<Card>, oriTarget: Int,
        toRivalList: List<Card>, 
        plays: List<Card>, 
        hards: List<Card>,
        minionNeededToBurst: ArrayList<Card>
    ): Pair<Double, List<SimulateWeightCard>> {

        var allCombinations = getAllCombinations(cards)

        var legalCombinations = allCombinations.filter { checkLegal(it, oriTarget, toRivalList, plays, hards) }

        var maxValueCombination = legalCombinations.sortedWith(
                compareBy<List<Card>> { getValue(it, toRivalList, plays, hards, minionNeededToBurst) }.thenBy { -it.size }
            ).lastOrNull()
        
        if (maxValueCombination == null) {
            return Pair(0.0, emptyList())
        }
        else {
            var maxValue = getValue(maxValueCombination, toRivalList, plays, hards, minionNeededToBurst)
            if (maxValue > 0) {
                return Pair(0.0 + maxValue, convertToSimulateWeightCard(maxValueCombination))
            }
            else {
                return Pair(0.0, emptyList())
            }
        }
    }


    fun convertToSimulateWeightCard(cards: List<Card>): List<SimulateWeightCard> {
        val result = mutableListOf<SimulateWeightCard>()
        for (card in cards) {
            result.add(SimulateWeightCard(card, CARD_WEIGHT_TRIE[card.cardId]?.weight ?: 1.0))
        }
        return result
    }

    fun sortCard(cards: List<SimulateWeightCard>): List<SimulateWeightCard> {
        cards.forEach { t ->
            t.powerWeight = CARD_WEIGHT_TRIE[t.card.cardId]?.powerWeight ?: 1.0
        }
        return cards.sortedByDescending { it.powerWeight }
    }

    fun getAllCombinations(input: List<Card>): List<List<Card>> {
        if (input.isEmpty()) return listOf(emptyList())
    
        val head = input.first()
        val tailCombinations = getAllCombinations(input.drop(1))
    
        return tailCombinations + tailCombinations.map { it + head }
    }

    fun checkLegal(input: List<Card>, target: Int,
        toRivalList: List<Card>, 
        plays: List<Card>, 
        hards: List<Card>
    ): Boolean {
        var luckCoin = input.count { it.cardId == "GAME_005" || it.cardId == "CORE_EX1_169" }
        var minionCount = input.count { it.cardType == CardTypeEnum.MINION } + plays.count { it.cardType == CardTypeEnum.MINION }
        return (input.sumOf { it.cost } <= target + luckCoin) && (minionCount <= 7)
    }

    fun getValue(
        action: List<Card>,
        toRivalList: List<Card>, 
        plays: List<Card>, 
        hards: List<Card>,
        minionNeededToBurst: ArrayList<Card>
    ): Int {
        var score = 0
        var rivalNumOnBattlefield = toRivalList.count { it.cardType == CardTypeEnum.MINION }
        var companionNumOnBattlefield = plays.size
        var handsNum = hards.size
        for (card in action) {
            val cardId = card.cardId
            when {
                cardId.startsWith("VAC_323") -> { // 麦芽岩浆
                    score += minOf(card.cost, rivalNumOnBattlefield)
                }
                cardId.startsWith("GDB_445") -> { // 陨石风暴
                    score += card.cost + (rivalNumOnBattlefield - companionNumOnBattlefield)
                }
                cardId.startsWith("GDB_320") -> { // 艾瑞达蛮兵 有时候会减费，hard code
                    score += 7
                }
                else -> {
                    score += card.cost
                }
            }
        }
    
        // 法强+1
        val powerPlusOnBattleField = plays.count { 
            (it.cardId == "GDB_310") or (it.cardId == "CS3_007") or (it.cardId == "CS2_052")
        }
        val powerPlusCount = action.count {
            (it.cardId == "GDB_310") or (it.cardId == "CS3_007")
        } + powerPlusOnBattleField
    
        for (card in action) {
            val cardId = card.cardId
            when {
                cardId == "TOY_508" -> { // 立体书
                    score += powerPlusCount
                }
                cardId.startsWith("VAC_323") -> { // 麦芽岩浆
                    score += powerPlusCount * rivalNumOnBattlefield
                }
                cardId.startsWith("GDB_445") -> { // 陨石风暴
                    score += powerPlusOnBattleField * (rivalNumOnBattlefield - companionNumOnBattlefield)
                }
            }
        }
    
        // 法术迸发
        val whetherSpell = action.count {
            it.cardType == CardTypeEnum.SPELL
        } > 0

        if (whetherSpell) {
            score += minionNeededToBurst.size * 2 // hard code
        }

        val spellCount = action.count { // 有情况硬币需要跳币，不能触发法术迸发，不想修了todo
            it.cardType == CardTypeEnum.SPELL && it.cardId != "GAME_005" && it.cardId != "GDB_445"
        }
    
        for (card in action) {
            val cardId = card.cardId
            when {
                cardId == "GDB_434" && spellCount > 0 -> { // 流彩巨岩
                    score += 3
                }
                cardId == "GDB_310" && spellCount > 0 -> { // 虚灵神谕者
                    score += 2
                }
            }
        }
    
        // 其他特殊效果
        for (card in action) {
            val cardId = card.cardId
            when (cardId) {
                "CS3_034" -> { // 织法者玛里苟斯
                    score += 10 - (handsNum - action.size)
                }
                "VAC_321" -> { // 伊辛迪奥斯
                    score += 5 * 2
                }
                "GDB_901" -> { // 极紫外破坏者
                    if (rivalNumOnBattlefield > 0) {
                        score += 1
                    }
                }
            }
        }
    
        return score
    }

}
