package org.openmole.gui.client.core.dataui

import org.openmole.gui.client.core.{ ClientService, GenericPanel }
import org.openmole.gui.misc.js.Forms._
import org.openmole.gui.misc.js.JsRxTags._
import rx._
import scalatags.JsDom.all._
import scalatags.JsDom.tags
import org.openmole.gui.misc.js.{ Forms ⇒ bs }
import org.openmole.gui.ext.dataui.PanelUI
import org.openmole.gui.misc.js.InputFilter
import IOPanelUIUtil._

/*
 * Copyright (C) 26/02/15 // mathieu.leclaire@openmole.org
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

class InAndOutPanelUI(val panel: GenericPanel, dataUI: InAndOutputDataUI) extends PanelUI {

  val inputFilter = InputFilter(pHolder = "Input prototype", inputID = InputFilter.protoFilterId1)

  val outputFilter = InputFilter(pHolder = "Output prototype", inputID = InputFilter.protoFilterId2)

  def filteredInputsUI = ClientService.prototypeDataBagUIs.map { p ⇒ defaultInOutputUI(p) }.filter { i ⇒
    inputFilter.contains(i.protoDataBagUI.name()) &&
      !inputFilter.nameFilter().isEmpty
  }

  def filteredOutputsUI = ClientService.prototypeDataBagUIs.map { p ⇒ emptyInOutputUI(p) }.filter { o ⇒
    outputFilter.contains(o.protoDataBagUI.name()) &&
      !outputFilter.nameFilter().isEmpty
  }

  def buildDefaultMapping = dataUI.mappingsFactory.build.fields.head

  //New button
  val newGlyph =
    //FIXME: THE SIZE OF THE GLYPH IS SMALLER THAN THE REST OF THE GROUP WHEN GROUPEL
    // bs.button(glyph(glyph_plus))(onclick := { () ⇒ add
    bs.button("Add")(`type` := "submit", onclick := { () ⇒
      println("ADD ")
      val filteringI = filteredInputsUI
      val filteringO = filteredOutputsUI
      if (filteringI.size == 1) {
        val in = filteringI.head
        if (filteringO.size == 1) addInAndOut(in.protoDataBagUI, filteringO.head.protoDataBagUI)
        else {
          println("add input")
          addInput(in.protoDataBagUI)
        }
      }
      else if (filteringO.size == 1) addOutput(filteringO.head.protoDataBagUI)
      else if (!inputFilter.nameFilter().isEmpty && outputFilter.nameFilter().isEmpty) {
        println("NEW")
        val newProto = buildProto(inputFilter.nameFilter())
        setCurrent(newProto)
        addInput(newProto)
      }
      else if (!outputFilter.nameFilter().isEmpty && inputFilter.nameFilter().isEmpty) {
        val newProto = buildProto(outputFilter.nameFilter())
        setCurrent(newProto)
        addOutput(newProto)
      }
    }
    ).render

  def clear = {
    inputFilter.clear
    outputFilter.clear
  }

  def addInput(pdb: PrototypeDataBagUI) = if (!dataUI.inputDataUI.exists(pdb) && !dataUI.inAndOutputsUI().map {
    _.in.protoDataBagUI
  }.exists(_.uuid == pdb.uuid)) {
    dataUI.inputDataUI += pdb
    clear
  }

  def addOutput(pdb: PrototypeDataBagUI) = if (!dataUI.outputDataUI.exists(pdb) && !dataUI.inAndOutputsUI().map {
    _.out.protoDataBagUI
  }.exists(_.uuid == pdb.uuid)) {
    dataUI.outputDataUI += pdb
    clear
  }

  def addInAndOut(i: PrototypeDataBagUI, o: PrototypeDataBagUI) = {
    dataUI += (i, o, buildDefaultMapping)
    dataUI.inputDataUI -= i
    dataUI.outputDataUI -= o
    clear
  }

  val view =
    bs.form(spacer20)(
      bs.formGroup( /*row + */ col_md_12)(
        bs.inputGroup(col_md_6 + col_md_offset_3)(
          inputFilter.tag,
          outputFilter.tag,
          bs.inputGroupButton(newGlyph)
        )),
      bs.formGroup(col_md_12)(Rx {
        bs.table(col_md_12 + striped)(
          buildHeaders(prototypeHeaderSequence ++ Seq("Default", buildDefaultMapping.key) ++ prototypeHeaderSequence),
          buildIOBody
        )
      }
      )
    )

  def allInOutDataUI: Seq[InOutputDataUI] = {
    println("IN : " + dataUI.inputDataUI.inoutputsUI().size)
    Seq(dataUI.inputDataUI, dataUI.outputDataUI)
  }

  def allInOutNames: Seq[String] = allInOutDataUI.flatMap {
    _.inoutputsUI().map {
      _.protoDataBagUI.name()
    }
  }

  //++ filteredInputsUI ++ filteredOutputsUI ++ dataUI.inAndOutputsUI()

  def buildIOBody /*(ioUI: Seq[InOutputUI], dataUI: InOutputDataUI)*/ =
    tbody(
      for (iAo ← dataUI.inAndOutputsUI()) yield {
        coloredTR(((buildPrototypeTableView(iAo.in, () ⇒ setCurrent(iAo.in.protoDataBagUI)) :+
          tags.td(iAo.mapping.panelUI.view)) ++
          (buildPrototypeTableView(iAo.out, () ⇒ setCurrent(iAo.out.protoDataBagUI)) :+
            delButtonTD(dataUI -= iAo))),
          () ⇒ false)
      },
      for (iodataUI ← allInOutDataUI) yield {
        (iodataUI match {
          case idataUI: InputDataUI ⇒ for (i ← idataUI.inoutputsUI() ++ filteredInputsUI) yield {
            coloredTR((buildPrototypeTableView(i, () ⇒ setCurrent(i.protoDataBagUI)) ++ emptyTD(4)) :+ delButtonTD(iodataUI -= i),
              () ⇒ filteredInputsUI.contains(i))
          }
          case odataUI: OutputDataUI ⇒ for (o ← odataUI.inoutputsUI() ++ filteredOutputsUI) yield {
            coloredTR((emptyTD(5) ++ buildPrototypeTableView(o, () ⇒ setCurrent(o.protoDataBagUI)) :+ delButtonTD(iodataUI -= o)),
              () ⇒ filteredOutputsUI.contains(o))
          }
        })
      }
    )

  def setCurrent(pdb: PrototypeDataBagUI) = {
    println("In and Out set current")
    save
    panel.currentDataBagUI().map {
      db ⇒
        panel.stack(db)
    }
    panel.setCurrent(pdb)
  }

  def save = {
    saveInOutputsUI(dataUI.inputDataUI.inoutputsUI())
    saveInOutputsUI(dataUI.outputDataUI.inoutputsUI())
    dataUI.inAndOutputsUI().map {
      _.mapping.panelUI.save
    }
  }

}