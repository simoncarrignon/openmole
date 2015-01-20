package org.openmole.gui.misc.js

/*
 * Copyright (C) 13/01/15 // mathieu.leclaire@openmole.org
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
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import rx._
import scalatags.JsDom.all._
import org.scalajs.jquery.jQuery
import org.openmole.gui.misc.js.JsRxTags._

class Select[T <: Displayable with Identifiable](autoID: String,
                                                 val contents: Var[Seq[T]],
                                                 default: Option[T] = None,
                                                 key: ClassKeyAggregator = Forms.emptyCK) {

  val jQid = "#" + autoID

  val content: Var[Option[T]] = Var(contents().size match {
    case 0 ⇒ None
    case _ ⇒ default match {
      case None ⇒ Some(contents()(0))
      case _ ⇒
        val ind = contents().indexOf(default.get)
        if (ind != -1) Some(contents()(ind)) else Some(contents()(0))
    }
  })

  val selector = Forms.buttonGroup()(
    a(
      `class` := "btn " + key.key + " dropdown-toggle", dataWith("toggle") := "dropdown", href := "#")(
        Rx {
          content().map {
            _.name
          }.getOrElse(contents()(0).name) + " "
        },
        span(`class` := "caret")
      ),
    ul(`class` := "dropdown-menu", id := autoID)(
      for (c ← contents().zipWithIndex) yield {
        scalatags.JsDom.tags.li(a(
          href := "#", onclick := { () ⇒ applyOnChange(c._2) })(c._1.name)
        )
      }
    )
  ).render

  def applyOnChange(ind: Int): Unit = {
    content() = Some(contents()(ind))
    content().map { c ⇒ jQuery(jQid).parents(".btn-group").find(".dropdown-toggle").html(c.name + " <span class=\"caret\"><span>")
      // setDefault
    }
  }

}
