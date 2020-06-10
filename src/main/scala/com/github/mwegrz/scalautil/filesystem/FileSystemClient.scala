package com.github.mwegrz.scalautil.filesystem

import java.io._
import java.net.URI
import java.nio.file.Paths
import java.time.Instant
import java.util.zip._

import com.github.mwegrz.app.Shutdownable
import Compression.Compression
import com.typesafe.config.Config
import org.apache.commons.pool.BasePoolableObjectFactory
import org.apache.commons.pool.impl.GenericObjectPool
import org.apache.commons.vfs2._
import org.apache.commons.vfs2.auth.StaticUserAuthenticator
import org.apache.commons.vfs2.impl.{ DefaultFileSystemConfigBuilder, StandardFileSystemManager }
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder

import scala.io.{ BufferedSource, Codec, Source }
import com.github.mwegrz.scalautil.{ ConfigOps, arm }

class FileSystemClient private (conf: Config) extends Shutdownable {
  import FileSystemClient._

  private val workingDir = Paths.get(System.getProperty("user.dir"))

  val baseUri: URI = {
    val u = new URI(conf.getString("url").replaceFirst("file://localhost/", "file:///"))
    u.getScheme match {
      case "file" =>
        val currFile = Paths.get(u.getPath.stripPrefix("/"))
        val newFile =
          (if (currFile.startsWith(".") || currFile.startsWith("src/main")) {
             workingDir.resolve(currFile)
           } else {
             currFile
           }).normalize
        if (u.toString.endsWith("/")) newFile.toUri
        else new URI(newFile.toUri.toString.stripSuffix("/"))
      case _ => u
    }
  }

  def basePath: String = baseUri.getPath.stripSuffix("/")

  private val opts = {
    val opts = new FileSystemOptions()
    val auth = {
      val username = conf.getString("username")
      val domain =
        if (username.contains('\\')) username.split("\\\\")(0) else ""
      val user =
        if (username.contains('\\')) username.split("\\\\")(1) else username
      val password = conf.getString("password")
      new StaticUserAuthenticator(domain, user, password)
    }

    val cf = DefaultFileSystemConfigBuilder.getInstance()
    cf.setUserAuthenticator(opts, auth)
    if (baseUri.getScheme == "sftp")
      SftpFileSystemConfigBuilder.getInstance().setUserDirIsRoot(opts, false)
    if (baseUri.getScheme == "ftp")
      FtpFileSystemConfigBuilder.getInstance().setUserDirIsRoot(opts, false)
    opts
  }

  private val pool = {
    val factory = new BasePoolableObjectFactory[StandardFileSystemManager] {
      override def makeObject: StandardFileSystemManager = {
        val m = VFS.getManager.asInstanceOf[StandardFileSystemManager]
        m.setBaseFile(m.resolveFile(baseUri.toString, opts))
        //m.setTemporaryFileStore(new DefaultFileReplicator(conf))
        m
      }

      override def destroyObject(obj: StandardFileSystemManager) = obj.close()

      override def passivateObject(obj: StandardFileSystemManager) = ()
    }
    val pool = new GenericObjectPool(factory)
    val maxActive = conf.getString("max-pool-size") match {
      case "auto" => 1
      case _      => conf.getInt("max-pool-size")
    }
    val maxIdle = maxActive
    pool.setMaxActive(maxActive)
    pool.setMaxIdle(maxIdle)

    val minIdle = conf.getString("min-idle") match {
      case "auto" => 1
      case _      => conf.getInt("max-idle")
    }

    pool.setMinIdle(minIdle)
    //p.setMinEvictableIdleTimeMillis(removeAfterIdleForMS)
    //p.setTimeBetweenEvictionRunsMillis(removeAfterIdleForMS)
    pool.setWhenExhaustedAction(GenericObjectPool.WHEN_EXHAUSTED_BLOCK)
    pool.setTestWhileIdle(true)
    pool
  }

  override def shutdown(): Unit = pool.close()

  def readBytes(path: String)(implicit
      compression: Option[Compression] = None,
      bufferSize: BufferSize = DefaultBufferSize
  ): Array[Byte] =
    withInputStream(path) { is =>
      val buf = new Array[Byte](bufferSize.value)
      val baos = new ByteArrayOutputStream()
      var nread = 0
      while ({
        nread = is.read(buf, 0, buf.length)
        nread
      } != -1) {
        baos.write(buf, 0, nread)
      }
      baos.toByteArray
    }(compression)

  def readString(path: String)(implicit
      encoding: Encoding = DefaultEncoding,
      compression: Option[Compression] = None,
      bufferSize: BufferSize = DefaultBufferSize
  ): String =
    readLines(path)(encoding, compression, bufferSize).mkString

