/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package runtime.taskmaster.common

import java.io.{IOException, InputStreamReader}
import java.util.regex.Pattern

import com.typesafe.scalalogging.LazyLogging

// https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/runtime/util/Hardware.java
object Hardware extends LazyLogging {

  private val LINUX_MEMORY_INFO_PATH = "/proc/meminfo"
  private val LINUX_MEMORY_REGEX: Pattern = Pattern.compile ("^MemTotal:\\s*(\\d+)\\s+kB$")

  /**
    * Gets the number of CPU cores (hardware contexts) that the JVM has access to.
    *
    * @return The number of CPU cores.
    */
  def getNumberCPUCores: Int =
    Runtime.getRuntime.availableProcessors


  import java.lang.management.ManagementFactory
  import java.lang.reflect.InvocationTargetException

  /**
    * Returns the size of the physical memory in bytes.
    *
    * @return the size of the physical memory in bytes or { @code -1}, if
    *                                                             the size could not be determined.
    */
  def getSizeOfPhysicalMemory: Long = { // first try if the JVM can directly tell us what the system memory is
    // this works only on Oracle JVMs
    try {
      val clazz = Class.forName("com.sun.management.OperatingSystemMXBean")
      val method = clazz.getMethod("getTotalPhysicalMemorySize")
      val operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean
      // someone may install different beans, so we need to check whether the bean
      // is in fact the sun management bean
      if (clazz.isInstance(operatingSystemMXBean)) return method.invoke(operatingSystemMXBean).asInstanceOf[Long]
    } catch {
      case e: ClassNotFoundException =>

      // this happens on non-Oracle JVMs, do nothing and use the alternative code paths
      case e@(_: NoSuchMethodException | _: IllegalAccessException | _: InvocationTargetException) =>
        logger.warn("Access to physical memory size: " + "com.sun.management.OperatingSystemMXBean incompatibly changed.", e)
    }
    // we now try the OS specific access paths
      OperatingSystem.get() match {
        case Linux =>
          getSizeOfPhysicalMemoryForLinux
        case Windows =>
          getSizeOfPhysicalMemoryForWindows
        case Mac =>
          getSizeOfPhysicalMemoryForMac
        case FreeBSD =>
          getSizeOfPhysicalMemoryForFreeBSD
        case Unknown =>
          logger.error("Cannot determine size of physical memory for unknown operating system")
          -1
        case _ =>
          logger.error("Unrecognized OS: " + OperatingSystem.get)
          -1
      }
  }


    import java.io.{BufferedReader, FileReader}
    /**
      * Returns the size of the physical memory in bytes on a Linux-based
      * operating system.
      *
      * @return the size of the physical memory in bytes or { @code -1}, if
      *         the size could not be determined
      */
    private def getSizeOfPhysicalMemoryForLinux: Long = {
      try {
        var line: String = null
        val lineReader = new BufferedReader(new FileReader(LINUX_MEMORY_INFO_PATH))
        while ({line = lineReader.readLine; line != null}) {
          val matcher = LINUX_MEMORY_REGEX.matcher(line)
          if (matcher.matches) {
            val totalMemory = matcher.group(1)
            lineReader.close()
            totalMemory.toLong * 1024L // Convert from kilobyte to byte
          }
        }
        logger.error("Cannot determine the size of the physical memory for Linux host (using '/proc/meminfo'). " + "Unexpected format.")
        -1
      } catch {
        case e: NumberFormatException =>
          logger.error("Cannot determine the size of the physical memory for Linux host (using '/proc/meminfo'). " + "Unexpected format.")
          -1
        case t: Throwable =>
          logger.error("Cannot determine the size of the physical memory for Linux host (using '/proc/meminfo') ", t)
          -1
      }
    }


  /**
    * Returns the size of the physical memory in bytes on a Mac OS-based
    * operating system
    *
    * @return the size of the physical memory in bytes or {-1}, if
    *         the size could not be determined
    */
  private def getSizeOfPhysicalMemoryForMac: Long = {
    var bi:BufferedReader = null
    try {
      val proc: Process = Runtime.getRuntime.exec("sysctl hw.memsize")
      bi = new BufferedReader(new InputStreamReader(proc.getInputStream))
      var line: String = null
      while ({line = bi.readLine; line != null}) {
        if (line.startsWith("hw.memsize")) {
          val memsize = line.split(":")(1)
            .trim()
            .toLong
          bi.close()
          proc.destroy()
          // return memsize
          memsize
        }
      }
    } catch {
      case t: Throwable =>
        logger.error("Cannot determine physical memory of machine for MacOS host", t)
        -1
    } finally {
      if (bi != null) {
        try {
          bi.close()
        } catch {case ignore:IOException =>}
      }
    }
    -1
  }

  /**
    * Returns the size of the physical memory in bytes on Windows.
    *
    * @return the size of the physical memory in bytes or {-1}, if
    *         the size could not be determined
    */
  private def getSizeOfPhysicalMemoryForWindows: Long = {
    var bi:BufferedReader = null
    try {
      val proc: Process = Runtime.getRuntime.exec("wmic memorychip get capacity")
      bi = new BufferedReader(new InputStreamReader(proc.getInputStream))
      var line: String = bi.readLine()
      if (line == null) {
        -1L
      }

      if (!line.startsWith("Capacity")) {
        -1L
      }

      var sizeOfPhysicalMemory: Long = 0

      while ({line = bi.readLine; line != null}) {
        if (!line.isEmpty) {
          line = line.replaceAll(" ", "")
          sizeOfPhysicalMemory += line.toLong
        }
      }
      sizeOfPhysicalMemory
    } catch {
      case t: Throwable =>
        logger.error("Cannot determine the size of the physical memory for Windows host " +
          "(using 'wmic memorychip')", t)
        -1
    } finally {
      if (bi != null) {
        try {
          bi.close()
        } catch {case ignore:IOException => }
      }
    }
    -1
  }

  private def getSizeOfPhysicalMemoryForFreeBSD: Long = {
    var bi:BufferedReader = null
    try {
      val proc: Process = Runtime.getRuntime.exec("sysctl hw.physmem")
      bi = new BufferedReader(new InputStreamReader(proc.getInputStream))
      var line: String = null
      while ({line = bi.readLine; line != null}) {
        if (line.startsWith("hw.physmem")) {
          val memsize = line.split(":")(1)
            .trim()
            .toLong
          bi.close()
          proc.destroy()
          // return memsize
          memsize
        }
      }
      logger.error("Cannot determine the size of the physical memory for FreeBSD host " +
        "(using 'sysctl hw.physmem').")
      -1
    } catch {
      case t: Throwable =>
        logger.error("Cannot determine the size of the physical memory for FreeBSD host " +
          "(using 'sysctl hw.physmem')", t)
        -1
    } finally {
      if (bi != null) {
        try {
          bi.close()
        } catch {case ignore:IOException =>}
      }
    }
    -1

  }

}
