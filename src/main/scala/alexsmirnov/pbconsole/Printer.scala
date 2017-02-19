package alexsmirnov.pbconsole

import alexsmirnov.pbconsole.serial.Port

/**
 * response : {"r":{"sv":1},"f":[1,0,8]}
 * @author asmirnov
 *
 */
class Printer(port: Port) {
  
  def start() {
    port.run()
  }
  
  def stop() {
    port.close()
  }
  
}