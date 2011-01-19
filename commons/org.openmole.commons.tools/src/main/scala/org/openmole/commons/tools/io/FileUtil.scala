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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openmole.commons.tools.io

import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import scala.collection.mutable.ListBuffer

object FileUtil {

  implicit val fileOrdering = new Ordering[File] {
    override def compare(left: File, right: File) = {
      left.getAbsolutePath.compareTo(right.getAbsolutePath)
    }
  }
  
  val DefaultBufferSize = 8 * 1024

  def lastModification(file: File): Long = {

    var lastModification = file.lastModified

    if (file.isDirectory) {
      val toProceed = new ListBuffer[File]
      toProceed += file

      while (!toProceed.isEmpty) {
        val f = toProceed.remove(0)

        if (f.lastModified > lastModification) {
          lastModification = f.lastModified
        }
        if (f.isDirectory) {
          for (child <- f.listFiles) {
            toProceed += child
          }
        }
      }
    }

    return lastModification
  }

  
  def listRecursive(file: File, filter: FileFilter): Iterable[File] = {
    val ret = new ListBuffer[File]
    applyRecursive(file, { f: File => if(filter.accept(f)) ret += f})
    ret
  }
  
  def applyRecursive(file: File, operation: File => Unit): Unit = {
    applyRecursive(file, operation, Set.empty)
  }

  def applyRecursive(file: File, operation: File => Unit, stopPath: Set[File]): Unit = {
    val toProceed = new ListBuffer[File]
    toProceed += file

    while (!toProceed.isEmpty) {
      val f = toProceed.remove(0)
      if (!stopPath.contains(f)) {
        operation(f)
        if (f.isDirectory()) {
          for (child <- f.listFiles) {
            toProceed += child
          }
        }
      }
    }
  }

  def dirContainsNoFileRecursive(dir: File): Boolean = {
    val toProceed = new ListBuffer[File]
    toProceed += dir

    while (!toProceed.isEmpty) {
      val f = toProceed.remove(0)
      for (sub <- f.listFiles) {
        if (sub.isFile) {
          return false
        } else if (sub.isDirectory) {
          toProceed += sub
        }
      }
    }
    return true
  }

  def recursiveDelete(dir: File): Boolean = {
    if(!dir.isDirectory) return dir.delete
    if (dir.exists) {
      val files = dir.listFiles
      for (i <- 0 until files.length) {
        if (files(i).isDirectory) recursiveDelete(files(i))
        else files(i).delete
      }
    }
    return dir.delete
  }
  
  @throws(classOf[IOException])
  def copy(fromF: File, toF: File) = {
    val toCopy = new ListBuffer[(File, File)]
    toCopy += ((fromF, toF))

    while (!toCopy.isEmpty) {
      val cur = toCopy.remove(0)
      val curFrom = cur._1
      val curTo = cur._2
      if (curFrom.isDirectory) {

        curTo.mkdir

        for (child <- curFrom.listFiles) {
          val to = new File(curTo, child.getName)
          toCopy += ((child, to))
        }
      } else {
        copyFile(curFrom, curTo)
      }
    }

  }
  
  @throws(classOf[IOException])
  def copyFile(fromF: File, toF: File): Unit = {
    val from = new FileInputStream(fromF).getChannel

    try {
      val to = new FileOutputStream(toF).getChannel
      try {
        copy(from, to)
      } finally {
        to.close
      }
    } finally {
      from.close
    }
  }
  
  @throws(classOf[IOException])
  def copy(source: FileChannel, destination: FileChannel): Unit = {
    destination.transferFrom(source, 0, source.size)
  }
  
  @throws(classOf[IOException])
  def copy(from: InputStream, to: OutputStream): Unit = {
    val buffer = new Array[Byte](DefaultBufferSize)
    Stream.continually(from.read(buffer)).takeWhile(_ != -1).foreach{ 
      count => to.write(buffer, 0, count)
    }
  }

  @throws(classOf[IOException])
  def copy(from: InputStream, to: OutputStream, maxRead: Int, timeout: Long) = {
    val buffer = new Array[Byte](maxRead)
    val executor = Executors.newSingleThreadExecutor
    val reader = new ReaderRunnable(buffer, from, maxRead)
    
    Stream.continually(
      {
        val futureRead = executor.submit(reader)
            
        try {
          futureRead.get(timeout, TimeUnit.MILLISECONDS)
        } catch {
          case (e: TimeoutException) =>
            futureRead.cancel(true)
            throw new IOException("Timout on reading " + maxRead + " bytes, read was longer than " + timeout + "ms.", e)
        }
      }).takeWhile(_ != -1).foreach{ 
      count => 
                
      val futureWrite = executor.submit(new WritterRunnable(buffer, to, count))

      try {
        futureWrite.get(timeout, TimeUnit.MILLISECONDS)
      } catch  {
        case (e: TimeoutException) =>
          futureWrite.cancel(true)
          throw new IOException("Timeout on writting " + count + " bytes, write was longer than " + timeout + " ms.", e);
      } 
    }     
  }

  def move(from: File, to: File) = {
    if (!from.renameTo(to)) {
      copy(from, to)
      from.delete
    }
  }
  
  def deleteDirectory(path: File): Boolean = {
    if( path.exists ) {		
      for(file <- path.listFiles) {
        if(file.isDirectory) {
          deleteDirectory(file)
        }
        else {
          file.delete
        }
      }
    }
    path.delete
  }
}
