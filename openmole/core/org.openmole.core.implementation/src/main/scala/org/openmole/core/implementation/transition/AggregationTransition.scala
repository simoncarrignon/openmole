/*
 * Copyright (C) 2010 reuillon
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

package org.openmole.core.implementation.transition

import java.util.logging.Logger
import org.openmole.misc.eventdispatcher.EventDispatcher
import org.openmole.misc.eventdispatcher.IObjectListenerWithArgs
import org.openmole.misc.exception.{InternalProcessingError, UserBadDataError}
import org.openmole.misc.tools.service.Priority
import org.openmole.core.implementation.tools.ContextBuffer
import org.openmole.core.implementation.tools.ContextAggregator
import org.openmole.core.model.capsule.ICapsule
import org.openmole.core.model.capsule.IGenericCapsule
import org.openmole.core.model.data.IContext
import org.openmole.core.model.job.IMoleJob
import org.openmole.core.model.mole.ITicket
import org.openmole.core.model.mole.IMoleExecution
import org.openmole.core.model.mole.ISubMoleExecution
import org.openmole.core.model.transition.IAggregationTransition
import org.openmole.core.model.transition.ICondition
import org.openmole.core.model.transition.ICondition._
import org.openmole.core.model.transition.IExplorationTransition
import org.openmole.core.model.transition.ISlot
import org.openmole.misc.tools.obj.ClassUtils._
import scala.collection.immutable.TreeMap
import scala.collection.mutable.HashSet
import scala.collection.mutable.ListBuffer

class AggregationTransition(start: ICapsule, end: ISlot, condition: ICondition = True, filtered: Set[String] = Set.empty[String], trigger: Option[ICondition] = None) extends Transition(start, end, condition, filtered) with IAggregationTransition {

  def this(start: ICapsule, end: IGenericCapsule) = this(start, end.defaultInputSlot, True, Set.empty[String])
    
  def this(start: ICapsule, end: IGenericCapsule, condition: ICondition) = this(start, end.defaultInputSlot, condition, Set.empty[String])

  def this(start: ICapsule, end: IGenericCapsule, condition: String) = this(start, end.defaultInputSlot, new Condition(condition), Set.empty[String])
    
  def this(start: ICapsule , slot: ISlot, condition: String) = this(start, slot, new Condition(condition), Set.empty[String])
    
  def this(start: ICapsule , slot: ISlot, condition: ICondition) = this(start, slot, condition, Set.empty[String])
   
  def this(start: ICapsule, end: IGenericCapsule, filtred: Array[String]) = this(start, end.defaultInputSlot, ICondition.True, filtred.toSet)
    
  def this(start: ICapsule, end: IGenericCapsule, condition: ICondition, filtred: Array[String]) = this(start, end.defaultInputSlot, condition, filtred.toSet)

  def this(start: ICapsule, end: IGenericCapsule, condition: String, filtred: Array[String]) = this(start, end.defaultInputSlot, new Condition(condition), filtred.toSet)
    
  def this(start: ICapsule , slot: ISlot, condition: String, filtred: Array[String]) = this(start, slot, new Condition(condition), filtred.toSet)

  
  def this(trigger: ICondition, start: ICapsule, end: IGenericCapsule) = this(start, end.defaultInputSlot, ICondition.True, Set.empty[String], Some(trigger))
    
  def this(trigger: ICondition, start: ICapsule, end: IGenericCapsule, condition: ICondition) = this(start, end.defaultInputSlot, condition, Set.empty[String], Some(trigger))

  def this(trigger: ICondition, start: ICapsule, end: IGenericCapsule, condition: String) = this(start, end.defaultInputSlot, new Condition(condition), Set.empty[String], Some(trigger))
    
  def this(trigger: ICondition, start: ICapsule , slot: ISlot, condition: String) = this(start, slot, new Condition(condition), Set.empty[String], Some(trigger))
    
  def this(trigger: ICondition, start: ICapsule , slot: ISlot, condition: ICondition) = this(start, slot, condition, Set.empty[String], Some(trigger))
   
  def this(trigger: ICondition, start: ICapsule, end: IGenericCapsule, filtred: Array[String]) = this(start, end.defaultInputSlot, ICondition.True, filtred.toSet, Some(trigger))
    
  def this(trigger: ICondition, start: ICapsule, end: IGenericCapsule, condition: ICondition, filtred: Array[String]) = this(start, end.defaultInputSlot, condition, filtred.toSet, Some(trigger))

  def this(trigger: ICondition, start: ICapsule, end: IGenericCapsule, condition: String, filtred: Array[String]) = this(start, end.defaultInputSlot, new Condition(condition), filtred.toSet, Some(trigger))
    
  def this(trigger: ICondition, start: ICapsule , slot: ISlot, condition: String, filtred: Array[String]) = this(start, slot, new Condition(condition), filtred.toSet, Some(trigger))
  

  def this(trigger: String, start: ICapsule, end: IGenericCapsule) = this(new Condition(trigger), start, end)  
  
  def this(trigger: String, start: ICapsule, end: IGenericCapsule, condition: ICondition) = this(new Condition(trigger), start, end, condition)

  def this(trigger: String, start: ICapsule, end: IGenericCapsule, condition: String) = this(new Condition(trigger), start, end, condition)
  
  def this(trigger: String, start: ICapsule , slot: ISlot, condition: String) = this(new Condition(trigger), start, slot, condition)
    
  def this(trigger: String, start: ICapsule , slot: ISlot, condition: ICondition) = this(new Condition(trigger), start, slot, condition)
   
  def this(trigger: String, start: ICapsule, end: IGenericCapsule, filtred: Array[String]) = this(new Condition(trigger), start, end, filtred)
    
  def this(trigger: String, start: ICapsule, end: IGenericCapsule, condition: ICondition, filtred: Array[String]) = this(new Condition(trigger), start, end, condition, filtred)

  def this(trigger: String, start: ICapsule, end: IGenericCapsule, condition: String, filtred: Array[String]) = this(new Condition(trigger), start, end, condition, filtred)
    
  def this(trigger: String, start: ICapsule , slot: ISlot, condition: String, filtred: Array[String]) = this(new Condition(trigger), start, slot, condition, filtred)

  
  override def _perform(context: IContext, ticket: ITicket, toClone: Set[String], subMole: ISubMoleExecution) = subMole.synchronized {
    val parentTicket = ticket.parent.getOrElse(throw new UserBadDataError("Aggregation transition should take place after an exploration."))
    
    if(!hasBeenPerformed(subMole, parentTicket)) {
      subMole.aggregationTransitionRegistry.consult(this, parentTicket) match {
        case Some(resultContexts) =>
          //Store the result context
          resultContexts ++= (context, toClone)
    
          trigger match {
            case Some(trigger) => {
                val toArrayManifests = Map.empty[String, Manifest[_]] ++ start.userOutputs.toList.map{d => d.prototype.name -> d.prototype.`type`}
                val context = ContextAggregator.aggregate(start.userOutputs, toArrayManifests, resultContexts.map{_.toVariable})
                if(trigger.evaluate(context)) {
                  aggregate(subMole, ticket)
                  if(allAggregationTransitionsPerformed(subMole, parentTicket)) subMole.cancel
                }
              }
            case None =>
          }
        case None => throw new InternalProcessingError("No context registred for aggregation.")
      }
    }
  }

  override def aggregate(subMole: ISubMoleExecution, ticket: ITicket) = subMole.synchronized {
    val parentTicket = ticket.parent.getOrElse(throw new UserBadDataError("Aggregation transition should take place after an exploration"))
    
    if(!hasBeenPerformed(subMole, parentTicket)) {
      val result = subMole.aggregationTransitionRegistry.remove(this, parentTicket).getOrElse(throw new InternalProcessingError("No context registred for the aggregation transition"))
      val endTask = end.capsule.task.getOrElse(throw new UserBadDataError("No task assigned for end capsule"))
      val startTask = start.task.getOrElse(throw new UserBadDataError("No task assigned for start capsule"))
      val subMoleParent = subMole.parent.getOrElse(throw new InternalProcessingError("Submole execution has no parent"))

      //Variable have are clonned in other transitions if necessary
      submitNextJobsIfReady(result, parentTicket, subMoleParent)
    }
  }
  
  override def hasBeenPerformed(subMole: ISubMoleExecution, ticket: ITicket) = !subMole.aggregationTransitionRegistry.isRegistred(this, ticket)
  
  private def allAggregationTransitionsPerformed(subMole: ISubMoleExecution, ticket: ITicket) = !oneAggregationTransitionNotPerformed(subMole, ticket)
  
  private def oneAggregationTransitionNotPerformed(subMole: ISubMoleExecution, ticket: ITicket): Boolean = {
    val alreadySeen = new HashSet[IGenericCapsule]
    val toProcess = new ListBuffer[(IGenericCapsule,Int)]
    toProcess += ((this.start, 0))
    
    while(!toProcess.isEmpty) {
      val cur = toProcess.remove(0)
      val capsule = cur._1
      val level = cur._2
      
      if(!alreadySeen(capsule)) {
        alreadySeen += capsule
        capsule.intputSlots.toList.flatMap{_.transitions}.foreach {
          case t: IExplorationTransition => if(level > 0) toProcess += ((t.start, level - 1))
          case t: IAggregationTransition =>
            if(level == 0 && t != this && !t.hasBeenPerformed(subMole, ticket)) return true
            toProcess += ((t.start, level + 1))
          case t => toProcess += ((t.start, level))
        }
        capsule.outputTransitions.foreach {
          case t: IExplorationTransition => toProcess += ((t.end.capsule, level + 1))
          case t: IAggregationTransition =>
            if(level == 0 && t != this && !t.hasBeenPerformed(subMole, ticket)) return true
            if(level > 0) toProcess += ((t.end.capsule, level - 1))
          case t => toProcess += ((t.end.capsule, level))
        }
      }
    }
    false
  }
 
}