  def readLines(path: String)(implicit
      encoding: Encoding = DefaultEncoding,
      compression: Option[Compression] = None,
      bufferSize: BufferSize = DefaultBufferSize
  ): List[String] =
    withFile(path) { file =>
      val is = {
        val is = file.getContent.getInputStream
        compression.fold(is)(c => decompressInputStream(is, compression))
      }
      val codec = Codec(encoding.value)
      def source: BufferedSource = {
        Source.createBufferedSource(
          is,
          bufferSize.value,
          () => source,
          () => is.close()
        )(codec)
      }
      source.getLines().toList
    }

  def writeBytes(path: String, bytes: Array[Byte])(implicit
      compression: Option[Compression] = None
  ): Unit =
    withOutputStream(path) { os =>
      val cos = compressOutputStream(os, compression)
      os.write(bytes)
    }(compression)

  def writeString(path: String, string: String)(implicit
      compression: Option[Compression] = None
  ): Unit =
    writeLines(path, List(string))(compression)

  def writeLines(path: String, lines: List[String])(implicit
      compression: Option[Compression] = None
  ): Unit =
    withOutputStream(path) { os =>
      val cos = compressOutputStream(os, compression)
      arm.using(new PrintWriter(cos)) { w =>
        lines foreach w.println
        w.flush()
      }
    }(compression)

  def exists(path: String): Boolean = withFile(path)(_.exists)

  def size(path: String): Long = withFile(path)(_.getContent.getSize)

  def lastModified(path: String): Instant =
    withFile(path)(a => Instant.ofEpochMilli(a.getContent.getLastModifiedTime))

  def setLastModified(path: String, instant: Instant): Unit =
    withFile(path)(_.getContent.setLastModifiedTime(instant.toEpochMilli))

  def info(path: String): FileInfo = withFile(path) { file => FileInfo.fromFileObject(file, file.getName) }

  def list(
      path: String,
      filter: Option[String => Boolean] = None,
      recursive: Boolean = true
  ): List[FileInfo] = {
    val selector = new FileSelector() {
      override def includeFile(fileInfo: FileSelectInfo): Boolean = {
        val fo = fileInfo.getFile
        //filter.fold[Boolean](true)(a => a(fo.getName.getBaseName))
        true
      }
      override def traverseDescendents(fileInfo: FileSelectInfo): Boolean =
        recursive
    }

    withFile(path) { dir =>
      if (dir.getType != FileType.FOLDER)
        throw new IllegalArgumentException(
          s"Path does not refer to a directory: ${dir.getName.getURI}"
        )
      val rootName = dir.getName
      //log.debug("Files found", "files" -> dir.getChildren.map(_.getName).mkString(","))
      //dir.findFiles(selector).toList map { file =>
      dir.getChildren.toList.map { file => FileInfo.fromFileObject(file, rootName) }
    }
  }

  /** Creates this file, if it does not exist.  Also creates any ancestor
    * folders which do not exist.  This method does nothing if the file
    * already exists and is a file.
    *
    * @param path file path
    * @throws FileSystemException If the file already exists with the wrong type, or the parent
    * folder is read-only, or on error creating this file or one of
    * its ancestors.
    */
  @throws[FileSystemException]
  def createFile(path: String): Unit = withFile(path) { file => file.createFile() }

  /** Creates this directory, if it does not exist. Also creates any ancestor directories which do not exist.
    * This method does nothing if the directory already exists.
    *
    * @param path directory path
    * @throws FileSystemException If the directoryalready exists with the wrong type, or the parent
    * directory is read-only, or on error creating this folder or one of
    * its ancestors.
    */
  @throws[FileSystemException]
  def createDirectory(path: String): Unit = withFile(path) { file => file.createFolder() }

  /** Deletes this file.  Does nothing if this file does not exist of if it is a
    * directory that has children.  Does not delete any descendents of this file.
    *
    * @return true if this object has been deleted
    * @throws FileSystemException If this file is a non-empty directory, or if this file is read-only,
    * or on error deleting this file.
    */
  @throws[FileSystemException]
  def delete(path: String): Boolean = withFile(path) { file => file.delete() }

  def changeDirectory(path: String): FileSystemClient = ???

  def inputStream(path: String): InputStream =
    new InputStream {
      private val manager = pool.borrowObject()
      private val file = manager.resolveFile(path, opts)
      private val is = file.getContent.getInputStream

      override def read(): Int = is.read()

      override def close(): Unit = {
        try {
          is.close()
        } finally {
          try {
            file.close()
          } finally {
            pool.returnObject(manager)
          }
        }
      }
    }

