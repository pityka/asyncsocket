package asyncsocket

import java.net.{SocketAddress, InetSocketAddress}
import java.nio.channels.{
  CompletionHandler,
  AsynchronousByteChannel,
  AsynchronousSocketChannel,
  AsynchronousChannelGroup
}
import java.nio.ByteBuffer
import scala.concurrent.{Promise, Future, ExecutionContext}

class PromiseCompletionHandler[T](p: Promise[T])
    extends CompletionHandler[T, Any] {
  def completed(t: T, attachment: Any): Unit = p.success(t)
  def failed(e: Throwable, attachment: Any): Unit = p.failure(e)

}

object PromiseCompletionHandler {
  def makeFuture[T](f: CompletionHandler[T, Any] => Unit): Future[T] = {
    val p = Promise[T]
    val h = new PromiseCompletionHandler(p)
    f(h)
    p.future
  }
}
import PromiseCompletionHandler._

class AsyncSocket(val channel: AsynchronousSocketChannel)
    extends AsyncByteChannel {

  def connect(address: SocketAddress): Future[Void] =
    makeFuture[Void](channel.connect(address, null, _))

  def connect(s: String, p: Int): Future[Void] =
    connect(new InetSocketAddress(s, p))

  def shutdownInput = channel.shutdownInput
  def shutdownOutput = channel.shutdownOutput

  def this() = this(AsynchronousSocketChannel.open)

  def this(cg: AsynchronousChannelGroup) =
    this(AsynchronousSocketChannel.open(cg))

}

trait AsyncByteChannel {

  val channel: AsynchronousByteChannel

  def close = channel.close

  def read(dst: ByteBuffer): Future[Integer] =
    makeFuture[Integer](channel.read(dst, null, _))

  def readFully(dst: ByteBuffer)(
      implicit ec: ExecutionContext): Future[ByteBuffer] = {
    if (!dst.hasRemaining) Future.successful(dst)
    else
      read(dst).flatMap { i =>
        if (i >= 0) readFully(dst)
        else Future.successful(dst)
      }

  }

  def read(i: Int)(implicit ec: ExecutionContext): Future[ByteBuffer] = {
    val bb = ByteBuffer.allocate(i)
    readFully(bb)
  }

  def write(src: ByteBuffer): Future[Integer] =
    makeFuture[Integer](channel.write(src, null, _))

  def writeFully(src: ByteBuffer)(
      implicit ec: ExecutionContext): Future[ByteBuffer] =
    if (!src.hasRemaining) Future.successful(src)
    else write(src).flatMap(i => writeFully(src))

}
