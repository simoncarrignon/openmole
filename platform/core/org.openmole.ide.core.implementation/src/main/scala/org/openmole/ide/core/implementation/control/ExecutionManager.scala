/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.openmole.ide.core.implementation.control

import java.awt.Dimension
import java.awt.Rectangle
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.io.PrintStream
import org.openide.util.Lookup
import org.openmole.core.implementation.execution.local.LocalExecutionEnvironment
import org.openmole.core.implementation.mole.MoleExecution
import org.openmole.core.model.execution.IEnvironment
import org.openmole.core.model.hook.IHook
import org.openmole.ide.misc.visualization.BarPlotter
import org.openmole.ide.misc.visualization.PiePlotter
import org.openmole.ide.misc.widget.MigPanel
import org.openmole.core.model.mole.IMoleExecution
import org.openmole.ide.core.implementation.serializer.MoleMaker
import org.openmole.ide.core.model.factory.IHookFactoryUI
import org.openmole.ide.core.model.control.IExecutionManager
import org.openmole.ide.core.model.workflow.IMoleSceneManager
import org.openmole.ide.core.model.panel.IHookPanelUI
import scala.collection.mutable.HashMap
import scala.swing.Action
import scala.swing.Menu
import scala.swing.MenuBar
import scala.swing.MenuItem
import scala.swing.Orientation
import scala.swing.ScrollPane
import scala.swing.SplitPane
import scala.swing.TabbedPane
import org.openmole.misc.eventdispatcher.EventDispatcher
import scala.collection.JavaConversions._
import scala.swing.TextArea
import org.openmole.core.model.job.State
import org.openmole.core.model.execution.ExecutionState

class ExecutionManager(manager : IMoleSceneManager) extends TabbedPane with IExecutionManager{
  val logTextArea = new TextArea{columns = 20;rows = 10}
  override val printStream = new PrintStream(new BufferedOutputStream(new TextAreaOutputStream(logTextArea),1024),true)
  override val (mole, capsuleMapping, prototypeMapping) = MoleMaker.buildMole(manager)
  var moleExecution: IMoleExecution = new MoleExecution(mole)
  var hookPanels= new HashMap[String,(IHookPanelUI,List[IHook])]
  var status = HashMap(State.READY-> 0,State.RUNNING-> 0,State.COMPLETED-> 0,State.FAILED-> 0,State.CANCELED-> 0)
  val wfPiePlotter = new PiePlotter("Workflow",Map("Ready"-> 0.0,"Running"-> 0.0,"Completed"-> 0.0,"Failed"-> 0.0,"Canceled"-> 0.0))
  val envBarPanel = new MigPanel(""){peer.add(wfPiePlotter.chartPanel)}
  val envBarPlotter = new BarPlotter("aaa ")
  var environments = new HashMap[IEnvironment,(String,HashMap[ExecutionState.ExecutionState,Double])]
  
  val hookMenu = new Menu("Hooks")
  val groupingMenu = new Menu("Grouping")
  Lookup.getDefault.lookupAll(classOf[IHookFactoryUI]).foreach{f=>hookMenu.contents+= new MenuItem(new AddHookRowAction(f))}
  val menuBar = new MenuBar{contents.append(hookMenu,groupingMenu)}
  menuBar.minimumSize = new Dimension(menuBar.size.width,30)
  val hookPanel = new MigPanel(""){contents+= (menuBar,"wrap")}
  
  val splitPane = new SplitPane(Orientation.Vertical) {
    leftComponent = new ScrollPane(envBarPanel)
    rightComponent = new ScrollPane(logTextArea)
  }
  
  System.setOut(new PrintStream(new BufferedOutputStream(new TextAreaOutputStream(logTextArea)),true))
  System.setErr(new PrintStream(new BufferedOutputStream(new TextAreaOutputStream(logTextArea)),true))
  
  prototypeMapping.foreach(p=>println("proto " + p._1.dataUI.name + " dim : " + p._1.dataUI.dim ))
  
  pages+= new TabbedPane.Page("Settings",hookPanel)
  pages+= new TabbedPane.Page("Execution progress", splitPane)
  
  
  
  def start = {
    cancel
    initBarPlotter
    hookPanels.values.foreach(_._2.foreach(_.release))
    val moleE = MoleMaker.buildMoleExecution(mole, manager, capsuleMapping)
    moleExecution = moleE._1
    EventDispatcher.listen(moleExecution,new JobCreatedListener,classOf[IMoleExecution.OneJobSubmitted])
    moleE._2.foreach(buildEmptyEnvPlotter)
    environments.values.foreach(v=>println("--- environment :: " + v._1))
    if(envBarPanel.peer.getComponentCount == 2) envBarPanel.peer.remove(1)
    envBarPanel.peer.add(envBarPlotter.chartPanel) 
    initPieChart
    hookPanels.keys.foreach{commitHook}
    repaint 
    revalidate
    moleExecution.start}
    
  def cancel = moleExecution.cancel
  
  def initBarPlotter {
    environments.clear
    buildEmptyEnvPlotter((LocalExecutionEnvironment.asInstanceOf[IEnvironment],"Local"))
  }

  def buildEmptyEnvPlotter(e: (IEnvironment,String)) = {
    val m = HashMap(ExecutionState.SUBMITTED->0.0,ExecutionState.READY-> 0.0,ExecutionState.RUNNING-> 0.0,ExecutionState.DONE-> 0.0,ExecutionState.FAILED-> 0.0,ExecutionState.KILLED-> 0.0)    
    environments+= e._1-> (e._2,m)
    EventDispatcher.listen(e._1,new JobCreatedOnEnvironmentListener(moleExecution,e._1),classOf[IEnvironment.JobSubmitted])}
  
  override def commitHook(hookClassName: String) {
    if (hookPanels.contains(hookClassName)) hookPanels(hookClassName)._2.foreach(_.release)
    hookPanels(hookClassName) =  (hookPanels(hookClassName)._1,hookPanels(hookClassName)._1.saveContent.map(_.coreObject))
  }
  
  def initPieChart = {
    status.keys.foreach(k=>status(k)=0)
    environments.values.foreach(env=>env._2.keys.foreach(k=> env._2(k) = 0))}
  
  class TextAreaOutputStream(textArea: TextArea) extends OutputStream {
    override def flush = textArea.repaint
    
    override def write(b:Int) = textArea.append(new String(Array[Byte](b.asInstanceOf[Byte])))
                      
    override def write(b: Array[Byte], off: Int,len: Int) = {
      textArea.append(new String(b,off,len))
      textArea.peer.scrollRectToVisible(new Rectangle(0, textArea.size.height - 2, 1, 1))
    }
  }
  
  class AddHookRowAction(fui: IHookFactoryUI) extends Action(fui.toString){
    def apply = {
      val cl = fui.coreClass.getCanonicalName
      if(hookPanels.contains(cl)) 
        hookPanels(cl)._1.addHook
      else {
        val pui = fui.buildPanelUI(ExecutionManager.this)
        hookPanel.peer.add(pui.peer)
        hookPanels+= cl-> (pui,List.empty)
      }
      hookPanels+= cl-> (hookPanels(cl)._1,hookPanels(cl)._1.saveContent.map(_.coreObject))
    }
  }
  
  
  class AddStrategyRowAction extends Action("S1"){
    def apply = {
      println("add strategy : not imp yet")
    }
  }
}
