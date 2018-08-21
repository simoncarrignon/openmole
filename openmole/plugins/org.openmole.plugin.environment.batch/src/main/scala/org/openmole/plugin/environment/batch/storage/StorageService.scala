/*
 * Copyright (C) 2012 reuillon
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

package org.openmole.plugin.environment.batch.storage

import java.io._

import org.openmole.core.communication.storage._
import org.openmole.core.preference.{ ConfigurationLocation, Preference }
import org.openmole.core.replication.{ ReplicaCatalog, ReplicationStorage }
import org.openmole.core.serializer._
import org.openmole.core.threadprovider.{ ThreadProvider, Updater }
import org.openmole.plugin.environment.batch.environment._
import org.openmole.plugin.environment.batch.refresh._
import org.openmole.tool.cache._
import org.openmole.tool.logger.JavaLogger
import squants.time.TimeConversions._

object StorageService extends JavaLogger {
  val DirRegenerate = ConfigurationLocation("StorageService", "DirRegenerate", Some(1 hours))

  implicit def replicationStorage[S](implicit services: BatchEnvironment.Services): ReplicationStorage[StorageService[S]] = new ReplicationStorage[StorageService[S]] {
    override def backgroundRmFile(storage: StorageService[S], path: String): Unit = StorageService.backgroundRmFile(storage, path)
    override def exists(storage: StorageService[S], path: String): Boolean = storage.exists(path)
    override def id(storage: StorageService[S]): String = storage.id
  }

  def apply[S](s: S)(implicit storageInterface: StorageInterface[S], environmentStorage: EnvironmentStorage[S]) = new StorageService[S](s)

  def backgroundRmFile(storageService: StorageService[_], path: String)(implicit services: BatchEnvironment.Services) = JobManager ! DeleteFile(storageService, path, false)
  def backgroundRmDir(storageService: StorageService[_], path: String)(implicit services: BatchEnvironment.Services) = JobManager ! DeleteFile(storageService, path, true)

}

class StorageService[S](val storage: S)(implicit storageInterface: StorageInterface[S], environmentStorage: EnvironmentStorage[S]) {

  override def toString: String = id

  def accessControl = storageInterface.accessControl(storage)

  def id = environmentStorage.id(storage)
  def environment = environmentStorage.environment(storage)

  def exists(path: String): Boolean = storageInterface.exists(storage, path)

  def rmDir(path: String): Unit = storageInterface.rmDir(storage, path)
  def rmFile(path: String): Unit = storageInterface.rmFile(storage, path)

  def makeDir(path: String): Unit = storageInterface.makeDir(storage, path)
  def child(path: String, name: String) = storageInterface.child(storage, path, name)

  def upload(src: File, dest: String, options: TransferOptions = TransferOptions.default) = storageInterface.upload(storage, src, dest, options)
  def download(src: String, dest: File, options: TransferOptions = TransferOptions.default) = storageInterface.download(storage, src, dest, options)

}
