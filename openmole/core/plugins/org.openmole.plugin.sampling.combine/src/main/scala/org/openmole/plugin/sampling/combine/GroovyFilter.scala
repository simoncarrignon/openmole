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

package org.openmole.plugin.sampling.combine

import org.openmole.core.workflow.tools.GroovyContextAdapter
import org.openmole.misc.tools.script.GroovyProxy
import org.openmole.core.workflow.data.Context

class GroovyFilter(code: String) extends Filter {
  @transient lazy val groovyProxy = new GroovyProxy(code, Iterable.empty) with GroovyContextAdapter

  def apply(factorsValues: Context) = groovyProxy.execute(factorsValues).asInstanceOf[Boolean]
}