package alexsmirnov.pbconsole

import alexsmirnov.pbconsole.serial.Port

class Printer(portName: String) {
  val port = Port(portName.r)
  
  def start() {
    port.run()
  }
  def stop() {
    port.close()
  }
  
}