  def outputStream(path: String): OutputStream =
    new OutputStream {
      private val manager = pool.borrowObject()
      private val file = manager.resolveFile(path, opts)
      private val os = file.getContent.getOutputStream

      override def write(b: Int): Unit = os.write(b)

      override def close(): Unit = {
        try {
          os.close()
        } finally {
          try {
            file.close()
          } finally {
            pool.returnObject(manager)
          }
        }
      }
    }

  def withInputStream[A](
      path: String
  )(f: InputStream => A)(implicit compression: Option[Compression] = None): A =
    withFile(path) { file =>
      arm.using(file.getContent.getInputStream) { is =>
        val cIs = decompressInputStream(is, compression)
        f(cIs)
      }
    }

  def withOutputStream[A](
      path: String
  )(f: OutputStream => A)(implicit compression: Option[Compression] = None): A =
    withFile(path) { file =>
      arm.using(file.getContent.getOutputStream) { os =>
        val cOs = compressOutputStream(os, compression)
        f(cOs)
      }
    }

  private def withFile[A](path: String)(f: FileObject => A): A =
    withManager { manager => arm.using(manager.resolveFile(path, opts)) { file => f(file) } }

  private def withManager[A](f: FileSystemManager => A): A = {
    val manager = pool.borrowObject()
    try {
      f(manager)
    } finally {
      pool.returnObject(manager)
    }
  }
}

object FileSystemClient {
  val DefaultEncoding = Encoding("UTF-8")
  val DefaultBufferSize = BufferSize(8192)
  //private val DefaultConfigPath = "file-system"
  //private val defaultReference = ConfigFactory.defaultReference.getConfig(DefaultConfigPath)

  case class Encoding(value: String)
  case class BufferSize(value: Int)

  case class FileInfo(
      name: String,
      lastModified: Option[Instant],
      isDirectory: Boolean,
      size: Option[Long]
  )

  object FileInfo {
    def fromFileObject(file: FileObject, baseName: FileName): FileInfo = {
      val uri = file.getName
      def name = baseName.getRelativeName(uri)
      val lastModified = Option(Instant.ofEpochMilli(file.getContent.getLastModifiedTime))
      val isDirectory = file.getType == FileType.FOLDER
      val size = if (isDirectory) None else Option(file.getContent.getSize)
      FileInfo(name, lastModified, isDirectory, size)
    }
  }

  def apply(config: Config): FileSystemClient =
    new FileSystemClient(config.withReferenceDefaults("file-system-client"))

  private def decompressInputStream(
      is: InputStream,
      compression: Option[Compression]
  ): InputStream =
    compression.fold(is) {
      case Compression.Gzip => new GZIPInputStream(is)
      case Compression.Zip =>
        val zipIn = new ZipInputStream(is)
        zipIn.getNextEntry
        zipIn
    }

  //TODO finish
  private def compressOutputStream(
      os: OutputStream,
      compression: Option[Compression]
  ): OutputStream =
    compression.fold(os) {
      case Compression.Gzip => new GZIPOutputStream(os)
      case Compression.Zip =>
        ???
      //val zippedOs = new ZipOutputStream(os)
      //zippedOs.putNextEntry(new ZipEntry())
      //zippedOs
    }

  def copy(fromFs: FileSystemClient, fromPath: String, toFs: FileSystemClient, toPath: String)(
      bufferSize: BufferSize = DefaultBufferSize,
      fromCompression: Option[Compression] = None,
      toCompression: Option[Compression] = None
  ): Unit = {
    if (isLocalFileSystem(fromFs) && isLocalFileSystem(toFs) && fromCompression.isEmpty && toCompression.isEmpty) {
      // Do zero copy
      arm.using(
        new RandomAccessFile(fromFs.basePath + "/" + fromPath, "r").getChannel,
        new RandomAccessFile(toFs.basePath + "/" + toPath, "rw").getChannel
      ) { (from, to) =>
        val position = 0
        val count = from.size()
        from.transferTo(position, count, to)
      }
    } else {
      // Do buffered copy
      fromFs.withInputStream(fromPath) { is =>
        toFs.withOutputStream(toPath) { os =>
          val buf = new Array[Byte](bufferSize.value)
          var nread = 0
          while ({
            nread = is.read(buf, 0, buf.length)
            nread
          } != -1) {
            os.write(buf, 0, nread)
          }
        }(toCompression)
      }(fromCompression)
    }
  }

  private def isLocalFileSystem(fs: FileSystemClient): Boolean =
    fs.baseUri.getScheme == "file" && (fs.baseUri.getHost == null || fs.baseUri.getHost == "")
}
