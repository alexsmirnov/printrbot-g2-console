package alexsmirnov.pbconsole.serial

trait Printer {
  
  def addStateListener(l: Port.StateEvent => Unit): Unit

  def sendLine(line: String): Unit

  def sendData(dataLine: Request): Unit

  def addReceiveListener(r: (CommandSource, Response) => Unit): Unit

  def addSendListener(l: Request => Unit): Unit
}