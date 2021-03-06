/*
 * Copyright (C) 22/11/12 Romain Reuillon
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

package org.openmole.plugin.method

import org.openmole.core.workflow.dsl._
import org.openmole.core.workflow.mole._
import org.openmole.core.workflow.puzzle._
import org.openmole.core.workflow.task._
import org.openmole.core.workflow.transition._
import org.openmole.plugin.task.tools._
import org.openmole.plugin.tool.pattern._
import org.openmole.core.context._
import org.openmole.core.expansion._
import org.openmole.tool.types._
import squants.time.Time
import mgo.double2Scalable
import org.openmole.core.workflow.builder._

package object evolution {

  implicit def scope = DefinitionScope.Internal

  val operatorExploration = 0.1

  type Objectives = Seq[Objective]
  type FitnessAggregation = Seq[Double] ⇒ Double
  type Genome = Seq[Genome.GenomeBound]

  implicit def intToCounterTerminationConverter(n: Long): AfterGeneration = AfterGeneration(n)
  implicit def durationToDurationTerminationConverter(d: Time): AfterDuration = AfterDuration(d)

  object OMTermination {
    def toTermination(oMTermination: OMTermination, integration: EvolutionWorkflow) =
      oMTermination match {
        case AfterGeneration(s) ⇒ (population: Vector[integration.I]) ⇒ integration.operations.afterGeneration(s, population)
        case AfterDuration(d) ⇒ (population: Vector[integration.I]) ⇒ integration.operations.afterDuration(d, population)
      }
  }

  sealed trait OMTermination
  case class AfterGeneration(steps: Long) extends OMTermination
  case class AfterDuration(duration: Time) extends OMTermination

  sealed trait EvolutionPattern

  object EvolutionPattern {
    def build[T](
      algorithm:    T,
      evaluation:   Puzzle,
      termination:  OMTermination,
      stochastic:   OptionalArgument[Stochastic] = None,
      parallelism:  Int                          = 1,
      distribution: EvolutionPattern             = SteadyState())(implicit wfi: WorkflowIntegration[T]) =
      distribution match {
        case s: SteadyState ⇒
          SteadyStateEvolution(
            algorithm = algorithm,
            evaluation = evaluation,
            parallelism = parallelism,
            termination = termination,
            wrap = s.wrap
          )
        case i: Island ⇒
          val steadyState =
            SteadyStateEvolution(
              algorithm = algorithm,
              evaluation = evaluation,
              termination = i.termination,
              wrap = i.wrap
            )

          IslandEvolution(
            island = steadyState,
            parallelism = parallelism,
            termination = termination,
            sample = i.sample
          )
      }

  }

  case class SteadyState(wrap: Boolean = false) extends EvolutionPattern
  case class Island(termination: OMTermination, sample: OptionalArgument[Int] = None, wrap: Boolean = true) extends EvolutionPattern

  import shapeless._

  def SteadyStateEvolution[T](algorithm: T, evaluation: Puzzle, termination: OMTermination, parallelism: Int = 1, wrap: Boolean = false)(implicit wfi: WorkflowIntegration[T]) = {
    val t = wfi(algorithm)

    val wrapped = wrapPuzzle(evaluation, t.inputPrototypes, t.outputPrototypes, wrap)
    val randomGenomes = BreedTask(algorithm, parallelism) set ((inputs, outputs) += t.populationPrototype)
    val scalingGenomeTask = ScalingGenomeTask(algorithm)
    val toOffspring = ToOffspringTask(algorithm)
    val elitismTask = ElitismTask(algorithm)
    val terminationTask = TerminationTask(algorithm, termination)
    val breed = BreedTask(algorithm, 1)

    val masterFirst =
      EmptyTask() set (
        (inputs, outputs) += (t.populationPrototype, t.genomePrototype, t.statePrototype),
        (inputs, outputs) += (t.objectives.map(_.prototype): _*)
      )

    val masterLast =
      EmptyTask() set (
        (inputs, outputs) += (t.populationPrototype, t.statePrototype, t.genomePrototype.toArray, t.terminatedPrototype, t.generationPrototype)
      )

    val masterFirstCapsule = Capsule(masterFirst)
    val elitismSlot = Slot(elitismTask)
    val masterLastSlot = Slot(masterLast)
    val terminationCapsule = Capsule(terminationTask)
    val breedSlot = Slot(breed)

    val master =
      (masterFirstCapsule --
        (toOffspring keep (Seq(t.statePrototype, t.genomePrototype) ++ t.objectives.map(_.prototype): _*)) --
        elitismSlot --
        terminationCapsule --
        breedSlot --
        masterLastSlot) &
        (masterFirstCapsule -- (elitismSlot keep t.populationPrototype)) &
        (elitismSlot -- (breedSlot keep t.populationPrototype)) &
        (elitismSlot -- (masterLastSlot keep t.populationPrototype)) &
        (terminationCapsule -- (masterLastSlot keep (t.terminatedPrototype, t.generationPrototype)))

    val masterTask = MoleTask(master) set (exploredOutputs += t.genomePrototype.toArray)

    val masterSlave = MasterSlave(
      randomGenomes,
      masterTask,
      scalingGenomeTask -- Strain(wrapped.evaluationPuzzle),
      state = Seq(t.populationPrototype, t.statePrototype),
      slaves = parallelism
    )

    val firstTask = InitialStateTask(algorithm)
    val firstCapsule = Capsule(firstTask, strain = true)

    val last = EmptyTask() set ((inputs, outputs) += (t.statePrototype, t.populationPrototype))

    val puzzle =
      ((firstCapsule -- masterSlave) >| (Capsule(last, strain = true) when t.terminatedPrototype)) &
        (firstCapsule oo (wrapped.evaluationPuzzle, filter = Block(t.populationPrototype, t.statePrototype)))

    val gaPuzzle = PuzzleContainer(puzzle, masterSlave.last, wrapped.delegate)

    gaPuzzle :: algorithm :: HNil
  }

  def IslandEvolution[HL <: HList, T](
    island:      HL,
    parallelism: Int,
    termination: OMTermination,
    sample:      OptionalArgument[Int] = None
  )(implicit
    wfi: WorkflowIntegrationSelector[HL, T],
    selectPuzzle: Puzzle.PuzzleSelector[HL]) = {
    val algorithm: T = wfi(island)
    implicit val wi = wfi.selected
    val t = wi(algorithm)

    val islandPopulationPrototype = t.populationPrototype.withName("islandPopulation")

    val masterFirst =
      EmptyTask() set (
        (inputs, outputs) += (t.populationPrototype, t.offspringPrototype, t.statePrototype)
      )

    val masterLast =
      EmptyTask() set (
        (inputs, outputs) += (t.populationPrototype, t.statePrototype, islandPopulationPrototype.toArray, t.terminatedPrototype, t.generationPrototype)
      )

    val elitismTask = ElitismTask(algorithm)
    val generateIsland = GenerateIslandTask(algorithm, sample, 1, islandPopulationPrototype)
    val terminationTask = TerminationTask(algorithm, termination)
    val islandPopulationToPopulation = AssignTask(islandPopulationPrototype → t.populationPrototype)
    val reassingRNGTask = ReassignStateRNGTask(algorithm)

    val fromIsland = FromIslandTask(algorithm)

    val populationToOffspring = AssignTask(t.populationPrototype → t.offspringPrototype)
    val elitismSlot = Slot(elitismTask)
    val terminationCapsule = Capsule(terminationTask)
    val masterLastSlot = Slot(masterLast)

    val master =
      (
        masterFirst --
        (elitismSlot keep (t.statePrototype, t.populationPrototype, t.offspringPrototype)) --
        terminationCapsule --
        (masterLastSlot keep (t.terminatedPrototype, t.generationPrototype, t.statePrototype))
      ) &
        (elitismSlot -- generateIsland -- masterLastSlot) &
        (elitismSlot -- (masterLastSlot keep t.populationPrototype))

    val masterTask = MoleTask(master) set (exploredOutputs += islandPopulationPrototype.toArray)

    val generateInitialIslands =
      GenerateIslandTask(algorithm, sample, parallelism, islandPopulationPrototype) set (
        (inputs, outputs) += t.statePrototype,
        outputs += t.populationPrototype
      )

    val islandCapsule = Slot(MoleTask(selectPuzzle(island)))

    val slaveFist = EmptyTask() set ((inputs, outputs) += (t.statePrototype, islandPopulationPrototype))

    val slave = slaveFist -- (islandPopulationToPopulation, reassingRNGTask) -- islandCapsule -- fromIsland -- populationToOffspring

    val masterSlave = MasterSlave(
      generateInitialIslands,
      masterTask,
      slave,
      state = Seq(t.populationPrototype, t.statePrototype),
      slaves = parallelism
    )

    val firstTask = InitialStateTask(algorithm)

    val firstCapsule = Capsule(firstTask, strain = true)

    val last = EmptyTask() set ((inputs, outputs) += (t.populationPrototype, t.statePrototype))

    val puzzle =
      ((firstCapsule -- masterSlave) >| (Capsule(last, strain = true) when t.terminatedPrototype)) &
        (firstCapsule oo (islandCapsule, Block(t.populationPrototype, t.statePrototype)))

    val gaPuzzle = PuzzleContainer(puzzle, masterSlave.last, Vector(islandCapsule))

    gaPuzzle :: algorithm :: HNil
  }

}
