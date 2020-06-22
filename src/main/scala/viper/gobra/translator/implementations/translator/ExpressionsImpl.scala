package viper.gobra.translator.implementations.translator

import viper.gobra.ast.{internal => in}
import viper.gobra.translator.interfaces.{Collector, Context}
import viper.gobra.translator.interfaces.translator.Expressions
import viper.gobra.translator.util.ViperWriter.CodeWriter
import viper.gobra.util.Violation
import viper.silver.{ast => vpr}

class ExpressionsImpl extends Expressions {

  import viper.gobra.translator.util.ViperWriter.CodeLevel._

  override def finalize(col: Collector): Unit = {

  }

  override def translate(x: in.Expr)(ctx: Context): CodeWriter[vpr.Exp] = {

    val (pos, info, errT) = x.vprMeta

    def goE(e: in.Expr): CodeWriter[vpr.Exp] = translate(e)(ctx)
    def goT(t: in.Type): vpr.Type = ctx.typ.translate(t)(ctx)

    x match {

      case unfold: in.Unfolding =>
        for {
          a <- ctx.loc.predicateAccess(unfold.op)(ctx)
          e <- goE(unfold.in)
        } yield vpr.Unfolding(a, e)(pos, info, errT)

      case in.PureFunctionCall(func, args, typ) =>
        val arity = ctx.loc.arity(typ)(ctx)
        val resultType = ctx.loc.ttype(typ)(ctx)
        for {
          vArgss <- sequence(args map (ctx.loc.argument(_)(ctx)))
          app = vpr.FuncApp(func.name, vArgss.flatten)(pos, info, resultType, errT)
          res <- if (arity == 1) unit(app) else {
            copyResult(app) flatMap (z => ctx.loc.copyFromTuple(z, typ)(ctx))
          }
        } yield res

      case in.PureMethodCall(recv, meth, args, typ) =>
        val arity = ctx.loc.arity(typ)(ctx)
        val resultType = ctx.loc.ttype(typ)(ctx)
        for {
          vRecvs <- ctx.loc.argument(recv)(ctx)
          vArgss <- sequence(args map (ctx.loc.argument(_)(ctx)))
          app = vpr.FuncApp(meth.uniqueName, vRecvs ++ vArgss.flatten)(pos, info, resultType, errT)
          res <- if (arity == 1) unit(app) else {
            copyResult(app) flatMap (z => ctx.loc.copyFromTuple(z, typ)(ctx))
          }
        } yield res

      case in.DfltVal(t) => ctx.loc.defaultValue(t)(x)(ctx)
      case in.Tuple(args) => Violation.violation("Tuples expressions are not supported at this point in time")
      case p: in.Deref => ctx.loc.evalue(p)(ctx)
      case f: in.FieldRef => ctx.loc.evalue(f)(ctx)
      case r: in.Ref => ctx.loc.evalue(r)(ctx)

      case in.Negation(op) => for{o <- goE(op)} yield vpr.Not(o)(pos, info, errT)

      case in.EqCmp(l, r) => ctx.loc.equal(l, r)(x)(ctx)
      case in.UneqCmp(l, r) => ctx.loc.equal(l, r)(x)(ctx).map(vpr.Not(_)(pos, info, errT))
      case in.LessCmp(l, r) => for {vl <- goE(l); vr <- goE(r)} yield vpr.LtCmp(vl, vr)(pos, info, errT)
      case in.AtMostCmp(l, r) => for {vl <- goE(l); vr <- goE(r)} yield vpr.LeCmp(vl, vr)(pos, info, errT)
      case in.GreaterCmp(l, r) => for {vl <- goE(l); vr <- goE(r)} yield vpr.GtCmp(vl, vr)(pos, info, errT)
      case in.AtLeastCmp(l, r) => for {vl <- goE(l); vr <- goE(r)} yield vpr.GeCmp(vl, vr)(pos, info, errT)

      case in.And(l, r) => for {vl <- goE(l); vr <- goE(r)} yield vpr.And(vl, vr)(pos, info, errT)
      case in.Or(l, r) => for {vl <- goE(l); vr <- goE(r)} yield vpr.Or(vl, vr)(pos, info, errT)

      case in.Add(l, r) => for {vl <- goE(l); vr <- goE(r)} yield vpr.Add(vl, vr)(pos, info, errT)
      case in.Sub(l, r) => for {vl <- goE(l); vr <- goE(r)} yield vpr.Sub(vl, vr)(pos, info, errT)
      case in.Mul(l, r) => for {vl <- goE(l); vr <- goE(r)} yield vpr.Mul(vl, vr)(pos, info, errT)
      case in.Mod(l, r) => for {vl <- goE(l); vr <- goE(r)} yield vpr.Mod(vl, vr)(pos, info, errT)
      case in.Div(l, r) => for {vl <- goE(l); vr <- goE(r)} yield vpr.Div(vl, vr)(pos, info, errT)
      case in.Old(op) => for { o <- goE(op) } yield vpr.Old(o)(pos, info, errT)
      case in.Conditional(cond, thn, els, _) => for {vcond <- goE(cond); vthn <- goE(thn); vels <- goE(els)
                                                  } yield vpr.CondExp(vcond, vthn, vels)(pos, info, errT)

      case in.SequenceLength(op) => for {
        opT <- goE(op)
      } yield vpr.SeqLength(opT)(pos, info, errT)

      case in.SequenceContains(left, right) => for {
        leftT <- goE(left)
        rightT <- goE(right)
      } yield vpr.SeqContains(leftT, rightT)(pos, info, errT)

      case in.SequenceLiteral(typ, exprs) => for {
        exprsT <- sequence(exprs map goE)
        typT = goT(typ)
      } yield exprsT.length match {
        case 0 => vpr.EmptySeq(typT)(pos, info, errT)
        case _ => vpr.ExplicitSeq(exprsT)(pos, info, errT)
      }
        
      case in.RangeSequence(low, high) => for {
        lowT <- goE(low)
        highT <- goE(high)
      } yield vpr.RangeSeq(lowT, highT)(pos, info, errT)

      case in.SequenceAppend(left, right) => for {
        leftT <- goE(left)
        rightT <- goE(right)
      } yield vpr.SeqAppend(leftT, rightT)(pos, info, errT)

      case in.SequenceUpdate(seq, left, right) => for {
        seqT <- goE(seq)
        leftT <- goE(left)
        rightT <- goE(right)
      } yield vpr.SeqUpdate(seqT, leftT, rightT)(pos, info, errT)

      case in.SequenceIndex(left, right) => for {
        leftT <- goE(left)
        rightT <- goE(right)
      } yield vpr.SeqIndex(leftT, rightT)(pos, info, errT)

      case in.SequenceDrop(left, right) => for {
        leftT <- goE(left)
        rightT <- goE(right)
      } yield vpr.SeqDrop(leftT, rightT)(pos, info, errT)

      case in.SequenceTake(left, right) => for {
        leftT <- goE(left)
        rightT <- goE(right)
      } yield vpr.SeqTake(leftT, rightT)(pos, info, errT)

      case in.SetLiteral(typ, exprs) => for {
        exprsT <- sequence(exprs map goE)
        typT = goT(typ)
      } yield exprsT.length match {
        case 0 => vpr.EmptySet(typT)(pos, info, errT)
        case _ => vpr.ExplicitSet(exprsT)(pos, info, errT)
      }

      case in.SetConversion(exp) => for {
        expT <- goE(exp)
      } yield expT.typ match {
        case vpr.SeqType(_) => ctx.seqToSet.create(expT)
        case t => Violation.violation(s"conversion of type $t to sets is not implemented")
      }

      case in.Union(left, right) => for {
        leftT <- goE(left)
        rightT <- goE(right)
      } yield vpr.AnySetUnion(leftT, rightT)(pos, info, errT)

      case in.Intersection(left, right) => for {
        leftT <- goE(left)
        rightT <- goE(right)
      } yield vpr.AnySetIntersection(leftT, rightT)(pos, info, errT)

      case in.SetMinus(left, right) => for {
        leftT <- goE(left)
        rightT <- goE(right)
      } yield vpr.AnySetMinus(leftT, rightT)(pos, info, errT)

      case in.Subset(left, right) => for {
        leftT <- goE(left)
        rightT <- goE(right)
      } yield vpr.AnySetSubset(leftT, rightT)(pos, info, errT)

      case in.SetContains(left, right) => for {
        leftT <- goE(left)
        rightT <- goE(right)
      } yield vpr.AnySetContains(leftT, rightT)(pos, info, errT)

      case in.SetCardinality(exp) => for {
        expT <- goE(exp)
      } yield vpr.AnySetCardinality(expT)(pos, info, errT)

      case in.MultisetLiteral(typ, exprs) => for {
        exprsT <- sequence(exprs map goE)
        typT = goT(typ)
      } yield exprsT.length match {
        case 0 => vpr.EmptyMultiset(typT)(pos, info, errT)
        case _ => vpr.ExplicitMultiset(exprsT)(pos, info, errT)
      }

      case in.Multiplicity(left, right) => for {
        leftT <- goE(left)
        rightT <- goE(right)
      } yield rightT.typ match {
        case vpr.SeqType(_) => ctx.seqMultiplicity.create(leftT, rightT)
        case t => Violation.violation(s"translation for multiplicity with type $t is not implemented")
      }

      case l: in.Lit => ctx.loc.literal(l)(ctx)
      case v: in.Var => ctx.loc.evalue(v)(ctx)
    }
  }
}
