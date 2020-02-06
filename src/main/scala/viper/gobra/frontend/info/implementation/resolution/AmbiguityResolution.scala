package viper.gobra.frontend.info.implementation.resolution

import viper.gobra.ast.frontend._
import viper.gobra.ast.frontend.{AstPattern => ap}
import viper.gobra.frontend.info.base.SymbolTable.isDefinedInScope
import viper.gobra.frontend.info.base.{SymbolTable => st}
import viper.gobra.frontend.info.implementation.TypeInfoImpl

trait AmbiguityResolution { this: TypeInfoImpl =>

  def resolveConversionOrUnaryCall[T](n: PConversionOrUnaryCall)
                                     (conversion: (PIdnUse, PExpression) => T)
                                     (unaryCall: (PIdnUse, PExpression) => T): Option[T] =
    if (pointsToType(n.base))      Some(conversion(n.base, n.arg))
    else if (pointsToData(n.base)) Some(unaryCall(n.base, n.arg))
    else None

  def resolveSelectionOrMethodExpr[T](n: PSelectionOrMethodExpr)
                                     (selection: (PIdnUse, PIdnUse) => T)
                                     (methodExp: (PIdnUse, PIdnUse) => T): Option[T] =
    if (pointsToType(n.base))      Some(methodExp(n.base, n.id))
    else if (pointsToData(n.base)) Some(selection(n.base, n.id))
    else None

  def resolveMPredOrMethExprOrRecvCall[T](n: PMPredOrMethRecvOrExprCall)
                                         (predOrMethCall: (PIdnUse, PIdnUse, Vector[PExpression]) => T)
                                         (predOrMethExprCall: (PIdnUse, PIdnUse, Vector[PExpression]) => T)
                                         : Option[T] =
    if (pointsToType(n.base))      Some(predOrMethCall(n.base, n.id, n.args))
    else if (pointsToData(n.base)) Some(predOrMethExprCall(n.base, n.id, n.args))
    else None

  def isDef[T](n: PIdnUnk): Boolean = !isDefinedInScope(sequentialDefenv.in(n), serialize(n))




  def typeOrExpr(n: PExpressionOrType): Either[PExpression, PType] = {
    n match {
      // Ambiguous nodes
      case n: PNamedOperand =>
        if (pointsToType(n.id)) Right(n) else Left(n)

      case n: PDeref =>
        if (typeOrExpr(n.base).isLeft) Left(n) else Right(n)

      case n: PDot => Left(n) // TODO: when we support packages, then it can also be the type defined in a package

      // Otherwise just expression or type
      case n: PExpression => Left(n)
      case n: PType => Right(n)
    }
  }


  def resolve(n: PExpressionOrType): Option[ap.Pattern] = n match {

    case n: PNamedOperand =>
      entity(n.id) match {
        case s: st.NamedType => Some(ap.NamedType(n.id, s))
        case s: st.Variable => Some(ap.LocalVariable(n.id, s))
        case s: st.Function => Some(ap.Function(n.id, s))
        case s: st.Predicate => Some(ap.Predicate(n.id, s))
        case _ => None
      }

    case n: PDeref =>
      typeOrExpr(n.base) match {
        case Left(expr) => Some(ap.Deref(expr))
        case Right(typ) => Some(ap.PointerType(typ))
      }

    case n: PDot =>
      (typeOrExpr(n.base), tryDotLookup(n.base, n.id)) match {

        case (Left(base), Some((s: st.StructMember, path))) => Some(ap.FieldSelection(base, n.id, path, s))
        case (Left(base), Some((s: st.Method, path))) => Some(ap.ReceivedMethod(base, n.id, path, s))
        case (Left(base), Some((s: st.Predicate, path))) => Some(ap.ReceivedPredicate(base, n.id, path, s))

        case (Right(base), Some((s: st.Method, path))) => Some(ap.MethodExpr(base, n.id, path, s))
        case (Right(base), Some((s: st.Predicate, path))) => Some(ap.PredicateExpr(base, n.id, path, s))

        case _ => None
      }

    case n: PInvoke =>
      typeOrExpr(n.base) match {
        case Right(t) => Some(ap.Conversion(t, n.args))
        case Left(e) =>
          resolve(e) match {
            case Some(p: ap.FunctionKind) => Some(ap.FunctionCall(p, n.args))
            case Some(p: ap.PredicateKind) => Some(ap.PredicateCall(p, n.args))
            case _ => None
          }
      }

      // unknown pattern
    case _ => None
  }


}
