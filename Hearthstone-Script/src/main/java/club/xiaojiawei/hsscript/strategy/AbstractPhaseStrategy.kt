package club.xiaojiawei.hsscript.strategy

import club.xiaojiawei.bean.Card
import club.xiaojiawei.bean.isValid
import club.xiaojiawei.config.log
import club.xiaojiawei.enums.StepEnum
import club.xiaojiawei.enums.WarPhaseEnum
import club.xiaojiawei.hsscript.bean.DeckStrategyThread
import club.xiaojiawei.hsscript.bean.log.ExtraEntity
import club.xiaojiawei.hsscript.bean.log.TagChangeEntity
import club.xiaojiawei.hsscript.data.CHANGE_ENTITY
import club.xiaojiawei.hsscript.data.FULL_ENTITY
import club.xiaojiawei.hsscript.data.SHOW_ENTITY
import club.xiaojiawei.hsscript.data.TAG_CHANGE
import club.xiaojiawei.hsscript.interfaces.closer.ThreadCloser
import club.xiaojiawei.hsscript.listener.log.PowerLogListener
import club.xiaojiawei.hsscript.status.PauseStatus
import club.xiaojiawei.hsscript.status.TaskManager
import club.xiaojiawei.hsscript.strategy.DeckStrategyActuator.discoverChooseCard
import club.xiaojiawei.hsscript.utils.PowerLogUtil.dealChangeEntity
import club.xiaojiawei.hsscript.utils.PowerLogUtil.dealFullEntity
import club.xiaojiawei.hsscript.utils.PowerLogUtil.dealShowEntity
import club.xiaojiawei.hsscript.utils.PowerLogUtil.dealTagChange
import club.xiaojiawei.hsscript.utils.PowerLogUtil.isRelevance
import club.xiaojiawei.hsscript.utils.PowerLogUtil.parseCommonEntity
import club.xiaojiawei.hsscript.utils.SystemUtil
import club.xiaojiawei.interfaces.PhaseStrategy
import club.xiaojiawei.status.War.currentPhase
import club.xiaojiawei.status.War.currentTurnStep
import club.xiaojiawei.status.War.me
import club.xiaojiawei.util.isTrue
import java.io.IOException

/**
 * 游戏阶段抽象类
 * @author 肖嘉威
 * @date 2022/11/26 17:59
 */
abstract class AbstractPhaseStrategy : PhaseStrategy {

    private var lastDiscoverEntityId: String? = null
    private var lastCreator: String? = null
    private var discoveryMap: MutableMap<String, Int> = mutableMapOf()

    override fun deal(line: String) {
        dealing = true
        try {
            beforeDeal()
            dealLog(line)
            afterDeal()
        } finally {
            dealing = false
        }
    }

