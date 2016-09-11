package asyncsocket

import java.net.{SocketAddress, InetSocketAddress}
import java.nio.channels.{
  CompletionHandler,
  AsynchronousByteChannel,
  AsynchronousSocketChannel,
  AsynchronousChannelGroup
}
import java.nio.ByteBuffer
import scala.concurrent.{Promise, Future}

class PromiseCompletionHandler[T](p: Promise[T])(
    f: CompletionHandler[T, Any] => Unit)
    extends CompletionHandler[T, Any] {
  def completed(t: T, attachment: Any): Unit =
    p.success(t)

  def failed(e: Throwable, attachment: Any): Unit = p.failure(e)

}

object PromiseCompletionHandler {
  def makeFuture[T](f: CompletionHandler[T, Any] => Unit): Future[T] = {
    val p = Promise[T]
    val h = new PromiseCompletionHandler(p)(f)
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

object Helper {
  def asyncDoWhileThen[T, K, R](dst: T)(
      dothis: (T, CompletionHandler[R, Any]) => Unit)(whileTrue: R => Boolean)(
      then: R => K): Future[K] = {
    val p = Promise[K]
    val ch = new CompletionHandler[R, Any] {
      def completed(i: R, attachment: Any): Unit =
        if (!whileTrue(i)) p.success(then(i))
        else dothis(dst, this)
      def failed(e: Throwable, att: Any): Unit = p.failure(e)
    }
    dothis(dst, ch)
    p.future
  }
}
import Helper._

trait AsyncByteChannel {

  val channel: AsynchronousByteChannel

  def close = channel.close

  def readWhileThen[K](dst: ByteBuffer)(w: Integer => Boolean)(
      then: Integer => K) =
    asyncDoWhileThen[ByteBuffer, K, Integer](dst)(channel.read(_, null, _))(w)(
      then)

  def writeWhileThen[K](dst: ByteBuffer)(w: Integer => Boolean)(
      then: Integer => K) =
    asyncDoWhileThen[ByteBuffer, K, Integer](dst)(channel.write(_, null, _))(
      w)(then)

  def read(dst: ByteBuffer): Future[Int] =
    readWhileThen(dst)(_ => false)(_.toInt)

  def readFully(dst: ByteBuffer): Future[ByteBuffer] =
    readWhileThen(dst)(i => dst.hasRemaining && i >= 0)(_ => dst)

  def read(i: Int): Future[ByteBuffer] = {
    val bb = ByteBuffer.allocate(i)
    readFully(bb)
  }

  def write(dst: ByteBuffer): Future[Int] =
    writeWhileThen(dst)(_ => false)(_.toInt)

  def writeFully(dst: ByteBuffer): Future[ByteBuffer] =
    writeWhileThen(dst)(i => dst.hasRemaining)(_ => dst)

}
