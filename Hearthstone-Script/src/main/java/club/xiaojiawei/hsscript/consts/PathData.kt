package club.xiaojiawei.hsscript.consts

import java.nio.file.Path

/**
 * @author 肖嘉威
 * @date 2024/10/13 16:45
 */

private val ROOT_PATH = System.getProperty("user.dir")

val MAIN_PATH = ROOT_PATH

val TEMP_VERSION_PATH: String = Path.of(ROOT_PATH, "new_version_temp").toString()

val LIBRARY_PATH: String = Path.of(ROOT_PATH, "lib").toString()
val DLL_PATH: String = Path.of(LIBRARY_PATH, "dll").toString()

val RESOURCE_PATH: String = Path.of(ROOT_PATH, "resource").toString()

val CONFIG_PATH: String = Path.of(ROOT_PATH, "config").toString()

val PLUGIN_PATH: String = Path.of(ROOT_PATH, "plugin").toString()