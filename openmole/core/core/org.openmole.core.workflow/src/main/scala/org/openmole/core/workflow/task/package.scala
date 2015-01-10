/*
 * Copyright (C) 2012 Romain Reuillon
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

package org.openmole.core.workflow

import org.openmole.core.workflow.puzzle._
import org.openmole.core.workflow.data._
import org.openmole.core.workflow.mole._

package object task {

  implicit class TaskToCapsuleDecorator(task: Task) {
    def toCapsule = new Capsule(task)
    def toStrainerCapsule = new StrainerCapsule(task)
  }

  def newRNG(context: Context) = Task.buildRNG(context)
}