package viper.gobra.translator.util

import viper.gobra.translator.interfaces.Collector
import viper.gobra.translator.interfaces.translator.Generator
import viper.gobra.util.Computation
import viper.silver.{ast => vpr}

object PrimitiveGenerator {

  trait PrimitiveGenerator[-A, +R] extends (A => R) with Generator {
    def gen(v: A): (R, Vector[vpr.Member])
  }

  def simpleGenerator[A, R](f: A => (R, Vector[vpr.Member])): PrimitiveGenerator[A, R] = new PrimitiveGenerator[A, R] {

    var generatedMember: Set[vpr.Member] = Set.empty
    val cashedGen: A => (R, Vector[vpr.Member]) = Computation.cashedComputation(f)

    override def gen(v: A): (R, Vector[vpr.Member]) = cashedGen(v)

    override def finalize(col: Collector): Unit = generatedMember foreach col.addMember

    override def apply(v: A): R = {
      val (r, ss) = gen(v)
      generatedMember ++= ss.toSet
      r
    }
  }
}