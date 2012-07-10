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

package org.openmole.core.batch.replication

import com.db4o.Db4o
import com.db4o.ObjectContainer
import com.db4o.ObjectServer
import com.db4o.ObjectSet
import com.db4o.config.ClientServerConfiguration
import com.db4o.cs.Db4oClientServer
import com.db4o.defragment.Defragment
import com.db4o.defragment.DefragmentConfig
import com.db4o.ta.TransparentPersistenceSupport
import java.io.File
import org.openmole.misc.tools.io.FileUtil._
import org.openmole.misc.tools.service.LockRepository
import org.openmole.misc.tools.service.Logger
import org.openmole.core.batch.control.AccessToken
import org.openmole.core.batch.control.ServiceDescription
import org.openmole.core.batch.environment.BatchEnvironment
import org.openmole.core.batch.environment.BatchEnvironment._
import org.openmole.core.batch.environment.Storage
import org.openmole.core.batch.file.GZURIFile
import org.openmole.core.batch.file.URIFile
import org.openmole.misc.updater.Updater
import org.openmole.misc.workspace.ConfigurationLocation
import org.openmole.misc.hashservice.HashService._
import org.openmole.misc.workspace.Workspace
import scala.collection.JavaConversions._
import scala.collection.immutable.TreeMap
import scala.collection.immutable.TreeSet
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.mutable.ListBuffer

object ReplicaCatalog extends Logger {

  val GCUpdateInterval = new ConfigurationLocation("ReplicaCatalog", "GCUpdateInterval")
  val ObjectRepoLocation = new ConfigurationLocation("ReplicaCatalog", "ObjectRepoLocation")
  val NoAccessCleanTime = new ConfigurationLocation("ReplicaCatalog", "NoAccessCleanTime")
  val lockRepository = new LockRepository[String]

  Workspace += (GCUpdateInterval, "PT5M")
  Workspace += (ObjectRepoLocation, ".objectRepository.bin")
  Workspace += (NoAccessCleanTime, "P30D")

  lazy val dbFile = Workspace.file(Workspace.preference(ObjectRepoLocation))

  var _objectServer: Option[ObjectServer] = None

  def open = {
    val objRepo = Workspace.file(Workspace.preference(ObjectRepoLocation))

    if (objRepo.exists) defrag(objRepo)
    Db4oClientServer.openServer(dB4oConfiguration, objRepo.getAbsolutePath, 0)
  }

  def withClient[T](f: ObjectContainer ⇒ T) = {
    val container = objectServer.openClient
    try f(container)
    finally container.close
  }

  def objectServer: ObjectServer = synchronized {
    _objectServer match {
      case Some(server) ⇒
        if (server.ext.objectContainer.ext.isClosed) {
          val server = open
          _objectServer = Some(server)
          server
        } else server
      case None ⇒
        val server = open
        _objectServer = Some(server)
        server
    }
  }

  Updater.registerForUpdate(new ReplicaCatalogGC, Workspace.preferenceAsDurationInMs(GCUpdateInterval))

  private def getReplica(
    hash: String,
    storageDescription: ServiceDescription,
    authenticationKey: String)(implicit objectContainer: ObjectContainer): Option[Replica] = {
    val set = objectContainer.queryByExample(new Replica(null, storageDescription.description, hash, authenticationKey, null, null))
    if (!set.isEmpty) Some(set.get(0)) else None
  }

  private def getReplica(
    src: File,
    hash: String,
    storageDescription: ServiceDescription,
    authenticationKey: String)(implicit objectContainer: ObjectContainer): Option[Replica] = {
    val set = objectContainer.queryByExample(new Replica(src.getCanonicalPath, storageDescription.description, hash, authenticationKey, null, null))

    set.size match {
      case 0 ⇒ None
      case 1 ⇒ Some(set.get(0))
      case _ ⇒ Some(fix(set))
    }
  }

  def getReplica(
    src: File,
    storageDescription: ServiceDescription,
    authenticationKey: String)(implicit objectContainer: ObjectContainer): ObjectSet[Replica] =
    objectContainer.queryByExample(new Replica(src.getCanonicalPath, storageDescription.description, null, authenticationKey, null, null))

