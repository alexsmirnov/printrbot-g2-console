package alexsmirnov.pbconsole

import org.scalatest.FlatSpec

class JobTest extends FlatSpec {
  
  "gcode parser" should "detect G0 command" in {
    println(Job.MoveParams.findAllMatchIn("X12Y32 Z11.5 E15 F2000").map{m => m.subgroups.mkString(",")}.mkString("[","],[","]"))
  }
}