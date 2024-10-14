package club.xiaojiawei.hsscript.strategy

import club.xiaojiawei.DeckStrategy
import club.xiaojiawei.bean.Card
import club.xiaojiawei.bean.isValid
import club.xiaojiawei.config.log
import club.xiaojiawei.hsscript.enums.ConfigEnum
import club.xiaojiawei.hsscript.utils.ConfigUtil
import club.xiaojiawei.hsscript.utils.GameUtil
import club.xiaojiawei.hsscript.utils.SystemUtil
import club.xiaojiawei.status.War
import club.xiaojiawei.status.War.isMyTurn

/**
 * 卡牌策略抽象类
 * @author 肖嘉威
 * @date 2022/11/29 17:29
 */
object DeckStrategyActuator {

    var deckStrategy: DeckStrategy? = null

    fun reset(){
        deckStrategy?.reset()

        checkSurrender()
    }

    fun changeCard() {
        if (!ConfigUtil.getBoolean(ConfigEnum.STRATEGY)) return
        log.info { "执行换牌策略" }
        if (!validPlayer()) return
        if (checkSurrender()) return

        val me = War.me
        try {
            val copyHandCards = HashSet(me.handArea.cards)
            deckStrategy?.executeChangeCard(copyHandCards)
            for (i in me.handArea.cards.indices) {
                val card = me.handArea.cards[i]
                if (!copyHandCards.contains(card)) {
                    log.info { "换掉起始卡牌：【entityId:" + card.entityId + "，entityName:" + card.entityName + "，cardId:" + card.cardId + "】" }
                    GameUtil.clickDiscover(i, me.handArea.cardSize())
                    SystemUtil.delayShortMedium()
                }
            }
            log.info { "执行换牌策略完毕" }
        } finally {
            for (i in 0..2) {
                GameUtil.CONFIRM_RECT.lClick(false)
                SystemUtil.delayShort()
            }
        }

        checkSurrender()
    }

    fun outCard() {
        if (!ConfigUtil.getBoolean(ConfigEnum.STRATEGY)) return
        log.info { "执行出牌策略" }
        if (!validPlayer()) return
        if (checkSurrender()) return

        try {
            War.me.let {
                log.info { "回合开始可用水晶数：" + it.usableResource }
            }
            deckStrategy?.executeOutCard()
            log.info { "执行出牌策略完毕" }
        } finally {
            GameUtil.cancelAction()
            SystemUtil.delayShort()
            for (i in 0 until 3) {
                if (!isMyTurn) break
                GameUtil.END_TURN_RECT.lClick(false)
                SystemUtil.delayMedium()
            }
        }

        checkSurrender()
    }

    fun discoverChooseCard(vararg cards: Card) {
        if (!ConfigUtil.getBoolean(ConfigEnum.STRATEGY)) return
        log.info { "执行发现选牌策略" }
        if (!validPlayer()) return
        if (checkSurrender()) return

        SystemUtil.delay(1000)
        val index = deckStrategy?.executeDiscoverChooseCard(*cards)?:0
        War.me.let {
            GameUtil.clickDiscover(index, it.handArea.cardSize())
            SystemUtil.delayShortMedium()
            val card = cards[index]
            log.info { "选择了：" + card.toSimpleString() }
        }
        log.info { "执行发现选牌策略完毕" }

        checkSurrender()
    }

    private fun validPlayer():Boolean{
        if (!War.rival.isValid() && War.me.isValid()){
            log.warn { "玩家无效" }
            return false
        }
        return true
    }

    private fun checkSurrender(): Boolean{
        deckStrategy?.let {
            if (it.needSurrender){
                log.info { "触发投降" }
                GameUtil.surrender()
                return true
            }
        }
        return false
    }

}