  def getReplica(
    storageDescription: ServiceDescription,
    authenticationKey: String)(implicit objectContainer: ObjectContainer): ObjectSet[Replica] =
    objectContainer.queryByExample(new Replica(null, storageDescription.description, null, authenticationKey, null, null))

  def inCatalog(
    storageDescription: ServiceDescription,
    authenticationKey: String)(implicit objectContainer: ObjectContainer): Set[String] =
    objectContainer.queryByExample[Replica](new Replica(null, storageDescription.description, null, authenticationKey, null, null)).map { _.destination }.toSet

  def inCatalog(
    src: Iterable[File],
    authenticationKey: String)(implicit objectContainer: ObjectContainer): Map[File, Set[ServiceDescription]] = {
    //transactionalOp( t => {
    if (src.isEmpty) return Map.empty

    val query = objectContainer.query
    query.constrain(classOf[Replica])

    query.descend("_authenticationKey").constrain(authenticationKey)
      .and(src.map { f ⇒ query.descend("_source").constrain(f.getCanonicalPath) }.reduceLeft((c1, c2) ⇒ c1.or(c2)))

    var ret = new HashMap[File, HashSet[ServiceDescription]]

    query.execute[Replica].foreach {
      replica ⇒ ret.getOrElseUpdate(replica.sourceFile, new HashSet[ServiceDescription]) += replica.storageDescription
    }

    ret.map { elt ⇒ (elt._1, elt._2.toSet) }.toMap
  }

  private def key(hash: String, storage: String, environmentKey: String): String = hash + "_" + storage + "_" + environmentKey
  private def key(r: Replica): String = key(r.hash, r.storageDescriptionString, r.authenticationKey)
  private def key(hash: String, storage: Storage): String = key(hash, storage.description.toString, storage.environment.authentication.key)

  def withSemaphore[T](key: String, objectContainer: ObjectContainer)(op: ⇒ T) = {
    //lockRepository.lock(key)
    logger.fine("Locking on " + key)
    objectContainer.ext.setSemaphore(key, Int.MaxValue)
    logger.fine("Locked on " + key)
    try op
    finally {
      logger.fine("Unlocked on " + key)
      //lockRepository.unlock(key)
      objectContainer.ext.releaseSemaphore(key)
    }
  }

  //Synchronization should be achieved outiside the replica for database caching and isolation purposes
  def uploadAndGet(
    src: File,
    srcPath: File,
    hash: String,
    storage: Storage,
    token: AccessToken): Replica = {
    //logger.fine("Looking for replica for" + srcPath.getAbsolutePath + "hash" + hash + ".")
    implicit val client = objectServer.openClient
    try withSemaphore(key(hash, storage), client) {
      val storageDescription = storage.description
      val authenticationKey = storage.environment.authentication.key

      val replica = getReplica(srcPath, hash, storageDescription, authenticationKey) match {
        case None ⇒
          //logger.fine("Not found Replica for" + srcPath.getAbsolutePath + " " + storage)
          getReplica(srcPath, storageDescription, authenticationKey).foreach { r ⇒ clean(r) }

          getReplica(hash, storageDescription, authenticationKey) match {
            case Some(sameContent) ⇒
              val replica = checkExists(sameContent, src, srcPath, hash, authenticationKey, storage, token)
              val newReplica = new Replica(srcPath.getCanonicalPath, storageDescription.description, hash, authenticationKey, replica.destination, replica.lastCheckExists)
              insert(newReplica)
              newReplica
            case None ⇒
              uploadAndInsert(src, srcPath, hash, authenticationKey, storage, token)
          }
        case Some(r) ⇒ {
          //logger.fine("Found Replica for " + srcPath.getAbsolutePath + " " + storage)
          client.activate(r, Int.MaxValue)
          checkExists(r, src, srcPath, hash, authenticationKey, storage, token)
        }
      }
      replica
    } finally client.close
  }

