package club.xiaojiawei.hsscript.data

import club.xiaojiawei.hsscript.enums.ConfigEnum
import club.xiaojiawei.hsscript.utils.ConfigUtil
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.HWND
import java.awt.Robot

/**
 * 存储脚本常量
 * @author 肖嘉威
 * @date 2023/7/3 21:12
 */

/**
 * 是否设置了炉石和战网的路径
 */
var haveProgramPath = true

/**
 * 游戏窗口句柄
 */
var GAME_HWND: HWND? = null
var GAME_REGION: Int = 0
const val GAME_CN_NAME: String = "炉石传说"
const val PLATFORM_CN_NAME: String = "战网"
const val PLATFORM_LOGIN_CN_NAME: String = "战网登录"
const val GAME_US_NAME: String = "Hearthstone"
const val PLATFORM_US_NAME: String = "Battle.net"

var MAX_LOG_SIZE_KB: Int = ConfigUtil.getInt(ConfigEnum.GAME_LOG_LIMIT)

var MAX_LOG_SIZE_B: Int = MAX_LOG_SIZE_KB * 1024

/**
 * 游戏窗口信息
 */
val GAME_RECT: WinDef.RECT = WinDef.RECT()

/**
 * 所有鼠标键盘模拟都需要此对象
 */
val ROBOT: Robot = Robot()

/**
 * 本脚本的程序名
 */
const val SCRIPT_NAME: String = "hs-script"

/**
 * 项目名
 */
const val PROJECT_NAME: String = "Hearthstone-Script"

/**
 * 炉石传说程序名
 */
const val GAME_PROGRAM_NAME: String = "$GAME_US_NAME.exe"

/**
 * 战网程序名
 */
const val PLATFORM_PROGRAM_NAME: String = "$PLATFORM_US_NAME.exe"

/**
 * 作者
 */
const val AUTHOR: String = "XiaoJiawei"


/*日志相关*/
const val VALUE: String = "value"
const val TAG: String = "tag"
const val SHOW_ENTITY: String = "SHOW_ENTITY"
const val FULL_ENTITY: String = "FULL_ENTITY"
const val TAG_CHANGE: String = "TAG_CHANGE"
const val CHANGE_ENTITY: String = "CHANGE_ENTITY"
const val LOST: String = "LOST"
const val WON: String = "WON"
const val CONCEDED: String = "CONCEDED"
const val COIN: String = "COIN"

fun reload() {
    MAX_LOG_SIZE_KB = ConfigUtil.getInt(ConfigEnum.GAME_LOG_LIMIT)
    MAX_LOG_SIZE_B = MAX_LOG_SIZE_KB * 1024
}