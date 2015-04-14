/*
 * Copyright (C) 2010 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openmole.plugin.task.groovy

import java.io.File
import org.openmole.core.serializer.plugin.Plugins
import org.openmole.core.workflow.task._
import org.openmole.misc.pluginmanager.PluginManager
import org.openmole.misc.tools.io.FileUtil.fileOrdering
import org.openmole.plugin.task.jvm._
import org.openmole.core.workflow.task._
import org.openmole.core.workflow.data._
import org.openmole.plugin.tool.groovy.ContextToGroovyCode

object GroovyTask {

  def newRNG(seed: Long) = org.openmole.misc.tools.service.Random.newRNG(seed)
  def newFile() = org.openmole.misc.workspace.Workspace.newFile
  def newDir() = org.openmole.misc.workspace.Workspace.newDir

  /**
   * Instanciate a builder for the groovy task
   *
   * val hello = GroovyTask("hello", "println('Hello world! ' + i + ' ' + j)")
   *
   * @see CodeTaskBuilder for more info on addImport, addLib...
   *
   * @param source the groovy source code
   */
  def apply(source: String)(implicit plugins: PluginSet = PluginSet.empty) =
    new JVMLanguageTaskBuilder { builder ⇒

      addImport("static org.openmole.plugin.task.groovy.GroovyTask.newRNG")
      addImport("static org.openmole.plugin.task.groovy.GroovyTask.newFile")
      addImport("static org.openmole.plugin.task.groovy.GroovyTask.newDir")

      def toTask =
        new GroovyTask(source) with Built
    }

}

sealed abstract class GroovyTask(val userSource: String) extends JVMLanguageTask with ContextToGroovyCode {

  def processCode(context: Context) = execute(context, outputs)

  override def source =
    imports.map("import " + _).mkString("\n") + "\n" + userSource

}