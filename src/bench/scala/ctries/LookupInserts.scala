package ctries



import Global._
import scala.testing.Benchmark



object LookupInsertsCHM extends Benchmark {
  import java.util.concurrent.ConcurrentHashMap
  val chm = new ConcurrentHashMap[Elem, Elem]
  
  def run() {
    val p = par.get
    val step = sz / p
    
    val ins = for (i <- 0 until p) yield new Worker(chm, i, step)
    
    for (i <- ins) i.start()
    for (i <- ins) i.join()
  }
  
  class Worker(chm: ConcurrentHashMap[Elem, Elem], n: Int, step: Int) extends Thread {
    override def run() {
      val ratio = lookupratio.get
      var i = 0
      while (i < sz) {
        // do an insert
        chm.put(elems(i), elems(i))
        i += 1
        
        // do some lookups
        var j = 0
        while (j < ratio) {
          chm.get(elems(j * 0x9e3775cd % i))
          j += 1
        }
      }
    }
  }
}