  private def checkExists(
    replica: Replica,
    src: File,
    srcPath: File,
    hash: String,
    authenticationKey: String,
    storage: Storage,
    token: AccessToken)(implicit objectContainer: ObjectContainer) =
    if (System.currentTimeMillis > (replica.lastCheckExists + Workspace.preferenceAsDurationInMs(BatchEnvironment.CheckFileExistsInterval))) {
      if (replica.destinationURIFile.exists(token)) {
        removeNoLock(replica)
        val toInsert = new Replica(replica.source, replica.storageDescriptionString, replica.hash, replica.authenticationKey, replica.destination, System.currentTimeMillis)
        insert(toInsert)
        toInsert
      } else {
        removeNoLock(replica)
        uploadAndInsert(src, srcPath, hash, authenticationKey, storage, token)
      }
    } else replica

  private def uploadAndInsert(
    src: File,
    srcPath: File,
    hash: String,
    authenticationKey: String,
    storage: Storage,
    token: AccessToken)(implicit objectContainer: ObjectContainer) = {
    val newFile = new GZURIFile(storage.persistentSpace(token).newFileInDir(hash, ".rep"))
    logger.fine("Uploading " + newFile)
    signalUpload(URIFile.copy(src, newFile, token), srcPath, storage)
    logger.fine("Uploaded " + newFile)
    val newReplica = new Replica(srcPath.getCanonicalPath, storage.description.description, hash, authenticationKey, newFile.location, System.currentTimeMillis)
    insert(newReplica)
    newReplica
  }

  private def fix(toFix: Iterable[Replica])(implicit objectContainer: ObjectContainer): Replica = {
    for (rep ← toFix.tail) objectContainer.delete(rep)
    toFix.head
  }

  def allReplicas(implicit objectContainer: ObjectContainer): Iterable[Replica] = {
    val q = objectContainer.query
    q.constrain(classOf[Replica])
    q.execute.toArray(Array[Replica]())
  }

  private def insert(replica: Replica)(implicit objectContainer: ObjectContainer) =
    try {
      objectContainer.store(replica)
      logger.fine("Insert " + replica.toString)
    } finally {
      objectContainer.commit
    }

  def remove(replica: Replica)(implicit objectContainer: ObjectContainer) =
    withSemaphore(key(replica), objectContainer) {
      removeNoLock(replica)
    }

  private def removeNoLock(replica: Replica)(implicit objectContainer: ObjectContainer) = {
    try objectContainer.delete(replica)
    finally objectContainer.commit
  }

  private def containsDestination(destination: String)(implicit objectContainer: ObjectContainer) = {
    val query = objectContainer.query
    query.descend("_destination").constrain(destination)
    !query.execute.isEmpty
  }

  def clean(replica: Replica)(implicit objectContainer: ObjectContainer) =
    withSemaphore(key(replica), objectContainer) {
      logger.fine("Cleaning replica " + replica.toString)
      removeNoLock(replica)

      if (!containsDestination(replica.destination)) URIFile.clean(new URIFile(replica.destination))
    }

  def cleanAll(implicit objectContainer: ObjectContainer) =
    for (rep ← allReplicas) clean(rep)

  private def dB4oConfiguration = {
    val configuration = Db4oClientServer.newServerConfiguration
    configuration.common.add(new TransparentPersistenceSupport)
    configuration.common.objectClass(classOf[Replica]).cascadeOnDelete(true)
    configuration.common.activationDepth(Int.MaxValue)

    configuration.common.objectClass(classOf[Replica]).objectField("_hash").indexed(true)
    configuration.common.objectClass(classOf[Replica]).objectField("_source").indexed(true)
    configuration.common.objectClass(classOf[Replica]).objectField("_storageDescription").indexed(true)
    configuration.common.objectClass(classOf[Replica]).objectField("_authenticationKey").indexed(true)
    configuration.common.objectClass(classOf[Replica]).objectField("_destination").indexed(true)

    configuration
  }

  private def defrag(db: File) = {
    val defragmentConfig = new DefragmentConfig(db.getAbsolutePath)
    defragmentConfig.forceBackupDelete(true)
    Defragment.defrag(defragmentConfig)
  }

}
