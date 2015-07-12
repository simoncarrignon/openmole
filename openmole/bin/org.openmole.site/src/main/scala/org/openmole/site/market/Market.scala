/*
 * Copyright (C) 2015 Romain Reuillon
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

package org.openmole.site.market

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.merge.MergeStrategy
import org.openmole.console._
import org.openmole.core.tools.service.Logger
import org.openmole.site.Config
import org.openmole.tool.file._
import org.openmole.tool.hash._
import org.openmole.tool.tar._

import scala.util.{ Success, Failure, Try }

object Market extends Logger {

  lazy val githubMarket = Repository("https://github.com/openmole/openmole-market.git")

  object Tags {
    lazy val stochastic = Tag("Stochastic")
    lazy val simulation = Tag("Simulation")
    lazy val machineLearning = Tag("Machine Learning")
    lazy val R = Tag("R")
    lazy val data = Tag("Data")
    lazy val native = Tag("Native Code")
    lazy val netlogo = Tag("NetLogo")
    lazy val java = Tag("Java")
  }

  case class Tag(label: String)
  case class Repository(url: String)

  import Tags._

  case class MarketRepository(url: String, entries: MarketEntry*)
  case class MarketEntry(name: String, directory: String, files: Seq[String], tags: Seq[Tag] = Seq.empty)

  def entries = Seq(
    MarketRepository("https://github.com/openmole/openmole-market.git",
      MarketEntry("Pi Computation", "pi", Seq("pi.oms"), Seq(stochastic, simulation)),
      MarketEntry("Random Forest", "randomforest", Seq("learn.oms"), Seq(stochastic, machineLearning, native, data)),
      MarketEntry("Hello World in R", "R-hello", Seq("R.oms"), Seq(R, data, native)),
      MarketEntry("Fire in NetLogo", "fire", Seq("explore.oms"), Seq(netlogo, stochastic, simulation)),
      MarketEntry("Hello World in Java", "java-hello", Seq("explore.oms"), Seq(java))
    )
  )

}

import java.io.File

import Market._

case class DeployedMarketEntry(
  archive: String,
  entry: MarketEntry,
  readme: Option[String],
  codes: Seq[String])

class Market(entries: Seq[MarketRepository], destination: File) {

  lazy val console = new Console()

  def archiveDirectoryName = "market"

  def generate(cloneDirectory: File, testScript: Boolean = true): Seq[DeployedMarketEntry] = {
    val archiveDirectory = destination / archiveDirectoryName
    archiveDirectory.mkdirs()
    for {
      entry ← entries
      repository = update(entry, cloneDirectory)
      project ← entry.entries
      if !testScript || test(repository, project, entry.url)
    } yield {
      val fileName = s"${project.name}.tgz".replace(" ", "_")
      val archive = archiveDirectory / fileName
      val projectDirectory = repository / project.directory
      projectDirectory archiveCompress archive
      val readme = projectDirectory / "README.md"
      DeployedMarketEntry(
        s"$archiveDirectoryName/$fileName",
        project,
        readme.contentOption,
        project.files.map(f ⇒ projectDirectory / f content)
      )
    }
  }

  def test(clone: File, project: MarketEntry, repository: String): Boolean = {
    def testScript(script: File): Try[Unit] = {
      def engine = console.newREPL(ConsoleVariables.empty)
      Try(engine.compiled(script.content))
    }

    project.files.forall {
      file ⇒
        testScript(clone / project.directory / file) match {
          case Failure(e) ⇒
            Log.logger.log(Log.WARNING, s"Project ${project} of repository $repository has been excluded", e)
            false
          case Success(_) ⇒ true
        }
    }
  }

  def update(repository: MarketRepository, cloneDirectory: File): File = {
    val directory = cloneDirectory / repository.url.hash.toString

    directory / ".git" exists () match {
      case true ⇒
        val repo = Git.open(directory)
        val cmd = repo.pull()
        cmd.setStrategy(MergeStrategy.THEIRS)
        cmd.call()
      case false ⇒
        val command = Git.cloneRepository
        command.setDirectory(directory)
        command.setURI(repository.url)
        command.call()
    }

    directory
  }
}

