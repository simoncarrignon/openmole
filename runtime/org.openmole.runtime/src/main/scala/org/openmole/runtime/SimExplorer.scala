/*
 * Copyright (C) 2012 reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openmole.runtime

import org.eclipse.equinox.app._
import scopt.immutable._
import org.openmole.misc.pluginmanager.PluginManager
import org.openmole.misc.logging.LoggerService
import java.io.File
import org.openmole.core.serializer.SerializerService
import org.openmole.misc.tools.service.Logger

class SimExplorer extends IApplication with Logger {

  override def start(context: IApplicationContext) = {
    try {
      val args = context.getArguments.get(IApplicationContext.APPLICATION_ARGS).asInstanceOf[Array[String]]

      case class Config(
        storage: Option[String] = None,
        inputMessage: Option[String] = None,
        outputMessage: Option[String] = None,
        path: Option[String] = None,
        pluginPath: Option[String] = None)

      val parser = new OptionParser[Config]("openmole", "0.x") {
        def options = Seq(
          opt("s", "storage", "Storage") {
            (v: String, c: Config) ⇒ c.copy(storage = Some(v))
          },
          opt("i", "input", "Path of the input message") {
            (v: String, c: Config) ⇒ c.copy(inputMessage = Some(v))
          },
          opt("o", "output", "Path of the output message") {
            (v: String, c: Config) ⇒ c.copy(outputMessage = Some(v))
          },
          opt("c", "path", "Path for the communication") {
            (v: String, c: Config) ⇒ c.copy(path = Some(v))
          },
          opt("p", "plugin", "Path for plugin dir to preload") {
            (v: String, c: Config) ⇒ c.copy(pluginPath = Some(v))
          })
      }

      val debug = args.contains("-d")
      val filtredArgs = args.filterNot((_: String) == "-d")
      if (debug) LoggerService.level("ALL")

      parser.parse(filtredArgs, Config()) foreach { config ⇒

        PluginManager.loadDir(new File(config.pluginPath.get))

        val storage = SerializerService.deserializeAndExtractFiles(new File(config.storage.get))

        new Runtime().apply(
          storage,
          config.path.get,
          config.inputMessage.get,
          config.outputMessage.get,
          debug)

      }
    } catch {
      case t: Throwable ⇒ logger.log(SEVERE, "Error durring runtime execution", t)
    }
    IApplication.EXIT_OK

  }
  override def stop = {}

}
