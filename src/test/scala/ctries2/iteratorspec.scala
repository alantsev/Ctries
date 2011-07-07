package ctries2



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import collection._



class IteratorSpec extends WordSpec with ShouldMatchers {
  
  "A ctrie2 iterator" should {
    
    "work for an empty trie" in {
      val ct = new ConcurrentTrie
      val it = ct.iterator
      
      it.hasNext should equal (false)
      evaluating { it.next() } should produce [NoSuchElementException]
    }
    
    def nonEmptyIteratorCheck(sz: Int) {
      val ct = new ConcurrentTrie[Wrap, Int]
      for (i <- 0 until sz) ct.put(new Wrap(i), i)
      
      val it = ct.iterator
      val tracker = mutable.Map[Wrap, Int]()
      for (i <- 0 until sz) {
        assert(it.hasNext == true)
        tracker += it.next
      }
      
      it.hasNext should equal (false)
      evaluating { it.next() } should produce [NoSuchElementException]
      tracker.size should equal (sz)
      tracker should equal (ct)
    }
    
    "work for a 1 element trie" in {
      nonEmptyIteratorCheck(1)
    }
    
    "work for a 2 element trie" in {
      nonEmptyIteratorCheck(2)
    }
    
    "work for a 3 element trie" in {
      nonEmptyIteratorCheck(3)
    }
    
    "work for a 5 element trie" in {
      nonEmptyIteratorCheck(5)
    }
    
    "work for a 10 element trie" in {
      nonEmptyIteratorCheck(10)
    }
    
    "work for a 20 element trie" in {
      nonEmptyIteratorCheck(20)
    }
    
    "work for a 50 element trie" in {
      nonEmptyIteratorCheck(50)
    }
     
    "work for a 100 element trie" in {
      nonEmptyIteratorCheck(100)
    }
    
    "work for a 1k element trie" in {
      nonEmptyIteratorCheck(1000)
    }
    
    "work for a 5k element trie" in {
      nonEmptyIteratorCheck(5000)
    }
    
    "work for a 75k element trie" in {
      nonEmptyIteratorCheck(75000)
    }
    
    "work for a 250k element trie" in {
      nonEmptyIteratorCheck(500000)
    }
    
    def nonEmptyCollideCheck(sz: Int) {
      val ct = new ConcurrentTrie[DumbHash, Int]
      for (i <- 0 until sz) ct.put(new DumbHash(i), i)
      
      val it = ct.iterator
      val tracker = mutable.Map[DumbHash, Int]()
      for (i <- 0 until sz) {
        assert(it.hasNext == true)
        tracker += it.next
      }
      
      it.hasNext should equal (false)
      evaluating { it.next() } should produce [NoSuchElementException]
      tracker.size should equal (sz)
      tracker should equal (ct)
    }
    
    "work for colliding hashcodes, 2 element trie" in {
      nonEmptyCollideCheck(2)
    }
    
    "work for colliding hashcodes, 3 element trie" in {
      nonEmptyCollideCheck(3)
    }
    
    "work for colliding hashcodes, 5 element trie" in {
      nonEmptyCollideCheck(5)
    }
    
    "work for colliding hashcodes, 10 element trie" in {
      nonEmptyCollideCheck(10)
    }
    
    "work for colliding hashcodes, 100 element trie" in {
      nonEmptyCollideCheck(100)
    }
    
    "work for colliding hashcodes, 500 element trie" in {
      nonEmptyCollideCheck(500)
    }
    
    "work for colliding hashcodes, 5k element trie" in {
      nonEmptyCollideCheck(5000)
    }
    
    def assertEqual(a: Map[Wrap, Int], b: Map[Wrap, Int]) {
      if (a != b) {
        println(a.size + " vs " + b.size)
        // println(a)
        // println(b)
        // println(a.toSeq.sortBy((x: (Wrap, Int)) => x._1.i))
        // println(b.toSeq.sortBy((x: (Wrap, Int)) => x._1.i))
      }
      assert(a == b)
    }
    
    "be consistent when taken with concurrent modifications" in {
      val sz = 25000
      val W = 25
      val S = 10
      val checks = 5
      val ct = new ConcurrentTrie[Wrap, Int]
      for (i <- 0 until sz) ct.put(new Wrap(i), i)
      
      class Modifier extends Thread {
        override def run() {
          for (i <- 0 until sz) ct.putIfAbsent(new Wrap(i), i) match {
            case Some(_) => ct.remove(new Wrap(i))
            case None => 
          }
        }
      }
      
      def consistentIteration(ct: ConcurrentTrie[Wrap, Int], checks: Int) {
        class Iter extends Thread {
          override def run() {
            val snap = ct.readOnlySnapshot()
            val initial = mutable.Map[Wrap, Int]()
            for (kv <- snap) initial += kv
            
            for (i <- 0 until checks) {
              assertEqual(snap.iterator.toMap, initial)
            }
          }
        }
        
        val iter = new Iter
        iter.start()
        iter.join()
      }
      
      val threads = for (_ <- 0 until W) yield new Modifier
      threads.foreach(_.start())
      for (_ <- 0 until S) consistentIteration(ct, checks)
      threads.foreach(_.join())
    }
    
    "be consistent with a concurrent removal with a well defined order" in {
      val sz = 450000
      val sgroupsize = 40
      val sgroupnum = 20
      val removerslowdown = 50
      val ct = new ConcurrentTrie[Wrap, Int]
      for (i <- 0 until sz) ct.put(new Wrap(i), i)
      
      class Remover extends Thread {
        override def run() {
          for (i <- 0 until sz) {
            assert(ct.remove(new Wrap(i)) == Some(i))
            for (i <- 0 until removerslowdown) ct.get(new Wrap(i)) // slow down, mate
          }
          //println("done removing")
        }
      }
      
      def consistentIteration(it: Iterator[(Wrap, Int)]) = {
        class Iter extends Thread {
          override def run() {
            val elems = it.toSeq
            if (elems.nonEmpty) {
              val minelem = elems.minBy((x: (Wrap, Int)) => x._1.i)._1.i
              assert(elems.forall(_._1.i >= minelem))
            }
          }
        }
        new Iter
      }
      
      val remover = new Remover
      remover.start()
      for (_ <- 0 until sgroupnum) {
        val iters = for (_ <- 0 until sgroupsize) yield consistentIteration(ct.iterator)
        iters.foreach(_.start())
        iters.foreach(_.join())
      }
      //println("done with iterators")
      remover.join()
    }
    
    "be consistent with a concurrent insertion with a well defined order" in {
      val sz = 450000
      val sgroupsize = 30
      val sgroupnum = 30
      val inserterslowdown = 50
      val ct = new ConcurrentTrie[Wrap, Int]
      
      class Inserter extends Thread {
        override def run() {
          for (i <- 0 until sz) {
            assert(ct.put(new Wrap(i), i) == None)
            for (i <- 0 until inserterslowdown) ct.get(new Wrap(i)) // slow down, mate
          }
          //println("done inserting")
        }
      }
      
      def consistentIteration(it: Iterator[(Wrap, Int)]) = {
        class Iter extends Thread {
          override def run() {
            val elems = it.toSeq
            if (elems.nonEmpty) {
              val maxelem = elems.maxBy((x: (Wrap, Int)) => x._1.i)._1.i
              assert(elems.forall(_._1.i <= maxelem))
            }
          }
        }
        new Iter
      }
      
      val inserter = new Inserter
      inserter.start()
      for (_ <- 0 until sgroupnum) {
        val iters = for (_ <- 0 until sgroupsize) yield consistentIteration(ct.iterator)
        iters.foreach(_.start())
        iters.foreach(_.join())
      }
      //println("done with iterators")
      inserter.join()
    }
    
    "work on a yet unevaluated snapshot" in {
      val sz = 50000
      val ct = new ConcurrentTrie[Wrap, Int]
      for (i <- 0 until sz) ct.update(new Wrap(i), i)
      
      val snap = ct.snapshot()
      val it = snap.iterator
      
      while (it.hasNext) it.next()
    }
    
  }
  
}
