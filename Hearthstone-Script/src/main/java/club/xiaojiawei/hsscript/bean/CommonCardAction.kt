package club.xiaojiawei.hsscript.bean

import club.xiaojiawei.CardAction
import club.xiaojiawei.bean.Card
import club.xiaojiawei.bean.area.PlayArea
import club.xiaojiawei.hsscript.enums.ConfigEnum
import club.xiaojiawei.hsscript.utils.ConfigUtil
import club.xiaojiawei.hsscript.utils.GameUtil
import club.xiaojiawei.status.War.me
import club.xiaojiawei.status.War.rival
import kotlin.math.min

import club.xiaojiawei.config.log

/**
 * 通用卡牌操作
 * @author 肖嘉威
 * @date 2024/9/5 22:42
 */
class CommonCardAction : CardAction(false) {

    private var lastRect: GameRect? = null

    private fun getCardRect(card: Card?): GameRect {
        if (card == null) {
            return GameRect.INVALID
        }
        val area = card.area
        var index: Int
        if (card === me.playArea.hero) {
            return GameUtil.MY_HERO_RECT
        } else if (card === rival.playArea.hero) {
            return GameUtil.RIVAL_HERO_RECT
        } else if (card === me.playArea.power) {
            return GameUtil.MY_POWER_RECT
        } else if (card === rival.playArea.power) {
            return GameUtil.RIVAL_POWER_RECT
        } else if (area === me.playArea) {
            if ((area.indexOfCard(card).also { index = it }) >= 0) {
                return GameUtil.getMyPlayCardRect(index, area.cardSize())
            }
        } else if (area === rival.playArea) {
            if ((area.indexOfCard(card).also { index = it }) >= 0) {
                return GameUtil.getRivalPlayCardRect(index, area.cardSize())
            }
        } else if (area === me.handArea) {
            if ((area.indexOfCard(card).also { index = it }) >= 0) {
                return GameUtil.getMyHandCardRect(index, area.cardSize())
            }
        }
        return GameRect.INVALID
    }

    override fun getCardId(): Array<String> {
        return emptyArray()
    }

    public override fun execPower(): Boolean {
        return me.let {
            execPower(min((it.playArea.cardSize()), it.playArea.maxSize - 1))
        }
    }

    public override fun execPower(card: Card): Boolean {
        var startRect: GameRect
        if ((GameUtil.getMyHandCardRect(me.handArea.indexOfCard(belongCard), belongCard!!.area!!.cardSize())
                .also { startRect = it }).isValid()
        ) {
            if (card.area is PlayArea) {
                val endRect = getCardRect(card)
                if (endRect.isValid()) {
                    var cardId = belongCard!!.cardId
                    if (cardId == "GDB_901") {
                        startRect.lClickMoveDoubleLClick(endRect)
                    }
                    else {
                        log.debug { "belongCard: ${belongCard}, card: ${card}" }
                        startRect.lClickMoveLClick(endRect)
                    }
                    lastRect = endRect
                    return true
                }
            }
        }
        return false
    }

    public override fun execPower(index: Int): Boolean {
        var startRect: GameRect
        if ((GameUtil.getMyHandCardRect(me.handArea.indexOfCard(belongCard), belongCard!!.area!!.cardSize())
                .also { startRect = it }).isValid()
        ) {
            val endRect = GameUtil.getMyPlayCardRect(index, me.playArea.cardSize())
            if (endRect.isValid()) {
                startRect.lClickMoveLClick(endRect)
                lastRect = endRect
                return true
            }
        }
        return false
    }

    public override fun execAttack(card: Card): Boolean {
        val startRect = if (belongCard === me.playArea.hero) {
            GameUtil.MY_HERO_RECT
        } else {
            GameUtil.getMyPlayCardRect(me.playArea.indexOfCard(belongCard), belongCard!!.area!!.cardSize())
        }
        if (startRect.isValid()) {
            if (card.area === rival.playArea) {
                val endRect = getCardRect(card)
                if (endRect.isValid()) {
                    startRect.lClickMoveLClick(endRect)
                    lastRect = endRect
                    return true
                }
            }
        }
        return false
    }

    public override fun execBuff(card: Card): Boolean {
        val startRect = if (belongCard === me.playArea.hero) {
            GameUtil.MY_HERO_RECT
        } else {
            GameUtil.getMyPlayCardRect(me.playArea.indexOfCard(belongCard), belongCard!!.area!!.cardSize())
        }
        if (startRect.isValid()) {
            val endRect = getCardRect(card)
            if (endRect.isValid()) {
                startRect.lClickMoveLClick(endRect)
                lastRect = endRect
                return true
            }
        }
        return false
    }

    public override fun execAttackHero(): Boolean {
        val startRect = if (belongCard === me.playArea.hero) {
            GameUtil.MY_HERO_RECT
        } else {
            GameUtil.getMyPlayCardRect(me.playArea.indexOfCard(belongCard), belongCard!!.area!!.cardSize())
        }
        if (startRect.isValid()) {
            if (belongCard!!.area == me.playArea) {
                startRect.lClickMoveLClick(GameUtil.RIVAL_HERO_RECT)
                lastRect = GameUtil.RIVAL_HERO_RECT
                return true
            }
        }
        return false
    }

    public override fun execPointTo(card: Card): Boolean {
        var startRect: GameRect? = null
        var endRect: GameRect? = null
        lastRect?.let {
            if (it.isValid()) {
                startRect = it
            }
        }
        if (startRect == null) {
            startRect = getCardRect(belongCard)
        }
        startRect.let {
            if (it.isValid()) {
                val cardRect = getCardRect(card)
                if (cardRect.isValid()) {
                    endRect = cardRect
                    it.move(endRect)
                    cardRect.lClick(false)
                }
            }
        }
        lastRect = endRect
        return endRect != null && endRect.isValid()
    }

    override fun createNewInstance(): CardAction {
        return CommonCardAction()
    }

    override fun lClick(): Boolean {
        val cardRect = getCardRect(belongCard)
        if (cardRect.isValid()) {
            cardRect.lClick()
            return true
        }
        return false
    }

    companion object {
        val DEFAULT: CardAction = CommonCardAction()

        fun reload() {
            mouseActionInterval = ConfigUtil.getInt(ConfigEnum.MOUSE_ACTION_INTERVAL)
        }

        init {
            reload()
        }
    }
}