    private fun dealLog(line: String) {
        val accessFile = PowerLogListener.logFile
        accessFile ?: return
        var l: String? = line
        var mark: Long
        while (!PauseStatus.isPause) {
            try {
                if (l == null) {
                    mark = accessFile.filePointer
                    SystemUtil.delay(1000)
                    if (accessFile.length() <= mark && me.isValid()) {
                        val cards: List<Card> = me.setasideArea.cards.toMutableList()
                        log.debug { "检测发现动作 ${cards} ${lastDiscoverEntityId} " }
                        if (cards.size > 0 && lastDiscoverEntityId != cards.last().entityId 
                        && lastCreator != cards.last().creator
                        && discoveryMap.contains(cards.last().entityId)
                        ) {
                            val discoveryCards: MutableList<Card> = mutableListOf()
                            for (card in cards.reversed()) {
                                card.zonePos = discoveryMap.getOrDefault(card.entityId, null)
                                if (card.entityId != lastDiscoverEntityId && !card.cardId.isBlank() && card.creator == cards.last().creator) {
                                    discoveryCards.add(card)
                                    if (card.zonePos == null) {
                                        log.debug { "lostPos, card: ${card}" }
                                    }
                                }
                                else {
                                    break;
                                }
                            }
                            
                            var total = discoveryCards.size
                            var lostPos = discoveryCards.count { it.zonePos == null }
                            if (total == 0) {
                                lastDiscoverEntityId = cards.last().entityId
                                lastCreator = cards.last().creator
                            }
                            else if (1 == lostPos || lostPos == 0) {
                                lastDiscoverEntityId = cards.last().entityId
                                lastCreator = cards.last().creator
                                var totalPos: Int = discoveryCards.sumOf { it.zonePos ?: 0 }
                                discoveryCards.filter { it.zonePos == null }.forEach { it.zonePos = (total - 1) * total / 2 - totalPos }
                                if (currentPhase != WarPhaseEnum.REPLACE_CARD && discoveryCards.size > 0) {
                                    log.debug { "触发发现动作 " }
                                    log.debug { "触发发现动作 ${discoveryCards} " }
                                    (DeckStrategyThread({
                                        discoverChooseCard(
                                            *(discoveryCards.sortedBy { it.zonePos } .toTypedArray())
                                        )
                                    }, "Discover Choose Card Thread").also { addTask(it) }).start()
                                    discoveryMap.clear()
                                }
                            }

                        }
                    }
                }
                else if (l.contains("GameState.DebugPrintEntityChoices") && l.contains("Entities[")) {
                    while (l != null && l.contains("GameState.DebugPrintEntityChoices") && l.contains("Entities[")) { // MYWEN discovery
                        val entitiesIndex = l.indexOf("Entities[")
                        val zoneIndex = entitiesIndex + "Entities[".length
                        val zonePos = l.substring(zoneIndex, zoneIndex + 1).trim().toInt()
    
                        val extraEntity = ExtraEntity()
                        parseCommonEntity(extraEntity, l)
                        log.debug { "extraEntity: ${extraEntity}"}
                        if (!extraEntity.cardId.isBlank() && !extraEntity.entityId.isBlank()) {
                            discoveryMap[extraEntity.entityId] = zonePos
                        }
                        l = accessFile.readLine()
                    }
                    continue
                } else if (isRelevance(l)) {
                    log.debug { l }
                    if (l.contains(TAG_CHANGE)) {
                        if (dealTagChangeThenIsOver(
                                l,
                                dealTagChange(l)
                            ) || currentTurnStep == StepEnum.FINAL_GAMEOVER
                        ) {
                            break
                        }
                    } else if (l.contains(SHOW_ENTITY)) {
                        if (dealShowEntityThenIsOver(l, dealShowEntity(l, accessFile))) {
                            break
                        }
                    } else if (l.contains(FULL_ENTITY)) {
                        if (dealFullEntityThenIsOver(l, dealFullEntity(l, accessFile))) {
                            break
                        }
                    } else if (l.contains(CHANGE_ENTITY)) {
                        if (dealChangeEntityThenIsOver(l, dealChangeEntity(l, accessFile))) {
                            break
                        }
                    } else {
                        if (dealOtherThenIsOver(l)) {
                            break
                        }
                    }
                }
                l = accessFile.readLine()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    protected fun beforeDeal() {
        WarPhaseEnum.find(this)?.let {
            log.info { "当前处于：" + it.comment }
        }
    }

    protected fun afterDeal() {
        WarPhaseEnum.find(this)?.let {
            log.info { it.comment + " -> 结束" }
        }
    }

    protected open fun dealTagChangeThenIsOver(line: String, tagChangeEntity: TagChangeEntity): Boolean {
        return false
    }

    protected open fun dealShowEntityThenIsOver(line: String, extraEntity: ExtraEntity): Boolean {
        return false
    }

    protected open fun dealFullEntityThenIsOver(line: String, extraEntity: ExtraEntity): Boolean {
        return false
    }

    protected open fun dealChangeEntityThenIsOver(line: String, extraEntity: ExtraEntity): Boolean {
        return false
    }

    protected open fun dealOtherThenIsOver(line: String): Boolean {
        return false
    }

    companion object : ThreadCloser {

        init {
            TaskManager.addTask(this)
        }

        var dealing = false
        private val tasks: MutableList<Thread> = mutableListOf()

        fun addTask(task: Thread) {
            tasks.add(task)
        }

        fun cancelAllTask() {
            val toList = tasks.toList()
            tasks.clear()
            toList.forEach {
                it.isAlive.isTrue {
                    it.interrupt()
                }
            }
        }

        override fun close() {
            cancelAllTask()
        }
    }

}
