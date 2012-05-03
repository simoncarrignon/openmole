/*
 * Copyright (C) 2012 mathieu
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

package org.openmole.ide.core.implementation.preference

import org.openmole.core.batch.environment.BatchEnvironment
import org.openmole.ide.core.implementation.dataproxy.Proxys
import org.openmole.ide.misc.widget.MigPanel
import scala.swing.Button
import scala.swing.ComboBox
import scala.swing.event.ButtonClicked

class EnvironmentSettingPanel extends MigPanel("wrap 2") {
  
  val combo = new ComboBox(Proxys.environments.filter{e => e.dataUI.coreObject match {
        case x : BatchEnvironment => true
        case _ => false
      }
    }.toList)
  
  val trashButton = new Button("Trash data")
  
  listenTo(`trashButton`)
  reactions += {
    case ButtonClicked(`trashButton`) => combo.selection.item.dataUI.coreObject
  }
  
  
  contents += combo
  contents += trashButton
}
